#include "BridgeClient.hpp"
#include "LauncherArgs.hpp"
#include "LauncherConfig.hpp"
#include "Logger.hpp"
#include "PipeSdkAdapter.hpp"

#include <windows.h>

#include <filesystem>
#include <iostream>
#include <stdexcept>
#include <string>

namespace {
std::filesystem::path executableDirectory() {
    std::wstring path(32768, L'\0');
    DWORD length = GetModuleFileNameW(nullptr, path.data(), static_cast<DWORD>(path.size()));
    if (length == 0 || length >= path.size()) {
        return std::filesystem::current_path();
    }
    path.resize(length);
    return std::filesystem::path(path).parent_path();
}
}

int wmain(int argc, wchar_t* argv[]) {
    const auto exeDir = executableDirectory();
    Logger logger(exeDir);

    try {
        const LauncherArgs args = parseLauncherArgs(argc, argv);
        logger.info("============================================================");
        logger.info(" XiaoDouyinBridge Live Companion Launcher starting");
        logger.info(" mateVersion=" + (args.mateVersion.empty() ? std::string("<unknown>") : args.mateVersion));
        logger.info(" layoutMode=" + std::to_string(args.layoutMode));
        logger.info(" maxChannels=" + std::to_string(args.maxChannels));
        logger.info(" pipeName received=" + std::string(args.pipeName.empty() ? "false" : "true"));

        const LauncherConfig config = loadLauncherConfig(exeDir);
        logger.info("[CONFIG] Bridge URL loaded");
        logger.info("[CONFIG] Launcher key loaded (value hidden)");

        BridgeClient bridge(config, logger);
        if (!bridge.healthCheck()) {
            logger.error("[STARTUP] Bridge is unavailable or launcher key is incorrect");
            return 20;
        }

        if (args.selfTest) {
            logger.info("[SELF-TEST] Bridge connectivity test passed");
            return 0;
        }

        if (!args.hasPipeArgs()) {
            logger.error("[STARTUP] Missing --pipeName or --maxChannels. This EXE must normally be started by Douyin Live Companion.");
            return 21;
        }

        auto pipeSdk = createPipeSdkAdapter(logger);
        if (!pipeSdk->available()) {
            logger.error("[STARTUP] This build does not contain the official Douyin PipeSDK adapter yet");
            return 22;
        }

        if (!pipeSdk->connect(args, [&](const std::string& jsonMessage) {
                if (!bridge.forwardPipeMessage(jsonMessage)) {
                    logger.warn("[FORWARD] Failed to forward one PipeSDK message; continuing");
                }
            })) {
            logger.error("[PIPESDK] Failed to connect to Live Companion");
            return 23;
        }

        if (!pipeSdk->subscribeOpenLiveData()) {
            logger.error("[PIPESDK] Failed to subscribe OPEN_LIVE_DATA");
            return 24;
        }

        logger.info("[PIPESDK] OPEN_LIVE_DATA subscribed; waiting for comments/fansclub events");
        pipeSdk->runUntilDisconnected();
        logger.info("[PIPESDK] Live Companion disconnected; launcher exiting as required by the platform");
        return 0;
    } catch (const std::exception& e) {
        logger.error(std::string("[FATAL] ") + e.what());
        return 1;
    } catch (...) {
        logger.error("[FATAL] Unknown exception");
        return 2;
    }
}
