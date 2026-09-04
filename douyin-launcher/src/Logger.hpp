#pragma once

#include <filesystem>
#include <mutex>
#include <string>

class Logger {
public:
    explicit Logger(const std::filesystem::path& executableDir);

    void info(const std::string& message);
    void warn(const std::string& message);
    void error(const std::string& message);

private:
    void write(const char* level, const std::string& message);

    std::filesystem::path filePath_;
    std::mutex mutex_;
};
