package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.stat.HealCrit;
import com.cyberday1.riftfall.stat.HealingDone;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.shadowsoffire.apothic_attributes.payload.CritParticlePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.impact.SpellImpacts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A Spell Engine heal takes the one crit roll on its caster's Apothic pair, and a crit shows
 * Apothic's own crit particle. Every heal takes its caster's healing done, and a heal on the caster itself its
 * self-healing too. The engine's effective caster, so a summon's heal rolls its owner's.
 */
@Mixin(value = SpellImpacts.class, remap = false)
public abstract class MixinSpellImpactsHeal {

    @WrapOperation(method = "performImpact",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;heal(F)V"))
    private static void riftfall$healCrit(LivingEntity target, float amount, Operation<Void> original,
                                          @Local(argsOnly = true) Level world,
                                          @Local(argsOnly = true) LivingEntity givenCaster,
                                          @Local(argsOnly = true) SpellExecution.ImpactContext context) {
        LivingEntity effective = context.effectiveCaster(world);
        LivingEntity caster = effective != null ? effective : givenCaster;
        float factor = HealCrit.roll(caster, target.getRandom());
        if (factor > 1.0F && world instanceof ServerLevel level) {
            PacketDistributor.sendToPlayersTrackingChunk(level, target.chunkPosition(),
                    new CritParticlePayload(target.getId()));
        }
        // Healing done, plus Tranquility on a heal the caster lands on itself.
        original.call(target, amount * factor * HealingDone.factor(caster, target));
    }
}
