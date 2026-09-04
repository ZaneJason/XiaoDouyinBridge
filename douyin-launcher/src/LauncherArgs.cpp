#include "LauncherArgs.hpp"

#include <windows.h>

#include <charconv>
#include <string_view>

namespace {
std::string toUtf8(const std::wstring& value) {
    if (value.empty()) {
        return {};
    }
    int size = WideCharToMultiByte(CP_UTF8, 0, value.c_str(), static_cast<int>(value.size()),
                                   nullptr, 0, nullptr, nullptr);
    std::string result(static_cast<size_t>(size), '\0');
    WideCharToMultiByte(CP_UTF8, 0, value.c_str(), static_cast<int>(value.size()),
                        result.data(), size, nullptr, nullptr);
    return result;
}

bool startsWith(const std::wstring& value, const wchar_t* prefix) {
    return value.rfind(prefix, 0) == 0;
}

int parseInt(const std::wstring& value, int fallback) {
    try {
        return std::stoi(value);
    } catch (...) {
        return fallback;
    }
}
}

LauncherArgs parseLauncherArgs(int argc, wchar_t* argv[]) {
    LauncherArgs result;

    for (int i = 1; i < argc; ++i) {
        const std::wstring arg = argv[i] == nullptr ? L"" : argv[i];
        if (startsWith(arg, L"--pipeName=")) {
            result.pipeName = toUtf8(arg.substr(11));
        } else if (startsWith(arg, L"--maxChannels=")) {
            result.maxChannels = parseInt(arg.substr(14), 0);
        } else if (startsWith(arg, L"--mateVersion=")) {
            result.mateVersion = toUtf8(arg.substr(14));
        } else if (startsWith(arg, L"--layoutMode=")) {
            result.layoutMode = parseInt(arg.substr(13), -1);
        } else if (arg == L"--self-test") {
            result.selfTest = true;
        }
    }

    return result;
}
