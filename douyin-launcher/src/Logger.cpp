#include "Logger.hpp"

#include <chrono>
#include <ctime>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <sstream>

Logger::Logger(const std::filesystem::path& executableDir) {
    const auto logDir = executableDir / "logs";
    std::error_code ec;
    std::filesystem::create_directories(logDir, ec);
    filePath_ = logDir / "xiaodouyin-launcher.log";
}

void Logger::info(const std::string& message) {
    write("INFO", message);
}

void Logger::warn(const std::string& message) {
    write("WARN", message);
}

void Logger::error(const std::string& message) {
    write("ERROR", message);
}

void Logger::write(const char* level, const std::string& message) {
    std::lock_guard<std::mutex> lock(mutex_);

    const auto now = std::chrono::system_clock::now();
    const std::time_t nowTime = std::chrono::system_clock::to_time_t(now);
    std::tm local{};
    localtime_s(&local, &nowTime);

    std::ostringstream line;
    line << std::put_time(&local, "%Y-%m-%d %H:%M:%S")
         << " " << level << " " << message;

    std::cout << line.str() << std::endl;

    std::ofstream output(filePath_, std::ios::app | std::ios::binary);
    if (output) {
        output << line.str() << "\n";
    }
}
