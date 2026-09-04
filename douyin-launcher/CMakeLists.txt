cmake_minimum_required(VERSION 3.20)
project(XiaoDouyinBridgeLauncher LANGUAGES CXX)

if(NOT WIN32)
    message(FATAL_ERROR "XiaoDouyinBridge Launcher currently supports Windows only")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(LAUNCHER_SOURCES
    src/main.cpp
    src/LauncherArgs.cpp
    src/LauncherConfig.cpp
    src/Logger.cpp
    src/BridgeClient.cpp
    src/PipeSdkAdapterOfficial.cpp
)

add_executable(XiaoDouyinBridgeLauncher ${LAUNCHER_SOURCES})
target_include_directories(XiaoDouyinBridgeLauncher PRIVATE src)
target_compile_definitions(XiaoDouyinBridgeLauncher PRIVATE UNICODE _UNICODE WIN32_LEAN_AND_MEAN NOMINMAX)
target_link_libraries(XiaoDouyinBridgeLauncher PRIVATE winhttp)

if(MSVC)
    target_compile_options(XiaoDouyinBridgeLauncher PRIVATE /W4 /utf-8 /EHsc)
endif()

set_target_properties(XiaoDouyinBridgeLauncher PROPERTIES
    OUTPUT_NAME "XiaoDouyinBridge"
)

# PipeSDK.dll is intentionally resolved at runtime from the same directory as the
# EXE. This lets CI build the launcher without redistributing Douyin's SDK binary.
# For a production package, copy the official x64 PipeSDK.dll next to the EXE.
