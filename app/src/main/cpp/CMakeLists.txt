cmake_minimum_required(VERSION 3.22.1)
project(ax12bridge LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# The SDK files are NOT stored in this repository. The build copies them in (see README):
#   app/src/main/jniLibs/arm64-v8a/*.so      app/src/main/cpp/sdk-include/rmAxSdk1/*.h
set(SDK_LIBS ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

add_library(rmAxSdk1 SHARED IMPORTED)
set_target_properties(rmAxSdk1 PROPERTIES
    IMPORTED_LOCATION ${SDK_LIBS}/librmAxSdk1.so
    IMPORTED_NO_SONAME TRUE)

# Qt6Core is linked directly because the bridge calls QtAndroidPrivate::initJNI.
add_library(Qt6Core SHARED IMPORTED)
set_target_properties(Qt6Core PROPERTIES
    IMPORTED_LOCATION ${SDK_LIBS}/libQt6Core_arm64-v8a.so
    IMPORTED_NO_SONAME TRUE)

set(BRIDGE_SOURCES bridge_core.cpp ax12bridge_jni.cpp)
option(AX12_SERIAL_LOCK_PATCH "Patch the bundled Qt serial library so the SDK can open the port" OFF)
if(AX12_SERIAL_LOCK_PATCH)
    list(APPEND BRIDGE_SOURCES serial_lock_patch.cpp)
endif()

add_library(ax12bridge SHARED ${BRIDGE_SOURCES})
target_include_directories(ax12bridge PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/sdk-include/rmAxSdk1)
if(AX12_SERIAL_LOCK_PATCH)
    target_compile_definitions(ax12bridge PRIVATE AX12_SERIAL_LOCK_PATCH=1)
endif()

find_library(lib_log log)
find_library(lib_android android)
# c++_shared must match the SDK (ANDROID_STL is set in app/build.gradle.kts)
target_link_libraries(ax12bridge PRIVATE rmAxSdk1 Qt6Core ${lib_log} ${lib_android})
