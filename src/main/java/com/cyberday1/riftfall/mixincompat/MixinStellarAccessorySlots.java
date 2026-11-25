package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarInventory;
import io.wispforest.accessories.client.gui.AccessoriesExperimentalScreen;
import io.wispforest.accessories.client.gui.components.ComponentUtils;
import io.wispforest.owo.ui.core.Surface;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.wispforest.accessories.client.gui.components.ComponentUtils", remap = false)
public abstract class MixinStellarAccessorySlots {
    @Shadow @Final @Mutable public static Surface BACKGROUND_SLOT_RENDERING_SURFACE;
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void riftfall$slots(CallbackInfo ci) {
        BACKGROUND_SLOT_RENDERING_SURFACE = (g, component) -> ComponentUtils.recursiveSearch(component,
                AccessoriesExperimentalScreen.ExtendedSlotComponent.class, slot -> StellarInventory.slot(g, slot.x(), slot.y()));
    }
}
