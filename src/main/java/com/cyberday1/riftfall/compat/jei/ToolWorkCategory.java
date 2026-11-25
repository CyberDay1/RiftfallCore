package com.cyberday1.riftfall.compat.jei;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.compat.viewer.ConversionDisplay;
import com.cyberday1.riftfall.compat.viewer.FitterExamples;
import com.cyberday1.riftfall.compat.viewer.ToolWorkDisplay;
import com.cyberday1.riftfall.compat.viewer.ViewerLang;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.init.RiftfallTags;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;

/** "Fitter: Tool Work": Fortune I to VIII and the looting-pick modes, standard and Toolwright. */
final class ToolWorkCategory extends ResidentCategory<ToolWorkDisplay> {
    static final RecipeType<ToolWorkDisplay> TYPE = RecipeType.create(RiftfallCore.MODID, "fitter_tool_work", ToolWorkDisplay.class);

    ToolWorkCategory(IGuiHelper gui) { super(TYPE, NpcRole.BLACKSMITH, ViewerLang.TOOL_WORK, gui); }

    /** The client world's Fortune; null before a world is joined, when the tools show unenchanted. */
    private static Holder<Enchantment> fortune() {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        return level.registryAccess().registry(Registries.ENCHANTMENT).flatMap(r -> r.getHolder(Enchantments.FORTUNE)).orElse(null);
    }

    @Override public void setRecipe(IRecipeLayoutBuilder layout, ToolWorkDisplay d, IFocusGroup focuses) {
        var inputs = new ArrayList<List<ItemStack>>();
        List<ItemStack> result;
        if (d.fortune()) {
            var fortune = fortune();
            inputs.add(FitterExamples.fortune(d.target() - 1, fortune));
            result = FitterExamples.fortune(d.target(), fortune);
        } else {
            inputs.add(List.of(new ItemStack(RiftfallItems.LOOTING_PICK.get())));
            result = List.of(FitterExamples.pick(d.mode().orElseThrow()));
        }
        inputs.add(FitterExamples.material(d.rarity(), d.material()));
        if (d.crystals() > 0) inputs.add(FitterExamples.tagged(RiftfallTags.RARE_CRYSTALS, d.crystals()));
        if (d.faceted() > 0) inputs.add(List.of(new ItemStack(RiftfallItems.FACETED_CRYSTAL.get(), d.faceted())));
        for (int i = 0; i < inputs.size(); i++) {
            var slot = input(layout, i, "input" + i, inputs.get(i));
            if (i == 0 && d.fortune()) slot.addRichTooltipCallback((view, tooltip) ->
                    tooltip.add(Component.translatable(ViewerLang.FORTUNE_TOOL).withStyle(ChatFormatting.GRAY)));
        }
        shards(layout, ConversionDisplay.shardStack(d.shards()));
        output(layout, "result", result);
    }

    @Override public void createRecipeExtras(IRecipeExtrasBuilder extras, ToolWorkDisplay d, IFocusGroup focuses) {
        title(extras, d.title());
        badge(extras, d.badge());
        arrow(extras);
        footer(extras, d.footer());
    }
}
