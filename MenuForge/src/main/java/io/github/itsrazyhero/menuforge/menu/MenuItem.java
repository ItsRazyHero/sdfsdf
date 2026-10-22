package io.github.itsrazyhero.menuforge.menu;

import io.github.itsrazyhero.menuforge.flow.Block;
import io.github.itsrazyhero.menuforge.flow.FlowContext;
import io.github.itsrazyhero.menuforge.flow.FlowRunner;

import java.util.List;
import java.util.Map;

/** A configured slot: base icon, conditional states, view requirements and click flows. */
public final class MenuItem {

    /** Bedrock-specific presentation of this item as a form button. */
    public record Bedrock(String text, String rightText, String image, boolean hidden) {}

    /** Alternative look used when its requirements pass (first match wins). */
    public record State(String name, List<Block> requirements, Icon icon) {}

    public final String key;
    public final List<Integer> slots;
    public final Icon icon;
    public final int priority;
    public final List<Block> viewRequirements;
    public final List<State> states;
    public final Map<ClickKind, List<Block>> flows;
    public final Bedrock bedrock;

    public MenuItem(String key, List<Integer> slots, Icon icon, int priority, List<Block> viewRequirements,
                    List<State> states, Map<ClickKind, List<Block>> flows, Bedrock bedrock) {
        this.key = key;
        this.slots = slots;
        this.icon = icon;
        this.priority = priority;
        this.viewRequirements = viewRequirements;
        this.states = states;
        this.flows = flows;
        this.bedrock = bedrock;
    }

    public boolean visible(FlowContext ctx) {
        return FlowRunner.check(viewRequirements, ctx);
    }

    /** Picks the icon to show right now: the first state whose requirements pass, else the base icon. */
    public Icon resolve(FlowContext ctx) {
        for (State state : states) {
            if (FlowRunner.check(state.requirements(), ctx)) return icon.merge(state.icon());
        }
        return icon;
    }

    public List<Block> flow(ClickKind kind) {
        List<Block> specific = kind == null ? null : flows.get(kind);
        if (specific != null && !specific.isEmpty()) return specific;
        return flows.getOrDefault(ClickKind.ANY, List.of());
    }

    public boolean hasFlow(ClickKind kind) {
        return !flow(kind).isEmpty();
    }

    public boolean interactive() {
        return flows.values().stream().anyMatch(f -> !f.isEmpty());
    }
}
