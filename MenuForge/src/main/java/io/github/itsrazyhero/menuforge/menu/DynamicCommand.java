package io.github.itsrazyhero.menuforge.menu;

import io.github.itsrazyhero.menuforge.MenuForgePlugin;
import io.github.itsrazyhero.menuforge.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** A per-menu command registered at runtime (so reloads can add/remove commands without a restart). */
public final class DynamicCommand extends Command {

    private final MenuForgePlugin plugin;
    private final String menuId;

    public DynamicCommand(MenuForgePlugin plugin, String name, List<String> aliases, String menuId) {
        super(name, "Opens the " + menuId + " menu", "/" + name, aliases);
        this.plugin = plugin;
        this.menuId = menuId;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.parse(plugin.msg("player-only")));
            return true;
        }
        Menu menu = plugin.menus().get(menuId);
        if (menu == null) {
            sender.sendMessage(Text.parse(plugin.msg("menu-not-found").replace("{menu}", menuId)));
            return true;
        }
        plugin.menus().open(player, menu, args, false);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) {
        return List.of();
    }
}
