package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarCurios;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets = "top.theillusivec4.curios.client.gui.RenderButton", remap = false)
public abstract class MixinStellarCuriosVisibility extends AbstractWidget {
    @Shadow @Final private int xTexStart;
    protected MixinStellarCuriosVisibility(int x, int y, int w, int h, Component title) { super(x, y, w, h, title); }

    @Redirect(method = "renderButtonOverlay", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"))
    private void riftfall$visibility(GuiGraphics g, ResourceLocation texture, int x, int y, float u, float v,
                                     int w, int h, int tw, int th) {
        StellarCurios.visibilityToggle(g, x, y, w, h, isHoveredOrFocused(), u == xTexStart);
    }
}
