package io.github.itsrazyhero.menuforge.pack;

import java.awt.image.BufferedImage;

/** Pure helpers of the pack builder, kept separate so they can be unit-tested without Bukkit. */
public final class PackBuilderMath {

    public static final int NEG_BASE = 0xF800; // U+F800+n advances -n px (n = 1..255)

    private PackBuilderMath() {}

    /** Minecraft trims transparent columns on the right when computing a glyph's advance; mirror that. */
    public static int effectiveWidth(BufferedImage image) {
        for (int x = image.getWidth() - 1; x >= 0; x--) {
            for (int y = 0; y < image.getHeight(); y++) {
                if ((image.getRGB(x, y) >>> 24) != 0) return x + 1;
            }
        }
        return image.getWidth();
    }

    /** Negative-space characters that move the cursor {@code px} pixels to the left. */
    public static String negative(int px) {
        StringBuilder sb = new StringBuilder();
        while (px > 0) {
            int step = Math.min(px, 255);
            sb.append((char) (NEG_BASE + step));
            px -= step;
        }
        return sb.toString();
    }

    /** The title starts 8px inside the container; the glyph advances width+1; then move back to the origin. */
    public static String prefix(char glyph, int width) {
        return negative(8) + glyph + negative(width + 1 - 8);
    }
}
