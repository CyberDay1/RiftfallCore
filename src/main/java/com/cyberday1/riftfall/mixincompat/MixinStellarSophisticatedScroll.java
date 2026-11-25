package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarSophisticated;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tall storages scroll: recolour NeoForge's default bar. Slot wells already come from GuiHelper. */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.controls.InventoryScrollPanel", remap = false)
public abstract class MixinStellarSophisticatedScroll {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void riftfall$colors(CallbackInfo ci) {
        var bar = (MixinStellarScrollPanelColors) this;
        bar.riftfall$track(StellarSophisticated.BAR_TRACK);
        bar.riftfall$shade(StellarSophisticated.BAR_SHADE);
        bar.riftfall$face(StellarSophisticated.BAR_FACE);
    }
}
