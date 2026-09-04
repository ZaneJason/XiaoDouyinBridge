package com.zanejason.xiaodouyinbridge.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerSyncListener implements Listener {
    private final XiaoDouyinBridgePlugin plugin;

    public PlayerSyncListener(XiaoDouyinBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.syncPlayer(event.getPlayer(), false), 40L);
    }
}
