package com.cyberday1.riftfall.boss;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The one goal that decides what a rift boss is looking at.
 *
 * <p>
 * A {@code Mob} keeps two {@code public final} goal lists, and the split is how a boss is borrowed
 * from a mod we know nothing about: {@code targetSelector} chooses a target, {@code goalSelector} acts
 * on whatever {@code getTarget()} returns. {@link RiftBosses#take} wipes the first and puts this in at
 * priority 0; the second is untouched, so the boss keeps its whole moveset and loses one opinion.
 *
 * <p>
 * The decision is re-derived every tick and written back whenever the mob is not already holding it,
 * which is what beats a boss that re-picks from its own {@code tick()} or attack code. Who it is comes
 * from {@link TargetRotation}; the threat table only says whether the fight has started.
 */
public final class ThreatGoal extends Goal {

    /**
     * How long a boss keeps chasing a player it can no longer see, in ticks. Five seconds, long enough
     * that ordinary movement never resets it; at zero a player sheds a boss by stepping behind a wall
     * for one tick, which every path a mob walks does anyway.
     */
    static final int SIGHT_MEMORY_TICKS = 100;

    /**
     * How much of the boss's own {@code FOLLOW_RANGE} counts as "in the fight". A multiplier rather
     * than a fixed distance, so a boss designed to hold a large arena keeps one.
     */
    static final double RANGE_FACTOR = 1.0;

    private final Mob mob;
    private final ThreatTable threat;
    private final TargetRotation rotation;

    /**
     * Who this goal decided the boss is fighting, which is not {@code mob.getTarget()}: reading the
     * mob back would let a boss that overwrote its own target pick its own holder.
     */
    @Nullable
    private UUID holder;

    /** The last tick on which each candidate was actually visible; see {@link #SIGHT_MEMORY_TICKS}. */
    private final Map<UUID, Integer> lastSeen = new HashMap<>();

    ThreatGoal(Mob mob, ThreatTable threat, TargetRotation rotation) {
        this.mob = mob;
        this.threat = threat;
        this.rotation = rotation;
        // TARGET only. Claiming MOVE or LOOK as well would lock those flags against the boss's own
        // goals, which are the ones that have to do the fighting.
        setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    /**
     * The player this goal has decided the boss is fighting, and so the only target it may have. Read
     * by {@link ThreatEvents}, which refuses every other. The goal decides and the gate enforces:
     * recomputing at the gate would give two authorities that must agree about eligibility, sight
     * memory and the rotation's draw, and their first disagreement is a boss flicking between two
     * targets.
     */
    @Nullable
    ServerPlayer held() {
        return holder == null ? null : player(holder);
    }

    @Override
    public boolean canUse() {
        return !candidates().isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    /**
     * ⚠ Load-bearing, not a default. {@code Mob#serverAiStep} calls {@code tickRunningGoals(false)} on
     * alternate ticks, so without this the re-assertion runs on half of them and a boss that re-picks
     * its own target wins every other tick.
     */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        assertTarget();
    }

    @Override
    public void tick() {
        assertTarget();
    }

    /**
     * Lets go of the target, but only if it is still the one we set. Runs on a wipe or with everybody
     * out of range; clearing unconditionally would stamp null over a target the boss's own code set.
     */
    @Override
    public void stop() {
        if (holder != null && mob.getTarget() instanceof ServerPlayer player
                && player.getUUID().equals(holder)) {
            mob.setTarget(null);
        }
        holder = null;
        rotation.release();
        lastSeen.clear();
    }

    /**
     * Everybody the boss could fight now, on the table or not: a player who has done nothing is
     * still a body in the room. Nobody at all until someone has pulled, so a boss waits to be pulled.
     */
    private List<UUID> candidates() {
        List<UUID> out = new ArrayList<>();
        if (threat.isEmpty() || !(mob.level() instanceof ServerLevel level)) {
            return out;
        }
        for (ServerPlayer player : level.players()) {
            if (eligible(player.getUUID())) {
                out.add(player.getUUID());
            }
        }
        return out;
    }

    private void assertTarget() {
        UUID next = rotation.next(mob.tickCount, candidates(), bound -> mob.getRandom().nextInt(bound));
        if (next == null) {
            holder = null;
            return;
        }
        holder = next;
        ServerPlayer player = player(next);
        // The decision above is unconditional; the write is guarded. setTarget posts
        // LivingChangeTargetEvent on every call, and firing that 20/s per boss to set a value that is
        // already set is noise through a hook other mods use. The comparison goes false the instant a
        // boss's own code moves the target, which is the case the re-assertion exists for.
        if (player != null && mob.getTarget() != player) {
            mob.setTarget(player);
        }
    }

    /**
     * Whether this player could be attacked at all right now. Each clause omitted is a way for a boss
     * to chase someone it cannot fight. The lookup covers three at once: gone home, sent home, logged
     * out are all not-found. Spectators and creative players are never the answer.
     */
    private boolean eligible(UUID id) {
        ServerPlayer player = player(id);
        if (player == null || !player.isAlive() || player.isSpectator() || player.isCreative()) {
            return false;
        }
        double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE) * RANGE_FACTOR;
        if (mob.distanceToSqr(player) > range * range) {
            return false;
        }
        if (mob.getSensing().hasLineOfSight(player)) {
            lastSeen.put(id, mob.tickCount);
            return true;
        }
        Integer seen = lastSeen.get(id);
        return seen != null && mob.tickCount - seen <= SIGHT_MEMORY_TICKS;
    }

    /**
     * The player with this id, if they are in the boss's level and it is a server level.
     * {@code ServerLevel#getEntity(UUID)} is per level, which is the point: it answers null for a
     * player standing in the overworld holding threat from a rift they walked out of.
     */
    @Nullable
    private ServerPlayer player(UUID id) {
        return mob.level() instanceof ServerLevel level && level.getEntity(id) instanceof ServerPlayer player
                ? player
                : null;
    }
}
