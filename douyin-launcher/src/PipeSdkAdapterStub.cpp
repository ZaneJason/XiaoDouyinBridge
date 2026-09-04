#include "PipeSdkAdapter.hpp"

class PipeSdkAdapterStub final : public PipeSdkAdapter {
public:
    explicit PipeSdkAdapterStub(Logger& logger) : logger_(logger) {}

    bool connect(const LauncherArgs&, MessageCallback) override {
        logger_.error("[PIPESDK] Official PipeSDK is not linked in this build.");
        logger_.error("[PIPESDK] Download pure_PipeSDK.zip from the Douyin developer console/docs and build with XDB_WITH_PIPESDK=ON.");
        return false;
    }

    bool subscribeOpenLiveData() override {
        return false;
    }

    void runUntilDisconnected() override {
    }

    bool available() const override {
        return false;
    }

private:
    Logger& logger_;
};

std::unique_ptr<PipeSdkAdapter> createPipeSdkAdapter(Logger& logger) {
    return std::make_unique<PipeSdkAdapterStub>(logger);
}
