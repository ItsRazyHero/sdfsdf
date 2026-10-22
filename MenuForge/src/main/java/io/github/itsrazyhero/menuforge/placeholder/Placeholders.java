package io.github.itsrazyhero.menuforge.placeholder;

import io.github.itsrazyhero.menuforge.MenuForgePlugin;
import io.github.itsrazyhero.menuforge.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Built-in %placeholders% plus PlaceholderAPI (via reflection, so no hard dependency). */
public final class Placeholders {

    private static final Pattern TOKEN = Pattern.compile("%([a-zA-Z0-9_:-]+)%");

    private final MenuForgePlugin plugin;
    private Method papi;

    public Placeholders(MenuForgePlugin plugin) {
        this.plugin = plugin;
        hookPapi();
    }

    public void hookPapi() {
        papi = null;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        try {
            Class<?> cls = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            papi = cls.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            plugin.getLogger().info("Hooked PlaceholderAPI.");
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("PlaceholderAPI found but could not be hooked: " + e);
        }
    }

    public boolean hasPapi() {
        return papi != null;
    }

    public String apply(Player player, String input, Map<String, String> extra) {
        if (input == null || input.isEmpty() || input.indexOf('%') < 0) return input == null ? "" : input;
        Matcher m = TOKEN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = extra != null ? extra.get(key) : null;
            if (value == null) value = internal(player, key.toLowerCase(Locale.ROOT));
            m.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : m.group()));
        }
        m.appendTail(sb);
        String out = sb.toString();
        if (papi != null && player != null && out.indexOf('%') >= 0) {
            try {
                out = (String) papi.invoke(null, player, out);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return out;
    }

    private String internal(Player p, String key) {
        if (key.startsWith("cooldown_")) {
            if (p == null) return "";
            long left = plugin.cooldowns().remaining(p.getUniqueId(), key.substring(9));
            return left <= 0 ? plugin.msg("cooldown-ready") : Durations.format(left, plugin.msg("cooldown-format"));
        }
        if (key.equals("online")) return String.valueOf(Bukkit.getOnlinePlayers().size());
        if (key.equals("max_online")) return String.valueOf(Bukkit.getMaxPlayers());
        if (key.equals("money")) return p == null ? "0" : plugin.vault().format(plugin.vault().balance(p));
        if (p == null) return null;
        return switch (key) {
            case "player", "player_name" -> p.getName();
            case "player_displayname" -> io.github.itsrazyhero.menuforge.util.Text.plain(p.displayName());
            case "player_uuid" -> p.getUniqueId().toString();
            case "player_world" -> p.getWorld().getName();
            case "player_x" -> String.valueOf(p.getLocation().getBlockX());
            case "player_y" -> String.valueOf(p.getLocation().getBlockY());
            case "player_z" -> String.valueOf(p.getLocation().getBlockZ());
            case "player_health" -> String.valueOf((int) Math.ceil(p.getHealth()));
            case "player_food" -> String.valueOf(p.getFoodLevel());
            case "player_level" -> String.valueOf(p.getLevel());
            case "player_exp" -> String.valueOf(p.getTotalExperience());
            case "player_gamemode" -> p.getGameMode().name().toLowerCase(Locale.ROOT);
            case "player_ping" -> String.valueOf(p.getPing());
            case "player_bedrock" -> String.valueOf(plugin.bedrock().isBedrock(p));
            default -> null;
        };
    }
}
