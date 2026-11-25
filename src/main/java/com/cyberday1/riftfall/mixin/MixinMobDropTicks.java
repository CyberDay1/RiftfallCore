package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.gear.RiftMobDrops;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** Includes subclass tick/death animations, even drops made after their call to super.tick(). */
@Mixin(ServerLevel.class)
public abstract class MixinMobDropTicks {
    @WrapMethod(method = "tickNonPassenger")
    private void riftfall$trackMobTick(Entity entity, Operation<Void> original) {
        if (RiftMobDrops.untracked(entity)) { original.call(entity); return; }
        RiftMobDrops.during(entity, () -> original.call(entity));
    }

    @WrapMethod(method = "tickPassenger")
    private void riftfall$trackPassengerTick(Entity vehicle, Entity passenger, Operation<Void> original) {
        if (RiftMobDrops.untracked(passenger)) { original.call(vehicle, passenger); return; }
        RiftMobDrops.during(passenger, () -> original.call(vehicle, passenger));
    }
}
