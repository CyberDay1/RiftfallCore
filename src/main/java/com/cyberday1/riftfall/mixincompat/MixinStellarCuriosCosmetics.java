package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarCurios;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets = "top.theillusivec4.curios.client.gui.CosmeticButton", remap = false)
public abstract class MixinStellarCuriosCosmetics extends AbstractWidget {
    protected MixinStellarCuriosCosmetics(int x, int y, int w, int h, Component title) { super(x, y, w, h, title); }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void riftfall$cosmetics(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        StellarCurios.cosmeticButton(g, x, y, w, h, isHoveredOrFocused(), sprite.getPath().startsWith("cosmetic_on"));
    }
}
