package io.github.itsrazyhero.menuforge.flow;

import io.github.itsrazyhero.menuforge.gui.ItemBuilder;
import io.github.itsrazyhero.menuforge.menu.Menu;
import io.github.itsrazyhero.menuforge.util.Durations;
import io.github.itsrazyhero.menuforge.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/** All built-in flow blocks. Conditions carry a deny branch that runs when the check fails. */
public final class Blocks {

    private Blocks() {}

    // ---------- conditions ----------

    public abstract static class Condition implements Block {
        protected final List<Block> deny;

        protected Condition(List<Block> deny) {
            this.deny = deny == null ? List.of() : deny;
        }

        protected abstract boolean test(FlowContext ctx);

        @Override
        public final boolean run(FlowContext ctx) {
            if (test(ctx)) return true;
            FlowRunner.run(deny, ctx);
            return false;
        }
    }

    /** Passes when the player's cooldown has expired; starts it again when passing. */
    public static final class Cooldown extends Condition {
        private final String key;
        private final long millis;
        private final boolean set;

        public Cooldown(String key, String duration, boolean set, List<Block> deny) {
            super(deny);
            this.key = key;
            this.millis = Durations.parseMillis(duration);
            this.set = set;
        }

        @Override
        protected boolean test(FlowContext ctx) {
            if (ctx.player.hasPermission("menuforge.bypass.cooldown")) return true;
            String k = ctx.fmt(key);
            if (ctx.plugin.cooldowns().remaining(ctx.player.getUniqueId(), k) > 0) return false;
            if (set) ctx.plugin.cooldowns().set(ctx.player.getUniqueId(), k, millis);
            return true;
        }
    }

    public static final class Permission extends Condition {
        private final String node;

        public Permission(String node, List<Block> deny) {
            super(deny);
            this.node = node;
        }

        @Override
        protected boolean test(FlowContext ctx) {
            return ctx.player.hasPermission(ctx.fmt(node));
        }
    }

    /** Passes when the player can afford it; takes the money when "take" is set. */
    public static final class Money extends Condition {
        private final String amount;
        private final boolean take;

        public Money(String amount, boolean take, List<Block> deny) {
            super(deny);
            this.amount = amount;
            this.take = take;
        }

        @Override
        protected boolean test(FlowContext ctx) {
            double a = Double.parseDouble(ctx.fmt(amount));
            var vault = ctx.plugin.vault();
            if (!vault.available()) return false;
            if (vault.balance(ctx.player) < a) return false;
            return !take || vault.withdraw(ctx.player, a);
        }
    }

    public static final class HasItem extends Condition {
        private final Material material;
        private final int amount;
        private final boolean take;

        public HasItem(String material, int amount, boolean take, List<Block> deny) {
            super(deny);
            this.material = ItemBuilder.material(material);
            this.amount = amount;
            this.take = take;
        }

        @Override
        protected boolean test(FlowContext ctx) {
            var inv = ctx.player.getInventory();
            if (!inv.containsAtLeast(new ItemStack(material), amount)) return false;
            if (take) inv.removeItemAnySlot(new ItemStack(material, amount));
            return true;
        }
    }

    /** Compares two placeholder strings: ==, !=, >, >=, <, <=, contains, starts, regex. */
    public static final class Compare extends Condition {
        private final String input, operator, output;

        public Compare(String input, String operator, String output, List<Block> deny) {
            super(deny);
            this.input = input;
            this.operator = operator == null ? "==" : operator.trim().toLowerCase(Locale.ROOT);
            this.output = output;
        }

        @Override
        protected boolean test(FlowContext ctx) {
            String a = ctx.fmt(input), b = ctx.fmt(output);
            return switch (operator) {
                case "==", "=", "equals" -> a.equalsIgnoreCase(b);
                case "!=", "not" -> !a.equalsIgnoreCase(b);
                case ">" -> num(a) > num(b);
                case ">=" -> num(a) >= num(b);
                case "<" -> num(a) < num(b);
                case "<=" -> num(a) <= num(b);
                case "contains" -> a.toLowerCase(Locale.ROOT).contains(b.toLowerCase(Locale.ROOT));
                case "starts" -> a.toLowerCase(Locale.ROOT).startsWith(b.toLowerCase(Locale.ROOT));
                case "regex" -> Pattern.compile(b).matcher(a).find();
                default -> false;
            };
        }

        private static double num(String s) {
            try {
                return Double.parseDouble(s.trim().replace(",", ""));
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
    }

    public static final class Chance extends Condition {
        private final double percent;

        public Chance(double percent, List<Block> deny) {
            super(deny);
            this.percent = percent;
        }

        @Override
        protected boolean test(FlowContext ctx) {
            return ThreadLocalRandom.current().nextDouble() * 100 < percent;
        }
    }

    public static final class InWorld extends Condition {
        private final List<String> worlds;

        public InWorld(List<String> worlds, List<Block> deny) {
            super(deny);
            this.worlds = worlds;
        }

        @Override
        protected boolean test(FlowContext ctx) {
            return worlds.stream().anyMatch(w -> w.equalsIgnoreCase(ctx.player.getWorld().getName()));
        }
    }

    public static final class IsBedrock extends Condition {
        public IsBedrock(List<Block> deny) {
            super(deny);
        }

        @Override
        protected boolean test(FlowContext ctx) {
            return ctx.plugin.bedrock().isBedrock(ctx.player);
        }
    }

    // ---------- actions ----------

    public record Delay(int ticks) implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            return true; // handled by FlowRunner
        }
    }

    public record Message(String text) implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            ctx.player.sendMessage(ctx.text(text));
            return true;
        }
    }

    public record Broadcast(String text) implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            Bukkit.broadcast(ctx.text(text));
            return true;
        }
    }

    public record ActionBar(String text) implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            ctx.player.sendActionBar(ctx.text(text));
            return true;
        }
    }

    public record ShowTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            ctx.player.showTitle(Title.title(ctx.text(title), ctx.text(subtitle),
                    Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L),
                            Duration.ofMillis(fadeOut * 50L))));
            return true;
        }
    }

    public record PlaySound(String sound, float volume, float pitch) implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            String key = ctx.fmt(sound).trim();
            if (key.isEmpty()) return true;
            // accept both ENTITY_PLAYER_LEVELUP and entity.player.levelup
            if (key.equals(key.toUpperCase(Locale.ROOT))) key = key.toLowerCase(Locale.ROOT).replace('_', '.');
            ctx.player.playSound(net.kyori.adventure.sound.Sound.sound(Key.key(key),
                    net.kyori.adventure.sound.Sound.Source.MASTER, volume, pitch));
            return true;
        }
    }

    public record ConsoleCommand(String command) implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), strip(ctx.fmt(command)));
            return true;
        }
    }

    public record PlayerCommand(String command) implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            ctx.player.performCommand(strip(ctx.fmt(command)));
            return true;
        }
    }

    /** Opens another menu (next tick, because we are usually inside an inventory click). */
    public record OpenMenu(String menu, String args) implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            String id = ctx.fmt(menu);
            String[] argv = args == null || args.isBlank() ? new String[0] : ctx.fmt(args).split(" ");
            Bukkit.getScheduler().runTask(ctx.plugin, () -> {
                Menu target = ctx.plugin.menus().get(id);
                if (target == null) {
                    ctx.player.sendMessage(Text.parse(ctx.plugin.msg("menu-not-found").replace("{menu}", id)));
                    return;
                }
                ctx.plugin.menus().open(ctx.player, target, argv, true);
            });
            return true;
        }
    }

    public record Close() implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            Bukkit.getScheduler().runTask(ctx.plugin, () -> ctx.player.closeInventory());
            return false;
        }
    }

    public record Back() implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            Bukkit.getScheduler().runTask(ctx.plugin, () -> ctx.plugin.menus().back(ctx.player));
            return true;
        }
    }

    public record Refresh() implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            Bukkit.getScheduler().runTask(ctx.plugin, () -> ctx.plugin.menus().refresh(ctx.player));
            return true;
        }
    }

    public record GiveMoney(String amount) implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            return ctx.plugin.vault().deposit(ctx.player, Double.parseDouble(ctx.fmt(amount)));
        }
    }

    public record GiveItem(String material, int amount, String name, List<String> lore) implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            ItemStack stack = ItemBuilder.simple(ItemBuilder.material(ctx.fmt(material)), amount,
                    name == null ? null : ctx.fmt(name),
                    lore == null ? List.of() : lore.stream().map(ctx::fmt).toList());
            ctx.player.getInventory().addItem(stack).values()
                    .forEach(rest -> ctx.player.getWorld().dropItemNaturally(ctx.player.getLocation(), rest));
            return true;
        }
    }

    public record ResetCooldown(String key) implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            ctx.plugin.cooldowns().reset(ctx.player.getUniqueId(), ctx.fmt(key));
            return true;
        }
    }

    public record Teleport(String world, double x, double y, double z, float yaw, float pitch) implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            World w = world == null || world.isBlank() ? ctx.player.getWorld() : Bukkit.getWorld(ctx.fmt(world));
            if (w == null) return false;
            Bukkit.getScheduler().runTask(ctx.plugin,
                    () -> ctx.player.teleportAsync(new Location(w, x, y, z, yaw, pitch)));
            return true;
        }
    }

    /** Set a temporary placeholder for the rest of the flow: %var_<name>%. */
    public record SetVar(String name, String value) implements Block {
        @Override
        public boolean run(FlowContext ctx) {
            ctx.args.put("var_" + name, ctx.fmt(value));
            return true;
        }
    }

    private static String strip(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
