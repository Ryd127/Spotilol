cmake_minimum_required(VERSION 3.22.1)

project(opusjni)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(OPUS_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "" FORCE)

add_subdirectory(src/main/cpp/libopus libopus-build)

add_library(opus-lib SHARED
    src/main/cpp/com_project_lol_opus_OpusDecoder.cpp)

target_link_libraries(opus-lib opus)

# Keep the packaged JNI library safe on Android devices using 16 KiB pages.
# NDK r28 enables flexible page sizes by default, but keep the linker contract
# explicit so PT_LOAD and GNU_RELRO layout cannot regress with toolchain changes.
target_link_options(opus-lib PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
