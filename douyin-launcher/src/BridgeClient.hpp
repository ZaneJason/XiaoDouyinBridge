#pragma once

#include "LauncherConfig.hpp"
#include "Logger.hpp"

#include <string>

class BridgeClient {
public:
    BridgeClient(LauncherConfig config, Logger& logger);

    bool healthCheck();
    bool forwardPipeMessage(const std::string& jsonMessage);

private:
    bool request(const wchar_t* method, const std::string& path, const std::string& body, unsigned long& statusCode);

    LauncherConfig config_;
    Logger& logger_;
};
