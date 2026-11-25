package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.RiftIrisDimension;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Picks the shader pack's End profile for a rift, and the profile the player chose for a home. Iris asks this
 * every frame and on each level change; neither id is ever in a pack's map, so Iris would otherwise use the
 * Overworld one. {@code require = 0}: if Iris renames this, rifts fall back to the old dark look rather than a crash.
 */
@Mixin(targets = "net.irisshaders.iris.Iris", remap = false)
public abstract class MixinIrisRiftLighting {

    @Inject(method = "getCurrentDimension", at = @At("HEAD"), cancellable = true, require = 0)
    private static void riftfall$riftLighting(CallbackInfoReturnable<Object> cir) {
        Object dimension = RiftIrisDimension.forLevel(Minecraft.getInstance().level);
        if (dimension != null) {
            cir.setReturnValue(dimension);
        }
    }
}
