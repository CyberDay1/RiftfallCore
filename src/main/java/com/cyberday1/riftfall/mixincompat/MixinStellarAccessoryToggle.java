package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarInventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets = "io.wispforest.accessories.client.gui.ToggleButton", remap = false)
public abstract class MixinStellarAccessoryToggle {
    @Shadow private boolean toggled;
    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void riftfall$toggle(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h) {
        StellarInventory.control(g, x, y, w, h, texture.getPath().contains("highlighted"), toggled);
    }
}
