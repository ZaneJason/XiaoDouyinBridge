#include "LauncherConfig.hpp"

#include <windows.h>

#include <algorithm>
#include <cstdlib>
#include <fstream>
#include <stdexcept>
#include <string>

namespace {
std::string trim(std::string value) {
    const char* whitespace = " \t\r\n";
    const auto first = value.find_first_not_of(whitespace);
    if (first == std::string::npos) {
        return {};
    }
    const auto last = value.find_last_not_of(whitespace);
    return value.substr(first, last - first + 1);
}

std::string env(const char* name) {
    char* value = nullptr;
    size_t size = 0;
    if (_dupenv_s(&value, &size, name) != 0 || value == nullptr) {
        return {};
    }
    std::string result(value);
    free(value);
    return result;
}

void applyEntry(LauncherConfig& config, const std::string& key, const std::string& value) {
    if (key == "bridge.base-url") {
        config.bridgeBaseUrl = value;
    } else if (key == "bridge.launcher-key") {
        config.launcherKey = value;
    } else if (key == "bridge.timeout-seconds") {
        try {
            config.requestTimeoutSeconds = std::clamp(std::stoi(value), 2, 60);
        } catch (...) {
            throw std::runtime_error("bridge.timeout-seconds must be an integer");
        }
    }
}
}

LauncherConfig loadLauncherConfig(const std::filesystem::path& executableDir) {
    LauncherConfig config;

    const auto configPath = executableDir / "launcher.conf";
    if (std::filesystem::exists(configPath)) {
        std::ifstream input(configPath);
        std::string line;
        while (std::getline(input, line)) {
            line = trim(line);
            if (line.empty() || line[0] == '#' || line[0] == ';') {
                continue;
            }

            const auto equals = line.find('=');
            if (equals == std::string::npos) {
                continue;
            }

            const std::string key = trim(line.substr(0, equals));
            const std::string value = trim(line.substr(equals + 1));
            applyEntry(config, key, value);
        }
    }

    const std::string envUrl = env("XIAODOUYINBRIDGE_URL");
    const std::string envKey = env("XIAODOUYINBRIDGE_LAUNCHER_KEY");
    if (!envUrl.empty()) {
        config.bridgeBaseUrl = envUrl;
    }
    if (!envKey.empty()) {
        config.launcherKey = envKey;
    }

    while (!config.bridgeBaseUrl.empty() && config.bridgeBaseUrl.back() == '/') {
        config.bridgeBaseUrl.pop_back();
    }

    if (config.bridgeBaseUrl.empty()) {
        throw std::runtime_error("Bridge URL is missing. Set bridge.base-url in launcher.conf or XIAODOUYINBRIDGE_URL.");
    }
    if (config.launcherKey.empty() || config.launcherKey == "change-me-launcher") {
        throw std::runtime_error("Launcher key is missing/default. Set bridge.launcher-key or XIAODOUYINBRIDGE_LAUNCHER_KEY.");
    }

    return config;
}
