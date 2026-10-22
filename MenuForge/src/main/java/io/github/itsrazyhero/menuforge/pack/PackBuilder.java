package io.github.itsrazyhero.menuforge.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.itsrazyhero.menuforge.MenuForgePlugin;
import io.github.itsrazyhero.menuforge.util.Text;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Turns every textures/*.png into a glyph of the default font. A menu title that starts with the glyph
 * (shifted 8px left with negative spaces) draws the image over the whole container: a custom inventory skin.
 * Because it lives in minecraft:default, the same title string works in any other plugin too.
 */
public final class PackBuilder {

    public record Glyph(char ch, int width, int height) {}

    public static final int PACK_FORMAT = 46; // 1.21.4
    private static final int FIRST_GLYPH = 0xE000;
    private static final int NEG_BASE = PackBuilderMath.NEG_BASE;

    private final MenuForgePlugin plugin;
    private final Map<String, Glyph> glyphs = new LinkedHashMap<>();
    private final File zip;
    private String sha1 = "";

    public PackBuilder(MenuForgePlugin plugin) {
        this.plugin = plugin;
        this.zip = new File(plugin.getDataFolder(), "pack/MenuForge-pack.zip");
    }

    public File texturesDir() {
        File dir = new File(plugin.getDataFolder(), "textures");
        dir.mkdirs();
        return dir;
    }

    public File zipFile() {
        return zip;
    }

    public String sha1() {
        return sha1;
    }

    public boolean hasTextures() {
        return !glyphs.isEmpty();
    }

    public Map<String, Glyph> glyphs() {
        return glyphs;
    }

    /** Rebuilds the zip from disk. Safe to call from any thread; only touches files. */
    public synchronized void build() {
        glyphs.clear();
        File[] files = texturesDir().listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        if (files == null || files.length == 0) {
            zip.delete();
            sha1 = "";
            return;
        }
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        try {
            zip.getParentFile().mkdirs();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream out = new ZipOutputStream(bytes)) {
                JsonArray providers = new JsonArray();
                JsonObject space = new JsonObject();
                space.addProperty("type", "space");
                JsonObject advances = new JsonObject();
                for (int n = 1; n <= 255; n++) advances.addProperty(String.valueOf((char) (NEG_BASE + n)), -n);
                space.add("advances", advances);
                providers.add(space);

                int index = 0;
                for (File file : files) {
                    BufferedImage image = ImageIO.read(file);
                    if (image == null) {
                        plugin.getLogger().warning("Texture " + file.getName() + " is not a readable PNG, skipped.");
                        continue;
                    }
                    String name = file.getName().substring(0, file.getName().length() - 4).toLowerCase();
                    char ch = (char) (FIRST_GLYPH + index++);
                    glyphs.put(name, new Glyph(ch, effectiveWidth(image), image.getHeight()));

                    JsonObject bitmap = new JsonObject();
                    bitmap.addProperty("type", "bitmap");
                    bitmap.addProperty("file", "menuforge:font/" + name + ".png");
                    bitmap.addProperty("ascent", 13);
                    bitmap.addProperty("height", image.getHeight());
                    JsonArray chars = new JsonArray();
                    chars.add(String.valueOf(ch));
                    bitmap.add("chars", chars);
                    providers.add(bitmap);

                    out.putNextEntry(new ZipEntry("assets/menuforge/textures/font/" + name + ".png"));
                    out.write(Files.readAllBytes(file.toPath()));
                    out.closeEntry();
                }
                for (String include : new String[]{"minecraft:include/space", "minecraft:include/default"}) {
                    JsonObject ref = new JsonObject();
                    ref.addProperty("type", "reference");
                    ref.addProperty("id", include);
                    providers.add(ref);
                }
                JsonObject font = new JsonObject();
                font.add("providers", providers);
                Gson gson = new GsonBuilder().disableHtmlEscaping().create();

                out.putNextEntry(new ZipEntry("assets/minecraft/font/default.json"));
                out.write(gson.toJson(font).getBytes(StandardCharsets.UTF_8));
                out.closeEntry();

                JsonObject meta = new JsonObject();
                JsonObject pack = new JsonObject();
                pack.addProperty("pack_format", PACK_FORMAT);
                pack.addProperty("description", "MenuForge menu skins");
                meta.add("pack", pack);
                out.putNextEntry(new ZipEntry("pack.mcmeta"));
                out.write(gson.toJson(meta).getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
            byte[] data = bytes.toByteArray();
            Files.write(zip.toPath(), data);
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
            plugin.getLogger().info("Resource pack built with " + glyphs.size() + " texture(s).");
        } catch (IOException | NoSuchAlgorithmException e) {
            plugin.getLogger().warning("Could not build resource pack: " + e);
        }
    }

    private static int effectiveWidth(BufferedImage image) {
        return PackBuilderMath.effectiveWidth(image);
    }

    /** MiniMessage title: glyph shifted onto the container, cursor moved back, then the real title text. */
    public String composeTitle(String texture, String text) {
        Glyph glyph = glyphs.get(texture.toLowerCase());
        if (glyph == null) return text;
        return "<white>" + prefix(glyph) + "</white>" + (text == null ? "" : text);
    }

    /** Same thing with legacy codes, for pasting into plugins that don't speak MiniMessage. */
    public String composeLegacyTitle(String texture, String text) {
        Glyph glyph = glyphs.get(texture.toLowerCase());
        if (glyph == null) return text;
        return "§f" + prefix(glyph) + "§r" + (text == null ? "" : Text.legacy(text));
    }

    private static String prefix(Glyph glyph) {
        return PackBuilderMath.prefix(glyph.ch(), glyph.width());
    }

    public String packUrl() {
        return plugin.web().publicAddress() + "/pack.zip?v=" + (sha1.length() > 8 ? sha1.substring(0, 8) : sha1);
    }

    public void send(Player player) {
        if (sha1.isEmpty() || !plugin.web().running()) return;
        var cfg = plugin.getConfig();
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo()
                        .uri(URI.create(packUrl()))
                        .hash(sha1)
                        .build())
                .prompt(Text.parse(cfg.getString("resource-pack.prompt", "")))
                .required(cfg.getBoolean("resource-pack.required", false))
                .replace(true)
                .build());
    }
}
