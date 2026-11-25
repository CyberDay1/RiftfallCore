package com.cyberday1.riftfall.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

public final class RunestoneItem extends Item {
    private final RuneGrade grade;
    public RunestoneItem(Properties properties, RuneGrade grade) { super(properties); this.grade = grade; }
    public RuneGrade grade() { return grade; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable(ToolLang.RUNE_GRADE, grade.displayName()).withStyle(grade.color));
        lines.add(Component.translatable(ToolLang.RUNE_INSTALL).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(ToolLang.RUNE_CHOOSE).withStyle(ChatFormatting.DARK_GRAY));
        for (var stat : ToolStat.values()) if (grade.supports(stat)) {
            lines.add(Component.translatable(ToolLang.RUNE_STAT, stat.displayName(), stat.bonus(grade.strength))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
