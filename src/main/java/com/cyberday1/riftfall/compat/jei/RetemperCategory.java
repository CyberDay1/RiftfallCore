package com.cyberday1.riftfall.compat.jei;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.compat.viewer.ConversionDisplay;
import com.cyberday1.riftfall.compat.viewer.FitterExamples;
import com.cyberday1.riftfall.compat.viewer.RetemperDisplay;
import com.cyberday1.riftfall.compat.viewer.ViewerLang;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/** "Fitter: Re-temper": one page per gear kind, plus the Master paths that discount it, with a target-tier picker. */
final class RetemperCategory extends ResidentCategory<RetemperDisplay> {
    static final RecipeType<RetemperDisplay> TYPE = RecipeType.create(RiftfallCore.MODID, "fitter_retemper", RetemperDisplay.class);
    private static final String GEAR = "gear", STOCK = "stock", SHARDS = "shards", RESULT = "result";

    RetemperCategory(IGuiHelper gui) { super(TYPE, NpcRole.BLACKSMITH, ViewerLang.RETEMPER, gui, TALL); }

    @Override public void setRecipe(IRecipeLayoutBuilder layout, RetemperDisplay d, IFocusGroup focuses) {
        int target = initial(RetemperDisplay.TIERS);
        input(layout, 0, GEAR, FitterExamples.retemper(d.kind(), target - 1)).addRichTooltipCallback((view, tooltip) ->
                tooltip.add(Component.translatable(ViewerLang.RETEMPER_ANY).withStyle(ChatFormatting.GRAY)));
        input(layout, 1, STOCK, List.of(FitterExamples.stock(d.stock(target))));
        shards(layout, SHARDS, ConversionDisplay.shardStack(d.shards(target)));
        output(layout, RESULT, FitterExamples.retemper(d.kind(), target));
    }

    @Override public void createRecipeExtras(IRecipeExtrasBuilder extras, RetemperDisplay d, IFocusGroup focuses) {
        title(extras, d.title());
        badge(extras, d.badge());
        arrow(extras);
        footer(extras, d.footer());
        picker(extras, RetemperDisplay.TIERS, initial(RetemperDisplay.TIERS), ViewerLang.TIER_BUTTON, ViewerLang.TIER_HINT, target -> {
            show(extras, GEAR, FitterExamples.retemper(d.kind(), target - 1));
            show(extras, STOCK, List.of(FitterExamples.stock(d.stock(target))));
            show(extras, SHARDS, List.of(ConversionDisplay.shardStack(d.shards(target))));
            show(extras, RESULT, FitterExamples.retemper(d.kind(), target));
        });
    }
}
