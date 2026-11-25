package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.client.StellarInventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Skin the background sprite only, during the survival recipe book's scoped render. */
@Mixin({RecipeBookTabButton.class, RecipeButton.class, StateSwitchingButton.class})
public abstract class MixinStellarRecipeControls extends AbstractWidget {
    protected MixinStellarRecipeControls(int x, int y, int w, int h, Component title) { super(x, y, w, h, title); }
    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void riftfall$control(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        if (!StellarInventory.recipeControl(g, sprite, x, y, w, h, isHoveredOrFocused(), isFocused()))
            g.blitSprite(sprite, x, y, w, h);
    }
}
