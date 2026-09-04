# XiaoDouyinBridge Live Companion Launcher

Windows x64 C++17 client for the Douyin Live Companion interactive-plugin route.

## What it does

The launcher is started by Douyin Live Companion with arguments similar to:

```text
XiaoDouyinBridge.exe --pipeName=784612345 --maxChannels=6 --mateVersion=8.x.x --layoutMode=0
```

It then:

1. Loads the official `PipeSDK.dll` from the EXE directory.
2. Calls the official `CreatePipeClient` API and opens the supplied pipe.
3. Subscribes to `OPEN_LIVE_DATA` with `x.subscribeEvent` and the required millisecond timestamp.
4. Receives `EVENT_MESSAGE` callbacks from Live Companion.
5. Forwards the raw `OPEN_LIVE_DATA` JSON to the Bridge server over HTTPS.
6. Exits when Live Companion sends `EVENT_DISCONNECTED`, or when `OPEN_WIN_CLOSE` is received.

The Bridge consumes `live_comment` and `live_fansclub` events. Other event types are safely ignored by the current Minecraft workflow.

## Official PipeSDK

The repository does **not** redistribute the vendor DLL. Download `pure_PipeSDK.zip` from the official Douyin interactive-tool developer guide and copy:

```text
pure_PipeSDK/bin/x64/PipeSDK.dll
```

beside:

```text
XiaoDouyinBridge.exe
```

The launcher uses the exact `IPipeClient` ABI from the supplied official SDK and dynamically resolves `CreatePipeClient` at runtime. No vendor `.lib` is needed to compile the launcher.

## Configuration

Copy:

```text
launcher.conf.example
```

to:

```text
launcher.conf
```

and configure:

```ini
bridge.base-url=https://douyin.example.com
bridge.launcher-key=replace-with-a-random-secret
bridge.timeout-seconds=10
```

The key must be the same value as the Bridge server environment variable:

```text
XIAODOUYINBRIDGE_LAUNCHER_KEY
```

Do not commit the real key.

## Bridge endpoints

The launcher uses:

```text
GET  /api/douyin/launcher/health
POST /api/douyin/launcher/event
Header: X-Launcher-Key
```

## Build

Windows / Visual Studio 2022 build tools:

```powershell
cmake -S douyin-launcher -B douyin-launcher/build -A x64
cmake --build douyin-launcher/build --config Release
```

The GitHub Actions workflow also builds and publishes `XiaoDouyinBridge-Launcher-Windows-x64`.

## Binding flow

```text
Minecraft: /douyin bind
        ↓
6-digit code
        ↓
Viewer comments in the current live room: 绑定 123456
        ↓
Live Companion OPEN_LIVE_DATA / live_comment
        ↓
Launcher → Bridge → MariaDB
        ↓
Minecraft UUID ↔ Douyin sec_open_id
```

Fan-club changes arrive as `live_fansclub`; the Bridge updates the persisted level and the Minecraft plugin picks it up on its normal sync interval.
