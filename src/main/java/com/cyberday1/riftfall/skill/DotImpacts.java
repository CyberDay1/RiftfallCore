package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
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
 * The {@code riftfall:bleed}, {@code riftfall:burn} and {@code riftfall:venom} impacts (H6). Each lands its type's DoT
 * on the target struck, sized from the casting node's {@link SkillNode.DotSpec} and the cast's own power, so the rank's
 * damage lever is in it; the rank's DoT seconds ride the cast modifier's effect duration.
 */
public final class DotImpacts {

    private DotImpacts() {
    }

    static void register() {
        for (DotType type : DotType.values()) {
            SpellHandlers.registerCustomImpact(RiftfallCore.id(type.key()),
                    (spell, power, caster, target, context) -> impact(type, spell, power, caster, target, context));
        }
    }

    /** The node whose ability is {@code spell}: a generated ability's path is its node's id. */
    static @Nullable SkillNode.DotSpec dotOf(Holder<Spell> spell, DotType type) {
        ResourceLocation id = spell.unwrapKey().map(k -> k.location()).orElse(null);
        if (id == null) {
            return null;
        }
        // A stored value's release spell lands its node's DoT: riftfall:stored/<node>.
        String path = id.getPath();
        boolean released = path.startsWith(Stored.DIR);
        if (released) {
            path = path.substring(Stored.DIR.length());
            if (path.endsWith(Stored.PULSE_SUFFIX)) {
                path = path.substring(0, path.length() - Stored.PULSE_SUFFIX.length());
            }
        }
        SkillDef def = SkillDefs.get(path);
        if (def == null || (!released && !def.cast().ability().equals(id)) || def.cast().dot() == null
                || def.cast().dot().type() != type) {
            return null;
        }
        return def.cast().dot();
    }

    private static SpellHandlers.ImpactResult impact(DotType type, Holder<Spell> spell, SpellPower.Result power,
                                                     LivingEntity caster, @Nullable Entity target,
                                                     SpellExecution.ImpactContext context) {
        if (caster.level().isClientSide || !(target instanceof LivingEntity living) || living == caster) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        SkillNode.DotSpec dot = dotOf(spell, type);
        if (dot == null) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        Spell.Modifier modifier = context.chargeModifier();
        double extra = modifier == null ? 0.0 : modifier.effect_duration_add;
        int ticks = (int) Math.round((dot.seconds() + extra) * 20.0);
        double perTick = Dots.perTick(power.nonCriticalValue(), dot.coefficient(), dot.seconds(),
                Dots.bonus(caster, type));
        Dots.apply(living, caster, type, perTick, ticks);
        return new SpellHandlers.ImpactResult(true, false);
    }
}
