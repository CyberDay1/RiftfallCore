package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.gear.RiftSilence;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Space Dissector's portals are entities that pull living things through on their own tick, and a pair only
 * ever links inside one level. One in a rift, however it got there, is removed before it acts.
 */
@Pseudo
@Mixin(targets = "it.hurts.sskirillss.relics.entities.DissectionEntity", remap = false)
public abstract class MixinRelicsRiftPortal {

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void riftfall$riftPortal(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide() && RiftSilence.inRift(self.level())) {
            RiftSilence.removedEntity();
            self.discard();
            ci.cancel();
        }
    }
}
