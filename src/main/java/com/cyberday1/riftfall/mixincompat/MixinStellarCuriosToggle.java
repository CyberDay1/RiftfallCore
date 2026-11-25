package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarCurios;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "top.theillusivec4.curios.client.gui.CuriosButton", remap = false)
public abstract class MixinStellarCuriosToggle extends AbstractWidget {
    protected MixinStellarCuriosToggle(int x, int y, int w, int h, Component title) { super(x, y, w, h, title); }

    // Run after Curios positions the button and checks whether the creative inventory tab is open.
    @Inject(method = "renderWidget", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/ImageButton;renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"), cancellable = true)
    private void riftfall$toggle(GuiGraphics g, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        StellarCurios.inventoryToggle(g, getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused());
        ci.cancel();
    }
}
