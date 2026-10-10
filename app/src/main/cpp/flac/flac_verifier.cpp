#include "flac_snapshot.h"
#include "flac_owned_fd.h"
#include <FLAC/stream_decoder.h>
#include <jni.h>
#include <unistd.h>
#include <sys/stat.h>
#include <cerrno>
#include <algorithm>
#include <memory>

namespace {
using namespace sazanami::flacverify;
} // namespace
extern "C" JNIEXPORT jobject JNICALL
Java_io_github_rsgarrido_sazanami_player_bitperfect_flac_FlacNativeVerifier_nativeVerify(
    JNIEnv* env, jobject, jint borrowedFd, jobject cancellation) {
    if (!cancellation) return nullptr;
    const auto tokenClass = env->GetObjectClass(cancellation);
    if (!tokenClass) return nullptr;
    const auto field = env->GetFieldID(tokenClass, "cancelled", "Z");
    if (!field) return nullptr;
    // Duplicate immediately. Never close or mutate ownership of Kotlin's original descriptor.
    const int duplicate = borrowedFd >= 0 ? ::dup(borrowedFd) : -1;
    const int saved = duplicate < 0 ? (borrowedFd >= 0 ? errno : EBADF) : 0;
    OwnedFd pending(duplicate);
    try {
        Verification verification(std::move(pending), [=] { return env->ExceptionCheck() || env->GetBooleanField(cancellation, field); });
        if (duplicate < 0) {
            verification.facts[DupErrno] = saved; verification.fail(Outcome::InputOpenFailed);
            verification.facts[Result] = static_cast<int>(verification.failure); verification.phase(Phase::Finished);
        } else {
            try { verification.decode(); } catch (...) { verification.fail(Outcome::DecoderError); }
            verification.finalize(); // All resources closed/deleted BEFORE allocating a JNI result.
        }
        if (env->ExceptionCheck()) return nullptr;
        return snapshotFlac(env, verification);
    } catch (...) {
        // Shared callback construction may allocate. OwnedFd/Verification unwind before returning to Kotlin.
        if (!env->ExceptionCheck()) {
            const auto error = env->FindClass("java/lang/IllegalStateException");
            if (error) env->ThrowNew(error, "FLAC verification owner unavailable");
        }
        return nullptr;
    }
}
