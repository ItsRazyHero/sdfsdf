package io.github.itsrazyhero.menuforge.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text helpers: MiniMessage with legacy (&a, &#rrggbb) code support. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Pattern LEGACY_CODE = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");
    private static final Pattern LEGACY_HEX = Pattern.compile("[&§]#([0-9a-fA-F]{6})");

    private static final Map<Character, String> TAGS = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"), Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"), Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"), Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"), Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"), Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"), Map.entry('k', "<obfuscated>"), Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"), Map.entry('n', "<underlined>"), Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>"));

    private Text() {}

    /** Converts legacy colour codes into MiniMessage tags so both styles can be mixed. */
    public static String legacyToMini(String input) {
        if (input == null || input.isEmpty()) return "";
        if (input.indexOf('&') < 0 && input.indexOf('§') < 0) return input;
        String out = LEGACY_HEX.matcher(input).replaceAll(m -> "<#" + m.group(1) + ">");
        Matcher m = LEGACY_CODE.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String tag = TAGS.get(Character.toLowerCase(m.group(1).charAt(0)));
            m.appendReplacement(sb, Matcher.quoteReplacement(tag == null ? m.group() : tag));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static Component parse(String input) {
        return MM.deserialize(legacyToMini(input));
    }

    /** Parse for item names / lore: Minecraft renders those italic by default, which nobody wants. */
    public static Component parseItem(String input) {
        return parse(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String plain(String input) {
        return plain(parse(input));
    }

    /** Legacy § string, used for Bedrock forms (Bedrock understands § codes but not components). */
    public static String legacy(Component component) {
        return LEGACY.serialize(component);
    }

    public static String legacy(String input) {
        return legacy(parse(input));
    }

    public static String mini(Component component) {
        return MM.serialize(component);
    }
}
