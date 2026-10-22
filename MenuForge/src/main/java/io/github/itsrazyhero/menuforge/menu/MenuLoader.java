package io.github.itsrazyhero.menuforge.menu;

import io.github.itsrazyhero.menuforge.MenuForgePlugin;
import io.github.itsrazyhero.menuforge.flow.Block;
import io.github.itsrazyhero.menuforge.flow.FlowParser;
import io.github.itsrazyhero.menuforge.gui.ItemBuilder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Reads menu YAML files into {@link Menu} objects. */
public final class MenuLoader {

    private final MenuForgePlugin plugin;

    public MenuLoader(MenuForgePlugin plugin) {
        this.plugin = plugin;
    }

    public Menu load(File file) {
        String id = file.getName().replaceFirst("\\.ya?ml$", "");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return parse(id, file, yaml);
    }

    public Menu parse(String id, File file, ConfigurationSection c) {
        FlowParser flows = new FlowParser(msg -> plugin.getLogger().warning("[" + id + "] " + msg));
        int rows = Math.max(1, Math.min(6, c.getInt("rows", c.getInt("size", 27) / 9)));

        List<MenuItem> items = new ArrayList<>();
        ConfigurationSection itemsSection = c.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection s = itemsSection.getConfigurationSection(key);
                if (s == null) continue;
                try {
                    items.add(parseItem(key, s, rows, flows));
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("[" + id + "] item '" + key + "' skipped: " + e.getMessage());
                }
            }
        }
        items.sort((a, b) -> Integer.compare(b.priority, a.priority));

        return new Menu(id, file,
                c.getString("title", id),
                c.getString("texture", ""),
                rows,
                c.getString("command", ""),
                c.getStringList("aliases"),
                c.getString("permission", ""),
                c.getInt("refresh", 0),
                c.getStringList("denied-worlds"),
                flows.parse(c.get("open-requirements")),
                flows.parse(c.get("on-open")),
                flows.parse(c.get("on-close")),
                c.getString("bedrock.content", ""),
                List.copyOf(items));
    }

    private MenuItem parseItem(String key, ConfigurationSection s, int rows, FlowParser flows) {
        List<Integer> slots = parseSlots(s, rows);
        Icon icon = parseIcon(s);

        Map<ClickKind, List<Block>> clicks = new EnumMap<>(ClickKind.class);
        for (ClickKind kind : ClickKind.values()) {
            List<Block> flow = flows.parse(s.get(kind.key));
            if (!flow.isEmpty()) clicks.put(kind, flow);
        }

        List<MenuItem.State> states = new ArrayList<>();
        for (Map<?, ?> raw : s.getMapList("states")) {
            Map<String, Object> state = new LinkedHashMap<>();
            raw.forEach((k, v) -> state.put(String.valueOf(k), v));
            states.add(new MenuItem.State(FlowParser.str(state, "label", "state"),
                    flows.parse(state.get("requirements")), iconFromMap(state)));
        }

        ConfigurationSection shop = s.getConfigurationSection("shop");
        if (shop != null) icon = applyShop(shop, icon, clicks, flows);

        ConfigurationSection b = s.getConfigurationSection("bedrock");
        MenuItem.Bedrock bedrock = new MenuItem.Bedrock(
                b == null ? "" : b.getString("text", ""),
                b == null ? "" : b.getString("right-text", ""),
                b == null ? "" : b.getString("image", ""),
                b != null && b.getBoolean("hidden", false));

        return new MenuItem(key, slots, icon, s.getInt("priority", 0),
                flows.parse(s.get("view-requirements")), List.copyOf(states), clicks, bedrock);
    }

    private Icon parseIcon(ConfigurationSection s) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String k : s.getKeys(false)) map.put(k, s.get(k));
        return iconFromMap(map);
    }

    private static Icon iconFromMap(Map<String, Object> m) {
        return new Icon(
                m.get("material") == null ? null : String.valueOf(m.get("material")),
                m.get("name") == null ? null : String.valueOf(m.get("name")),
                m.get("lore") == null ? null : FlowParser.strList(m.get("lore")),
                m.get("amount") == null ? null : FlowParser.num(m, "amount", 1).intValue(),
                m.get("glow") == null ? null : FlowParser.bool(m, "glow", false),
                m.get("custom-model-data") == null ? null : FlowParser.num(m, "custom-model-data", 0).intValue(),
                m.get("item-model") == null ? null : String.valueOf(m.get("item-model")),
                m.get("head") == null ? null : String.valueOf(m.get("head")),
                m.get("hide-tooltip") == null ? null : FlowParser.bool(m, "hide-tooltip", false),
                m.get("hide-flags") == null ? null : FlowParser.bool(m, "hide-flags", true));
    }

    /** slot: 4 | slot: "0-8" | slots: [1, 2, "10-16"] | slots: "0-8,18-26" */
    static List<Integer> parseSlots(ConfigurationSection s, int rows) {
        Set<Integer> out = new LinkedHashSet<>();
        List<Object> raw = new ArrayList<>();
        if (s.isList("slots")) raw.addAll(s.getList("slots", List.of()));
        else if (s.contains("slots")) raw.add(s.get("slots"));
        if (s.contains("slot")) raw.add(s.get("slot"));
        for (Object o : raw) {
            for (String part : String.valueOf(o).split(",")) {
                part = part.trim();
                if (part.isEmpty()) continue;
                if (part.contains("-")) {
                    String[] range = part.split("-", 2);
                    int from = Integer.parseInt(range[0].trim()), to = Integer.parseInt(range[1].trim());
                    for (int i = Math.min(from, to); i <= Math.max(from, to); i++) out.add(i);
                } else {
                    out.add(Integer.parseInt(part));
                }
            }
        }
        List<Integer> list = new ArrayList<>();
        for (int slot : out) if (slot >= 0 && slot < rows * 9) list.add(slot);
        return list;
    }

    /** shop: {buy: 100, sell: 25, amount: 1} expands into buy/sell flows and lore lines. */
    private Icon applyShop(ConfigurationSection shop, Icon icon, Map<ClickKind, List<Block>> clicks, FlowParser flows) {
        var cfg = plugin.getConfig();
        String currency = cfg.getString("shop.currency", "$");
        String material = icon.materialOr("STONE");
        int amount = shop.getInt("amount", icon.amountOr(1));
        String itemName = prettify(material);
        List<String> lore = new ArrayList<>(icon.lore() == null ? List.of() : icon.lore());
        if (!lore.isEmpty()) lore.add("");

        if (shop.contains("buy")) {
            String price = shop.getString("buy");
            lore.add(fill(cfg.getString("shop.buy-lore"), currency, price, amount, itemName));
            clicks.putIfAbsent(ClickKind.LEFT, flows.parse(List.of(
                    Map.of("type", "money", "amount", price, "take", true, "deny", List.of(
                            Map.of("type", "message", "text", fill(cfg.getString("shop.not-enough-money"), currency, price, amount, itemName)))),
                    Map.of("type", "give-item", "material", material, "amount", amount),
                    Map.of("type", "message", "text", fill(cfg.getString("shop.bought"), currency, price, amount, itemName)),
                    "[sound] entity.experience_orb.pickup")));
        }
        if (shop.contains("sell")) {
            String price = shop.getString("sell");
            lore.add(fill(cfg.getString("shop.sell-lore"), currency, price, amount, itemName));
            clicks.putIfAbsent(ClickKind.RIGHT, flows.parse(List.of(
                    Map.of("type", "has-item", "material", material, "amount", amount, "take", true, "deny", List.of(
                            Map.of("type", "message", "text", fill(cfg.getString("shop.not-enough-items"), currency, price, amount, itemName)))),
                    Map.of("type", "give-money", "amount", price),
                    Map.of("type", "message", "text", fill(cfg.getString("shop.sold"), currency, price, amount, itemName)),
                    "[sound] entity.experience_orb.pickup")));
        }
        lore.add(fill(cfg.getString("shop.hint-lore"), currency, "", amount, itemName));
        Icon over = new Icon(null, icon.name() == null ? "<white>" + itemName : null, lore, null, null, null, null, null, null, null);
        return icon.merge(over);
    }

    private static String fill(String template, String currency, String price, int amount, String item) {
        if (template == null) return "";
        return template.replace("{currency}", currency).replace("{price}", price)
                .replace("{amount}", String.valueOf(amount)).replace("{item}", item);
    }

    public static String prettify(String material) {
        String name = ItemBuilder.material(material).name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
