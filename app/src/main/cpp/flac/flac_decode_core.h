#pragma once
#include "flac_pcm_core.h"
#include "flac_owned_fd.h"
#include <FLAC/stream_decoder.h>
#include <sys/stat.h>
#include <cerrno>
#include <algorithm>
#include <memory>
#include <functional>
#include <utility>
#include <vector>

namespace sazanami::flacverify {
struct DecoderDelete { void operator()(FLAC__StreamDecoder* p) const { if (p) FLAC__stream_decoder_delete(p); } };
struct Verification {
    std::function<bool()> stopRequested;
    std::function<bool(uint64_t, const uint8_t*)> consumeCanonical;
    std::function<void(const Verification&)> metadataAccepted;
    std::function<void(Outcome)> failureObserved;
    std::function<void(FLAC__StreamDecoder*)> interactiveProcess;
    std::function<bool()> quiesceRequested;
    bool reloadMetadata=false,stagingSeek=false;
    uint64_t originalNext=0,seekTarget=0,seekAttempts=0,unpublishedCanonical=0,unpublishedRaw=0;
    off_t origin=0;
    std::vector<uint8_t> targetBlock;
    uint64_t targetBlockFirst=0,targetBlockFrames=0;
    bool wholeFile = true;
    bool enforceExpectedTotal = false; // Session 11 only; Sessions 9/10 retain their established gates.
    std::array<int64_t, FieldCount> facts{};
    std::array<uint8_t, 16> md5{};
    std::array<int64_t, 8> errors{};
    size_t errorCount = 0, prefixOffset = 0;
    std::array<uint8_t, 4> prefix{};
    Pcm pcm{nullptr}; // Only accepted STREAMINFO establishes the immutable source format.
    Outcome failure = Outcome::Pending;
    OwnedFd input;
    bool physicalEof = false, accepted = false;
    uint64_t fed = 0;
    bool seekCapable() const { return facts[Seekable]==1 && facts[FileSize]>0; }
    static FLAC__StreamDecoderSeekStatus seek(const FLAC__StreamDecoder*,FLAC__uint64 offset,void* context) {
        auto& v=*static_cast<Verification*>(context);
        if(!v.seekCapable()) return FLAC__STREAM_DECODER_SEEK_STATUS_UNSUPPORTED;
        if(v.cancelled() || offset>static_cast<uint64_t>(v.facts[FileSize]) ||
            offset>static_cast<uint64_t>(std::numeric_limits<off_t>::max())-static_cast<uint64_t>(v.origin)) return FLAC__STREAM_DECODER_SEEK_STATUS_ERROR;
        if(::lseek(v.input.value,v.origin+static_cast<off_t>(offset),SEEK_SET)<0) {
            v.facts[ReadErrno]=errno;v.fail(Outcome::DecoderError);return FLAC__STREAM_DECODER_SEEK_STATUS_ERROR;
        }
        v.fed=offset;v.prefixOffset=v.prefix.size();v.physicalEof=false;return FLAC__STREAM_DECODER_SEEK_STATUS_OK;
    }
    static FLAC__bool eof(const FLAC__StreamDecoder*,void* context) { return static_cast<Verification*>(context)->physicalEof; }
    Verification(OwnedFd duplicate, std::function<bool()> stop, bool whole = true)
        : stopRequested(std::move(stop)), wholeFile(whole), input(std::move(duplicate)) {
        facts[Version] = 2; facts[InitStatus] = -1; facts[DecoderState] = -1;
        facts[Seekable] = -1; facts[FileSize] = -1; phase(Phase::Idle);
    }
    void phase(Phase value) { facts[Lifecycle] |= 1LL << static_cast<int>(value); }
    void fail(Outcome value) {
        if (failure == Outcome::Pending) {
            failure = value;
            if (failureObserved) failureObserved(value);
        }
    }
    bool cancelled() {
        if (stopRequested && stopRequested()) { fail(Outcome::Cancelled); return true; }
        return false;
    }
    ssize_t readInput(uint8_t* bytes, size_t count) {
        for (;;) {
            if (cancelled() || failure != Outcome::Pending) return -1;
            const ssize_t read = ::read(input.value, bytes, std::min<size_t>(count, 65536));
            if (read < 0 && errno == EINTR) continue;
            if (read < 0) { facts[ReadErrno] = errno; fail(Outcome::DecoderError); }
            if (read == 0) physicalEof = true;
            return read;
        }
    }
    static FLAC__StreamDecoderReadStatus read(const FLAC__StreamDecoder*, FLAC__byte* bytes, size_t* count, void* context) {
        auto& v = *static_cast<Verification*>(context);
        if (!count || !*count || !bytes || v.cancelled() || v.failure != Outcome::Pending) return FLAC__STREAM_DECODER_READ_STATUS_ABORT;
        if (v.prefixOffset < v.prefix.size()) {
            const size_t n = std::min(*count, v.prefix.size() - v.prefixOffset);
            std::copy_n(v.prefix.data() + v.prefixOffset, n, bytes); v.prefixOffset += n; *count = n; v.fed += n;
            return FLAC__STREAM_DECODER_READ_STATUS_CONTINUE;
        }
        const auto n = v.readInput(bytes, *count);
        if (n < 0) { *count = 0; return FLAC__STREAM_DECODER_READ_STATUS_ABORT; }
        *count = static_cast<size_t>(n);
        if (!canAdd(v.fed, *count)) { v.fail(Outcome::CounterOverflow); return FLAC__STREAM_DECODER_READ_STATUS_ABORT; }
        v.fed += *count;
        return n == 0 ? FLAC__STREAM_DECODER_READ_STATUS_END_OF_STREAM : FLAC__STREAM_DECODER_READ_STATUS_CONTINUE;
    }
    static FLAC__StreamDecoderTellStatus tell(const FLAC__StreamDecoder*, FLAC__uint64* offset, void* context) {
        *offset = static_cast<Verification*>(context)->fed; return FLAC__STREAM_DECODER_TELL_STATUS_OK;
    }
    static FLAC__StreamDecoderLengthStatus length(const FLAC__StreamDecoder*, FLAC__uint64* size, void* context) {
        const auto known = static_cast<Verification*>(context)->facts[FileSize];
        if (known < 0) return FLAC__STREAM_DECODER_LENGTH_STATUS_UNSUPPORTED;
        *size = static_cast<uint64_t>(known); return FLAC__STREAM_DECODER_LENGTH_STATUS_OK;
    }
    static void metadata(const FLAC__StreamDecoder*, const FLAC__StreamMetadata* block, void* context) {
        auto& v = *static_cast<Verification*>(context);
        if (v.cancelled() || v.failure != Outcome::Pending) return;
        if(block && block->type==FLAC__METADATA_TYPE_STREAMINFO && v.reloadMetadata) {
            const auto& s=block->data.stream_info;
            if(s.sample_rate!=v.facts[Rate] || s.channels!=v.facts[Channels] || s.bits_per_sample!=v.facts[Bits] ||
                s.total_samples!=static_cast<uint64_t>(v.facts[Total]) || s.max_blocksize!=v.facts[MaxBlock] || s.min_blocksize!=v.facts[MinBlock] ||
                s.min_framesize!=v.facts[MinFrame] || s.max_framesize!=v.facts[MaxFrame] ||
                !std::equal(v.md5.begin(),v.md5.end(),s.md5sum)) {++v.facts[Contradictions];v.fail(Outcome::FormatContradiction);}
            v.reloadMetadata=false;return;
        }
        if (!block || block->type != FLAC__METADATA_TYPE_STREAMINFO || v.facts[StreamInfo]) {
            ++v.facts[Contradictions]; v.fail(Outcome::FormatContradiction); return;
        }
        const auto& s = block->data.stream_info;
        v.facts[StreamInfo] = 1; v.facts[Rate] = s.sample_rate; v.facts[Channels] = s.channels; v.facts[Bits] = s.bits_per_sample;
        v.facts[Total] = static_cast<int64_t>(s.total_samples);
        v.facts[MinBlock] = s.min_blocksize; v.facts[MaxBlock] = s.max_blocksize;
        v.facts[MinFrame] = s.min_framesize; v.facts[MaxFrame] = s.max_framesize;
        std::copy_n(s.md5sum, 16, v.md5.begin());
        v.facts[Md5Available] = std::any_of(v.md5.begin(), v.md5.end(), [](uint8_t byte) { return byte != 0; }) ? 1 : 0;
        if (!s.min_blocksize || s.max_blocksize < s.min_blocksize || s.max_blocksize > 65535 ||
            s.total_samples > kCounterMax || (s.min_framesize && s.max_framesize && s.min_framesize > s.max_framesize)) {
            v.fail(Outcome::DecoderError); return;
        }
        v.accepted = supported(s.sample_rate, s.channels, s.bits_per_sample);
        if (!v.accepted) v.fail(Outcome::Unsupported);
        else {
            try {
                v.pcm.format=sazanami::directpcm::Format::create(s.sample_rate,s.channels,s.bits_per_sample);
                v.phase(Phase::FormatAccepted); if (v.metadataAccepted) v.metadataAccepted(v);
            } catch (...) { v.fail(Outcome::CleanupFailed); } // Never unwind a format/queue allocation through libFLAC C callbacks.
        }
    }
    static void error(const FLAC__StreamDecoder*, FLAC__StreamDecoderErrorStatus status, void* context) {
        auto& v = *static_cast<Verification*>(context);
        if (v.facts[DecoderErrors] < INT64_MAX) ++v.facts[DecoderErrors]; else v.fail(Outcome::CounterOverflow);
        if (v.errorCount < v.errors.size()) v.errors[v.errorCount++] = status;
        v.fail(Outcome::DecoderError); // Never hash a repaired/concealed block after a libFLAC error.
    }
    static FLAC__StreamDecoderWriteStatus write(const FLAC__StreamDecoder*, const FLAC__Frame* frame,
        const FLAC__int32* const channels[], void* context) {
        auto& v = *static_cast<Verification*>(context);
        if (v.cancelled() || v.failure != Outcome::Pending) return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
        if (!canAdd(v.pcm.callbacks, 1)) { v.fail(Outcome::CounterOverflow); return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT; }
        ++v.pcm.callbacks;
        if (!v.accepted || !frame ||
            !supported(frame->header.sample_rate, frame->header.channels, frame->header.bits_per_sample) ||
            frame->header.sample_rate!=v.facts[Rate] || frame->header.channels!=v.facts[Channels] || frame->header.bits_per_sample!=v.facts[Bits] ||
            !channels || !channels[0] || !channels[1] ||
            !frame->header.blocksize || frame->header.blocksize > static_cast<uint32_t>(v.facts[MaxBlock])) {
            ++v.facts[Contradictions]; v.fail(Outcome::FormatContradiction); return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
        }
        const auto& h = frame->header;
        const bool ordered = v.seekAttempts ? h.number_type==FLAC__FRAME_NUMBER_TYPE_SAMPLE_NUMBER && h.number.sample_number==v.originalNext :
            h.number_type == FLAC__FRAME_NUMBER_TYPE_SAMPLE_NUMBER ? h.number.sample_number == v.pcm.frames :
            h.number_type == FLAC__FRAME_NUMBER_TYPE_FRAME_NUMBER && h.number.frame_number == v.pcm.blocks;
        if (!ordered) { ++v.facts[FrameOrderErrors]; ++v.facts[Contradictions]; v.fail(Outcome::FormatContradiction); return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT; }
        const auto originalStart=v.seekAttempts?v.originalNext:v.pcm.frames;
        if(v.enforceExpectedTotal && (originalStart>static_cast<uint64_t>(v.facts[Total]) || h.blocksize>static_cast<uint64_t>(v.facts[Total])-originalStart)) {
            ++v.facts[Contradictions];v.fail(Outcome::FormatContradiction);return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
        }
        if (!canAdd(v.pcm.frames, h.blocksize) || !canAdd(v.pcm.bytes, static_cast<uint64_t>(h.blocksize) * v.pcm.frameBytes()) || !canAdd(v.pcm.blocks, 1)) {
            v.fail(Outcome::CounterOverflow); return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
        }
        v.phase(Phase::Decoding);
        if(v.stagingSeek) {
            if(h.number_type!=FLAC__FRAME_NUMBER_TYPE_SAMPLE_NUMBER || h.number.sample_number!=v.seekTarget || v.targetBlockFrames ||
                h.blocksize>static_cast<uint32_t>(v.facts[MaxBlock]) || h.blocksize>static_cast<uint64_t>(v.facts[Total])-v.seekTarget) {
                v.fail(Outcome::FormatContradiction);return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
            }
            try {v.targetBlock.resize(static_cast<size_t>(h.blocksize)*v.pcm.frameBytes());} catch(...) {v.fail(Outcome::CleanupFailed);return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;}
            for(uint32_t i=0;i<h.blocksize;++i) {
                if(v.cancelled() || !v.pcm.append(channels[0][i],channels[1][i],v.targetBlock.data()+i*v.pcm.frameBytes())) {
                    v.fail(v.pcm.failure==Outcome::Pending?Outcome::Cancelled:v.pcm.failure);return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
                }
            }
            v.targetBlockFirst=v.seekTarget;v.targetBlockFrames=h.blocksize;v.originalNext=v.seekTarget+h.blocksize;
            ++v.pcm.blocks;return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE;
        }
        for (uint32_t i = 0; i < h.blocksize; ++i) {
            if(v.interactiveProcess && v.quiesceRequested && v.quiesceRequested()) {
                if(!canAdd(v.unpublishedRaw,h.blocksize-i)) {v.fail(Outcome::CounterOverflow);return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;}
                v.unpublishedRaw+=h.blocksize-i;v.originalNext+=h.blocksize-i;++v.pcm.blocks;
                return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE; // Return to decoder owner, never seek inside its callback.
            }
            const auto original=v.seekAttempts?v.originalNext:v.pcm.frames;
            if (v.enforceExpectedTotal && (!v.facts[Total] || original>=static_cast<uint64_t>(v.facts[Total]))) {
                ++v.facts[Contradictions]; v.fail(Outcome::FormatContradiction);
                return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT; // Never serialize a frame beyond STREAMINFO.
            }
            uint8_t canonical[6]{};
            if ((i % 1024 == 0 && v.cancelled()) || !v.pcm.append(channels[0][i], channels[1][i], v.consumeCanonical ? canonical : nullptr)) {
                if (v.pcm.failure != Outcome::Pending) v.fail(v.pcm.failure);
                return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
            }
            if (v.consumeCanonical && !v.consumeCanonical(original, canonical)) {
                if(v.interactiveProcess && v.quiesceRequested && v.quiesceRequested() && !v.cancelled()) {
                    if(!canAdd(v.unpublishedCanonical,1) || !canAdd(v.unpublishedRaw,h.blocksize-i-1)) {v.fail(Outcome::CounterOverflow);return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;}
                    ++v.unpublishedCanonical;v.unpublishedRaw+=h.blocksize-i-1;v.originalNext=original+h.blocksize-i;++v.pcm.blocks;
                    return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE;
                }
                v.fail(Outcome::Cancelled); return FLAC__STREAM_DECODER_WRITE_STATUS_ABORT;
            }
            v.originalNext=original+1;
        }
        ++v.pcm.blocks; return FLAC__STREAM_DECODER_WRITE_STATUS_CONTINUE;
    }
    void decode() {
        phase(Phase::DocumentOpened);
        origin = ::lseek(input.value, 0, SEEK_CUR);
        facts[Seekable] = origin >= 0 ? 1 : errno == ESPIPE ? 0 : -1;
        struct stat info{};
        if (origin >= 0 && ::fstat(input.value, &info) == 0 && S_ISREG(info.st_mode) && info.st_size >= origin)
            facts[FileSize] = static_cast<int64_t>(info.st_size - origin);
        size_t found = 0;
        while (found < 4) {
            const auto n = readInput(prefix.data() + found, 4 - found);
            if (n <= 0) { if (failure == Outcome::Pending) fail(Outcome::Truncated); return; }
            found += static_cast<size_t>(n);
        }
        if (prefix != std::array<uint8_t,4>{'f','L','a','C'}) { fail(Outcome::NotNativeFlac); return; }
        facts[Recognized] = 1;
        std::unique_ptr<FLAC__StreamDecoder, DecoderDelete> decoder(FLAC__stream_decoder_new());
        if (!decoder) { fail(Outcome::DecoderError); return; }
        facts[Created] = 1; phase(Phase::DecoderCreated);
        const bool md5Set = FLAC__stream_decoder_set_md5_checking(decoder.get(), wholeFile);
        facts[Md5Enabled] = FLAC__stream_decoder_get_md5_checking(decoder.get()) ? 1 : 0;
        const bool filters = FLAC__stream_decoder_set_metadata_ignore_all(decoder.get()) &&
            FLAC__stream_decoder_set_metadata_respond(decoder.get(), FLAC__METADATA_TYPE_STREAMINFO);
        if (!filters || !md5Set || (facts[Md5Enabled] != (wholeFile ? 1 : 0))) { fail(Outcome::DecoderError); decoder.reset(); facts[DecoderDeleted] = 1; return; }
        // The standalone verifier has no seek/flush/reset. Full playback enables callbacks only for a capable source.
        const bool callbacks=interactiveProcess && seekCapable();
        facts[InitStatus] = FLAC__stream_decoder_init_stream(decoder.get(), read, callbacks?seek:nullptr,
            facts[Seekable] == 1 ? tell : nullptr, facts[FileSize] >= 0 ? length : nullptr,
            callbacks?eof:nullptr, write, metadata, error, this);
        if (facts[InitStatus] == FLAC__STREAM_DECODER_INIT_STATUS_OK) {
            phase(Phase::MetadataReading);
            facts[MetadataOk] = FLAC__stream_decoder_process_until_end_of_metadata(decoder.get()) ? 1 : 0;
            if(interactiveProcess && failure==Outcome::Pending && facts[MetadataOk] && facts[StreamInfo] && accepted) interactiveProcess(decoder.get());
            else {
            if (failure == Outcome::Pending && facts[MetadataOk] && facts[StreamInfo] && accepted)
                facts[ProcessOk] = FLAC__stream_decoder_process_until_end_of_stream(decoder.get()) ? 1 : 0;
            facts[DecoderState] = FLAC__stream_decoder_get_state(decoder.get()); // Capture BEFORE finish resets it.
            facts[CleanEos] = facts[ProcessOk] && facts[DecoderState] == FLAC__STREAM_DECODER_END_OF_STREAM && physicalEof ? 1 : 0;
            if (facts[CleanEos]) phase(Phase::EndOfStream);
            phase(Phase::Verifying); facts[FinishCalled] = 1;
            facts[FinishOk] = FLAC__stream_decoder_finish(decoder.get()) ? 1 : 0;
            if (failure == Outcome::Pending && facts[CleanEos] && facts[Md5Available] && facts[Md5Enabled] && !facts[DecoderErrors])
                facts[Md5Verification] = facts[FinishOk] ? 1 : 2;
            }
        } else fail(Outcome::DecoderError);
        decoder.reset(); facts[DecoderDeleted] = 1;
    }
    void finalize() {
        facts[PhysicalEof] = physicalEof ? 1 : 0;
        facts[FdClosed] = input.closeNow() ? 1 : 0;
        facts[ResourceError] = facts[FdClosed] ? 0 : 1;
        facts[Callbacks] = static_cast<int64_t>(pcm.callbacks); facts[Blocks] = static_cast<int64_t>(pcm.blocks);
        facts[Frames] = facts[SamplesPerChannel] = static_cast<int64_t>(pcm.frames);
        facts[Bytes] = static_cast<int64_t>(pcm.bytes); facts[Hash] = hashBits(pcm.hash);
        facts[MinL] = pcm.minL; facts[MaxL] = pcm.maxL; facts[MinR] = pcm.minR; facts[MaxR] = pcm.maxR;
        facts[MinObserved] = pcm.minimum; facts[MaxObserved] = pcm.maximum; facts[RangeErrors] = static_cast<int64_t>(pcm.rangeErrors);
        const bool released = facts[FdClosed] && (!facts[Created] || facts[DecoderDeleted]);
        if (!released) failure = Outcome::CleanupFailed;
        if(failure==Outcome::Pending && seekAttempts) failure=released && facts[CleanEos] && facts[FinishOk] && originalNext==static_cast<uint64_t>(facts[Total]) ? Outcome::SeekedEof : Outcome::Truncated;
        if (failure == Outcome::Pending && wholeFile) failure = classify(facts[Recognized], facts[InitStatus] == 0, facts[StreamInfo], accepted,
            facts[ProcessOk], facts[CleanEos], facts[Md5Enabled], facts[Md5Available], facts[FinishOk],
            static_cast<uint64_t>(facts[Total]), pcm, static_cast<uint64_t>(facts[DecoderErrors]), static_cast<uint64_t>(facts[Contradictions]), released);
        if (!wholeFile && failure == Outcome::Pending) failure = Outcome::Truncated; // Prefix EOF is never duration success.
        facts[Result] = static_cast<int>(failure); phase(Phase::Finished);
    }
};
} // namespace sazanami::flacverify
