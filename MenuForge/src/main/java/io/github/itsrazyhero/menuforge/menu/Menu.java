package io.github.itsrazyhero.menuforge.menu;

import io.github.itsrazyhero.menuforge.flow.Block;
import io.github.itsrazyhero.menuforge.flow.FlowContext;
import io.github.itsrazyhero.menuforge.util.Text;
import net.kyori.adventure.text.Component;

import java.io.File;
import java.util.List;

/** An immutable, parsed menu definition. */
public record Menu(String id, File file, String title, String texture, int rows, String command, List<String> aliases,
                   String permission, int refresh, List<String> deniedWorlds, List<Block> openRequirements,
                   List<Block> onOpen, List<Block> onClose, String bedrockContent, List<MenuItem> items) {

    public int size() {
        return rows * 9;
    }

    /** Title with placeholders applied and, when a texture skin is set, the font glyph prefixed. */
    public Component title(FlowContext ctx) {
        String text = ctx.fmt(title);
        if (texture != null && !texture.isBlank()) text = ctx.plugin.pack().composeTitle(texture, text);
        return Text.parse(text);
    }

    public MenuItem item(String key) {
        for (MenuItem item : items) if (item.key.equals(key)) return item;
        return null;
    }
}
