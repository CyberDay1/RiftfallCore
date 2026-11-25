package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.skill.RiftSpellGate;
import com.cyberday1.riftfall.skill.RiftSpellEngine;
import com.cyberday1.riftfall.skill.SkillCasts;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.delivery.SpellDelivery;
import net.spell_engine.internals.target.SpellTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/**
 * A player's cast through the engine's own path (an item, a trap) pays Riftfall's mana and cooldown
 * before {@code SpellDelivery#resolveAndDeliver} runs, and starts none of the engine's cooldown. A
 * passive trigger instead needs a Riftfall grant and its internal cooldown, and rides the node's power.
 * A cast that cannot pay delivers nothing, so the engine's completion never runs.
 */
@Mixin(value = SpellExecution.class, remap = false)
public abstract class MixinSpellExecution {

    @WrapOperation(method = "performSpell", at = @At(value = "INVOKE",
            target = "Lnet/spell_engine/internals/delivery/SpellDelivery;resolveAndDeliver(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/Holder;Lnet/spell_engine/internals/target/SpellTarget$SearchResult;Lnet/spell_engine/internals/SpellExecution$ImpactContext;Ljava/util/function/Consumer;)Z"))
    private static boolean riftfall$deliverPaid(Level world, LivingEntity caster, Holder<Spell> spell,
                                                SpellTarget.SearchResult targets, SpellExecution.ImpactContext context,
                                                Consumer<SpellExecution.DeliveryCompletion> completion,
                                                Operation<Boolean> original) {
        // Avoid the engine's missing-target HUD packet as well as the slot key's debug line.
        if (RiftSpellEngine.missingTarget(spell.value(), targets)) return false;
        if (RiftSpellEngine.movementBusy(caster, spell.value())) return false;
        return RiftSpellGate.deliverPaid(caster, spell, context, targets.entities(),
                priced -> SkillCasts.delivered(completed -> {
                    if (spell.unwrapKey().orElseThrow().location().getNamespace().equals("riftfall")
                            && RiftSpellEngine.isFreeAssault(spell.value()) && targets.entities().isEmpty()) {
                        RiftSpellEngine.freeAssault(caster, spell, targets, priced);
                        completed.accept(true);
                        completion.accept(new SpellExecution.DeliveryCompletion(true));
                        return true;
                    }
                    if (spell.unwrapKey().orElseThrow().location().getNamespace().equals("riftfall")
                            && RiftSpellEngine.isEmptySwing(spell.value(), targets)) {
                        completed.accept(true);
                        completion.accept(new SpellExecution.DeliveryCompletion(true));
                        return true;
                    }
                    return original.call(world, caster, spell, targets, priced,
                        (Consumer<SpellExecution.DeliveryCompletion>) done -> {
                            completed.accept(done.success());
                            completion.accept(done);
                        });
                }));
    }

    @WrapOperation(method = "performSpell", at = @At(value = "INVOKE",
            target = "Lnet/spell_engine/internals/cost/SpellCooldowns;imposeAttemptCooldown(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/Holder;)V"))
    private static void riftfall$noEngineAttemptCooldown(Player player, Holder<Spell> spell, Operation<Void> original) {
        if (RiftSpellGate.enginePrices(player)) {
            original.call(player, spell);
        }
    }
}
