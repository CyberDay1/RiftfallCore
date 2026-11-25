package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarSophisticated;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;

/** Storage search and the result-list search: grey fill to an inset. */
@Pseudo
@Mixin(targets = {"net.p3pp3rf1y.sophisticatedcore.client.gui.SearchBox",
        "net.p3pp3rf1y.sophisticatedcore.client.gui.controls.LargeResultSelector$ResultSearchBox"}, remap = false)
public abstract class MixinStellarSophisticatedSearch {
    @ModifyArg(method = "renderBg", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"), index = 4)
    private int riftfall$fill(int color) { return StellarSophisticated.SEARCH; }
}
