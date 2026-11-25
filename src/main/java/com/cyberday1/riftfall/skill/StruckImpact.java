package com.cyberday1.riftfall.skill;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.internals.SpellExecution;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code riftfall:enemy_struck} impact: counts the enemy it lands on into the caster's open {@link EnemyHits}.
 * HARMFUL in the ability file, so the engine runs it on enemies only; generated first in the impact list, so a hit
 * that kills still counts.
 */
public final class StruckImpact {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("riftfall", "enemy_struck");

    private StruckImpact() {
    }

    static void register() {
        SpellHandlers.registerCustomImpact(ID, StruckImpact::impact);
    }

    private static SpellHandlers.ImpactResult impact(Holder<Spell> spell, SpellPower.Result power, LivingEntity caster,
                                                     @Nullable Entity target, SpellExecution.ImpactContext context) {
        if (caster.level().isClientSide || !(target instanceof LivingEntity living) || living == caster) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        EnemyHits.struck(caster.getUUID(), living.getUUID());
        return new SpellHandlers.ImpactResult(true, false);
    }
}
