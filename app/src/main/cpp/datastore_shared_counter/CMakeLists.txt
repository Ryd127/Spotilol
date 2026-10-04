cmake_minimum_required(VERSION 3.22.1)

project(datastore_shared_counter)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Werror")

add_library(shared_counter STATIC shared_counter.cc)
add_library(datastore_shared_counter SHARED jni/androidx_datastore_core_SharedCounter.cc)

target_link_libraries(datastore_shared_counter shared_counter)
target_link_options(
    datastore_shared_counter
    PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
