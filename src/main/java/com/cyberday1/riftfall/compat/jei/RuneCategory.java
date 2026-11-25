package com.cyberday1.riftfall.compat.jei;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.compat.viewer.ConversionDisplay;
import com.cyberday1.riftfall.compat.viewer.FitterExamples;
import com.cyberday1.riftfall.compat.viewer.RuneDisplay;
import com.cyberday1.riftfall.compat.viewer.ViewerLang;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** "Fitter: Runestones": one page per rune grade, standard and Toolwright, with a pick-level picker for the fee. */
final class RuneCategory extends ResidentCategory<RuneDisplay> {
    static final RecipeType<RuneDisplay> TYPE = RecipeType.create(RiftfallCore.MODID, "fitter_runestones", RuneDisplay.class);
    private static final String PICK = "pick", SHARDS = "shards", RESULT = "result";

    RuneCategory(IGuiHelper gui) { super(TYPE, NpcRole.BLACKSMITH, ViewerLang.RUNESTONES, gui, TALL); }

    @Override public void setRecipe(IRecipeLayoutBuilder layout, RuneDisplay d, IFocusGroup focuses) {
        int level = RuneDisplay.LEVELS.getFirst();
        input(layout, 0, PICK, List.of(FitterExamples.pick(level)));
        input(layout, 1, "rune", List.of(new ItemStack(d.grade().item())))
                .addRichTooltipCallback((view, tooltip) -> d.effectLines().forEach(tooltip::add));
        shards(layout, SHARDS, ConversionDisplay.shardStack(d.fee(level)));
        output(layout, RESULT, List.of(FitterExamples.pick(level + 1)));
    }

    @Override public void createRecipeExtras(IRecipeExtrasBuilder extras, RuneDisplay d, IFocusGroup focuses) {
        title(extras, d.title());
        badge(extras, d.badge());
        arrow(extras);
        footer(extras, d.footer());
        picker(extras, RuneDisplay.LEVELS, RuneDisplay.LEVELS.getFirst(), ViewerLang.LEVEL_BUTTON, ViewerLang.LEVEL_HINT, level -> {
            show(extras, PICK, List.of(FitterExamples.pick(level)));
            show(extras, SHARDS, List.of(ConversionDisplay.shardStack(d.fee(level))));
            show(extras, RESULT, List.of(FitterExamples.pick(level + 1)));
        });
    }
}
