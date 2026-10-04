cmake_minimum_required(VERSION 3.22.1)

project("androidx.graphics.path")

set(VERSION_SCRIPT "${CMAKE_CURRENT_SOURCE_DIR}/libandroidx.graphics.path.map")

add_library(
    androidx.graphics.path
    SHARED
    Conic.cpp
    PathIterator.cpp
    pathway.cpp
)

target_compile_options(
    androidx.graphics.path
    PRIVATE
    -std=c++17
    -Wno-unused-command-line-argument
    -fno-exceptions
    -fno-unwind-tables
    -fno-asynchronous-unwind-tables
    -fno-rtti
    -ffast-math
    -ffp-contract=fast
    -fvisibility-inlines-hidden
    -fvisibility=hidden
    -fomit-frame-pointer
    -ffunction-sections
    -fdata-sections
)

target_link_options(
    androidx.graphics.path
    PRIVATE
    "-Wl,--version-script=${VERSION_SCRIPT}"
    "-Wl,--hash-style=both"
    "-Wl,--gc-sections"
    "-Wl,-Bsymbolic-functions"
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
    "-nostdlib++"
)
