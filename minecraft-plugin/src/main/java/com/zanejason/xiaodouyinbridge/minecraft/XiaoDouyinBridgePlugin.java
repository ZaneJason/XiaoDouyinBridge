package com.zanejason.xiaodouyinbridge.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class XiaoDouyinBridgePlugin extends JavaPlugin {
    private final Map<UUID, Integer> knownLevels = new ConcurrentHashMap<>();
    private BridgeClient bridgeClient;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        rebuildClient();

        DouyinCommand command = new DouyinCommand(this);
        if (getCommand("douyin") != null) {
            getCommand("douyin").setExecutor(command);
            getCommand("douyin").setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new PlayerSyncListener(this), this);
        startSyncTask();
        getLogger().info("XiaoDouyinBridge 已启动。");
    }

    public void reloadBridgeConfig() {
        reloadConfig();
        rebuildClient();
    }

    public BridgeClient bridgeClient() {
        return bridgeClient;
    }

    public void syncPlayer(Player player, boolean announceUpgrade) {
        bridgeClient.getBinding(player.getUniqueId())
                .whenComplete((binding, error) -> {
                    if (error != null) {
                        getLogger().warning("同步 " + player.getName() + " 粉丝团等级失败: " + error.getMessage());
                        return;
                    }
                    Bukkit.getScheduler().runTask(this, () -> applyBinding(player, binding, announceUpgrade));
                });
    }

    private void applyBinding(Player player, Optional<BridgeClient.BindingInfo> binding, boolean announceUpgrade) {
        if (!player.isOnline()) {
            return;
        }

        if (binding.isEmpty()) {
            knownLevels.remove(player.getUniqueId());
            player.setDisplayName(player.getName());
            player.setPlayerListName(player.getName());
            return;
        }

        int newLevel = Math.max(0, binding.get().fansClubLevel());
        Integer oldLevel = knownLevels.put(player.getUniqueId(), newLevel);
        String prefix = ChatColor.LIGHT_PURPLE + "[团Lv." + newLevel + "] " + ChatColor.WHITE;
        player.setDisplayName(prefix + player.getName());
        player.setPlayerListName(prefix + player.getName());

        if (announceUpgrade && oldLevel != null && newLevel > oldLevel) {
            Bukkit.broadcastMessage(
                    ChatColor.GOLD + "🎉 " + ChatColor.YELLOW + player.getName()
                            + ChatColor.WHITE + " 的小理粉丝团等级提升到了 "
                            + ChatColor.LIGHT_PURPLE + "Lv." + newLevel + ChatColor.WHITE + "！");
        }
    }

    private void rebuildClient() {
        String baseUrl = getConfig().getString("bridge.base-url", "http://127.0.0.1:8765");
        String apiKey = getConfig().getString("bridge.api-key", "change-me");
        bridgeClient = new BridgeClient(baseUrl, apiKey);
    }

    private void startSyncTask() {
        long seconds = Math.max(10L, getConfig().getLong("bridge.sync-seconds", 30L));
        long ticks = seconds * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                syncPlayer(player, true);
            }
        }, 40L, ticks);
    }
}
