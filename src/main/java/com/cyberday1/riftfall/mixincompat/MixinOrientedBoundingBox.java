package com.cyberday1.riftfall.mixincompat;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Parallel box axes have no separating cross axis, including vectors containing negative zero. */
@Mixin(targets = "net.spell_engine.internals.delivery.melee.OrientedBoundingBox", remap = false)
public abstract class MixinOrientedBoundingBox {
    @Inject(method = "Separated", at = @At("HEAD"), cancellable = true)
    private static void riftfall$ignoreZeroAxis(Vec3[] a, Vec3[] b, Vec3 axis, CallbackInfoReturnable<Boolean> cir) {
        // Vec3.equals distinguishes -0.0 from +0.0; coordinate comparisons deliberately do not.
        if (axis.x == 0.0 && axis.y == 0.0 && axis.z == 0.0) cir.setReturnValue(false);
    }
}
