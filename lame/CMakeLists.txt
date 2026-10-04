cmake_minimum_required(VERSION 3.22.1)

project(mp3lame)

set(LAME_DIR src/main/cpp/libmp3lame)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/${LAME_DIR})

file(GLOB LAME_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/${LAME_DIR}/*.c)

add_library(mp3lame-lib SHARED
    src/main/cpp/net_qiujuer_lame_Lame.cpp
    ${LAME_SOURCES})

# Keep the packaged JNI library safe on Android devices using 16 KiB pages.
# NDK r28 enables flexible page sizes by default, but keep the linker contract
# explicit so PT_LOAD and GNU_RELRO layout cannot regress with toolchain changes.
target_link_options(mp3lame-lib PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
