#include "PipeSdkAdapter.hpp"

#include <windows.h>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <filesystem>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>
#include <utility>

// This is the exact ABI subset used by the official pure_PipeSDK package supplied
// by the developer. We load PipeSDK.dll dynamically so the vendor binary does not
// need to be committed to this public repository; production packaging only needs
// to place the official x64 PipeSDK.dll next to XiaoDouyinBridge.exe.
namespace DouyinPipeSdkAbi {

enum IPC_EVENT_TYPE {
    EVENT_CONNECTED,
    EVENT_BROKEN,
    EVENT_DISCONNECTED,
    EVENT_MESSAGE,
    EVENT_CONNECTION_RESET,
    EVENT_PACKET,
};

struct IPC_PACKET;

typedef void (*PIPE_CALLBACK)(IPC_EVENT_TYPE, UINT32, const CHAR*, UINT32, void*);

class IMSUnknown {
public:
    virtual LONG AddRef() = 0;
    virtual LONG Release() = 0;
    virtual LONG QueryInterface(const GUID& riid, void** lpp) = 0;
};

class IPipeClient : public IMSUnknown {
public:
    virtual BOOL IsConnected() = 0;
    virtual void SetCallback(PIPE_CALLBACK cb, void* args) = 0;
    virtual BOOL Open(LPCWSTR pszNAME, UINT32 maxChannels) = 0;
    virtual void Close() = 0;
    virtual BOOL SendMessage(UINT32 mss, const CHAR* data, UINT32 size) = 0;
    virtual BOOL WritePacket(const IPC_PACKET* apk, UINT32 timeout) = 0;
    virtual BOOL WritePacketAsync(const IPC_PACKET* apk) = 0;
};

using CreatePipeClientFn = BOOL (WINAPI*)(IPipeClient**);
using QueryVersionFn = LPCSTR (WINAPI*)();

} // namespace DouyinPipeSdkAbi

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

std::wstring utf8ToWide(const std::string& value) {
    if (value.empty()) {
        return {};
    }
    const int size = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS,
                                         value.data(), static_cast<int>(value.size()),
                                         nullptr, 0);
    if (size <= 0) {
        throw std::runtime_error("invalid UTF-8 pipe name");
    }
    std::wstring result(static_cast<size_t>(size), L'\0');
    if (MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS,
                            value.data(), static_cast<int>(value.size()),
                            result.data(), size) <= 0) {
        throw std::runtime_error("failed to convert pipe name to UTF-16");
    }
    return result;
}

std::string win32Error(const char* operation) {
    std::ostringstream out;
    out << operation << " failed, Win32 error=" << GetLastError();
    return out.str();
}

std::int64_t unixMillis() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count();
}

bool containsEventName(const std::string& json, const char* eventName) {
    const std::string compact = std::string("\"eventName\":\"") + eventName + "\"";
    const std::string spaced = std::string("\"eventName\": \"") + eventName + "\"";
    return json.find(compact) != std::string::npos || json.find(spaced) != std::string::npos;
}

class OfficialPipeSdkAdapter final : public PipeSdkAdapter {
public:
    explicit OfficialPipeSdkAdapter(Logger& logger) : logger_(logger) {
        const auto dllPath = executableDirectory() / L"PipeSDK.dll";
        module_ = LoadLibraryW(dllPath.c_str());
        if (module_ == nullptr) {
            loadError_ = "PipeSDK.dll not found next to XiaoDouyinBridge.exe (" +
                         win32Error("LoadLibraryW") + ")";
            return;
        }

        createPipeClient_ = reinterpret_cast<DouyinPipeSdkAbi::CreatePipeClientFn>(
                GetProcAddress(module_, "CreatePipeClient"));
        queryVersion_ = reinterpret_cast<DouyinPipeSdkAbi::QueryVersionFn>(
                GetProcAddress(module_, "QueryVersion"));

        if (createPipeClient_ == nullptr) {
            loadError_ = "PipeSDK.dll is missing CreatePipeClient export";
            FreeLibrary(module_);
            module_ = nullptr;
            return;
        }

        if (queryVersion_ != nullptr) {
            const char* version = queryVersion_();
            if (version != nullptr) {
                logger_.info(std::string("[PIPESDK] SDK version=") + version);
            }
        }
    }

    ~OfficialPipeSdkAdapter() override {
        shutdownClient();
        if (module_ != nullptr) {
            FreeLibrary(module_);
            module_ = nullptr;
        }
    }

    bool available() const override {
        if (module_ == nullptr || createPipeClient_ == nullptr) {
            if (!availabilityLogged_.exchange(true)) {
                logger_.error("[PIPESDK] " + loadError_);
            }
            return false;
        }
        return true;
    }

    bool connect(const LauncherArgs& args, MessageCallback callback) override {
        if (!available()) {
            return false;
        }
        if (!args.hasPipeArgs()) {
            logger_.error("[PIPESDK] pipeName/maxChannels missing");
            return false;
        }

        callback_ = std::move(callback);
        disconnected_.store(false);
        connected_.store(false);

        DouyinPipeSdkAbi::IPipeClient* client = nullptr;
        if (!createPipeClient_(&client) || client == nullptr) {
            logger_.error("[PIPESDK] CreatePipeClient failed");
            return false;
        }
        client_ = client;
        client_->SetCallback(&OfficialPipeSdkAdapter::pipeCallback, this);

        const std::wstring pipeName = utf8ToWide(args.pipeName);
        logger_.info("[PIPESDK] Opening Live Companion pipe, maxChannels=" +
                     std::to_string(args.maxChannels));
        if (!client_->Open(pipeName.c_str(), static_cast<UINT32>(args.maxChannels))) {
            logger_.error("[PIPESDK] IPipeClient::Open returned FALSE");
            shutdownClient();
            return false;
        }

        if (client_->IsConnected()) {
            connected_.store(true);
        }

        std::unique_lock<std::mutex> lock(stateMutex_);
        stateCv_.wait_for(lock, std::chrono::seconds(8), [&] {
            return connected_.load() || disconnected_.load();
        });

        if (!connected_.load() || disconnected_.load()) {
            logger_.error("[PIPESDK] Timed out waiting for EVENT_CONNECTED");
            shutdownClient();
            return false;
        }

        logger_.info("[PIPESDK] Connected to Douyin Live Companion");
        return true;
    }

    bool subscribeOpenLiveData() override {
        if (client_ == nullptr || !connected_.load()) {
            return false;
        }

        if (!subscribeEvent("OPEN_LIVE_DATA")) {
            return false;
        }

        // This event lets the launcher exit cleanly when the streamer closes the
        // plugin control panel. EVENT_DISCONNECTED is still treated as authoritative.
        if (!subscribeEvent("OPEN_WIN_CLOSE")) {
            logger_.warn("[PIPESDK] OPEN_WIN_CLOSE subscription failed; EVENT_DISCONNECTED will still stop the launcher");
        }
        return true;
    }

    void runUntilDisconnected() override {
        std::unique_lock<std::mutex> lock(stateMutex_);
        stateCv_.wait(lock, [&] { return disconnected_.load(); });
    }

private:
    static void pipeCallback(DouyinPipeSdkAbi::IPC_EVENT_TYPE type,
                             UINT32 messageId,
                             const CHAR* data,
                             UINT32 size,
                             void* args) {
        if (args == nullptr) {
            return;
        }
        static_cast<OfficialPipeSdkAdapter*>(args)->onPipeEvent(type, messageId, data, size);
    }

    void onPipeEvent(DouyinPipeSdkAbi::IPC_EVENT_TYPE type,
                     UINT32 messageId,
                     const CHAR* data,
                     UINT32 size) {
        switch (type) {
            case DouyinPipeSdkAbi::EVENT_CONNECTED:
                connected_.store(true);
                logger_.info("[PIPESDK] EVENT_CONNECTED");
                stateCv_.notify_all();
                break;

            case DouyinPipeSdkAbi::EVENT_MESSAGE: {
                std::string message;
                if (data != nullptr && size > 0) {
                    message.assign(data, data + size);
                }
                if (message.empty()) {
                    return;
                }

                if (containsEventName(message, "OPEN_WIN_CLOSE")) {
                    logger_.info("[PIPESDK] OPEN_WIN_CLOSE received");
                    disconnected_.store(true);
                    stateCv_.notify_all();
                }

                // Forward EVENT_MESSAGE unchanged. Bridge only consumes
                // type=event,eventName=OPEN_LIVE_DATA and safely ignores responses.
                if (callback_) {
                    callback_(message);
                }
                break;
            }

            case DouyinPipeSdkAbi::EVENT_DISCONNECTED:
                logger_.info("[PIPESDK] EVENT_DISCONNECTED received");
                disconnected_.store(true);
                connected_.store(false);
                stateCv_.notify_all();
                break;

            case DouyinPipeSdkAbi::EVENT_BROKEN:
                logger_.warn("[PIPESDK] EVENT_BROKEN received; stopping launcher");
                disconnected_.store(true);
                connected_.store(false);
                stateCv_.notify_all();
                break;

            case DouyinPipeSdkAbi::EVENT_CONNECTION_RESET:
                logger_.warn("[PIPESDK] EVENT_CONNECTION_RESET received; stopping launcher");
                disconnected_.store(true);
                connected_.store(false);
                stateCv_.notify_all();
                break;

            case DouyinPipeSdkAbi::EVENT_PACKET:
                break;
        }

        (void) messageId;
    }

    bool subscribeEvent(const char* eventName) {
        const std::int64_t timestamp = unixMillis();
        const UINT32 messageId = nextMessageId_.fetch_add(1);
        const std::string requestId = "xdb-" + std::to_string(timestamp) + "-" + std::to_string(messageId);

        std::ostringstream json;
        json << "{\"type\":\"request\","
             << "\"reqId\":\"" << requestId << "\","
             << "\"method\":\"x.subscribeEvent\","
             << "\"params\":{\"eventName\":\"" << eventName << "\","
             << "\"timestamp\":" << timestamp << "}}";

        const std::string payload = json.str();
        if (!client_->SendMessage(messageId, payload.data(), static_cast<UINT32>(payload.size()))) {
            logger_.error(std::string("[PIPESDK] SendMessage failed while subscribing ") + eventName);
            return false;
        }

        logger_.info(std::string("[PIPESDK] Subscription request sent: ") + eventName +
                     " reqId=" + requestId);
        return true;
    }

    void shutdownClient() {
        DouyinPipeSdkAbi::IPipeClient* client = client_;
        client_ = nullptr;
        if (client != nullptr) {
            client->SetCallback(nullptr, nullptr);
            client->Close();
            client->Release();
        }
        connected_.store(false);
    }

    Logger& logger_;
    HMODULE module_ = nullptr;
    DouyinPipeSdkAbi::CreatePipeClientFn createPipeClient_ = nullptr;
    DouyinPipeSdkAbi::QueryVersionFn queryVersion_ = nullptr;
    DouyinPipeSdkAbi::IPipeClient* client_ = nullptr;
    MessageCallback callback_;

    std::atomic<bool> connected_{false};
    std::atomic<bool> disconnected_{false};
    mutable std::atomic<bool> availabilityLogged_{false};
    std::atomic<UINT32> nextMessageId_{1};
    std::mutex stateMutex_;
    std::condition_variable stateCv_;
    std::string loadError_ = "official PipeSDK is unavailable";
};

} // namespace

std::unique_ptr<PipeSdkAdapter> createPipeSdkAdapter(Logger& logger) {
    return std::make_unique<OfficialPipeSdkAdapter>(logger);
}
