# XiaoDouyinBridge

抖音直播粉丝团与 Minecraft Spigot 服务器联动桥接项目。

## 第一阶段目标

- Minecraft 玩家执行 `/douyin bind` 获取 6 位绑定码
- Bridge 保存 Minecraft UUID 与绑定码
- 通过开发接口模拟抖音用户完成绑定
- 同步 `fansClubLevel` 到 Minecraft
- TAB 列表与玩家显示名展示 `[团Lv.X]`
- 粉丝团升级时全服广播

> 第一阶段先打通 Minecraft ↔ Bridge 链路。抖音官方直播互动事件将在下一阶段接入并替换开发模拟接口。

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

## 本地启动 Bridge

```bash
mvn -pl bridge-server -am spring-boot:run
```

默认地址：`http://127.0.0.1:8765`

默认 API Key：`change-me`

正式部署前请修改 `bridge.api-key`。

## Minecraft 插件

构建：

```bash
mvn -pl minecraft-plugin -am package
```

生成的 JAR 放进 Spigot 的 `plugins/` 目录。

配置文件：

```yaml
bridge:
  base-url: "http://127.0.0.1:8765"
  api-key: "change-me"
  sync-seconds: 30
```

## 玩家命令

```text
/douyin bind
/douyin info
/douyin reload
```

`reload` 仅管理员可用。

## 第一阶段模拟流程

1. Minecraft 玩家执行 `/douyin bind`
2. 得到类似 `572914` 的绑定码
3. 使用开发接口模拟抖音完成绑定
4. 插件周期同步粉丝团等级
5. 玩家 TAB / 显示名变成 `[团Lv.12] Fee_God`

后续会把第 3 步替换成抖音官方直播评论/粉丝团事件处理。