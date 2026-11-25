package com.cyberday1.riftfall.skill;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.impact.SpellImpacts;
import net.spell_engine.internals.target.EntityRelations;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.utils.WorldScheduler;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The {@code riftfall:rampage} impact: the Shifter's "Primal Rampage" (chain leaps between enemies, aim each leap).
 * The cast strikes its aimed enemy; this then leaps on to {@link #HOPS} - 1 more, one every {@link #HOP_TICKS}, each
 * time to the unstruck enemy within {@link #RADIUS} nearest the caster's crosshair, and runs the ability's own impacts
 * on it (the leap behind it, the hit, any DoT). So the player steers each leap by looking. A hop's own pass of this
 * impact does nothing, and a run ends early when no enemy is left in reach or the caster is gone.
 */
public final class Rampage {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("riftfall", "rampage");
    /** Enemies a rampage strikes, the aimed one included. */
    public static final int HOPS = 6;
    /** Ticks between leaps: time to look at the next enemy. */
    public static final int HOP_TICKS = 10;
    /** How far from the caster the next enemy may stand. */
    public static final double RADIUS = 6.0;

    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static long hops;

    private Rampage() {
    }

    private static final class Run {
        final Set<UUID> struck = new HashSet<>();
        int left = HOPS - 1;
        boolean hopping;
    }

    static void register() {
        SpellHandlers.registerCustomImpact(ID, Rampage::impact);
    }

    private static SpellHandlers.ImpactResult impact(Holder<Spell> spell, SpellPower.Result power, LivingEntity caster,
                                                     @Nullable Entity target, SpellExecution.ImpactContext context) {
        if (!(caster.level() instanceof ServerLevel level) || !(target instanceof LivingEntity living)
                || living == caster) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        Run running = RUNS.get(caster.getUUID());
        if (running != null && running.hopping) {
            return new SpellHandlers.ImpactResult(true, false);
        }
        Run run = new Run();
        run.struck.add(living.getUUID());
        RUNS.put(caster.getUUID(), run);
        schedule(level, caster, spell, context, run);
        return new SpellHandlers.ImpactResult(true, false);
    }

    private static void schedule(ServerLevel level, LivingEntity caster, Holder<Spell> spell,
                                 SpellExecution.ImpactContext context, Run run) {
        ((WorldScheduler) level).schedule(HOP_TICKS, () -> hop(level, caster, spell, context, run));
    }

    private static void hop(ServerLevel level, LivingEntity caster, Holder<Spell> spell,
                            SpellExecution.ImpactContext context, Run run) {
        if (RUNS.get(caster.getUUID()) != run) {
            return;
        }
        LivingEntity next = caster.isAlive() && caster.level() == level ? next(level, caster, run) : null;
        if (next == null) {
            RUNS.remove(caster.getUUID());
            return;
        }
        run.struck.add(next.getUUID());
        run.hopping = true;
        try {
            Vec3 at = next.position().add(0.0, next.getBbHeight() / 2.0, 0.0);
            SpellImpacts.performImpacts(level, caster, next, next, spell, spell.value().impacts,
                    context.position(at), false, null);
            hops++;
        } finally {
            run.hopping = false;
        }
        if (--run.left > 0) {
            schedule(level, caster, spell, context, run);
        } else {
            RUNS.remove(caster.getUUID());
        }
    }

    /** The unstruck enemy in reach nearest the crosshair, or null. */
    private static @Nullable LivingEntity next(ServerLevel level, LivingEntity caster, Run run) {
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(RADIUS),
                e -> e != caster && e.isAlive() && !run.struck.contains(e.getUUID()) && e.isAttackable()
                        && e.distanceToSqr(caster) <= RADIUS * RADIUS
                        && EntityRelations.actionAllowed(SpellTarget.FocusMode.AREA, SpellTarget.Intent.HARMFUL,
                        caster, e));
        int best = aimed(caster.getEyePosition(), caster.getLookAngle(),
                near.stream().map(e -> e.position().add(0.0, e.getBbHeight() / 2.0, 0.0)).toList());
        return best < 0 ? null : near.get(best);
    }

    /** The index of the point at the smallest angle from {@code look} seen from {@code eye}; -1 for none. */
    static int aimed(Vec3 eye, Vec3 look, List<Vec3> points) {
        int best = -1;
        double bestCos = -2.0;
        Vec3 ahead = look.normalize();
        for (int i = 0; i < points.size(); i++) {
            Vec3 to = points.get(i).subtract(eye);
            double length = to.length();
            double cos = length < 1.0E-6 ? 1.0 : to.dot(ahead) / length;
            if (cos > bestCos) {
                bestCos = cos;
                best = i;
            }
        }
        return best;
    }

    /**
     * After a rampage's strike number {@code struck} (1 is the aimed enemy): the next leap waits {@link #HOP_TICKS},
     * time to look at the next enemy (ruled 2026-11-16), until {@link #HOPS} are struck. A strike after the first is a hop.
     */
    static void afterStrike(int struck, java.util.function.BiConsumer<Integer, Runnable> schedule, Runnable leap,
                            Runnable end) {
        if (struck > 1) {
            hops++;
        }
        if (struck < HOPS) {
            schedule.accept(HOP_TICKS, leap);
        } else {
            end.run();
        }
    }

    /** Leaps taken after the first strike, since boot. Probe reads. */
    public static long hops() {
        return hops;
    }
}
