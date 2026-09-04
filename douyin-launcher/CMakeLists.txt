cmake_minimum_required(VERSION 3.20)
project(XiaoDouyinBridgeLauncher LANGUAGES CXX)

if(NOT WIN32)
    message(FATAL_ERROR "XiaoDouyinBridge Launcher currently supports Windows only")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(XDB_WITH_PIPESDK "Build with the official Douyin PipeSDK adapter" OFF)
set(XDB_PIPESDK_ROOT "" CACHE PATH "Path to extracted official Douyin pure_PipeSDK package")

set(LAUNCHER_SOURCES
    src/main.cpp
    src/LauncherArgs.cpp
    src/LauncherConfig.cpp
    src/Logger.cpp
    src/BridgeClient.cpp
)

if(XDB_WITH_PIPESDK)
    if(NOT XDB_PIPESDK_ROOT)
        message(FATAL_ERROR "XDB_WITH_PIPESDK=ON requires -DXDB_PIPESDK_ROOT=<official SDK path>")
    endif()
    list(APPEND LAUNCHER_SOURCES src/PipeSdkAdapterOfficial.cpp)
else()
    list(APPEND LAUNCHER_SOURCES src/PipeSdkAdapterStub.cpp)
endif()

add_executable(XiaoDouyinBridgeLauncher ${LAUNCHER_SOURCES})
target_include_directories(XiaoDouyinBridgeLauncher PRIVATE src)
target_compile_definitions(XiaoDouyinBridgeLauncher PRIVATE UNICODE _UNICODE WIN32_LEAN_AND_MEAN NOMINMAX)
target_link_libraries(XiaoDouyinBridgeLauncher PRIVATE winhttp)

if(MSVC)
    target_compile_options(XiaoDouyinBridgeLauncher PRIVATE /W4 /utf-8 /EHsc)
endif()

if(XDB_WITH_PIPESDK)
    target_compile_definitions(XiaoDouyinBridgeLauncher PRIVATE XDB_WITH_PIPESDK=1)
    target_include_directories(XiaoDouyinBridgeLauncher PRIVATE "${XDB_PIPESDK_ROOT}/include")
    # PipeSDK 的具体 .lib 文件名由官方压缩包决定；拿到官方 SDK 后在此处绑定。
    # target_link_libraries(XiaoDouyinBridgeLauncher PRIVATE "${XDB_PIPESDK_ROOT}/lib/x64/PipeSDK.lib")
endif()

set_target_properties(XiaoDouyinBridgeLauncher PROPERTIES
    OUTPUT_NAME "XiaoDouyinBridge"
)
