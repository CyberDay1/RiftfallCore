package com.cyberday1.riftfall.compat.jei;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.compat.viewer.ConversionDisplay;
import com.cyberday1.riftfall.compat.viewer.ViewerLang;
import com.cyberday1.riftfall.home.npc.conversion.ResidentConversionRecipe;
import com.cyberday1.riftfall.init.RiftfallRecipes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/** "Quarry Specialist: Conversions": every shipped conversion at every rank and path, each with its badge. */
final class ConversionCategory extends ResidentCategory<RecipeHolder<ResidentConversionRecipe>> {
    static final RecipeType<RecipeHolder<ResidentConversionRecipe>> TYPE = RecipeType.createFromVanilla(RiftfallRecipes.RESIDENT_CONVERSION.get());

    ConversionCategory(IGuiHelper gui) { super(TYPE, NpcRole.QUARRY_SPECIALIST, ViewerLang.CONVERSIONS, gui); }

    @Override public void setRecipe(IRecipeLayoutBuilder layout, RecipeHolder<ResidentConversionRecipe> holder, IFocusGroup focuses) {
        var d = ConversionDisplay.of(holder);
        var lists = new ArrayList<List<ItemStack>>();
        for (var input : d.inputs()) lists.add(ConversionDisplay.stacks(input));
        inputs(layout, lists);
        if (d.shards() > 0) shards(layout, d.shardStack());
        d.catalyst().ifPresent(c -> layout.addInputSlot(SHARD_X, CATALYST_Y).setStandardSlotBackground().addIngredients(c));
        output(layout, d.result());
    }

    @Override public void createRecipeExtras(IRecipeExtrasBuilder extras, RecipeHolder<ResidentConversionRecipe> holder, IFocusGroup focuses) {
        var d = ConversionDisplay.of(holder);
        title(extras, d.result().getHoverName());
        badge(extras, d.badge());
        arrow(extras);
        if (d.variant()) footer(extras, d.orderLine(), Component.translatable(ViewerLang.REPLACES));
        else footer(extras, d.orderLine());
    }
}
