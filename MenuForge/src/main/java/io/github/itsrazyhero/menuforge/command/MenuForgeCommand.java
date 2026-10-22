package io.github.itsrazyhero.menuforge.command;

import io.github.itsrazyhero.menuforge.MenuForgePlugin;
import io.github.itsrazyhero.menuforge.menu.Menu;
import io.github.itsrazyhero.menuforge.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class MenuForgeCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("open", "reload", "list", "create", "pack", "title", "cooldown", "editor", "help");

    private final MenuForgePlugin plugin;

    public MenuForgeCommand(MenuForgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) return help(sender);
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("open") && !sender.hasPermission("menuforge.admin")) {
            sender.sendMessage(Text.parse(plugin.msg("no-permission")));
            return true;
        }
        switch (sub) {
            case "open" -> open(sender, args);
            case "reload" -> {
                plugin.reloadConfig();
                plugin.rebuildPack(false);
                int n = plugin.menus().loadAll();
                reply(sender, plugin.msg("reloaded").replace("{menus}", String.valueOf(n)));
            }
            case "list" -> {
                reply(sender, "<white>" + plugin.menus().all().size() + "</white> menus loaded:");
                for (Menu m : plugin.menus().all()) {
                    String cmdText = m.command().isBlank() ? "" : " <dark_gray>(/" + m.command() + ")";
                    sender.sendMessage(Text.parse(" <gray>• <click:run_command:'/menuforge open " + m.id() + "'><yellow>" + m.id()
                            + "</yellow></click>" + cmdText + " <dark_gray>" + m.rows() + " rows, " + m.items().size() + " items"));
                }
            }
            case "create" -> create(sender, args);
            case "pack" -> {
                plugin.rebuildPack(true);
                reply(sender, "Resource pack rebuilt with <white>" + plugin.pack().glyphs().size() + "</white> texture(s) and re-sent to online players.");
            }
            case "title" -> {
                if (args.length < 2) {
                    reply(sender, "<red>Usage: /mf title <texture>");
                    return true;
                }
                String legacy = plugin.pack().composeLegacyTitle(args[1], "");
                String mini = plugin.pack().composeTitle(args[1], "");
                if (legacy.isEmpty()) {
                    reply(sender, "<red>No texture named <white>" + args[1] + "</white>. Put a PNG in plugins/MenuForge/textures/.");
                    return true;
                }
                reply(sender, "Title for <white>" + args[1] + "</white> (click to copy):");
                sender.sendMessage(Component.text(" [legacy  §f...§r] ", NamedTextColor.GREEN).clickEvent(ClickEvent.copyToClipboard(legacy))
                        .append(Component.text("for DeluxeMenus & co.", NamedTextColor.GRAY)));
                sender.sendMessage(Component.text(" [MiniMessage] ", NamedTextColor.GREEN).clickEvent(ClickEvent.copyToClipboard(mini))
                        .append(Component.text("for plugins that use MiniMessage", NamedTextColor.GRAY)));
            }
            case "cooldown" -> {
                if (args.length < 4 || !args[1].equalsIgnoreCase("reset")) {
                    reply(sender, "<red>Usage: /mf cooldown reset <player> <key>");
                    return true;
                }
                var target = Bukkit.getOfflinePlayer(args[2]);
                plugin.cooldowns().reset(target.getUniqueId(), args[3]);
                reply(sender, "Cooldown <white>" + args[3] + "</white> reset for <white>" + args[2] + "</white>.");
            }
            case "editor" -> {
                if (!plugin.web().running()) {
                    reply(sender, "<red>The web server is disabled (web.enabled in config.yml).");
                    return true;
                }
                String url = plugin.web().publicAddress();
                reply(sender, "Editor: <click:open_url:'" + url + "'><aqua><u>" + url + "</u></aqua></click>");
                sender.sendMessage(Component.text(" Token: ", NamedTextColor.GRAY)
                        .append(Component.text(plugin.token(), NamedTextColor.WHITE)
                                .clickEvent(ClickEvent.copyToClipboard(plugin.token())))
                        .append(Component.text(" (click to copy)", NamedTextColor.DARK_GRAY)));
            }
            default -> help(sender);
        }
        return true;
    }

    private void open(CommandSender sender, String[] args) {
        if (args.length < 2) {
            reply(sender, "<red>Usage: /mf open <menu> [player] [args...]");
            return;
        }
        Menu menu = plugin.menus().get(args[1]);
        if (menu == null) {
            reply(sender, plugin.msg("menu-not-found").replace("{menu}", args[1]));
            return;
        }
        Player target;
        String[] rest;
        if (args.length >= 3 && Bukkit.getPlayerExact(args[2]) != null && sender.hasPermission("menuforge.open.others")) {
            target = Bukkit.getPlayerExact(args[2]);
            rest = Arrays.copyOfRange(args, 3, args.length);
        } else if (sender instanceof Player p) {
            target = p;
            rest = Arrays.copyOfRange(args, 2, args.length);
        } else {
            reply(sender, plugin.msg("player-only"));
            return;
        }
        plugin.menus().open(target, menu, rest, false);
    }

    private void create(CommandSender sender, String[] args) {
        if (args.length < 2) {
            reply(sender, "<red>Usage: /mf create <id> [rows] [command]");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]+")) {
            reply(sender, "<red>Use only letters, digits, _ and -.");
            return;
        }
        File file = new File(plugin.menus().directory(), id + ".yml");
        if (file.exists()) {
            reply(sender, "<red>Menu <white>" + id + "</white> already exists.");
            return;
        }
        int rows = args.length > 2 ? Math.max(1, Math.min(6, Integer.parseInt(args[2]))) : 3;
        String command = args.length > 3 ? args[3] : id;
        String yaml = """
                title: "<dark_gray>%s"
                rows: %d
                command: %s
                refresh: 0
                items:
                  example:
                    slot: %d
                    material: NETHER_STAR
                    name: "<gold>Hello!"
                    lore:
                      - "<gray>Edit me in plugins/MenuForge/menus/%s.yml"
                      - "<gray>or in the web editor (/mf editor)."
                    left-click:
                      - "[message] <green>You clicked the example item."
                      - "[sound] entity.experience_orb.pickup"
                """.formatted(id, rows, command, rows * 9 / 2, id);
        try {
            plugin.watcher().suppress(2000);
            Files.writeString(file.toPath(), yaml, StandardCharsets.UTF_8);
            plugin.menus().loadAll();
            reply(sender, "Created menu <white>" + id + "</white> (/" + command + ").");
        } catch (IOException e) {
            reply(sender, "<red>Could not write file: " + e.getMessage());
        }
    }

    private boolean help(CommandSender sender) {
        reply(sender, "Commands:");
        for (String line : new String[]{
                "/mf open <menu> [player] [args]", "/mf list", "/mf reload", "/mf create <id> [rows] [command]",
                "/mf pack <gray>- rebuild & resend the resource pack", "/mf title <texture> <gray>- copy a skin title for other plugins",
                "/mf cooldown reset <player> <key>", "/mf editor <gray>- web editor link + token"}) {
            sender.sendMessage(Text.parse(" <yellow>" + line));
        }
        return true;
    }

    private void reply(CommandSender sender, String mini) {
        sender.sendMessage(Text.parse(plugin.msg("prefix") + mini));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, String[] args) {
        if (args.length == 1) return filter(SUBS.stream(), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("open") || args[0].equalsIgnoreCase("title"))) {
            return args[0].equalsIgnoreCase("open")
                    ? filter(plugin.menus().all().stream().map(Menu::id), args[1])
                    : filter(plugin.pack().glyphs().keySet().stream(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("open")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cooldown")) return List.of("reset");
        return List.of();
    }

    private static List<String> filter(Stream<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).sorted().toList();
    }
}
