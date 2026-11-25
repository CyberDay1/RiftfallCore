package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.skill.Reactions;
import dev.shadowsoffire.apothic_attributes.impl.AttributeEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Apothic's dodge ({@code apothic_attributes:dodge_chance}, the curio route) cancels a melee hit or a
 * projectile's impact in its two {@code AttributeEvents#dodge} handlers. Right after each cancel, Riftfall reports the
 * dodge, as it does for Iron's.
 */
@Mixin(value = AttributeEvents.class, remap = false)
public abstract class MixinApothicDodge {

    @Inject(method = "dodge(Lnet/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent;setCanceled(Z)V",
                    shift = At.Shift.AFTER))
    private void riftfall$dodgedHit(LivingIncomingDamageEvent event, CallbackInfo ci) {
        Reactions.dodged(event.getEntity(), event.getSource(), event.getAmount());
    }

    @Inject(method = "dodge(Lnet/neoforged/neoforge/event/entity/ProjectileImpactEvent;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/event/entity/ProjectileImpactEvent;setCanceled(Z)V",
                    shift = At.Shift.AFTER))
    private void riftfall$dodgedProjectile(ProjectileImpactEvent event, CallbackInfo ci) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity dodger) {
            Reactions.dodgedProjectile(dodger, event.getProjectile());
        }
    }
}
