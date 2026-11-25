package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.shield.RiftShields;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The shield pool pays from the figure {@code LivingDamageEvent.Pre} settled on, after every listener
 * at every priority, so no pack listener can add damage the shield never saw. Both
 * {@code LivingEntity#actuallyHurt} and {@code Player#actuallyHurt} take their number from here.
 */
@Mixin(value = CommonHooks.class, remap = false)
public abstract class MixinCommonHooks {

    /** Inventory collectors (e.g. Create deployers) consume event drops before a LOWEST listener can clear them. */
    @Inject(method = "onLivingDrops", at = @At("HEAD"), cancellable = true)
    private static void riftfall$stopMobDropsBeforeCollectors(LivingEntity entity,
            net.minecraft.world.damagesource.DamageSource source,
            java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops, boolean recentlyHit,
            CallbackInfoReturnable<Boolean> callback) {
        if (com.cyberday1.riftfall.gear.RiftMobDrops.suppress(entity)) {
            drops.clear();
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "onLivingDamagePre", at = @At("RETURN"), cancellable = true)
    private static void riftfall$shieldAfterEveryListener(LivingEntity entity, DamageContainer container,
                                                          CallbackInfoReturnable<Float> cir) {
        float damage = cir.getReturnValueF();
        float left = RiftShields.absorb(entity, damage);
        if (left != damage) {
            container.setNewDamage(left);
            cir.setReturnValue(left);
        }
    }
}
