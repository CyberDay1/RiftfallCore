package com.cyberday1.riftfall.skill;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.entity.SummonedEntity;
import net.spell_engine.internals.SpellExecution;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A caster's own Spell Engine summons: {@code riftfall:command_summons} sets them on the enemy struck,
 * {@code riftfall:recall_summons} brings them to the caster, and no player keeps more than {@link #MAX_PER_OWNER}
 * alive (the oldest goes when another joins).
 */
public final class SummonControl {

    public static final ResourceLocation COMMAND = ResourceLocation.fromNamespaceAndPath("riftfall", "command_summons");
    public static final ResourceLocation RECALL = ResourceLocation.fromNamespaceAndPath("riftfall", "recall_summons");
    /** Live summons a player may own at once. */
    public static final int MAX_PER_OWNER = 8;
    /** How far from the caster a command or recall reaches its summons (they follow within 32). */
    static final double REACH = 48.0;
    /** How far from a joining summon its owner's others are counted. */
    static final double CAP_REACH = 96.0;

    private static final AtomicLong COMMANDED = new AtomicLong();
    private static final AtomicLong RECALLED = new AtomicLong();
    private static final AtomicLong CAPPED = new AtomicLong();

    private SummonControl() {
    }

    static void register() {
        SpellHandlers.registerCustomImpact(COMMAND, SummonControl::command);
        SpellHandlers.registerCustomImpact(RECALL, SummonControl::recall);
        NeoForge.EVENT_BUS.addListener(SummonControl::onJoin);
    }

    /** How many of an owner's live summons must go before one more joins under the cap. */
    public static int excess(int live, int cap) {
        return Math.max(0, live - cap + 1);
    }

    private static SpellHandlers.ImpactResult command(Holder<Spell> spell, SpellPower.Result power, LivingEntity caster,
                                                      @Nullable Entity target, SpellExecution.ImpactContext context) {
        if (!(target instanceof LivingEntity victim) || !victim.isAlive()) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        int set = 0;
        for (SummonedEntity summon : owned(caster, REACH)) {
            if (summon.canAttackTarget(victim, caster)) {
                summon.setTarget(victim);
                set++;
            }
        }
        COMMANDED.addAndGet(set);
        return new SpellHandlers.ImpactResult(true, false);
    }

    private static SpellHandlers.ImpactResult recall(Holder<Spell> spell, SpellPower.Result power, LivingEntity caster,
                                                     @Nullable Entity target, SpellExecution.ImpactContext context) {
        List<SummonedEntity> summons = owned(caster, REACH);
        for (int i = 0; i < summons.size(); i++) {
            // A ring around the caster, so they do not stack in one block.
            double angle = 2.0 * Math.PI * i / summons.size();
            SummonedEntity summon = summons.get(i);
            summon.setTarget(null);
            summon.getNavigation().stop();
            summon.teleportTo(caster.getX() + 1.5 * Math.cos(angle), caster.getY(), caster.getZ() + 1.5 * Math.sin(angle));
        }
        RECALLED.addAndGet(summons.size());
        return new SpellHandlers.ImpactResult(true, false);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof SummonedEntity joining)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        UUID owner = joining.getOwnerUUID();
        if (owner == null || level.getPlayerByUUID(owner) == null) {
            return;
        }
        List<SummonedEntity> live = level.getEntitiesOfClass(SummonedEntity.class,
                joining.getBoundingBox().inflate(CAP_REACH),
                e -> e != joining && e.isAlive() && owner.equals(e.getOwnerUUID()));
        int drop = excess(live.size(), MAX_PER_OWNER);
        if (drop == 0) {
            return;
        }
        live.sort(Comparator.comparingInt((SummonedEntity e) -> e.tickCount).reversed());
        for (int i = 0; i < drop; i++) {
            live.get(i).discard();
        }
        CAPPED.addAndGet(drop);
    }

    /** The caster's live summons within {@code reach}. */
    static List<SummonedEntity> owned(LivingEntity caster, double reach) {
        UUID id = caster.getUUID();
        return caster.level().getEntitiesOfClass(SummonedEntity.class, caster.getBoundingBox().inflate(reach),
                e -> e.isAlive() && id.equals(e.getOwnerUUID()));
    }

    /** Summons set on a target, recalled, and discarded by the cap. Probe reads. */
    public static long commanded() {
        return COMMANDED.get();
    }

    public static long recalled() {
        return RECALLED.get();
    }

    public static long capped() {
        return CAPPED.get();
    }
}
