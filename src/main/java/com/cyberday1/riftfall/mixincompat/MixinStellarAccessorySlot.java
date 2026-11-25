package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarInventory;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.PositionedRectangle;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.wispforest.accessories.client.gui.AccessoriesExperimentalScreen$ExtendedSlotComponent", remap = false)
public abstract class MixinStellarAccessorySlot {
    @Shadow private boolean isBatched;
    @Inject(method = "draw", at = @At("HEAD"))
    private void riftfall$slot(OwoUIDrawContext g, int mouseX, int mouseY, float partial, float delta, CallbackInfo ci) {
        if (!isBatched) {
            PositionedRectangle slot = (PositionedRectangle) this;
            StellarInventory.slot(g, slot.x(), slot.y());
        }
    }
}
