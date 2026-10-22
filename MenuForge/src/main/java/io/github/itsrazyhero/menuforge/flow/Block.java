package io.github.itsrazyhero.menuforge.flow;

/** One node of a click flow. Returns false to stop the chain (a failed condition, a close, ...). */
@FunctionalInterface
public interface Block {
    boolean run(FlowContext ctx);
}
