package com.cyberday1.riftfall.skill;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.internals.SpellExecution;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The {@code riftfall:reset_cooldowns} impact: an active's "Cooldown Reset". Every running timer of the caster's
 * castable nodes ends now, except an ultimate's and the source ability's. Projectile impacts can arrive after
 * their own cooldown starts, so source exclusion must be explicit.
 */
public final class CooldownReset {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("riftfall", "reset_cooldowns");

    private static final AtomicLong RESETS = new AtomicLong();
    private static final AtomicLong TIMERS = new AtomicLong();

    private CooldownReset() {
    }

    static void register() {
        SpellHandlers.registerCustomImpact(ID, CooldownReset::impact);
    }

    /** Whether a reset may end {@code nodeId}'s timer: a castable that is not an ultimate. */
    public static boolean resets(SkillTable table, String nodeId) {
        return resets(table, nodeId, null);
    }

    public static boolean resets(SkillTable table, String nodeId, @Nullable ResourceLocation sourceAbility) {
        SkillDef def = table.get(nodeId);
        return def != null && def.kind() != SkillBook.Kind.ULTIMATE
                && (sourceAbility == null || !sourceAbility.equals(def.cast().ability()));
    }

    private static SpellHandlers.ImpactResult impact(Holder<Spell> spell, SpellPower.Result power, LivingEntity caster,
                                                     @Nullable Entity target, SpellExecution.ImpactContext context) {
        if (!(caster instanceof ServerPlayer player)) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        SkillTable table = SkillDefs.table();
        ResourceLocation sourceAbility = spell.unwrapKey().map(key -> key.location()).orElse(null);
        List<String> moved = SkillCooldowns.shorten(player.getUUID(), player.level().getGameTime(), Integer.MAX_VALUE,
                node -> resets(table, node, sourceAbility));
        RESETS.incrementAndGet();
        TIMERS.addAndGet(moved.size());
        Set<String> ended = new HashSet<>(moved);
        SkillBook book = SkillService.bookOf(player);
        for (int slot = 0; slot < SkillBook.SLOTS; slot++) {
            if (ended.contains(book.slot(slot))) {
                SkillSync.cooldown(player, slot);
            }
        }
        return new SpellHandlers.ImpactResult(true, false);
    }

    /** Impacts that ran, and timers they ended. Probe reads. */
    public static long resets() {
        return RESETS.get();
    }

    public static long timers() {
        return TIMERS.get();
    }
}
