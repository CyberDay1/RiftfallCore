package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarCurios;
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
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;

/** Skin the separate Curios screen without replacing its slots, pagination or player preview. */
@Pseudo
@Mixin(targets = "top.theillusivec4.curios.client.gui.CuriosScreen", remap = false)
public abstract class MixinStellarCuriosScreen extends EffectRenderingInventoryScreen<AbstractContainerMenu> {
    @Shadow @Final private RecipeBookComponent recipeBookGui;
    protected MixinStellarCuriosScreen(AbstractContainerMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Redirect(method = "renderBg", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void riftfall$background(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int w, int h) {
        StellarInventory.background(g, x, y, w, h, menu);
    }

    @Redirect(method = "lambda$renderBg$5", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void riftfall$sidePanel(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int w, int h) {
        var curios = (CuriosContainer) menu;
        if (!StellarCurios.panelPiece(g, x, y, u, v, w, h, curios.grid.size(),
                curios.grid.isEmpty() ? 0 : curios.grid.getFirst(), curios.totalPages > 1))
            g.blit(texture, x, y, u, v, w, h);
    }

    @ModifyArg(method = "renderLabels", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"), index = 4)
    private int riftfall$label(int color) { return StellarInventory.labelColor(); }

    @Redirect(method = "init", at = @At(value = "NEW", target = "net/minecraft/client/gui/components/ImageButton"))
    private ImageButton riftfall$recipes(int x, int y, int w, int h, WidgetSprites sprites, Button.OnPress press) {
        return StellarInventory.recipeButton(x, y, w, h, sprites, press, recipeBookGui::isVisible);
    }
}
