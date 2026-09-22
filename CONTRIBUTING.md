# Contributing to XiaoDouyinBridge

Thanks for your interest in improving XiaoDouyinBridge.

The project spans three areas:

- `bridge-server/`: Java 21 / Spring Boot bridge service
- `minecraft-plugin/`: Spigot 1.21.1 plugin
- `douyin-launcher/`: Windows C++17 launcher for Douyin Live Companion

## Before opening a change

Please open an issue first for:

- behavior changes that affect the protocol between components
- changes to the Douyin PipeSDK integration
- new persistent database fields or migrations
- security-sensitive changes involving API keys, signatures, or authentication

Small documentation fixes and narrowly scoped bug fixes can go directly to a pull request.

## Development setup

### Java modules

Requirements:

- Java 21
- Maven 3.9+

Build all Java modules:

```bash
mvn -B package
```

Build only the Minecraft plugin:

```bash
mvn -pl minecraft-plugin -am package
```

Run the bridge server locally:

```bash
mvn -pl bridge-server -am spring-boot:run
```

### Windows launcher

Requirements:

- Windows 10/11
- Visual Studio 2019 or newer
- CMake 3.20+

Build:

```powershell
cmake -S douyin-launcher -B douyin-launcher/build -A x64
cmake --build douyin-launcher/build --config Release
```

The official `PipeSDK.dll` is not redistributed by this repository. For production testing, place the official x64 DLL from Douyin beside `XiaoDouyinBridge.exe`.

## Pull request checklist

Before submitting a pull request:

- keep the change focused and explain the problem it solves
- do not commit real database passwords, AppSecret values, API keys, launcher keys, or data secrets
- update documentation when configuration or runtime behavior changes
- verify `mvn -B package` for Java changes
- verify the Windows launcher build for C++ changes when applicable
- describe manual test steps for behavior that cannot be covered by CI

## Commit style

Short Conventional Commit-style messages are preferred, for example:

- `feat: add ...`
- `fix: handle ...`
- `docs: clarify ...`
- `ci: update ...`
- `chore: ...`

## Reporting security issues

Please do not publish secrets or exploit details in a public issue. If a report contains sensitive material, contact the maintainer privately through GitHub before sharing details publicly.
