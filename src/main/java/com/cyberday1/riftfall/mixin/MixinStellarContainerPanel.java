package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.client.StellarContainers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A surface for themed screens whose renderBg draws nothing of its own. */
@Mixin(AbstractContainerScreen.class)
public abstract class MixinStellarContainerPanel {
    @Inject(method = "renderBackground", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"))
    private void riftfall$panel(GuiGraphics g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        StellarContainers.background((AbstractContainerScreen<?>) (Object) this, g);
    }
}
