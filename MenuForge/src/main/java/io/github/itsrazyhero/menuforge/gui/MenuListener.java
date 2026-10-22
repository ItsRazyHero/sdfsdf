package io.github.itsrazyhero.menuforge.gui;

import io.github.itsrazyhero.menuforge.MenuForgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class MenuListener implements Listener {

    private final MenuForgePlugin plugin;

    public MenuListener(MenuForgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuSession session)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == event.getInventory()) session.click(event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuSession) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuSession session) session.closed();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.menus().forget(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("resource-pack.send-on-join", true)) return;
        if (!plugin.pack().hasTextures() || !plugin.web().running()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline() && !plugin.bedrock().isBedrock(event.getPlayer())) {
                plugin.pack().send(event.getPlayer());
            }
        }, 40L);
    }
}
