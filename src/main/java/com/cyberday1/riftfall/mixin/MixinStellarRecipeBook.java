package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.client.StellarInventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.RecipeBookMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/** The survival inventory's recipe panel; other crafting screens retain their own art. */
@Mixin(RecipeBookComponent.class)
public abstract class MixinStellarRecipeBook {
    @Shadow protected RecipeBookMenu<?, ?> menu;
    @WrapMethod(method = "render")
    private void riftfall$recipeScope(GuiGraphics g, int mouseX, int mouseY, float partial, Operation<Void> original) {
        StellarInventory.renderRecipes(StellarInventory.inventoryRecipes(menu), () -> original.call(g, mouseX, mouseY, partial));
    }
    @Redirect(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void riftfall$background(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int w, int h) {
        if (StellarInventory.inventoryRecipes(menu)) StellarInventory.recipeBackground(g, x, y, w, h);
        else g.blit(texture, x, y, u, v, w, h);
    }
}
