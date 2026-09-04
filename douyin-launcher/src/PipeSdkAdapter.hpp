#pragma once

#include "LauncherArgs.hpp"
#include "Logger.hpp"

#include <atomic>
#include <functional>
#include <memory>
#include <string>

class PipeSdkAdapter {
public:
    using MessageCallback = std::function<void(const std::string&)>;

    virtual ~PipeSdkAdapter() = default;

    virtual bool connect(const LauncherArgs& args, MessageCallback callback) = 0;
    virtual bool subscribeOpenLiveData() = 0;
    virtual void runUntilDisconnected() = 0;
    virtual bool available() const = 0;
};

std::unique_ptr<PipeSdkAdapter> createPipeSdkAdapter(Logger& logger);
