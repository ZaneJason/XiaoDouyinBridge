#pragma once

#include <optional>
#include <string>

struct LauncherArgs {
    std::string pipeName;
    int maxChannels = 0;
    std::string mateVersion;
    int layoutMode = -1;
    bool selfTest = false;

    bool hasPipeArgs() const {
        return !pipeName.empty() && maxChannels > 0;
    }
};

LauncherArgs parseLauncherArgs(int argc, wchar_t* argv[]);
