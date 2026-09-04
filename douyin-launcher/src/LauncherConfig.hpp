#pragma once

#include <filesystem>
#include <string>

struct LauncherConfig {
    std::string bridgeBaseUrl;
    std::string launcherKey;
    int requestTimeoutSeconds = 10;
};

LauncherConfig loadLauncherConfig(const std::filesystem::path& executableDir);
