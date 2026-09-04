package com.zanejason.xiaodouyinbridge.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class DouyinCommand implements CommandExecutor, TabCompleter {
    private final XiaoDouyinBridgePlugin plugin;

    public DouyinCommand(XiaoDouyinBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("xiaodouyin.admin")) {
                sender.sendMessage(ChatColor.RED + "你没有权限执行这个命令。");
                return true;
            }
            plugin.reloadBridgeConfig();
            sender.sendMessage(ChatColor.GREEN + "XiaoDouyinBridge 配置已重载。");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令需要玩家在游戏内执行。");
            return true;
        }

        if (args[0].equalsIgnoreCase("bind")) {
            player.sendMessage(ChatColor.GRAY + "正在生成抖音绑定码...");
            plugin.bridgeClient().requestBinding(player.getUniqueId(), player.getName())
                    .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        if (error != null) {
                            player.sendMessage(ChatColor.RED + "生成绑定码失败：" + rootMessage(error));
                            return;
                        }
                        player.sendMessage("");
                        player.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━ " + ChatColor.LIGHT_PURPLE
                                + ChatColor.BOLD + "抖音账号绑定" + ChatColor.RESET + ChatColor.DARK_GRAY + " ━━━━━━━━");
                        player.sendMessage(ChatColor.WHITE + "你的绑定码：" + ChatColor.YELLOW + ChatColor.BOLD + result.code());
                        player.sendMessage(ChatColor.GRAY + "有效期 10 分钟。下一步将在直播间发送该绑定码完成绑定。");
                        player.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("");
                    }));
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {
            plugin.bridgeClient().getBinding(player.getUniqueId())
                    .whenComplete((binding, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        if (error != null) {
                            player.sendMessage(ChatColor.RED + "查询失败：" + rootMessage(error));
                            return;
                        }
                        if (binding.isEmpty()) {
                            player.sendMessage(ChatColor.YELLOW + "你还没有绑定抖音账号。使用 /douyin bind 开始绑定。");
                            return;
                        }

                        BridgeClient.BindingInfo info = binding.get();
                        player.sendMessage("");
                        player.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━ " + ChatColor.LIGHT_PURPLE
                                + ChatColor.BOLD + "小理粉丝团" + ChatColor.RESET + ChatColor.DARK_GRAY + " ━━━━━━━━");
                        player.sendMessage(ChatColor.WHITE + "Minecraft：" + ChatColor.AQUA + info.minecraftName());
                        player.sendMessage(ChatColor.WHITE + "抖音昵称：" + ChatColor.YELLOW + info.douyinNickname());
                        player.sendMessage(ChatColor.WHITE + "粉丝团等级：" + ChatColor.LIGHT_PURPLE + ChatColor.BOLD + "Lv." + info.fansClubLevel());
                        player.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("");
                    }));
            return true;
        }

        sendUsage(sender);
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "/douyin bind " + ChatColor.GRAY + "- 生成绑定码");
        sender.sendMessage(ChatColor.YELLOW + "/douyin info " + ChatColor.GRAY + "- 查看绑定和粉丝团等级");
        if (sender.hasPermission("xiaodouyin.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/douyin reload " + ChatColor.GRAY + "- 重载配置");
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        List<String> options = sender.hasPermission("xiaodouyin.admin")
                ? List.of("bind", "info", "reload")
                : List.of("bind", "info");
        String prefix = args[0].toLowerCase();
        return options.stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
