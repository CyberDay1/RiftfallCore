package com.cyberday1.riftfall.compat.jei;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.compat.viewer.ConversionDisplay;
import com.cyberday1.riftfall.compat.viewer.FitterExamples;
import com.cyberday1.riftfall.compat.viewer.InfoPages;
import com.cyberday1.riftfall.compat.viewer.ResidentIngredient;
import com.cyberday1.riftfall.compat.viewer.RetemperDisplay;
import com.cyberday1.riftfall.compat.viewer.RuneDisplay;
import com.cyberday1.riftfall.compat.viewer.SalvageDisplay;
import com.cyberday1.riftfall.compat.viewer.SwapDisplay;
import com.cyberday1.riftfall.compat.viewer.ToolWorkDisplay;
import com.cyberday1.riftfall.home.npc.conversion.ResidentConversions;
import com.cyberday1.riftfall.init.RiftfallTags;
import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;

/**
 * Riftfall in JEI. JEI finds this by its annotation and nothing else names it, so without JEI the package never loads.
 * Phase 1: the resident ingredient, the Quarry Specialist's two categories, and the information pages. Phase 2: the
 * Fitter's four.
 */
@JeiPlugin
public final class RiftfallJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(RiftfallCore.MODID, "jei");

    @Override public ResourceLocation getPluginUid() { return UID; }

    @Override public void registerIngredients(IModIngredientRegistration registration) {
        registration.register(ResidentIngredients.TYPE, ResidentIngredient.SERVICED, new ResidentIngredients.Helper(),
                new ResidentIngredients.Renderer(), ResidentIngredient.CODEC);
    }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new ConversionCategory(gui), new SwapCategory(gui), new SalvageCategory(gui),
                new RetemperCategory(gui), new ToolWorkCategory(gui), new RuneCategory(gui));
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            var manager = level.getRecipeManager();
            if (ResidentConversions.safe(manager))
                registration.addRecipes(ConversionCategory.TYPE, ConversionDisplay.all(ResidentConversions.forRole(manager, NpcRole.QUARRY_SPECIALIST)));
            else LogUtils.getLogger().warn("Riftfall JEI: the server refuses every resident conversion, so none are shown");
        }
        var crystals = new ArrayList<Item>();
        BuiltInRegistries.ITEM.getTag(RiftfallTags.THEME_CRYSTALS).ifPresent(set -> set.forEach(h -> crystals.add(h.value())));
        registration.addRecipes(SwapCategory.TYPE, SwapDisplay.all(crystals, item -> new ItemStack(item).is(RiftfallTags.RARE_CRYSTALS)));
        registration.addRecipes(SalvageCategory.TYPE, SalvageDisplay.all());
        // A kind with no stand-in items (no Curios trinkets loaded) would be a page with nothing in it.
        registration.addRecipes(RetemperCategory.TYPE, RetemperDisplay.all().stream().filter(d -> !FitterExamples.items(d.kind()).isEmpty()).toList());
        registration.addRecipes(ToolWorkCategory.TYPE, ToolWorkDisplay.all());
        registration.addRecipes(RuneCategory.TYPE, RuneDisplay.all());

        for (var page : InfoPages.items()) {
            var stacks = new ArrayList<ItemStack>();
            for (var id : page.items()) BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> stacks.add(new ItemStack(item)));
            page.tag().flatMap(BuiltInRegistries.ITEM::getTag).ifPresent(set -> set.forEach(h -> stacks.add(new ItemStack(h))));
            if (!stacks.isEmpty()) registration.addItemStackInfo(stacks, page.lines().toArray(Component[]::new));
        }
        for (var resident : ResidentIngredient.SERVICED)
            registration.addIngredientInfo(resident, ResidentIngredients.TYPE, InfoPages.resident(resident.role()).toArray(Component[]::new));
    }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ResidentIngredients.TYPE, ResidentIngredient.of(NpcRole.QUARRY_SPECIALIST),
                ConversionCategory.TYPE, SwapCategory.TYPE);
        registration.addRecipeCatalyst(ResidentIngredients.TYPE, ResidentIngredient.of(NpcRole.BLACKSMITH),
                SalvageCategory.TYPE, RetemperCategory.TYPE, ToolWorkCategory.TYPE, RuneCategory.TYPE);
    }
}
