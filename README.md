# XiaoDouyinBridge

抖音直播粉丝团与 Minecraft Spigot 1.21.1 服务器联动桥接项目。

## 当前能力

### Minecraft

- `/douyin bind` 生成 6 位绑定码
- `/douyin info` 查看绑定与粉丝团等级
- TAB / 玩家显示名展示 `[团Lv.X]`
- 粉丝团等级变化后由插件周期同步到游戏

### Bridge Server

- 接收抖音官方 HTTP 直播数据回调
- 按官方算法校验 `x-signature`
- 处理 `live_fansclub` 真实加团 / 升级 / 退团事件
- 处理 `live_comment`，支持观众发送 `绑定 123456` 完成 MC ↔ 抖音账号绑定
- 根据 `sec_openid` 把粉丝团变化同步到对应 Minecraft UUID
- 调用抖音官方 `getAccessToken`
- 调用官方「获取直播信息」接口
- 启动 `live_fansclub` / `live_comment` 数据推送任务
- 绑定后可调用官方「获取粉丝团信息」接口做一次校准
- 对 `msg_id` 做去重，避免平台重复推送导致重复处理

> `api/dev/fansclub/*` 模拟接口仍保留用于本地测试，但正式链路不依赖它。

## 项目结构

```text
XiaoDouyinBridge/
├─ bridge-server/       # Spring Boot Bridge 服务
└─ minecraft-plugin/    # Spigot 1.21.1 插件
```

## 环境

- Java 21
- Maven 3.9+
- Spigot 1.21.1

## Bridge 配置

推荐通过环境变量配置：

```bash
export XIAODOUYINBRIDGE_API_KEY='换成你自己的随机字符串'
export DOUYIN_APP_ID='ttxxxxxxxxxxxx'
export DOUYIN_APP_SECRET='你的 AppSecret'
export DOUYIN_DATA_SECRET='直播间数据能力开发配置里的数据密钥'
```

Windows PowerShell：

```powershell
$env:XIAODOUYINBRIDGE_API_KEY='换成你自己的随机字符串'
$env:DOUYIN_APP_ID='ttxxxxxxxxxxxx'
$env:DOUYIN_APP_SECRET='你的 AppSecret'
$env:DOUYIN_DATA_SECRET='直播间数据能力开发配置里的数据密钥'
```

**不要把真实 AppSecret 或数据密钥提交到 GitHub。**

启动 Bridge：

```bash
mvn -pl bridge-server -am spring-boot:run
```

默认监听：

```text
http://0.0.0.0:8765
```

## 抖音开放平台要做的事情

1. 创建「直播小玩法 / 互动插件」应用并通过对应准入流程。
2. 申请「获取粉丝团互动数据」能力。
3. 为“评论绑定”同时申请直播间评论互动数据能力。
4. 如控制台提供「直播间观众粉丝团详细信息」能力，也建议一并申请。
5. 在「直播间数据能力开发配置」中配置数据推送地址和数据密钥。
6. 正式环境回调地址指向：

```text
https://你的域名/api/douyin/live-data/callback
```

该路径同时支持 `HEAD`，供开放平台自测工具检查可用性。

## 启动真实直播数据推送

### 方式 A：通过玩法 launch token（推荐）

直播伴侣 / 玩法客户端启动时拿到 launch token 后，请求 Bridge：

```http
POST /api/douyin/session/start
X-Bridge-Key: <你的 bridge key>
Content-Type: application/json

{
  "launchToken": "抖音启动玩法时给的 token"
}
```

Bridge 会通过官方接口取得 `room_id` / `anchor_open_id`，再启动：

```text
live_fansclub
live_comment
```

### 方式 B：已知直播间信息时手动启动

```http
POST /api/douyin/session/start-manual
X-Bridge-Key: <你的 bridge key>
Content-Type: application/json

{
  "roomId": "直播间 roomId",
  "anchorOpenId": "主播 openId",
  "anchorNickname": "主播昵称"
}
```

查看 Bridge 当前状态：

```http
GET /api/douyin/session/status
X-Bridge-Key: <你的 bridge key>
```

## 玩家真实绑定流程

Minecraft 玩家：

```text
/douyin bind
```

例如游戏返回：

```text
绑定码：572914
```

该玩家使用自己的抖音账号，在当前已经挂载 XiaoDouyinBridge 玩法的直播间发送：

```text
绑定 572914
```

抖音平台真实评论回调：

```text
live_comment
  ↓
sec_openid + nickname + 绑定码
  ↓
XiaoDouyinBridge
  ↓
Minecraft UUID ↔ 抖音 sec_openid
```

之后真实粉丝团事件：

```text
live_fansclub
fansclub_reason_type = 1   # 升级
fansclub_level = 13
  ↓
Bridge 找到绑定的 Minecraft UUID
  ↓
[团Lv.13] Fee_God
```

退团事件如果平台下发 `fansclub_reason_type = 16`，Bridge 会把等级同步为 0。

## 关于“真实数据”

正式模式下等级来源不是手工填写：

- 实时等级变化以抖音官方 `live_fansclub` 回调中的 `fansclub_level` 为准。
- Bridge 会使用开放平台配置的数据密钥验证 `x-signature`，验签失败的请求直接拒绝。
- 绑定时还会尝试调用官方「获取粉丝团信息」接口进行校准；该接口官方响应字段名为 `level_layer`。

只有当应用已经获得对应开放能力、玩法实际挂载在直播间并成功启动数据推送任务后，抖音才会向 Bridge 推送真实数据。

## Minecraft 插件配置

```yaml
bridge:
  base-url: "http://127.0.0.1:8765"
  api-key: "与 XIAODOUYINBRIDGE_API_KEY 一致"
  sync-seconds: 30
```

构建：

```bash
mvn -pl minecraft-plugin -am package
```

生成的 JAR 放进 Spigot 的 `plugins/` 目录。
