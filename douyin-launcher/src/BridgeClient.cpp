#include "BridgeClient.hpp"

#include <windows.h>
#include <winhttp.h>

#include <sstream>
#include <stdexcept>
#include <vector>

namespace {
std::wstring toWide(const std::string& value) {
    if (value.empty()) {
        return {};
    }
    const int size = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value.data(),
                                         static_cast<int>(value.size()), nullptr, 0);
    if (size <= 0) {
        throw std::runtime_error("invalid UTF-8 string");
    }
    std::wstring result(static_cast<size_t>(size), L'\0');
    MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value.data(),
                        static_cast<int>(value.size()), result.data(), size);
    return result;
}

std::string winError(const char* operation) {
    std::ostringstream out;
    out << operation << " failed, Win32 error=" << GetLastError();
    return out.str();
}

struct HttpHandle {
    HINTERNET value = nullptr;
    ~HttpHandle() {
        if (value != nullptr) {
            WinHttpCloseHandle(value);
        }
    }
};
}

BridgeClient::BridgeClient(LauncherConfig config, Logger& logger)
        : config_(std::move(config)), logger_(logger) {
}

bool BridgeClient::healthCheck() {
    unsigned long status = 0;
    const bool sent = request(L"GET", "/api/douyin/launcher/health", "", status);
    if (!sent || status < 200 || status >= 300) {
        logger_.error("[BRIDGE] Health check failed, HTTP status=" + std::to_string(status));
        return false;
    }
    logger_.info("[BRIDGE] Health check OK");
    return true;
}

bool BridgeClient::forwardPipeMessage(const std::string& jsonMessage) {
    if (jsonMessage.empty()) {
        return true;
    }

    unsigned long status = 0;
    const bool sent = request(L"POST", "/api/douyin/launcher/event", jsonMessage, status);
    if (!sent || status < 200 || status >= 300) {
        logger_.warn("[BRIDGE] Event forward failed, HTTP status=" + std::to_string(status));
        return false;
    }
    return true;
}

bool BridgeClient::request(const wchar_t* method,
                           const std::string& path,
                           const std::string& body,
                           unsigned long& statusCode) {
    statusCode = 0;
    try {
        const std::string fullUrl = config_.bridgeBaseUrl + path;
        std::wstring url = toWide(fullUrl);

        URL_COMPONENTS components{};
        components.dwStructSize = sizeof(components);
        components.dwSchemeLength = static_cast<DWORD>(-1);
        components.dwHostNameLength = static_cast<DWORD>(-1);
        components.dwUrlPathLength = static_cast<DWORD>(-1);
        components.dwExtraInfoLength = static_cast<DWORD>(-1);

        if (!WinHttpCrackUrl(url.c_str(), static_cast<DWORD>(url.size()), 0, &components)) {
            logger_.error("[BRIDGE] " + winError("WinHttpCrackUrl"));
            return false;
        }

        const std::wstring host(components.lpszHostName, components.dwHostNameLength);
        std::wstring requestPath(components.lpszUrlPath, components.dwUrlPathLength);
        if (components.dwExtraInfoLength > 0) {
            requestPath.append(components.lpszExtraInfo, components.dwExtraInfoLength);
        }
        if (requestPath.empty()) {
            requestPath = L"/";
        }

        HttpHandle session;
        session.value = WinHttpOpen(L"XiaoDouyinBridgeLauncher/1.0",
                                    WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY,
                                    WINHTTP_NO_PROXY_NAME,
                                    WINHTTP_NO_PROXY_BYPASS,
                                    0);
        if (session.value == nullptr) {
            logger_.error("[BRIDGE] " + winError("WinHttpOpen"));
            return false;
        }

        const int timeoutMs = config_.requestTimeoutSeconds * 1000;
        WinHttpSetTimeouts(session.value, timeoutMs, timeoutMs, timeoutMs, timeoutMs);

        HttpHandle connection;
        connection.value = WinHttpConnect(session.value, host.c_str(), components.nPort, 0);
        if (connection.value == nullptr) {
            logger_.error("[BRIDGE] " + winError("WinHttpConnect"));
            return false;
        }

        DWORD flags = components.nScheme == INTERNET_SCHEME_HTTPS ? WINHTTP_FLAG_SECURE : 0;
        HttpHandle requestHandle;
        requestHandle.value = WinHttpOpenRequest(connection.value,
                                                 method,
                                                 requestPath.c_str(),
                                                 nullptr,
                                                 WINHTTP_NO_REFERER,
                                                 WINHTTP_DEFAULT_ACCEPT_TYPES,
                                                 flags);
        if (requestHandle.value == nullptr) {
            logger_.error("[BRIDGE] " + winError("WinHttpOpenRequest"));
            return false;
        }

        const std::wstring launcherHeader = L"X-Launcher-Key: " + toWide(config_.launcherKey) + L"\r\n";
        const std::wstring contentType = L"Content-Type: application/json; charset=utf-8\r\n";
        const std::wstring headers = launcherHeader + contentType;

        LPVOID bodyPtr = body.empty() ? WINHTTP_NO_REQUEST_DATA : const_cast<char*>(body.data());
        DWORD bodySize = static_cast<DWORD>(body.size());

        if (!WinHttpSendRequest(requestHandle.value,
                                headers.c_str(),
                                static_cast<DWORD>(headers.size()),
                                bodyPtr,
                                bodySize,
                                bodySize,
                                0)) {
            logger_.error("[BRIDGE] " + winError("WinHttpSendRequest"));
            return false;
        }

        if (!WinHttpReceiveResponse(requestHandle.value, nullptr)) {
            logger_.error("[BRIDGE] " + winError("WinHttpReceiveResponse"));
            return false;
        }

        DWORD statusSize = sizeof(statusCode);
        if (!WinHttpQueryHeaders(requestHandle.value,
                                 WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                                 WINHTTP_HEADER_NAME_BY_INDEX,
                                 &statusCode,
                                 &statusSize,
                                 WINHTTP_NO_HEADER_INDEX)) {
            logger_.error("[BRIDGE] " + winError("WinHttpQueryHeaders"));
            return false;
        }

        return true;
    } catch (const std::exception& e) {
        logger_.error(std::string("[BRIDGE] HTTP client exception: ") + e.what());
        return false;
    }
}
