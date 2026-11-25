package com.cyberday1.riftfall.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.spell.tooltip.TooltipTokens;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads one spell out of Spell Engine's synced registry. The only class on the client that names a
 * Spell Engine type, so {@link AbilityLookup} can refuse to load it when the engine is missing.
 */
final class SpellEngineAbilities {

    private SpellEngineAbilities() {
    }

    static AbilityFacts read(Level level, String ability) {
        ResourceLocation id = ResourceLocation.tryParse(ability);
        if (id == null) {
            return AbilityFacts.NONE;
        }
        Spell spell = SpellRegistry.from(level).get(id);
        if (spell == null) {
            return AbilityFacts.NONE;
        }
        double damage = 0.0;
        double heal = 0.0;
        List<AbilityFacts.Effect> effects = new ArrayList<>();
        if (spell.impacts != null) {
            for (Spell.Impact impact : spell.impacts) {
                Spell.Impact.Action action = impact == null ? null : impact.action;
                if (action == null || action.type == null) {
                    continue;
                }
                switch (action.type) {
                    case DAMAGE -> damage = action.damage == null ? damage
                            : Math.max(damage, action.damage.spell_power_coefficient);
                    case HEAL -> heal = action.heal == null ? heal
                            : Math.max(heal, action.heal.spell_power_coefficient);
                    case STATUS_EFFECT -> {
                        if (action.status_effect != null && action.status_effect.effect_id != null) {
                            effects.add(new AbilityFacts.Effect(action.status_effect.effect_id,
                                    action.status_effect.duration, action.status_effect.amplifier));
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        Spell.Target target = spell.target;
        String type = target == null || target.type == null ? "" : target.type.name();
        boolean self = target != null && target.area != null && target.area.include_caster;
        String school = spell.school == null || spell.school.id == null ? "" : spell.school.id.toString();
        return new AbilityFacts(true, school, damage, heal, spell.range, type, self, effects);
    }

    /** Spell Engine's tooltip has no ranked estimate: stage tokens read as their base coefficient, shields as what they grow with. */
    static void registerStageTokens(Level level) {
        for (ResourceLocation id : SpellRegistry.from(level).keySet()) {
            if (id.getNamespace().equals(com.cyberday1.riftfall.RiftfallCore.MODID)) {
                TooltipTokens.registerCustom(id, args -> SkillDescription.fallbacks(args.description(),
                        SkillsScreen::stageCoefficient, SkillsScreen::shieldAmount));
            }
        }
    }
}
