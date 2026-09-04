# XiaoDouyinBridge

抖音直播粉丝团与 Minecraft Spigot 1.21.1 服务器联动桥接项目。

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

已完成：

- 解析直播伴侣启动参数：`--pipeName` / `--maxChannels` / `--mateVersion` / `--layoutMode`
- `launcher.conf` / 环境变量配置
- HTTPS 连接 Bridge
- Launcher health check
- 原样转发 PipeSDK EVENT_MESSAGE JSON
- 日志输出到 `logs/xiaodouyin-launcher.log`
- PipeSDK 做成独立 adapter，避免把平台 ABI 散落在业务代码里

待完成：

- 使用官方当前版本 `pure_PipeSDK.zip` 的真实头文件 / lib 完成 `PipeSdkAdapterOfficial.cpp`
- 订阅 `OPEN_LIVE_DATA`
- 收到 `EVENT_DISCONNECTED` / `OPEN_WIN_CLOSE` 后退出
- 最终生成可上传抖音开放平台的生产 EXE 包

> 仓库不会猜测或手写第三方 C++ ABI。生产 adapter 必须对照你从抖音官方文档下载的实际 PipeSDK 版本编译。

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

不带官方 PipeSDK 的 core CI 编译：

```powershell
cmake -S douyin-launcher -B douyin-launcher/build -DXDB_WITH_PIPESDK=OFF
cmake --build douyin-launcher/build --config Release
```

这只能验证 Launcher 的参数解析、配置、日志和 HTTPS Bridge 通信，不是最终抖音生产包。

生产构建需要先从抖音官方文档下载当前 `pure_PipeSDK.zip`，然后按实际 SDK 目录配置：

```powershell
cmake -S douyin-launcher -B douyin-launcher/build `
  -DXDB_WITH_PIPESDK=ON `
  -DXDB_PIPESDK_ROOT='D:\sdk\PipeSDK'
```

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
