package com.cyberday1.riftfall.tool;

import com.cyberday1.riftfall.init.RiftfallItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

public enum RuneGrade {
    COMMON("Common", 1, ChatFormatting.WHITE),
    UNCOMMON("Uncommon", 2, ChatFormatting.GREEN),
    RARE("Rare", 3, ChatFormatting.AQUA),
    EXOTIC("Exotic", 5, ChatFormatting.LIGHT_PURPLE);

    /** English only; players see {@link #key()}. */
    public final String label;
    public final int strength;
    public final ChatFormatting color;
    RuneGrade(String label, int strength, ChatFormatting color) { this.label = label; this.strength = strength; this.color = color; }
    public String key() { return ToolLang.GRADE + ToolLang.lower(this); }
    public Component displayName() { return Component.translatable(key()); }
    public static RuneGrade of(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof RunestoneItem rune ? rune.grade() : null;
    }
    public Item item() {
        return switch (this) {
            case COMMON -> RiftfallItems.COMMON_RUNESTONE.get();
            case UNCOMMON -> RiftfallItems.UNCOMMON_RUNESTONE.get();
            case RARE -> RiftfallItems.RARE_RUNESTONE.get();
            case EXOTIC -> RiftfallItems.EXOTIC_RUNESTONE.get();
        };
    }
    public boolean supports(ToolStat stat) { return ordinal() >= stat.minimum.ordinal(); }
}
