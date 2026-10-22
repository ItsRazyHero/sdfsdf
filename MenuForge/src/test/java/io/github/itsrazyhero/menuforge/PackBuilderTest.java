package io.github.itsrazyhero.menuforge;

import io.github.itsrazyhero.menuforge.pack.PackBuilderMath;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackBuilderTest {

    @Test
    void effectiveWidthIgnoresTransparentColumns() {
        BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(10, 5, 0xFF000000);
        img.setRGB(40, 20, 0x80FFFFFF);
        assertEquals(41, PackBuilderMath.effectiveWidth(img));
    }

    @Test
    void negativeSpaceSplitsAbove255() {
        assertEquals("", PackBuilderMath.negative(8));
        assertEquals(2, PackBuilderMath.negative(300).length());
        assertEquals("", PackBuilderMath.negative(0));
    }

    @Test
    void titlePrefixMovesCursorBackToOrigin() {
        // 176px wide glyph: shift -8, advance 177, shift back 169
        String prefix = PackBuilderMath.prefix('', 176);
        assertEquals("" + (char) (0xF800 + 169), prefix);
    }
}
