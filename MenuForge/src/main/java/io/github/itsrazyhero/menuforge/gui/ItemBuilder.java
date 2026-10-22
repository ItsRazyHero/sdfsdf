package io.github.itsrazyhero.menuforge.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.github.itsrazyhero.menuforge.flow.FlowContext;
import io.github.itsrazyhero.menuforge.menu.Icon;
import io.github.itsrazyhero.menuforge.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Builds ItemStacks from icons, applying placeholders and formatting. */
public final class ItemBuilder {

    private ItemBuilder() {}

    public static Material material(String name) {
        if (name == null) return Material.STONE;
        Material m = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        return m == null ? Material.BARRIER : m;
    }

    public static ItemStack build(Icon icon, FlowContext ctx) {
        Material material = material(ctx.fmt(icon.materialOr("STONE")));
        ItemStack stack = new ItemStack(material, Math.max(1, Math.min(99, icon.amountOr(1))));
        stack.editMeta(meta -> {
            if (icon.name() != null && !icon.name().isEmpty()) meta.displayName(Text.parseItem(ctx.fmt(icon.name())));
            if (icon.lore() != null && !icon.lore().isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : icon.lore()) lore.add(Text.parseItem(ctx.fmt(line)));
                meta.lore(lore);
            }
            if (Boolean.TRUE.equals(icon.glow())) meta.setEnchantmentGlintOverride(true);
            if (icon.customModelData() != null && icon.customModelData() > 0) setCustomModelData(meta, icon.customModelData());
            if (icon.itemModel() != null && !icon.itemModel().isBlank()) {
                NamespacedKey key = NamespacedKey.fromString(ctx.fmt(icon.itemModel()));
                if (key != null) meta.setItemModel(key);
            }
            if (Boolean.TRUE.equals(icon.hideTooltip())) meta.setHideTooltip(true);
            if (!Boolean.FALSE.equals(icon.hideFlags())) meta.addItemFlags(ItemFlag.values());
            if (meta instanceof SkullMeta skull && icon.head() != null && !icon.head().isBlank()) {
                applyHead(skull, ctx.fmt(icon.head()));
            }
        });
        return stack;
    }

    public static ItemStack simple(Material material, int amount, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        stack.editMeta(meta -> {
            if (name != null && !name.isEmpty()) meta.displayName(Text.parseItem(name));
            if (lore != null && !lore.isEmpty()) meta.lore(lore.stream().map(Text::parseItem).toList());
        });
        return stack;
    }

    @SuppressWarnings("deprecation")
    private static void setCustomModelData(ItemMeta meta, int value) {
        meta.setCustomModelData(value);
    }

    /** head: player name, "%player_name%", a base64 textures value, or a textures.minecraft.net URL. */
    private static void applyHead(SkullMeta skull, String head) {
        String value = head.trim();
        if (value.startsWith("http")) {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + value + "\"}}}";
            value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }
        if (value.length() > 40) {
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)), "MenuForge");
            profile.setProperty(new ProfileProperty("textures", value));
            skull.setPlayerProfile(profile);
        } else if (!value.isEmpty()) {
            // Name-only profiles are resolved lazily by the client, so no blocking lookup is needed here.
            skull.setPlayerProfile(Bukkit.createProfile(null, value));
        }
    }
}
