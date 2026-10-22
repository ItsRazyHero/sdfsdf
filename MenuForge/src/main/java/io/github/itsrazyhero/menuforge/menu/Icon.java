package io.github.itsrazyhero.menuforge.menu;

import java.util.List;

/** Visual description of a slot. Nullable fields mean "inherit" when a state is layered over the base icon. */
public record Icon(String material, String name, List<String> lore, Integer amount, Boolean glow,
                   Integer customModelData, String itemModel, String head, Boolean hideTooltip, Boolean hideFlags) {

    public static final Icon EMPTY = new Icon(null, null, null, null, null, null, null, null, null, null);

    /** Returns a copy of this icon with every non-null field of {@code over} applied on top. */
    public Icon merge(Icon over) {
        if (over == null) return this;
        return new Icon(
                over.material != null ? over.material : material,
                over.name != null ? over.name : name,
                over.lore != null ? over.lore : lore,
                over.amount != null ? over.amount : amount,
                over.glow != null ? over.glow : glow,
                over.customModelData != null ? over.customModelData : customModelData,
                over.itemModel != null ? over.itemModel : itemModel,
                over.head != null ? over.head : head,
                over.hideTooltip != null ? over.hideTooltip : hideTooltip,
                over.hideFlags != null ? over.hideFlags : hideFlags);
    }

    public String materialOr(String def) {
        return material == null || material.isBlank() ? def : material;
    }

    public int amountOr(int def) {
        return amount == null ? def : amount;
    }
}
