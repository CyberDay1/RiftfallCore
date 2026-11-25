package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarInventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** I-key inventory: keep Aether's menu, crafting, player renderer and accessory toggles. */
@Pseudo
@Mixin(targets = "com.aetherteam.aether.client.gui.screen.inventory.AetherAccessoriesScreen", remap = false)
public abstract class MixinStellarAetherInventory extends EffectRenderingInventoryScreen<AbstractContainerMenu> {
    @Shadow @Final private RecipeBookComponent recipeBookComponent;
    protected MixinStellarAetherInventory(AbstractContainerMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Redirect(method = "renderBg", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void riftfall$background(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int w, int h) {
        StellarInventory.aetherBackground(g, x, y, imageWidth, h, menu);
    }

    @ModifyArg(method = "renderLabels", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"), index = 4)
    private int riftfall$label(int color) { return StellarInventory.labelColor(); }

    @Redirect(method = "init", at = @At(value = "NEW", target = "net/minecraft/client/gui/components/ImageButton"))
    private ImageButton riftfall$recipes(int x, int y, int w, int h, WidgetSprites sprites, Button.OnPress press) {
        return StellarInventory.recipeButton(x, y, w, h, sprites, press, recipeBookComponent::isVisible);
    }
}
