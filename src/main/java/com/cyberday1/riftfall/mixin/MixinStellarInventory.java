package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.client.StellarInventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Replace only vanilla inventory art. Entity rendering, recipe-book offsets and mod-added slots remain native. */
@Mixin(InventoryScreen.class)
public abstract class MixinStellarInventory extends EffectRenderingInventoryScreen<InventoryMenu> {
    @Shadow @Final private RecipeBookComponent recipeBookComponent;
    protected MixinStellarInventory(InventoryMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Redirect(method = "renderBg", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void riftfall$background(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int w, int h) {
        StellarInventory.background(g, x, y, w, h, menu);
    }

    @ModifyArg(method = "renderLabels", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"), index = 4)
    private int riftfall$labelColor(int original) { return StellarInventory.labelColor(); }

    @Redirect(method = "init", at = @At(value = "NEW", target = "net/minecraft/client/gui/components/ImageButton"))
    private ImageButton riftfall$recipeButton(int x, int y, int w, int h, WidgetSprites sprites, Button.OnPress press) {
        return StellarInventory.recipeButton(x, y, w, h, sprites, press, recipeBookComponent::isVisible);
    }
}
