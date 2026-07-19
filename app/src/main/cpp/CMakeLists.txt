cmake_minimum_required(VERSION 3.22.1)
project("whisper")

# Set C and C++ flags
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -DNDEBUG -std=c11")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -DNDEBUG -std=c++11")

# Include directories (assuming everything is in the same directory)
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Add the downloaded whisper.cpp and ggml sources along with our JNI wrapper
add_library(whisper SHARED 
    whisper_jni.cpp
    whisper.cpp
    ggml.c
    ggml-alloc.c
    ggml-backend.c
)

find_library(log-lib log)

target_link_libraries(whisper ${log-lib})
