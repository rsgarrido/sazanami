cmake_minimum_required(VERSION 3.22.1)

project(cdplaya_native LANGUAGES CXX)

add_library(
        cdplaya_native
        SHARED
        src/main/cpp/native_audio_decoder.cpp
)

target_compile_features(cdplaya_native PRIVATE cxx_std_17)
target_compile_options(
        cdplaya_native
        PRIVATE
        -Wall
        -Wextra
        -Wpedantic
)

find_library(log_library log REQUIRED)
find_library(media_ndk_library mediandk REQUIRED)

target_link_libraries(
        cdplaya_native
        PRIVATE
        ${log_library}
        ${media_ndk_library}
)
