# Official Douyin PipeSDK

This directory is intentionally empty in Git.

Download the current **pure_PipeSDK.zip** from the official Douyin Open Platform document:

- 互动工具开发者指南（仅直播伴侣）

Do not copy an SDK from an unofficial mirror and do not commit vendor binaries/secrets to this repository unless redistribution is explicitly permitted by its license.

After extracting the official package, keep its original `include` / `lib` / DLL layout. The final `PipeSdkAdapterOfficial.cpp` must be compiled against the exact header version from that package so we do not guess or hard-code a vendor ABI.

The platform currently starts the EXE with arguments similar to:

```text
XiaoDouyinBridge.exe --pipeName=784612345 --maxChannels=6 --mateVersion=8.x.x --layoutMode=0
```

The launcher subscribes to:

```text
OPEN_LIVE_DATA
```

and forwards the original PipeSDK event JSON to:

```text
POST /api/douyin/launcher/event
X-Launcher-Key: <launcher key>
```
