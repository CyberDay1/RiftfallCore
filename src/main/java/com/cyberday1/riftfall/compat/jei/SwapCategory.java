package com.cyberday1.riftfall.compat.jei;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.compat.viewer.SwapDisplay;
import com.cyberday1.riftfall.compat.viewer.ViewerLang;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;

import java.util.List;

/** "Quarry Specialist: Crystal Swap": one row per target crystal and per Master path that changes the cost. */
final class SwapCategory extends ResidentCategory<SwapDisplay> {
    static final RecipeType<SwapDisplay> TYPE = RecipeType.create(RiftfallCore.MODID, "quarry_crystal_swap", SwapDisplay.class);

    SwapCategory(IGuiHelper gui) { super(TYPE, NpcRole.QUARRY_SPECIALIST, ViewerLang.SWAPS, gui); }

    @Override public void setRecipe(IRecipeLayoutBuilder layout, SwapDisplay d, IFocusGroup focuses) {
        inputs(layout, List.of(d.inputs()));
        shards(layout, d.shardStack());
        output(layout, d.result());
    }

    @Override public void createRecipeExtras(IRecipeExtrasBuilder extras, SwapDisplay d, IFocusGroup focuses) {
        title(extras, d.title());
        badge(extras, d.badge());
        arrow(extras);
        footer(extras, d.costLine(), d.requirement());
    }
}
