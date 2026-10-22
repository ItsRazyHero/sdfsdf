package io.github.itsrazyhero.menuforge.bedrock;

import io.github.itsrazyhero.menuforge.MenuForgePlugin;
import io.github.itsrazyhero.menuforge.flow.FlowContext;
import io.github.itsrazyhero.menuforge.flow.FlowRunner;
import io.github.itsrazyhero.menuforge.hook.BedrockHook;
import io.github.itsrazyhero.menuforge.menu.ClickKind;
import io.github.itsrazyhero.menuforge.menu.Icon;
import io.github.itsrazyhero.menuforge.menu.Menu;
import io.github.itsrazyhero.menuforge.menu.MenuItem;
import io.github.itsrazyhero.menuforge.menu.MenuLoader;
import io.github.itsrazyhero.menuforge.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Renders a menu as a Bedrock simple form: one button per interactive item (two if it has a right-click flow). */
public final class BedrockMenus {

    private record Entry(MenuItem item, ClickKind kind, BedrockHook.Button button) {}

    private final MenuForgePlugin plugin;

    public BedrockMenus(MenuForgePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean open(Player player, Menu menu, FlowContext ctx) {
        if (!plugin.bedrock().available()) return false;
        List<Entry> entries = new ArrayList<>();
        List<MenuItem> ordered = new ArrayList<>(menu.items());
        ordered.sort(Comparator.comparingInt(i -> i.slots.isEmpty() ? Integer.MAX_VALUE : i.slots.getFirst()));
        for (MenuItem item : ordered) {
            if (item.bedrock.hidden() || !item.interactive() || !item.visible(ctx)) continue;
            Icon icon = item.resolve(ctx);
            String label = item.bedrock.text().isBlank()
                    ? (icon.name() == null ? MenuLoader.prettify(icon.materialOr("STONE")) : icon.name())
                    : item.bedrock.text();
            label = Text.legacy(ctx.fmt(label));
            String image = ctx.fmt(item.bedrock.image());
            boolean hasLeft = item.hasFlow(ClickKind.LEFT);
            if (hasLeft) entries.add(new Entry(item, ClickKind.LEFT, new BedrockHook.Button(label, image)));
            if (item.flows.containsKey(ClickKind.RIGHT)) {
                String right = item.bedrock.rightText().isBlank() ? label + " §7(right click)" : Text.legacy(ctx.fmt(item.bedrock.rightText()));
                entries.add(new Entry(item, ClickKind.RIGHT, new BedrockHook.Button(right, image)));
            }
        }
        String title = Text.legacy(ctx.fmt(menu.title()));
        String content = Text.legacy(ctx.fmt(menu.bedrockContent()));
        return plugin.bedrock().sendSimpleForm(player, title, content, entries.stream().map(Entry::button).toList(),
                index -> Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.menus().bedrockClosed(player);
                    if (index < 0 || index >= entries.size() || !player.isOnline()) return;
                    Entry entry = entries.get(index);
                    FlowRunner.run(entry.item().flow(entry.kind()), ctx.with("item", entry.item().key));
                }),
                () -> Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.menus().bedrockClosed(player);
                    if (player.isOnline()) FlowRunner.run(menu.onClose(), ctx);
                }));
    }
}
