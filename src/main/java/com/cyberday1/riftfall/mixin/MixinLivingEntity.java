package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.rift.RiftDeaths;
import com.cyberday1.riftfall.gear.RiftMobDrops;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * R-19 in a rift: no death protection, whoever implements it. Wraps the call in {@code hurt} rather
 * than the method, because mods inject at the method's HEAD (Artifacts' chorus totem, Simply
 * Swords' revival weapons) and run before {@code LivingUseTotemEvent} is ever fired.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @WrapMethod(method = "hurt")
    private boolean riftfall$trackDamageDrops(DamageSource source, float amount, Operation<Boolean> original) {
        return RiftMobDrops.during((LivingEntity) (Object) this, () -> original.call(source, amount));
    }

    @WrapMethod(method = "die")
    private void riftfall$trackDeathDrops(DamageSource source, Operation<Void> original) {
        RiftMobDrops.during((LivingEntity) (Object) this, () -> original.call(source));
    }

    @WrapOperation(
            method = "hurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;checkTotemDeathProtection"
                            + "(Lnet/minecraft/world/damagesource/DamageSource;)Z"))
    private boolean riftfall$noDeathProtectionInARift(LivingEntity self, DamageSource source,
                                                      Operation<Boolean> original) {
        return !RiftDeaths.refusesDeathProtection(self) && original.call(self, source);
    }
}
