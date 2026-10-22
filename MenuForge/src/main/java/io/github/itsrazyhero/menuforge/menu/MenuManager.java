package io.github.itsrazyhero.menuforge.menu;

import io.github.itsrazyhero.menuforge.MenuForgePlugin;
import io.github.itsrazyhero.menuforge.flow.FlowContext;
import io.github.itsrazyhero.menuforge.flow.FlowRunner;
import io.github.itsrazyhero.menuforge.gui.MenuSession;
import io.github.itsrazyhero.menuforge.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Holds loaded menus, their commands, opens them and keeps per-player "back" history. */
public final class MenuManager {

    private record HistoryEntry(String menu, String[] args) {}

    private final MenuForgePlugin plugin;
    private final MenuLoader loader;
    private final Map<String, Menu> menus = new LinkedHashMap<>();
    private final List<DynamicCommand> commands = new ArrayList<>();
    private final Map<UUID, Deque<HistoryEntry>> history = new HashMap<>();
    /** Menu currently shown to a Bedrock player as a form (forms are not inventories). */
    private final Map<UUID, HistoryEntry> bedrockOpen = new HashMap<>();

    public MenuManager(MenuForgePlugin plugin) {
        this.plugin = plugin;
        this.loader = new MenuLoader(plugin);
    }

    public File directory() {
        return new File(plugin.getDataFolder(), "menus");
    }

    public int loadAll() {
        // close open sessions so nobody keeps a stale menu
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof MenuSession) p.closeInventory();
        }
        unregisterCommands();
        menus.clear();
        File dir = directory();
        dir.mkdirs();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml") || n.endsWith(".yaml"));
        if (files != null) {
            for (File file : files) {
                try {
                    Menu menu = loader.load(file);
                    menus.put(menu.id(), menu);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Could not load menu " + file.getName() + ": " + e.getMessage());
                }
            }
        }
        registerCommands();
        return menus.size();
    }

    private void registerCommands() {
        CommandMap map = Bukkit.getCommandMap();
        for (Menu menu : menus.values()) {
            if (menu.command() == null || menu.command().isBlank()) continue;
            DynamicCommand cmd = new DynamicCommand(plugin, menu.command().toLowerCase(Locale.ROOT),
                    menu.aliases().stream().map(a -> a.toLowerCase(Locale.ROOT)).toList(), menu.id());
            if (!map.register("menuforge", cmd)) {
                plugin.getLogger().warning("Command /" + menu.command() + " of menu " + menu.id()
                        + " clashes with another command; reachable as /menuforge:" + menu.command());
            }
            commands.add(cmd);
        }
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
    }

    private void unregisterCommands() {
        CommandMap map = Bukkit.getCommandMap();
        Map<String, org.bukkit.command.Command> known = map.getKnownCommands();
        for (DynamicCommand cmd : commands) {
            cmd.unregister(map);
            known.values().removeIf(c -> c == cmd);
        }
        commands.clear();
    }

    public Menu get(String id) {
        if (id == null) return null;
        Menu menu = menus.get(id);
        if (menu != null) return menu;
        for (Menu m : menus.values()) if (m.id().equalsIgnoreCase(id)) return m;
        return null;
    }

    public Collection<Menu> all() {
        return menus.values();
    }

    public static Map<String, String> argsMap(String[] args) {
        Map<String, String> map = new HashMap<>();
        if (args == null) return map;
        for (int i = 0; i < args.length; i++) map.put("arg_" + (i + 1), args[i]);
        map.put("args", String.join(" ", args));
        return map;
    }

    /**
     * Opens a menu for a player after checking world, permission and open requirements.
     * When {@code remember} is set the currently open menu is pushed to the back-history.
     */
    public boolean open(Player player, Menu menu, String[] args, boolean remember) {
        FlowContext ctx = new FlowContext(plugin, player, menu, argsMap(args));
        if (menu.deniedWorlds().stream().anyMatch(w -> w.equalsIgnoreCase(player.getWorld().getName()))) {
            player.sendMessage(Text.parse(plugin.msg("denied-world")));
            return false;
        }
        if (!menu.permission().isBlank() && !player.hasPermission(menu.permission())) {
            player.sendMessage(Text.parse(plugin.msg("no-permission")));
            return false;
        }
        if (!FlowRunner.check(menu.openRequirements(), ctx)) return false;

        if (remember) {
            HistoryEntry current = current(player);
            if (current != null && !current.menu().equals(menu.id())) {
                history.computeIfAbsent(player.getUniqueId(), u -> new ArrayDeque<>()).push(current);
            }
        }

        if (plugin.bedrock().isBedrock(player)) {
            if (plugin.bedrockMenus().open(player, menu, ctx)) {
                bedrockOpen.put(player.getUniqueId(), new HistoryEntry(menu.id(), args == null ? new String[0] : args));
                FlowRunner.run(menu.onOpen(), ctx);
                return true;
            }
            // fall through to the Java inventory when forms are unavailable
        }
        MenuSession session = new MenuSession(plugin, menu, player, ctx, args == null ? new String[0] : args);
        session.open();
        FlowRunner.run(menu.onOpen(), ctx);
        return true;
    }

    private HistoryEntry current(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuSession session) {
            return new HistoryEntry(session.menu().id(), session.args());
        }
        return bedrockOpen.get(player.getUniqueId());
    }

    public void bedrockClosed(Player player) {
        bedrockOpen.remove(player.getUniqueId());
    }

    public void back(Player player) {
        Deque<HistoryEntry> stack = history.get(player.getUniqueId());
        HistoryEntry previous = stack == null ? null : stack.poll();
        if (previous == null) {
            player.closeInventory();
            return;
        }
        Menu menu = get(previous.menu());
        if (menu != null) open(player, menu, previous.args(), false);
    }

    public void refresh(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuSession session) session.render();
    }

    public void forget(Player player) {
        history.remove(player.getUniqueId());
        bedrockOpen.remove(player.getUniqueId());
    }

    public void clearHistory(Player player) {
        history.remove(player.getUniqueId());
    }
}
