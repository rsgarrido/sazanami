#pragma once
#include "flac_decode_core.h"
#include <jni.h>
namespace sazanami::flacverify {
inline jobject snapshotFlac(JNIEnv* env, const Verification& v) {
    jclass cls = env->FindClass("io/github/rsgarrido/sazanami/player/bitperfect/flac/FlacNativeSnapshot");
    if (!cls) return nullptr;
    const auto constructor = env->GetMethodID(cls, "<init>", "([J[B[J[JLjava/lang/String;)V");
    if (!constructor) return nullptr;
    auto numbers = env->NewLongArray(FieldCount); auto md5 = env->NewByteArray(16);
    size_t rows = 0; std::array<jlong, 7*9> examples{};
    for (size_t i = 0; i < 7; ++i) if (v.pcm.exampleUsed[i]) {
        for (size_t j = 0; j < 9; ++j) examples[rows*9+j] = v.pcm.examples[i][j]; ++rows;
    }
    auto pcm = env->NewLongArray(static_cast<jsize>(rows*9)); auto errors = env->NewLongArray(static_cast<jsize>(v.errorCount));
    auto version = env->NewStringUTF(FLAC__VERSION_STRING);
    if (!numbers || !md5 || !pcm || !errors || !version) return nullptr;
    std::array<jlong, FieldCount> facts{}; std::copy(v.facts.begin(), v.facts.end(), facts.begin());
    std::array<jlong,8> errorCodes{}; std::copy(v.errors.begin(), v.errors.end(), errorCodes.begin());
    std::array<jbyte,16> digest{}; std::memcpy(digest.data(), v.md5.data(), 16);
    env->SetLongArrayRegion(numbers, 0, FieldCount, facts.data()); env->SetByteArrayRegion(md5, 0, 16, digest.data());
    env->SetLongArrayRegion(pcm, 0, static_cast<jsize>(rows*9), examples.data());
    env->SetLongArrayRegion(errors, 0, static_cast<jsize>(v.errorCount), errorCodes.data());
    if (env->ExceptionCheck()) return nullptr;
    return env->NewObject(cls, constructor, numbers, md5, pcm, errors, version);
}
}
