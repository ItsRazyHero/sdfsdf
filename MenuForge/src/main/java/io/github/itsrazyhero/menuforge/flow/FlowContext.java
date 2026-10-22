package io.github.itsrazyhero.menuforge.flow;

import io.github.itsrazyhero.menuforge.MenuForgePlugin;
import io.github.itsrazyhero.menuforge.menu.Menu;
import io.github.itsrazyhero.menuforge.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/** Everything a flow block needs: the player, the menu it runs in and extra placeholders (command args). */
public final class FlowContext {

    public final MenuForgePlugin plugin;
    public final Player player;
    public final Menu menu;
    public final Map<String, String> args;

    public FlowContext(MenuForgePlugin plugin, Player player, Menu menu, Map<String, String> args) {
        this.plugin = plugin;
        this.player = player;
        this.menu = menu;
        this.args = args == null ? new HashMap<>() : new HashMap<>(args);
        if (menu != null) this.args.putIfAbsent("menu", menu.id());
    }

    /** Applies placeholders to a raw config string. */
    public String fmt(String raw) {
        return plugin.placeholders().apply(player, raw, args);
    }

    /** Placeholders + MiniMessage/legacy formatting. */
    public Component text(String raw) {
        return Text.parse(fmt(raw));
    }

    public FlowContext with(String key, String value) {
        FlowContext copy = new FlowContext(plugin, player, menu, args);
        copy.args.put(key, value);
        return copy;
    }
}
