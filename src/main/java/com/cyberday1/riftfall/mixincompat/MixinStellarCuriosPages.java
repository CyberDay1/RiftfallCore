package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarCurios;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets = "top.theillusivec4.curios.client.gui.PageButton", remap = false)
public abstract class MixinStellarCuriosPages extends AbstractWidget {
    protected MixinStellarCuriosPages(int x, int y, int w, int h, Component title) { super(x, y, w, h, title); }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void riftfall$page(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int w, int h) {
        // Native UVs include the hover offset; retain its page-state updates and tooltip above this draw.
        StellarCurios.pageButton(g, x, y, w, h, isHoveredOrFocused(), active, u == 32 || u == 54);
    }
}
