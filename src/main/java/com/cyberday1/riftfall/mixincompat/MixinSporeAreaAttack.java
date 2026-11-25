package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.mob.SporeCombat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashSet;
import java.util.Set;

@Pseudo
@Mixin(targets = "com.Harbinger.Spore.Sentities.AI.AOEMeleeAttackGoal", remap = false)
public abstract class MixinSporeAreaAttack {
    @Unique private final Set<Entity> riftfall$hit = new HashSet<>();

    @Inject(method = "checkAndPerformAttack", at = @At("HEAD"))
    private void riftfall$startSwing(CallbackInfo ci) { riftfall$hit.clear(); }

    @Redirect(method = "checkAndPerformAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/PathfinderMob;doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean riftfall$oneHitPerTarget(PathfinderMob mob, Entity target) {
        return (!SporeCombat.managed(mob) || riftfall$hit.add(target)) && mob.doHurtTarget(target);
    }
}
