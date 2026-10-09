cmake_minimum_required(VERSION 3.22.1)

project(sazanami_native LANGUAGES CXX)

add_library(
        sazanami_native
        SHARED
        src/main/cpp/native_audio_decoder.cpp
)

target_compile_features(sazanami_native PRIVATE cxx_std_17)
target_compile_options(
        sazanami_native
        PRIVATE
        -Wall
        -Wextra
        -Wpedantic
)

find_library(log_library log REQUIRED)
find_library(media_ndk_library mediandk REQUIRED)

target_link_libraries(
        sazanami_native
        PRIVATE
        ${log_library}
        ${media_ndk_library}
)

# Independent USB diagnostic JNI boundary; no waveform/MediaCodec dependency.
add_library(sazanami_usb_diagnostics SHARED src/main/cpp/usb_fs_diagnostics.cpp)
target_compile_features(sazanami_usb_diagnostics PRIVATE cxx_std_17)
target_compile_options(sazanami_usb_diagnostics PRIVATE -Wall -Wextra -Wpedantic)
