# XiaoDouyinBridge

[![Build XiaoDouyinBridge](https://github.com/ZaneJason/XiaoDouyinBridge/actions/workflows/build.yml/badge.svg)](https://github.com/ZaneJason/XiaoDouyinBridge/actions/workflows/build.yml)

**XiaoDouyinBridge is an open-source integration bridge that connects Douyin Live Companion events to Minecraft Spigot servers through a Windows C++ launcher and a Java/Spring Boot service.**

抖音直播粉丝团与 Minecraft Spigot 1.21.1 服务器联动桥接项目。项目由 Windows C++ Launcher、Spring Boot Bridge Server 和 Minecraft 插件组成，目标是把直播互动事件以可部署、可维护的方式同步到游戏服务器。

> **Status:** Active development. GitHub Actions builds the Java components and the Windows launcher. Production launcher use requires the official Douyin x64 `PipeSDK.dll`, which is intentionally not redistributed by this repository.

- [Roadmap](ROADMAP.md)
- [Contributing](CONTRIBUTING.md)
- [Build workflow](https://github.com/ZaneJason/XiaoDouyinBridge/actions/workflows/build.yml)

## 当前架构

```text
抖音直播伴侣
   ↓ PipeSDK / OPEN_LIVE_DATA
XiaoDouyinBridge.exe（Windows 互动插件）
   ↓ HTTPS + X-Launcher-Key
阿里云 Bridge Server
   ↕ MariaDB
   ↑ HTTPS
Minecraft Spigot 插件
```

对于「互动插件 / 仅直播伴侣」产物，主链路直接使用直播伴侣 PipeSDK 推送的 `OPEN_LIVE_DATA`。这条数据里包含评论、粉丝团变更以及观众的 `sec_open_id` / `fansclub_level`，因此不需要 Launcher 自己猜 `room_id`。

Bridge 仍保留抖音官方服务端 HTTP callback / launch-token session 相关代码，方便其它接入模式和联调，但当前直播伴侣互动插件路线优先走 Launcher ingress。

## 当前能力

### Minecraft

- `/douyin bind` 生成 6 位绑定码
- `/douyin info` 查看绑定与粉丝团等级
- TAB / 玩家显示名展示 `[团Lv.X]`
- 粉丝团等级变化后由插件周期同步到游戏

### Bridge Server

- MariaDB 持久化绑定关系、粉丝团等级、10 分钟临时绑定码
- 启动时自动执行 `schema.sql` 建表
- 处理 `live_comment`：观众发送 `绑定 123456` 完成 MC ↔ 抖音账号绑定
- 处理 `live_fansclub`：加团 / 升级 / 退团同步到 MC
- 同时兼容 PipeSDK 字段 `sec_open_id` 与服务端回调字段 `sec_openid`
- `msg_id` 去重
- 官方 HTTP callback `x-signature` 验签
- 独立的 Launcher 接入 Key，不与 Minecraft API Key 共用
- 控制台 + 滚动文件日志

### Windows Launcher

当前已实现：

- 解析直播伴侣启动参数：`--pipeName` / `--maxChannels` / `--mateVersion` / `--layoutMode`
- `launcher.conf` / 环境变量配置
- HTTPS 连接 Bridge 与 Launcher health check
- 运行时从 EXE 同目录加载官方 x64 `PipeSDK.dll`
- 建立直播伴侣 Pipe 连接并订阅 `OPEN_LIVE_DATA`
- 原样转发 PipeSDK `EVENT_MESSAGE` JSON
- 收到 `OPEN_WIN_CLOSE`、`EVENT_DISCONNECTED`、broken/reset 事件后安全退出
- 日志输出到 `logs/xiaodouyin-launcher.log`
- GitHub Actions 编译并上传 Windows x64 Launcher artifact

> 官方 `PipeSDK.dll` 属于平台 SDK，不会提交或重新分发到本仓库。生产运行时请从抖音官方开发资源取得与当前平台版本匹配的 x64 DLL，并放到 `XiaoDouyinBridge.exe` 同目录。

## 项目结构

```text
XiaoDouyinBridge/
├─ bridge-server/       # Spring Boot Bridge 服务
├─ minecraft-plugin/    # Spigot 1.21.1 插件
└─ douyin-launcher/     # Windows C++17 直播伴侣互动插件
```

## 环境

- Java 21
- Maven 3.9+
- MariaDB 10.5+ / MySQL 兼容协议
- Spigot 1.21.1
- Windows 10/11 + Visual Studio 2019+ / CMake（Launcher）
- 抖音官方 PipeSDK（生产 Launcher）

## MariaDB

默认数据库名：

```text
xiaodouyinbridge
```

Bridge 启动时自动创建：

```text
xdb_binding
xdb_pending_binding
```

只需要提前创建数据库和数据库账号，不需要手工建表。

## Bridge 配置

Linux 推荐通过环境变量：

```bash
export XIAODOUYINBRIDGE_DB_URL='jdbc:mariadb://127.0.0.1:3306/xiaodouyinbridge'
export XIAODOUYINBRIDGE_DB_USER='xiaodouyinbridge'
export XIAODOUYINBRIDGE_DB_PASSWORD='你的数据库强密码'

# Minecraft 插件访问 Bridge
export XIAODOUYINBRIDGE_API_KEY='随机的 Minecraft Bridge Key'

# Windows 直播伴侣 EXE 访问 Bridge，单独使用一个 Key
export XIAODOUYINBRIDGE_LAUNCHER_KEY='另一个随机的 Launcher Key'

# 服务端官方 callback / OpenAPI 路线使用
export DOUYIN_APP_ID='ttxxxxxxxxxxxx'
export DOUYIN_APP_SECRET='你的 AppSecret'
export DOUYIN_DATA_SECRET='直播间数据能力开发配置里的数据密钥'
```

**不要把真实数据库密码、AppSecret、API Key、Launcher Key 或数据密钥提交到 GitHub。**

启动 Bridge：

```bash
mvn -pl bridge-server -am spring-boot:run
```

服务端联调日志：

```bash
tail -f logs/xiaodouyinbridge.log
```

## 直播伴侣 Launcher → Bridge

Bridge 提供：

```text
GET  /api/douyin/launcher/health
POST /api/douyin/launcher/event
```

两者都要求：

```text
X-Launcher-Key: <XIAODOUYINBRIDGE_LAUNCHER_KEY>
```

Launcher 把 PipeSDK 收到的完整消息原样 POST 到 `/api/douyin/launcher/event`。Bridge 只消费：

```json
{
  "type": "event",
  "eventName": "OPEN_LIVE_DATA",
  "params": {
    "payload": []
  }
}
```

其它 PipeSDK request/response/event 会被安全忽略。

## Launcher 本地配置

把：

```text
douyin-launcher/launcher.conf.example
```

复制成 EXE 同目录：

```text
launcher.conf
```

例如：

```properties
bridge.base-url=https://douyin.example.com
bridge.launcher-key=你的独立LauncherKey
bridge.timeout-seconds=10
```

也可以使用 Windows 环境变量：

```powershell
$env:XIAODOUYINBRIDGE_URL='https://douyin.example.com'
$env:XIAODOUYINBRIDGE_LAUNCHER_KEY='你的独立LauncherKey'
```

## Launcher 构建

Windows x64 构建：

```powershell
cmake -S douyin-launcher -B douyin-launcher/build -A x64
cmake --build douyin-launcher/build --config Release
```

构建过程不需要把官方 PipeSDK 提交到仓库，因为 Launcher 使用 Windows 动态加载方式解析 `PipeSDK.dll`。CI 因此可以验证 C++ Launcher 的编译，并生成不包含厂商 DLL 的 artifact。

生产运行时：

1. 从抖音官方开发资源取得当前 x64 `PipeSDK.dll`。
2. 将 DLL 放到 `XiaoDouyinBridge.exe` 同目录。
3. 复制 `launcher.conf.example` 为 `launcher.conf` 并配置 Bridge URL 与 Launcher Key。
4. 由抖音直播伴侣按平台约定启动 Launcher。

## 玩家绑定流程

```text
Minecraft 玩家
/douyin bind
       ↓
绑定码 572914
       ↓
玩家在主播直播间发送：绑定 572914
       ↓
直播伴侣 OPEN_LIVE_DATA / live_comment
       ↓
XiaoDouyinBridge.exe
       ↓ HTTPS
Bridge Server
       ↓
MariaDB：Minecraft UUID ↔ sec_open_id
```

评论事件本身会带 `fansclub_level`，所以首次绑定即可获得当前事件里的粉丝团等级；之后 `live_fansclub` 变更事件继续实时更新等级。

例如：

```text
live_fansclub
fansclub_reason_type = 1
fansclub_level = 13
       ↓
Bridge 更新 MariaDB
       ↓
Minecraft 插件周期同步
       ↓
[团Lv.13] Fee_God
```

`fansclub_reason_type = 16` 时等级同步为 `0`。

## Minecraft 插件配置

Bridge 与 Minecraft 不在同一台机器时：

```yaml
bridge:
  base-url: "https://douyin.example.com"
  api-key: "与 XIAODOUYINBRIDGE_API_KEY 一致"
  sync-seconds: 30
```

构建：

```bash
mvn -pl minecraft-plugin -am package
```

生成的 JAR 放进 Spigot 的 `plugins/` 目录。


## 开源协作

欢迎提交 Bug、兼容性问题和改进建议。提交代码前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)，当前开发方向见 [ROADMAP.md](ROADMAP.md)。

请勿在 Issue、PR、日志或配置示例中提交真实数据库密码、AppSecret、API Key、Launcher Key 或数据密钥。
