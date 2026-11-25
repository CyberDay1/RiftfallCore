package com.cyberday1.riftfall.compat.jei;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.compat.viewer.FitterExamples;
import com.cyberday1.riftfall.compat.viewer.SalvageDisplay;
import com.cyberday1.riftfall.compat.viewer.ViewerLang;
import com.cyberday1.riftfall.home.npc.smith.SmithService;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** "Fitter: Salvage": one page per rarity, standard and Prospector, with a tier picker. */
final class SalvageCategory extends ResidentCategory<SalvageDisplay> {
    static final RecipeType<SalvageDisplay> TYPE = RecipeType.create(RiftfallCore.MODID, "fitter_salvage", SalvageDisplay.class);
    private static final String GEAR = "gear", MATERIAL = "material", STOCK = "stock";

    SalvageCategory(IGuiHelper gui) { super(TYPE, NpcRole.BLACKSMITH, ViewerLang.SALVAGE, gui, TALL); }

    private static List<ItemStack> material(SalvageDisplay d, int tier) {
        var stack = SmithService.materialFor(d.rarity(), d.material(tier));
        return stack.isEmpty() ? List.of() : List.of(stack);
    }

    @Override public void setRecipe(IRecipeLayoutBuilder layout, SalvageDisplay d, IFocusGroup focuses) {
        int tier = initial(SalvageDisplay.TIERS);
        input(layout, 0, GEAR, FitterExamples.salvage(d.rarity(), tier)).addRichTooltipCallback((view, tooltip) ->
                tooltip.add(Component.translatable(ViewerLang.SALVAGE_ANY).withStyle(ChatFormatting.GRAY)));
        output(layout, 0, MATERIAL, material(d, tier));
        output(layout, 1, STOCK, List.of(FitterExamples.stock(d.stock(tier))));
    }

    @Override public void createRecipeExtras(IRecipeExtrasBuilder extras, SalvageDisplay d, IFocusGroup focuses) {
        title(extras, d.title());
        badge(extras, d.badge());
        arrow(extras);
        var footer = d.footer();
        footer(extras, 0, footer.getFirst(), SalvageDisplay.refusalLines());
        if (footer.size() > 1) footer(extras, 1, footer.get(1), List.of());
        picker(extras, SalvageDisplay.TIERS, initial(SalvageDisplay.TIERS), ViewerLang.TIER_BUTTON, ViewerLang.TIER_HINT, tier -> {
            show(extras, GEAR, FitterExamples.salvage(d.rarity(), tier));
            show(extras, MATERIAL, material(d, tier));
            show(extras, STOCK, List.of(FitterExamples.stock(d.stock(tier))));
        });
    }
}
