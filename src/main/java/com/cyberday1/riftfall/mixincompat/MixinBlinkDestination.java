package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.skill.BlinkTravel;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.impact.SpellImpacts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve engine costs, modifiers and teleport feedback; replace only native forward destination selection. */
@Mixin(value = SpellImpacts.class, remap = false)
public abstract class MixinBlinkDestination {
    @WrapOperation(method = "performImpact", at = @At(value = "INVOKE",
            target = "Lnet/spell_engine/utils/TargetHelper;findTeleportDestination(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/Vec3;FI)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 riftfall$aim(LivingEntity caster, Vec3 look, float range, int clearance, Operation<Vec3> original,
                                    @Local(argsOnly = true) Holder<Spell> spell,
                                    @Share("riftfallBlink") LocalBooleanRef nativeBlink) {
        nativeBlink.set(spell.unwrapKey().map(key -> key.location().getNamespace().equals("riftfall")).orElse(false));
        return nativeBlink.get() ? BlinkTravel.destination(caster, look, range) : original.call(caster, look, range, clearance);
    }

    @WrapOperation(method = "performImpact", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/spell_engine/utils/TargetHelper;findSolidBlockBelow(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/Level;F)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 riftfall$keepLanding(Entity caster, Vec3 point, Level world, float drop, Operation<Vec3> original,
                                           @Share("riftfallBlink") LocalBooleanRef nativeBlink) {
        return nativeBlink.get() ? point : original.call(caster, point, world, drop);
    }

    @WrapOperation(method = "performImpact", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;randomTeleport(DDDZ)Z"))
    private static boolean riftfall$exactLanding(LivingEntity caster, double x, double y, double z, boolean particles,
                                                Operation<Boolean> original,
                                                @Share("riftfallBlink") LocalBooleanRef nativeBlink) {
        if (!nativeBlink.get()) return original.call(caster, x, y, z, particles);
        // randomTeleport performs a second downward ground search, undoing an aimed ledge or airborne landing.
        caster.teleportTo(x, y, z);
        caster.fallDistance = 0;
        return true;
    }
}
