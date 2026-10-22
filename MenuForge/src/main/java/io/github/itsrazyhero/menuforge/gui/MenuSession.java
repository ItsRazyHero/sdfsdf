package io.github.itsrazyhero.menuforge.gui;

import io.github.itsrazyhero.menuforge.MenuForgePlugin;
import io.github.itsrazyhero.menuforge.flow.FlowContext;
import io.github.itsrazyhero.menuforge.flow.FlowRunner;
import io.github.itsrazyhero.menuforge.menu.ClickKind;
import io.github.itsrazyhero.menuforge.menu.Menu;
import io.github.itsrazyhero.menuforge.menu.MenuItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

/** One open Java-edition menu for one player. */
public final class MenuSession implements InventoryHolder {

    private final MenuForgePlugin plugin;
    private final Menu menu;
    private final Player player;
    private final FlowContext ctx;
    private final String[] args;
    private final Inventory inventory;
    private final MenuItem[] slotItems;
    private BukkitTask refreshTask;

    public MenuSession(MenuForgePlugin plugin, Menu menu, Player player, FlowContext ctx, String[] args) {
        this.plugin = plugin;
        this.menu = menu;
        this.player = player;
        this.ctx = ctx;
        this.args = args;
        this.inventory = Bukkit.createInventory(this, menu.size(), menu.title(ctx));
        this.slotItems = new MenuItem[menu.size()];
    }

    public void open() {
        render();
        player.openInventory(inventory);
        if (menu.refresh() > 0) {
            refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::render, menu.refresh(), menu.refresh());
        }
    }

    /** Re-evaluates view requirements, states and placeholders for every slot. */
    public void render() {
        java.util.Arrays.fill(slotItems, null);
        for (MenuItem item : menu.items()) { // already sorted by priority, highest first
            if (!item.visible(ctx)) continue;
            for (int slot : item.slots) {
                if (slot < slotItems.length && slotItems[slot] == null) slotItems[slot] = item;
            }
        }
        for (int slot = 0; slot < slotItems.length; slot++) {
            MenuItem item = slotItems[slot];
            inventory.setItem(slot, item == null ? null : ItemBuilder.build(item.resolve(ctx), ctx));
        }
    }

    public void click(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= slotItems.length) return;
        MenuItem item = slotItems[slot];
        if (item == null) return;
        ClickKind kind = ClickKind.from(event.getClick());
        if (kind == null) return;
        var flow = item.flow(kind);
        if (flow.isEmpty()) return;
        FlowRunner.run(flow, ctx.with("slot", String.valueOf(slot)).with("item", item.key));
    }

    public void closed() {
        if (refreshTask != null) refreshTask.cancel();
        FlowRunner.run(menu.onClose(), ctx);
    }

    public Menu menu() {
        return menu;
    }

    public String[] args() {
        return args;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
