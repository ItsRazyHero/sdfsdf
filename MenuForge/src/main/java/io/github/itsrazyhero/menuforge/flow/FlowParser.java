package io.github.itsrazyhero.menuforge.flow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Turns config lists into blocks. Each entry is either a map ({type: message, text: "hi"})
 * or a shorthand string ("[message] hi", "[console] give %player_name% diamond", "[delay] 20").
 */
public final class FlowParser {

    private final Consumer<String> warn;

    public FlowParser(Consumer<String> warn) {
        this.warn = warn;
    }

    public List<Block> parse(Object raw) {
        List<Block> out = new ArrayList<>();
        if (raw == null) return out;
        if (!(raw instanceof List<?> list)) {
            Block single = parseOne(raw);
            if (single != null) out.add(single);
            return out;
        }
        for (Object entry : list) {
            Block block = parseOne(entry);
            if (block != null) out.add(block);
        }
        return out;
    }

    private Block parseOne(Object entry) {
        Map<String, Object> map;
        if (entry instanceof String s) {
            map = fromShorthand(s);
        } else if (entry instanceof Map<?, ?> m) {
            map = new LinkedHashMap<>();
            m.forEach((k, v) -> map.put(String.valueOf(k), v));
        } else {
            warn.accept("Unknown flow entry: " + entry);
            return null;
        }
        if (map == null) return null;
        String type = str(map, "type", "").toLowerCase(Locale.ROOT).replace('_', '-');
        List<Block> deny = parse(map.get("deny"));
        try {
            return switch (type) {
                case "cooldown" -> new Blocks.Cooldown(str(map, "key", "default"), str(map, "duration", "60s"),
                        bool(map, "set", true), deny);
                case "permission", "perm" -> new Blocks.Permission(str(map, "value", str(map, "permission", "")), deny);
                case "money", "has-money" -> new Blocks.Money(str(map, "amount", "0"), bool(map, "take", false), deny);
                case "has-item", "item" -> new Blocks.HasItem(str(map, "material", "STONE"), num(map, "amount", 1).intValue(),
                        bool(map, "take", false), deny);
                case "placeholder", "compare" -> new Blocks.Compare(str(map, "input", ""), str(map, "operator", "=="),
                        str(map, "output", ""), deny);
                case "chance" -> new Blocks.Chance(num(map, "percent", 50).doubleValue(), deny);
                case "world" -> new Blocks.InWorld(strList(map.get("worlds")), deny);
                case "is-bedrock", "bedrock" -> new Blocks.IsBedrock(deny);
                case "delay", "wait" -> new Blocks.Delay(num(map, "ticks", 20).intValue());
                case "message", "msg", "tell" -> new Blocks.Message(str(map, "text", ""));
                case "broadcast" -> new Blocks.Broadcast(str(map, "text", ""));
                case "actionbar" -> new Blocks.ActionBar(str(map, "text", ""));
                case "title" -> new Blocks.ShowTitle(str(map, "title", ""), str(map, "subtitle", ""),
                        num(map, "fade-in", 10).intValue(), num(map, "stay", 60).intValue(), num(map, "fade-out", 10).intValue());
                case "sound" -> new Blocks.PlaySound(str(map, "sound", ""), num(map, "volume", 1).floatValue(),
                        num(map, "pitch", 1).floatValue());
                case "console" -> new Blocks.ConsoleCommand(str(map, "command", ""));
                case "player", "command" -> new Blocks.PlayerCommand(str(map, "command", ""));
                case "open", "open-menu" -> new Blocks.OpenMenu(str(map, "menu", ""), str(map, "args", ""));
                case "close" -> new Blocks.Close();
                case "back" -> new Blocks.Back();
                case "refresh" -> new Blocks.Refresh();
                case "give-money" -> new Blocks.GiveMoney(str(map, "amount", "0"));
                case "give-item" -> new Blocks.GiveItem(str(map, "material", "STONE"), num(map, "amount", 1).intValue(),
                        map.get("name") == null ? null : str(map, "name", ""), strList(map.get("lore")));
                case "reset-cooldown" -> new Blocks.ResetCooldown(str(map, "key", "default"));
                case "teleport", "tp" -> new Blocks.Teleport(str(map, "world", ""), num(map, "x", 0).doubleValue(),
                        num(map, "y", 64).doubleValue(), num(map, "z", 0).doubleValue(),
                        num(map, "yaw", 0).floatValue(), num(map, "pitch", 0).floatValue());
                case "set-var", "var" -> new Blocks.SetVar(str(map, "name", "x"), str(map, "value", ""));
                default -> {
                    warn.accept("Unknown block type '" + type + "'");
                    yield null;
                }
            };
        } catch (RuntimeException e) {
            warn.accept("Bad block " + map + ": " + e.getMessage());
            return null;
        }
    }

    /** "[type] rest" shorthand. The remainder maps onto the block's main field. */
    public static Map<String, Object> fromShorthand(String s) {
        String t = s.trim();
        if (!t.startsWith("[")) return Map.of("type", "message", "text", t);
        int end = t.indexOf(']');
        if (end < 0) return Map.of("type", "message", "text", t);
        String type = t.substring(1, end).trim().toLowerCase(Locale.ROOT);
        String rest = t.substring(end + 1).trim();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        switch (type) {
            case "console", "player", "command" -> map.put("command", rest);
            case "open" -> {
                String[] parts = rest.split(" ", 2);
                map.put("menu", parts[0]);
                if (parts.length > 1) map.put("args", parts[1]);
            }
            case "sound" -> {
                String[] parts = rest.split("[;,] *");
                map.put("sound", parts[0]);
                if (parts.length > 1) map.put("volume", Float.parseFloat(parts[1]));
                if (parts.length > 2) map.put("pitch", Float.parseFloat(parts[2]));
            }
            case "delay", "wait" -> map.put("ticks", Integer.parseInt(rest));
            case "permission", "perm" -> map.put("value", rest);
            case "money", "give-money" -> map.put("amount", rest);
            case "cooldown" -> {
                String[] parts = rest.split(" ");
                map.put("key", parts[0]);
                if (parts.length > 1) map.put("duration", parts[1]);
            }
            case "title" -> {
                String[] parts = rest.split(";", 2);
                map.put("title", parts[0]);
                if (parts.length > 1) map.put("subtitle", parts[1]);
            }
            case "reset-cooldown" -> map.put("key", rest);
            case "give-item" -> {
                String[] parts = rest.split(" ");
                map.put("material", parts[0]);
                if (parts.length > 1) map.put("amount", Integer.parseInt(parts[1]));
            }
            case "chance" -> map.put("percent", Double.parseDouble(rest));
            default -> map.put("text", rest);
        }
        return map;
    }

    public static String str(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }

    public static boolean bool(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        if (v instanceof Boolean b) return b;
        return v == null ? def : Boolean.parseBoolean(String.valueOf(v));
    }

    public static Number num(Map<String, Object> map, String key, Number def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n;
        if (v == null) return def;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @SuppressWarnings("unchecked")
    public static List<String> strList(Object v) {
        if (v == null) return List.of();
        if (v instanceof List<?> l) return ((List<Object>) l).stream().map(String::valueOf).toList();
        return List.of(String.valueOf(v));
    }
}
