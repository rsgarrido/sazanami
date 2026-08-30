#include <jni.h>

#include <media/NdkMediaCodec.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <limits>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

namespace {

    constexpr int64_t kCodecTimeoutUs = 2'000;
    constexpr int kSparseSamplePointCount = 64;
    constexpr int kOutputBuffersPerSample = 2;
    constexpr int kMaxDecodeIterationsPerSample = 32;
    constexpr int kMinimumSuccessfulSamplePoints = 4;
    constexpr auto kMaxAnalysisDuration = std::chrono::milliseconds(2'000);
    constexpr int kMaxSampledFramesPerBuffer = 512;
    constexpr float kWaveformRmsWeight = 0.78F;
    constexpr float kWaveformContrastExponent = 1.35F;
    constexpr float kWaveformVisibleEnergyFloor = 0.04F;

    constexpr int kEncodingPcm16Bit = 2;
    constexpr int kEncodingPcm8Bit = 3;
    constexpr int kEncodingPcmFloat = 4;
    constexpr int kEncodingPcm24BitPacked = 21;
    constexpr int kEncodingPcm32Bit = 22;

    constexpr const char* kKeyMime = "mime";
    constexpr const char* kKeyDurationUs = "durationUs";
    constexpr const char* kKeyChannelCount = "channel-count";
    constexpr const char* kKeySampleRate = "sample-rate";
    constexpr const char* kKeyPcmEncoding = "pcm-encoding";

    struct DecodeSession {
        std::atomic_bool cancelled{false};
    };

    struct MediaExtractorDeleter {
        void operator()(AMediaExtractor* extractor) const {
            if (extractor != nullptr) {
                AMediaExtractor_delete(extractor);
            }
        }
    };

    struct MediaFormatDeleter {
        void operator()(AMediaFormat* format) const {
            if (format != nullptr) {
                AMediaFormat_delete(format);
            }
        }
    };

    class MediaCodecOwner {
    public:
        explicit MediaCodecOwner(AMediaCodec* codec) : codec_(codec) {}

        ~MediaCodecOwner() {
            if (codec_ != nullptr) {
                if (started_) {
                    AMediaCodec_stop(codec_);
                }
                AMediaCodec_delete(codec_);
            }
        }

        MediaCodecOwner(const MediaCodecOwner&) = delete;
        MediaCodecOwner& operator=(const MediaCodecOwner&) = delete;

        AMediaCodec* get() const { return codec_; }
        void markStarted() { started_ = true; }

    private:
        AMediaCodec* codec_ = nullptr;
        bool started_ = false;
    };

    using MediaExtractorPtr = std::unique_ptr<AMediaExtractor, MediaExtractorDeleter>;
    using MediaFormatPtr = std::unique_ptr<AMediaFormat, MediaFormatDeleter>;

    struct PcmFormat {
        int channelCount = 1;
        int sampleRate = 0;
        int encoding = kEncodingPcm16Bit;
    };

    int bytesPerSample(int encoding) {
        switch (encoding) {
            case kEncodingPcm8Bit:
                return 1;
            case kEncodingPcm16Bit:
                return 2;
            case kEncodingPcm24BitPacked:
                return 3;
            case kEncodingPcm32Bit:
            case kEncodingPcmFloat:
                return 4;
            default:
                return 0;
        }
    }

    template <typename T>
    T readLittleEndian(const uint8_t* source) {
        T value{};
        std::memcpy(&value, source, sizeof(T));
#if __BYTE_ORDER__ == __ORDER_BIG_ENDIAN__
        if constexpr (sizeof(T) == 2) {
        const auto raw = static_cast<uint16_t>(value);
        value = static_cast<T>(__builtin_bswap16(raw));
    } else if constexpr (sizeof(T) == 4) {
        uint32_t raw{};
        std::memcpy(&raw, &value, sizeof(raw));
        raw = __builtin_bswap32(raw);
        std::memcpy(&value, &raw, sizeof(raw));
    }
#endif
        return value;
    }

    double readAmplitude(const uint8_t* source, int encoding) {
        double amplitude = 0.0;
        switch (encoding) {
            case kEncodingPcm8Bit: {
                const int sample = static_cast<int>(*source) - 128;
                amplitude = std::abs(sample) / 128.0;
                break;
            }
            case kEncodingPcm16Bit: {
                const int16_t sample = readLittleEndian<int16_t>(source);
                amplitude = std::abs(static_cast<double>(sample)) / 32768.0;
                break;
            }
            case kEncodingPcm24BitPacked: {
                int32_t sample = static_cast<int32_t>(source[0]) |
                                 (static_cast<int32_t>(source[1]) << 8) |
                                 (static_cast<int32_t>(source[2]) << 16);
                if ((sample & 0x00800000) != 0) {
                    sample |= static_cast<int32_t>(0xFF000000);
                }
                amplitude = std::abs(static_cast<double>(sample)) / 8388608.0;
                break;
            }
            case kEncodingPcm32Bit: {
                const int32_t sample = readLittleEndian<int32_t>(source);
                amplitude = std::abs(static_cast<double>(sample)) / 2147483648.0;
                break;
            }
            case kEncodingPcmFloat: {
                const float sample = readLittleEndian<float>(source);
                amplitude = std::isfinite(sample) ? std::abs(static_cast<double>(sample)) : 0.0;
                break;
            }
            default:
                break;
        }
        return std::clamp(amplitude, 0.0, 1.0);
    }

    PcmFormat readPcmFormat(AMediaFormat* format, const PcmFormat& fallback) {
        if (format == nullptr) {
            return fallback;
        }

        PcmFormat result = fallback;
        int32_t value = 0;
        if (AMediaFormat_getInt32(format, kKeyChannelCount, &value) && value > 0) {
            result.channelCount = value;
        }
        if (AMediaFormat_getInt32(format, kKeySampleRate, &value) && value > 0) {
            result.sampleRate = value;
        }
        if (AMediaFormat_getInt32(format, kKeyPcmEncoding, &value)) {
            result.encoding = value;
        }
        return result;
    }

    struct BufferEnergy {
        float rms = 0.0F;
        float peakWeighted = 0.0F;
    };

    std::optional<BufferEnergy> calculateBufferEnergy(
            const uint8_t* buffer,
            size_t size,
            const PcmFormat& format
    ) {
        if (buffer == nullptr || size == 0 || format.channelCount <= 0) {
            return std::nullopt;
        }

        const int bytesPerPcmSample = bytesPerSample(format.encoding);
        if (bytesPerPcmSample <= 0) {
            return std::nullopt;
        }

        const size_t bytesPerFrame = static_cast<size_t>(bytesPerPcmSample) *
                                     static_cast<size_t>(format.channelCount);
        if (bytesPerFrame == 0 || size < bytesPerFrame) {
            return std::nullopt;
        }

        const size_t frameCount = size / bytesPerFrame;
        const size_t sampleStride = std::max<size_t>(
                1,
                frameCount / static_cast<size_t>(kMaxSampledFramesPerBuffer)
        );

        double squaredTotal = 0.0;
        double fourthPowerTotal = 0.0;
        uint64_t sampledFrames = 0;
        for (size_t frameIndex = 0; frameIndex < frameCount; frameIndex += sampleStride) {
            const size_t frameOffset = frameIndex * bytesPerFrame;
            if (frameOffset + bytesPerFrame > size) {
                break;
            }

            double channelTotal = 0.0;
            for (int channel = 0; channel < format.channelCount; ++channel) {
                const size_t sampleOffset = frameOffset +
                                            static_cast<size_t>(channel) * static_cast<size_t>(bytesPerPcmSample);
                channelTotal += readAmplitude(buffer + sampleOffset, format.encoding);
            }
            const double amplitude = channelTotal / static_cast<double>(format.channelCount);
            const double squared = amplitude * amplitude;
            squaredTotal += squared;
            fourthPowerTotal += squared * squared;
            sampledFrames += 1;
        }

        if (sampledFrames == 0) {
            return std::nullopt;
        }

        const double divisor = static_cast<double>(sampledFrames);
        const double rms = std::sqrt(std::max(0.0, squaredTotal / divisor));
        // The fourth-power mean reacts to attacks/transients more strongly than RMS,
        // without letting one clipped PCM sample dominate the whole waveform point.
        const double peakWeighted = std::pow(
                std::max(0.0, fourthPowerTotal / divisor),
                0.25
        );
        if (!std::isfinite(rms) || !std::isfinite(peakWeighted)) {
            return std::nullopt;
        }

        return BufferEnergy{
                static_cast<float>(std::clamp(rms, 0.0, 1.0)),
                static_cast<float>(std::clamp(peakWeighted, 0.0, 1.0))
        };
    }

    float blendWaveformEnergy(float rms, float peakWeighted) {
        constexpr float kPeakWeightedWeight = 1.0F - kWaveformRmsWeight;
        return std::clamp(
                rms * kWaveformRmsWeight + peakWeighted * kPeakWeightedWeight,
                0.0F,
                1.0F
        );
    }

    std::vector<float> interpolateAndNormalizeSamples(
            std::vector<float> samples,
            int barCount
    ) {
        if (samples.empty() || barCount <= 0) {
            return {};
        }

        std::vector<size_t> validIndices;
        validIndices.reserve(samples.size());
        for (size_t index = 0; index < samples.size(); ++index) {
            if (std::isfinite(samples[index])) {
                validIndices.push_back(index);
            }
        }
        const size_t minimumSuccessfulSamples = std::min(
                samples.size(),
                static_cast<size_t>(kMinimumSuccessfulSamplePoints)
        );
        if (validIndices.size() < minimumSuccessfulSamples) {
            return {};
        }

        const size_t firstValid = validIndices.front();
        std::fill(samples.begin(), samples.begin() + static_cast<std::ptrdiff_t>(firstValid), samples[firstValid]);

        for (size_t validIndex = 1; validIndex < validIndices.size(); ++validIndex) {
            const size_t left = validIndices[validIndex - 1];
            const size_t right = validIndices[validIndex];
            const float leftValue = samples[left];
            const float rightValue = samples[right];
            const size_t distance = right - left;
            for (size_t index = left + 1; index < right; ++index) {
                const float progress = static_cast<float>(index - left) / static_cast<float>(distance);
                samples[index] = leftValue + (rightValue - leftValue) * progress;
            }
        }

        const size_t lastValid = validIndices.back();
        std::fill(samples.begin() + static_cast<std::ptrdiff_t>(lastValid + 1), samples.end(), samples[lastValid]);

        std::vector<float> result(static_cast<size_t>(barCount), 0.0F);
        if (barCount == 1 || samples.size() == 1) {
            result[0] = samples.front();
        } else {
            const double sourceMaximum = static_cast<double>(samples.size() - 1);
            const double targetMaximum = static_cast<double>(barCount - 1);
            for (int index = 0; index < barCount; ++index) {
                const double sourcePosition = static_cast<double>(index) * sourceMaximum / targetMaximum;
                const auto left = static_cast<size_t>(std::floor(sourcePosition));
                const auto right = std::min(left + 1, samples.size() - 1);
                const float progress = static_cast<float>(sourcePosition - static_cast<double>(left));
                result[static_cast<size_t>(index)] =
                        samples[left] + (samples[right] - samples[left]) * progress;
            }
        }

        const float maximum = *std::max_element(result.begin(), result.end());
        if (maximum <= 0.0F || !std::isfinite(maximum)) {
            return result;
        }
        for (float& amplitude : result) {
            const float normalized = std::clamp(amplitude / maximum, 0.0F, 1.0F);
            if (normalized <= 0.0F) {
                amplitude = 0.0F;
                continue;
            }

            // A gentle power curve separates the mid/high energy values common in mastered
            // music. Keep a tiny non-zero floor so quieter material remains legible in the
            // small seekbar canvases without turning silence into a visible bar.
            const float contrasted = std::pow(normalized, kWaveformContrastExponent);
            amplitude = std::clamp(
                    kWaveformVisibleEnergyFloor +
                    (1.0F - kWaveformVisibleEnergyFloor) * contrasted,
                    0.0F,
                    1.0F
            );
        }
        return result;
    }


    std::vector<int> buildSparseSampleOrder(int samplePointCount) {
        if (samplePointCount <= 0) {
            return {};
        }
        if (samplePointCount == 1) {
            return {0};
        }

        std::vector<int> order;
        order.reserve(static_cast<size_t>(samplePointCount));
        std::vector<bool> added(static_cast<size_t>(samplePointCount), false);
        auto addIndex = [&](int index) {
            if (index >= 0 && index < samplePointCount && !added[static_cast<size_t>(index)]) {
                added[static_cast<size_t>(index)] = true;
                order.push_back(index);
            }
        };

        addIndex(0);
        addIndex(samplePointCount - 1);

        struct Interval {
            int left;
            int right;
        };
        std::vector<Interval> intervals;
        intervals.push_back({0, samplePointCount - 1});
        size_t cursor = 0;
        while (cursor < intervals.size() && order.size() < static_cast<size_t>(samplePointCount)) {
            const Interval interval = intervals[cursor++];
            if (interval.right - interval.left <= 1) {
                continue;
            }
            const int middle = interval.left + (interval.right - interval.left) / 2;
            addIndex(middle);
            intervals.push_back({interval.left, middle});
            intervals.push_back({middle, interval.right});
        }

        for (int index = 0; index < samplePointCount; ++index) {
            addIndex(index);
        }
        return order;
    }

    std::optional<float> decodeSampleWindow(
            DecodeSession* session,
            AMediaExtractor* extractor,
            AMediaCodec* codec,
            PcmFormat* pcmFormat
    ) {
        bool inputEnded = false;
        double squaredRmsTotal = 0.0;
        double squaredPeakWeightedTotal = 0.0;
        int outputBufferCount = 0;

        for (int iteration = 0; iteration < kMaxDecodeIterationsPerSample; ++iteration) {
            if (session->cancelled.load(std::memory_order_relaxed)) {
                return std::nullopt;
            }

            if (!inputEnded) {
                const ssize_t inputIndex = AMediaCodec_dequeueInputBuffer(codec, kCodecTimeoutUs);
                if (inputIndex >= 0) {
                    size_t inputCapacity = 0;
                    uint8_t* inputBuffer = AMediaCodec_getInputBuffer(
                            codec,
                            static_cast<size_t>(inputIndex),
                            &inputCapacity
                    );
                    if (inputBuffer == nullptr || inputCapacity == 0) {
                        return std::nullopt;
                    }

#if __ANDROID_API__ >= 28
                    const ssize_t expectedSampleSize = AMediaExtractor_getSampleSize(extractor);
                if (expectedSampleSize > 0 &&
                    static_cast<size_t>(expectedSampleSize) > inputCapacity) {
                    return std::nullopt;
                }
#endif
                    const ssize_t sampleSize = AMediaExtractor_readSampleData(
                            extractor,
                            inputBuffer,
                            inputCapacity
                    );
                    if (sampleSize < 0) {
                        if (AMediaCodec_queueInputBuffer(
                                codec,
                                static_cast<size_t>(inputIndex),
                                0,
                                0,
                                0,
                                AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM
                        ) != AMEDIA_OK) {
                            return std::nullopt;
                        }
                        inputEnded = true;
                    } else {
                        const int64_t sampleTimeUs = std::max<int64_t>(
                                0,
                                AMediaExtractor_getSampleTime(extractor)
                        );
                        if (AMediaCodec_queueInputBuffer(
                                codec,
                                static_cast<size_t>(inputIndex),
                                0,
                                static_cast<size_t>(sampleSize),
                                static_cast<uint64_t>(sampleTimeUs),
                                0
                        ) != AMEDIA_OK) {
                            return std::nullopt;
                        }
                        AMediaExtractor_advance(extractor);
                    }
                } else if (inputIndex < AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                    return std::nullopt;
                }
            }

            AMediaCodecBufferInfo bufferInfo{};
            const ssize_t outputIndex = AMediaCodec_dequeueOutputBuffer(
                    codec,
                    &bufferInfo,
                    kCodecTimeoutUs
            );
            if (outputIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormatPtr outputFormat(AMediaCodec_getOutputFormat(codec));
                *pcmFormat = readPcmFormat(outputFormat.get(), *pcmFormat);
                continue;
            }
            if (outputIndex >= 0) {
                const bool outputEnded =
                        (bufferInfo.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0;
                if (bufferInfo.size > 0) {
                    size_t outputCapacity = 0;
                    uint8_t* outputBuffer = AMediaCodec_getOutputBuffer(
                            codec,
                            static_cast<size_t>(outputIndex),
                            &outputCapacity
                    );
                    if (outputBuffer == nullptr) {
                        AMediaCodec_releaseOutputBuffer(codec, static_cast<size_t>(outputIndex), false);
                        return std::nullopt;
                    }
                    const auto energy = calculateBufferEnergy(
                            outputBuffer,
                            static_cast<size_t>(bufferInfo.size),
                            *pcmFormat
                    );
                    if (energy.has_value()) {
                        squaredRmsTotal +=
                                static_cast<double>(energy->rms) * static_cast<double>(energy->rms);
                        squaredPeakWeightedTotal +=
                                static_cast<double>(energy->peakWeighted) *
                                static_cast<double>(energy->peakWeighted);
                        outputBufferCount += 1;
                    }
                }

                if (AMediaCodec_releaseOutputBuffer(
                        codec,
                        static_cast<size_t>(outputIndex),
                        false
                ) != AMEDIA_OK) {
                    return std::nullopt;
                }

                if (outputBufferCount >= kOutputBuffersPerSample || outputEnded) {
                    if (outputBufferCount == 0) {
                        return std::nullopt;
                    }
                    const double divisor = static_cast<double>(outputBufferCount);
                    return blendWaveformEnergy(
                            static_cast<float>(std::sqrt(squaredRmsTotal / divisor)),
                            static_cast<float>(std::sqrt(squaredPeakWeightedTotal / divisor))
                    );
                }
            } else if (outputIndex != AMEDIACODEC_INFO_TRY_AGAIN_LATER &&
                       outputIndex != AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
                return std::nullopt;
            }
        }

        if (outputBufferCount == 0) {
            return std::nullopt;
        }
        const double divisor = static_cast<double>(outputBufferCount);
        return blendWaveformEnergy(
                static_cast<float>(std::sqrt(squaredRmsTotal / divisor)),
                static_cast<float>(std::sqrt(squaredPeakWeightedTotal / divisor))
        );
    }

    bool startsWithAudioMime(const char* mime) {
        constexpr char prefix[] = "audio/";
        return mime != nullptr && std::strncmp(mime, prefix, sizeof(prefix) - 1) == 0;
    }

    std::vector<float> decodeWaveform(
            DecodeSession* session,
            int fd,
            int64_t offset,
            int64_t length,
            int64_t fallbackDurationUs,
            int barCount
    ) {
        if (session == nullptr || fd < 0 || offset < 0 || length <= 0 || barCount <= 0 || barCount > 4096) {
            return {};
        }

        MediaExtractorPtr extractor(AMediaExtractor_new());
        if (!extractor || AMediaExtractor_setDataSourceFd(extractor.get(), fd, offset, length) != AMEDIA_OK) {
            return {};
        }

        MediaFormatPtr inputFormat;
        std::string mimeType;
        const size_t trackCount = AMediaExtractor_getTrackCount(extractor.get());
        for (size_t trackIndex = 0; trackIndex < trackCount; ++trackIndex) {
            MediaFormatPtr candidate(AMediaExtractor_getTrackFormat(extractor.get(), trackIndex));
            if (!candidate) {
                continue;
            }
            const char* candidateMime = nullptr;
            if (!AMediaFormat_getString(candidate.get(), kKeyMime, &candidateMime) ||
                !startsWithAudioMime(candidateMime)) {
                continue;
            }
            if (AMediaExtractor_selectTrack(extractor.get(), trackIndex) != AMEDIA_OK) {
                return {};
            }
            mimeType = candidateMime;
            inputFormat = std::move(candidate);
            break;
        }

        if (!inputFormat || mimeType.empty()) {
            return {};
        }

        int64_t durationUs = fallbackDurationUs;
        int64_t formatDurationUs = 0;
        if (AMediaFormat_getInt64(inputFormat.get(), kKeyDurationUs, &formatDurationUs) &&
            formatDurationUs > 0) {
            durationUs = formatDurationUs;
        }
        durationUs = std::max<int64_t>(1, durationUs);

        MediaCodecOwner codec(AMediaCodec_createDecoderByType(mimeType.c_str()));
        if (codec.get() == nullptr) {
            return {};
        }
        if (AMediaCodec_configure(codec.get(), inputFormat.get(), nullptr, nullptr, 0) != AMEDIA_OK) {
            return {};
        }
        if (AMediaCodec_start(codec.get()) != AMEDIA_OK) {
            return {};
        }
        codec.markStarted();

        PcmFormat pcmFormat = readPcmFormat(inputFormat.get(), PcmFormat{});
        const int samplePointCount = std::min(barCount, kSparseSamplePointCount);
        std::vector<float> samples(
                static_cast<size_t>(samplePointCount),
                std::numeric_limits<float>::quiet_NaN()
        );
        const auto startedAt = std::chrono::steady_clock::now();

        const std::vector<int> sampleOrder = buildSparseSampleOrder(samplePointCount);
        bool decoderHasProducedOutput = false;
        for (const int sampleIndex : sampleOrder) {
            if (session->cancelled.load(std::memory_order_relaxed)) {
                return {};
            }
            if (std::chrono::steady_clock::now() - startedAt > kMaxAnalysisDuration) {
                break;
            }

            if (decoderHasProducedOutput) {
                const int64_t maximumTargetUs = std::max<int64_t>(0, durationUs - 1);
                const int64_t targetUs = samplePointCount == 1
                                         ? 0
                                         : static_cast<int64_t>(
                                                 static_cast<long double>(maximumTargetUs) *
                                                 static_cast<long double>(sampleIndex) /
                                                 static_cast<long double>(samplePointCount - 1)
                                         );
                if (AMediaExtractor_seekTo(
                        extractor.get(),
                        targetUs,
                        AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC
                ) != AMEDIA_OK) {
                    continue;
                }
                if (AMediaCodec_flush(codec.get()) != AMEDIA_OK) {
                    return {};
                }
            } else if (sampleIndex != 0) {
                // The first decode deliberately starts at the beginning. MediaCodec documents
                // extra codec-specific-data handling when flush happens before first output.
                continue;
            }

            const auto sample = decodeSampleWindow(
                    session,
                    extractor.get(),
                    codec.get(),
                    &pcmFormat
            );
            if (sample.has_value()) {
                samples[static_cast<size_t>(sampleIndex)] = *sample;
                decoderHasProducedOutput = true;
            }
        }

        return interpolateAndNormalizeSamples(std::move(samples), barCount);
    }

    jfloatArray toJFloatArray(JNIEnv* env, const std::vector<float>& values) {
        if (values.empty() || values.size() > static_cast<size_t>(std::numeric_limits<jsize>::max())) {
            return nullptr;
        }
        auto result = env->NewFloatArray(static_cast<jsize>(values.size()));
        if (result == nullptr) {
            return nullptr;
        }
        env->SetFloatArrayRegion(
                result,
                0,
                static_cast<jsize>(values.size()),
                values.data()
        );
        return result;
    }

    std::mutex gSessionsMutex;
    std::unordered_map<jlong, std::shared_ptr<DecodeSession>> gSessions;
    std::atomic<jlong> gNextSessionHandle{1};

    std::shared_ptr<DecodeSession> getSession(jlong handle) {
        std::lock_guard<std::mutex> lock(gSessionsMutex);
        const auto entry = gSessions.find(handle);
        return entry == gSessions.end() ? nullptr : entry->second;
    }

    jlong createSession() {
        try {
            const jlong handle = gNextSessionHandle.fetch_add(1, std::memory_order_relaxed);
            auto session = std::make_shared<DecodeSession>();
            std::lock_guard<std::mutex> lock(gSessionsMutex);
            gSessions[handle] = std::move(session);
            return handle;
        } catch (...) {
            return 0;
        }
    }

    void destroySession(jlong handle) {
        std::lock_guard<std::mutex> lock(gSessionsMutex);
        gSessions.erase(handle);
    }

}  // namespace

namespace {

    constexpr char kNativeAudioBridgeClassName[] =
            "io/github/rsgarrido/sazanami/player/nativeaudio/NativeAudioBridge";

    jint nativeApiVersion(JNIEnv*, jobject) {
        return 1;
    }

    jlong nativeCreateSession(JNIEnv*, jobject) {
        return createSession();
    }

    void nativeCancelSession(JNIEnv*, jobject, jlong handle) {
        if (const auto session = getSession(handle); session != nullptr) {
            session->cancelled.store(true, std::memory_order_relaxed);
        }
    }

    jfloatArray nativeDecodeWaveform(
            JNIEnv* env,
            jobject,
            jlong handle,
            jint fd,
            jlong offset,
            jlong length,
            jlong fallbackDurationUs,
            jint barCount
    ) {
        const auto session = getSession(handle);
        if (session == nullptr) {
            return nullptr;
        }

        try {
            const auto values = decodeWaveform(
                    session.get(),
                    static_cast<int>(fd),
                    static_cast<int64_t>(offset),
                    static_cast<int64_t>(length),
                    static_cast<int64_t>(fallbackDurationUs),
                    static_cast<int>(barCount)
            );
            return toJFloatArray(env, values);
        } catch (...) {
            return nullptr;
        }
    }

    void nativeDestroySession(JNIEnv*, jobject, jlong handle) {
        destroySession(handle);
    }

    JNINativeMethod kNativeMethods[] = {
            {
                    const_cast<char*>("nativeApiVersion"),
                    const_cast<char*>("()I"),
                    reinterpret_cast<void*>(nativeApiVersion)
            },
            {
                    const_cast<char*>("nativeCreateSession"),
                    const_cast<char*>("()J"),
                    reinterpret_cast<void*>(nativeCreateSession)
            },
            {
                    const_cast<char*>("nativeCancelSession"),
                    const_cast<char*>("(J)V"),
                    reinterpret_cast<void*>(nativeCancelSession)
            },
            {
                    const_cast<char*>("nativeDecodeWaveform"),
                    const_cast<char*>("(JIJJJI)[F"),
                    reinterpret_cast<void*>(nativeDecodeWaveform)
            },
            {
                    const_cast<char*>("nativeDestroySession"),
                    const_cast<char*>("(J)V"),
                    reinterpret_cast<void*>(nativeDestroySession)
            }
    };

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    if (vm == nullptr) {
        return JNI_ERR;
    }

    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK || env == nullptr) {
        return JNI_ERR;
    }

    jclass bridgeClass = env->FindClass(kNativeAudioBridgeClassName);
    if (bridgeClass == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        return JNI_ERR;
    }

    const jint registerResult = env->RegisterNatives(
            bridgeClass,
            kNativeMethods,
            static_cast<jint>(sizeof(kNativeMethods) / sizeof(kNativeMethods[0]))
    );
    env->DeleteLocalRef(bridgeClass);

    if (registerResult != JNI_OK) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
