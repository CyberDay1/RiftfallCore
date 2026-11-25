package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.skill.CasterHeals;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.impact.SpellImpacts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;

/**
 * Per target, {@code SpellImpacts#performImpacts} runs a caster heal even when the hit before it was
 * refused or never allowed, and {@code performImpact} reports a hit as done whatever {@code hurt}
 * answered. This records the answer and drops a Riftfall caster heal whose hit did not land.
 */
@Mixin(value = SpellImpacts.class, remap = false)
public abstract class MixinSpellImpactsDrain {

    @WrapOperation(method = "performImpacts(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Ljava/util/List;Lnet/spell_engine/internals/SpellExecution$ImpactContext;ZLnet/spell_engine/api/spell/Spell$Impact$Action$Type;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/spell_engine/internals/impact/SpellImpacts;performImpact(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/spell_engine/api/spell/Spell$Impact;Lnet/spell_engine/internals/SpellExecution$ImpactContext;Ljava/util/Collection;)Z"))
    private static boolean riftfall$healNeedsTheHit(Level world, LivingEntity caster, Entity target,
                                                   Holder<Spell> spell, Spell.Impact impact,
                                                   SpellExecution.ImpactContext context,
                                                   Collection<ServerPlayer> trackers, Operation<Boolean> original,
                                                   @Share("hitMissed") LocalBooleanRef hitMissed) {
        if (!com.cyberday1.riftfall.skill.RiftSpellEngine.allowsFreeImpact(caster, spell, impact)) return false;
        Spell.Impact.Action.Type type = impact.action.type;
        if (type == Spell.Impact.Action.Type.HEAL && CasterHeals.withholds(
                spell.unwrapKey().map(ResourceKey::location).orElse(null),
                impact.action.apply_to_caster, hitMissed.get())) {
            CasterHeals.withheld();
            return false;
        }
        if (type != Spell.Impact.Action.Type.DAMAGE) {
            return original.call(world, caster, target, spell, impact, context, trackers);
        }
        CasterHeals.hitStarting();
        boolean done = original.call(world, caster, target, spell, impact, context, trackers);
        if (!CasterHeals.hitLanded()) {
            hitMissed.set(true);
        }
        return done;
    }

    @WrapOperation(method = "performImpact", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private static boolean riftfall$recordHit(Entity target, DamageSource source, float amount,
                                              Operation<Boolean> original) {
        boolean landed = com.cyberday1.riftfall.skill.SkillDamage.dealing(() -> original.call(target, source, amount));
        CasterHeals.hurtAnswered(landed);
        return landed;
    }
}
