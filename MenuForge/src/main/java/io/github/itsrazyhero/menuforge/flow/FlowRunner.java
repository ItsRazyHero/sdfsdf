package io.github.itsrazyhero.menuforge.flow;

import java.util.List;

/** Executes a list of blocks in order; a "delay" block schedules the rest of the chain. */
public final class FlowRunner {

    private FlowRunner() {}

    /** Runs the whole chain. Returns true when every block passed (no condition failed, no stop). */
    public static boolean run(List<Block> blocks, FlowContext ctx) {
        return run(blocks, 0, ctx);
    }

    private static boolean run(List<Block> blocks, int from, FlowContext ctx) {
        for (int i = from; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (block instanceof Blocks.Delay delay) {
                int next = i + 1;
                if (next < blocks.size()) {
                    ctx.plugin.getServer().getScheduler().runTaskLater(ctx.plugin, () -> {
                        if (ctx.player.isOnline()) run(blocks, next, ctx);
                    }, delay.ticks());
                }
                return true;
            }
            if (!ctx.player.isOnline()) return false;
            try {
                if (!block.run(ctx)) return false;
            } catch (RuntimeException e) {
                ctx.plugin.getLogger().warning("Flow block " + block.getClass().getSimpleName()
                        + " failed for " + ctx.player.getName() + ": " + e);
                return false;
            }
        }
        return true;
    }

    /** Evaluates a requirement list without side effects beyond the deny branches. */
    public static boolean check(List<Block> requirements, FlowContext ctx) {
        return requirements == null || requirements.isEmpty() || run(requirements, ctx);
    }
}
