#include "PipeSdkAdapter.hpp"

// IMPORTANT:
// This translation unit must be completed against the exact header files from the
// official Douyin pure_PipeSDK.zip package. The public documentation describes the
// behavior (CreatePipeClient, SetCallback, SendMessage and EVENT_DISCONNECTED) but
// does not publish enough ABI detail to safely redeclare the vendor C++ interface.
//
// We intentionally do not guess the ABI here. Once the official SDK package is
// placed under douyin-launcher/third_party/PipeSDK (or supplied through
// XDB_PIPESDK_ROOT), wire its real headers/library into CMake and implement:
//
// 1. CreatePipeClient(pipeName, maxChannels, ...)
// 2. IPipeClient::SetCallback(...)
// 3. x.subscribeEvent for OPEN_LIVE_DATA using SendMessage
// 4. Forward EVENT_MESSAGE JSON unchanged to MessageCallback
// 5. Exit when EVENT_DISCONNECTED / OPEN_WIN_CLOSE is received

#error "Official Douyin PipeSDK headers are required to build the production launcher adapter."
