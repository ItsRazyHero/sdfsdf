package io.github.itsrazyhero.menuforge.menu;

import org.bukkit.event.inventory.ClickType;

/** Click flavours a menu item can react to. Config keys: left-click, right-click, middle-click, shift-left, shift-right, click. */
public enum ClickKind {
    LEFT("left-click"), RIGHT("right-click"), MIDDLE("middle-click"),
    SHIFT_LEFT("shift-left"), SHIFT_RIGHT("shift-right"), ANY("click");

    public final String key;

    ClickKind(String key) {
        this.key = key;
    }

    public static ClickKind from(ClickType type) {
        return switch (type) {
            case LEFT, DOUBLE_CLICK -> LEFT;
            case RIGHT -> RIGHT;
            case MIDDLE, CREATIVE -> MIDDLE;
            case SHIFT_LEFT -> SHIFT_LEFT;
            case SHIFT_RIGHT -> SHIFT_RIGHT;
            default -> null;
        };
    }
}
