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
#include <string>
#include <unordered_map>
#include <vector>

namespace {

    constexpr int64_t kCodecTimeoutUs = 10'000;
    constexpr int kMaxIdleIterations = 1'000;
    constexpr auto kMaxAnalysisDuration = std::chrono::seconds(45);
    constexpr int kMaxSampledFramesPerBuffer = 512;
    constexpr int64_t kMicrosPerSecond = 1'000'000;

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

    class WaveformAccumulator {
    public:
        WaveformAccumulator(int barCount, int64_t durationUs)
                : squaredTotals_(static_cast<size_t>(barCount), 0.0),
                  sampleCounts_(static_cast<size_t>(barCount), 0),
                  durationUs_(std::max<int64_t>(1, durationUs)) {}

        bool add(
                const uint8_t* buffer,
                size_t size,
                int64_t presentationTimeUs,
                const PcmFormat& format
        ) {
            if (buffer == nullptr || size == 0 || format.channelCount <= 0 || format.sampleRate <= 0) {
                return false;
            }

            const int bytesPerPcmSample = bytesPerSample(format.encoding);
            if (bytesPerPcmSample <= 0) {
                return false;
            }

            const size_t bytesPerFrame = static_cast<size_t>(bytesPerPcmSample) *
                                         static_cast<size_t>(format.channelCount);
            if (bytesPerFrame == 0 || size < bytesPerFrame) {
                return false;
            }

            const size_t frameCount = size / bytesPerFrame;
            const size_t sampleStride = std::max<size_t>(
                    1,
                    frameCount / static_cast<size_t>(kMaxSampledFramesPerBuffer)
            );

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
                const int64_t frameTimeUs = std::max<int64_t>(0, presentationTimeUs) +
                                            static_cast<int64_t>(frameIndex) * kMicrosPerSecond /
                                            static_cast<int64_t>(format.sampleRate);
                const long double bucketPosition =
                        static_cast<long double>(frameTimeUs) * static_cast<long double>(squaredTotals_.size()) /
                        static_cast<long double>(durationUs_);
                const auto bucket = static_cast<size_t>(std::clamp<long double>(
                        bucketPosition,
                        0.0L,
                        static_cast<long double>(squaredTotals_.size() - 1)
                ));

                squaredTotals_[bucket] += amplitude * amplitude;
                sampleCounts_[bucket] += 1;
            }
            return true;
        }

        std::vector<float> normalizedAmplitudes() const {
            if (squaredTotals_.empty()) {
                return {};
            }

            bool hasSamples = false;
            std::vector<float> amplitudes(squaredTotals_.size(), 0.0F);
            float maximum = 0.0F;
            for (size_t index = 0; index < amplitudes.size(); ++index) {
                const uint64_t count = sampleCounts_[index];
                if (count == 0) {
                    continue;
                }
                hasSamples = true;
                const double meanSquare = squaredTotals_[index] / static_cast<double>(count);
                const float rms = static_cast<float>(std::sqrt(std::max(0.0, meanSquare)));
                amplitudes[index] = std::isfinite(rms) ? std::max(0.0F, rms) : 0.0F;
                maximum = std::max(maximum, amplitudes[index]);
            }

            if (!hasSamples) {
                return {};
            }
            if (maximum <= 0.0F) {
                return amplitudes;
            }

            for (float& amplitude : amplitudes) {
                amplitude = std::clamp(amplitude / maximum, 0.0F, 1.0F);
            }
            return amplitudes;
        }

    private:
        std::vector<double> squaredTotals_;
        std::vector<uint64_t> sampleCounts_;
        int64_t durationUs_;
    };

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
        WaveformAccumulator accumulator(barCount, durationUs);
        bool inputEnded = false;
        bool outputEnded = false;
        int idleIterations = 0;
        const auto startedAt = std::chrono::steady_clock::now();

        while (!outputEnded) {
            if (session->cancelled.load(std::memory_order_relaxed)) {
                return {};
            }
            if (std::chrono::steady_clock::now() - startedAt > kMaxAnalysisDuration) {
                return {};
            }

            bool madeProgress = false;
            if (!inputEnded) {
                const ssize_t inputIndex = AMediaCodec_dequeueInputBuffer(codec.get(), kCodecTimeoutUs);
                if (inputIndex < AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                    return {};
                }
                if (inputIndex >= 0) {
                    size_t inputCapacity = 0;
                    uint8_t* inputBuffer = AMediaCodec_getInputBuffer(
                            codec.get(),
                            static_cast<size_t>(inputIndex),
                            &inputCapacity
                    );
                    if (inputBuffer == nullptr || inputCapacity == 0) {
                        return {};
                    }

#if __ANDROID_API__ >= 28
                    const ssize_t expectedSampleSize = AMediaExtractor_getSampleSize(extractor.get());
                if (expectedSampleSize > 0 &&
                    static_cast<size_t>(expectedSampleSize) > inputCapacity) {
                    return {};
                }
#endif
                    const ssize_t sampleSize = AMediaExtractor_readSampleData(
                            extractor.get(),
                            inputBuffer,
                            inputCapacity
                    );
                    if (sampleSize < 0) {
                        if (AMediaCodec_queueInputBuffer(
                                codec.get(),
                                static_cast<size_t>(inputIndex),
                                0,
                                0,
                                0,
                                AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM
                        ) != AMEDIA_OK) {
                            return {};
                        }
                        inputEnded = true;
                    } else {
                        const int64_t sampleTimeUs = std::max<int64_t>(
                                0,
                                AMediaExtractor_getSampleTime(extractor.get())
                        );
                        if (AMediaCodec_queueInputBuffer(
                                codec.get(),
                                static_cast<size_t>(inputIndex),
                                0,
                                static_cast<size_t>(sampleSize),
                                static_cast<uint64_t>(sampleTimeUs),
                                0
                        ) != AMEDIA_OK) {
                            return {};
                        }
                        AMediaExtractor_advance(extractor.get());
                    }
                    madeProgress = true;
                }
            }

            AMediaCodecBufferInfo bufferInfo{};
            const ssize_t outputIndex = AMediaCodec_dequeueOutputBuffer(
                    codec.get(),
                    &bufferInfo,
                    kCodecTimeoutUs
            );
            if (outputIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormatPtr outputFormat(AMediaCodec_getOutputFormat(codec.get()));
                pcmFormat = readPcmFormat(outputFormat.get(), pcmFormat);
                madeProgress = true;
            } else if (outputIndex >= 0) {
                if (bufferInfo.size > 0) {
                    size_t outputCapacity = 0;
                    uint8_t* outputBuffer = AMediaCodec_getOutputBuffer(
                            codec.get(),
                            static_cast<size_t>(outputIndex),
                            &outputCapacity
                    );
                    if (outputBuffer == nullptr) {
                        return {};
                    }
                    // On API <=35, NDK documents outputCapacity/offset as unreliable while
                    // bufferInfo.size is authoritative. The buffer pointer is the start of data.
                    const size_t validSize = static_cast<size_t>(bufferInfo.size);
                    if (!accumulator.add(
                            outputBuffer,
                            validSize,
                            bufferInfo.presentationTimeUs,
                            pcmFormat
                    )) {
                        return {};
                    }
                }
                outputEnded = (bufferInfo.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0;
                if (AMediaCodec_releaseOutputBuffer(
                        codec.get(),
                        static_cast<size_t>(outputIndex),
                        false
                ) != AMEDIA_OK) {
                    return {};
                }
                madeProgress = true;
            } else if (outputIndex != AMEDIACODEC_INFO_TRY_AGAIN_LATER &&
                       outputIndex != AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
                return {};
            }

            idleIterations = madeProgress ? 0 : idleIterations + 1;
            if (idleIterations > kMaxIdleIterations) {
                return {};
            }
        }

        return accumulator.normalizedAmplitudes();
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
            "com/example/cdplaya/player/nativeaudio/NativeAudioBridge";

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
