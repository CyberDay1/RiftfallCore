package com.cyberday1.riftfall.story;

import com.cyberday1.riftfall.rift.RiftRunReport;
import com.cyberday1.riftfall.skill.SkillBook;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * What each server event proves, and what a player is owed from the record. No server, no world: {@link StoryBridge}
 * calls these and grants the result. Every method that records returns only what is new, so a replayed event
 * returns nothing.
 */
public final class StoryRules {
    /** Receipt for the Core's line at a player's first defender's fall (beat 1). */
    public static final String FIRST_ANCHOR = "beat.first_anchor";
    /** Receipt prefix for the guaranteed first shard, one per run: {@code first_shard@<run>}. */
    public static final String FIRST_SHARD = "first_shard@";

    private StoryRules() {}

    /** A1.01: the player touched their own home's Core. True once, so the greeting plays once. */
    public static Set<StoryMilestone> introduced(StoryState state, UUID player) {
        return state.record(player, StoryMilestone.INTRODUCED, "core") ? EnumSet.of(StoryMilestone.INTRODUCED)
                : EnumSet.noneOf(StoryMilestone.class);
    }

    /**
     * Decision 11: a boss owes this player a World Shard their own roll did not give, until one has reached home.
     * A shard won inside a rift is lost on a death, so the guarantee stays open after a failed run and closes on the
     * first clear with a safe return or the first offering. At most one per run, so a second boss in the same rift
     * pays nothing extra. Call only when the roll had no shard; true records the run's receipt.
     */
    public static boolean firstShard(StoryState state, UUID player, String run) {
        if (state.has(player, StoryMilestone.FIRST_CLEAR) || state.has(player, StoryMilestone.OFFERED)) return false;
        return state.firstTime(player, FIRST_SHARD + run);
    }

    /** Beat 1 plays at the first defender's fall a player is present for, once. */
    public static boolean firstAnchor(StoryState state, UUID player) {
        return state.firstTime(player, FIRST_ANCHOR);
    }

    /**
     * Beat 2's last sentence promises "four more" shards to the next tile: true only when that is the count left,
     * so a shared home that is part way to its next tile hears the beat without it.
     */
    public static boolean fourMoreIsTrue(int homeShards, int perTile) {
        return perTile > 0 && homeShards > 0 && perTile - homeShards % perTile == 4;
    }

    /** A player went through a rift's door and onto its roster. Entering proves a key was ready too. */
    public static Set<StoryMilestone> entered(StoryState state, UUID player, String run) {
        Set<StoryMilestone> fresh = EnumSet.noneOf(StoryMilestone.class);
        if (state.record(player, StoryMilestone.RIFT_ENTERED, run)) fresh.add(StoryMilestone.RIFT_ENTERED);
        if (state.record(player, StoryMilestone.KEY_READY, "entered")) fresh.add(StoryMilestone.KEY_READY);
        return fresh;
    }

    /** A key crafted, forged or seated in a breaker. */
    public static Set<StoryMilestone> keyReady(StoryState state, UUID player, String how) {
        return state.record(player, StoryMilestone.KEY_READY, how) ? EnumSet.of(StoryMilestone.KEY_READY)
                : EnumSet.noneOf(StoryMilestone.class);
    }

    /**
     * One entrant's settled run. The clear is the clear bonus's own rule ({@code RiftRuns.earnsTheClear}): the run
     * was cleared and this player got out alive. Entry, defeat and clear all carry the same run id.
     */
    public static Set<StoryMilestone> settled(StoryState state, RiftRunReport report, String run) {
        Set<StoryMilestone> fresh = entered(state, report.player(), run);
        if (!report.bosses().isEmpty() && state.record(report.player(), StoryMilestone.DEFENDER_DEFEATED, run)) {
            fresh.add(StoryMilestone.DEFENDER_DEFEATED);
        }
        if (report.cleared() && report.escaped() && !report.died()
                && state.record(report.player(), StoryMilestone.FIRST_CLEAR, run)) {
            fresh.add(StoryMilestone.FIRST_CLEAR);
        }
        return fresh;
    }

    /**
     * A shard consumed by a Core. The feeder's participation is theirs; the contribution belongs to the home whose
     * Core it is, which for an ally is not their own home.
     */
    public static Set<StoryMilestone> offered(StoryState state, UUID player, UUID coreHome, int tiles) {
        Set<StoryMilestone> fresh = EnumSet.noneOf(StoryMilestone.class);
        if (state.record(player, StoryMilestone.OFFERED, coreHome.toString())) fresh.add(StoryMilestone.OFFERED);
        if (state.recordHome(coreHome, StoryMilestone.HOME_OFFERING)) fresh.add(StoryMilestone.HOME_OFFERING);
        state.seeTiles(coreHome, tiles);
        return fresh;
    }

    public static Set<StoryMilestone> residentMet(StoryState state, UUID player, String role) {
        state.meet(player, role);
        return state.record(player, StoryMilestone.RESIDENT_MET, role) ? EnumSet.of(StoryMilestone.RESIDENT_MET)
                : EnumSet.noneOf(StoryMilestone.class);
    }

    /** The build milestones, read from the skill book rather than stored beside it. */
    public static Set<StoryMilestone> build(SkillBook book) {
        Set<StoryMilestone> held = EnumSet.noneOf(StoryMilestone.class);
        if (book.skillClass() != null) held.add(StoryMilestone.CLASS_CHOSEN);
        if (book.loadout().stream().anyMatch(Objects::nonNull)) held.add(StoryMilestone.SKILL_EQUIPPED);
        if (book.ascended()) held.add(StoryMilestone.ASCENDED);
        return held;
    }

    /**
     * Everything this player should hold: their own milestones, their build, and whatever their current home holds.
     * Catch-up is this union: a late joiner gets the home's facts and none of a teammate's personal ones.
     */
    public static Set<StoryMilestone> owed(StoryState state, UUID player, @Nullable UUID currentHome, SkillBook book) {
        Set<StoryMilestone> owed = EnumSet.noneOf(StoryMilestone.class);
        owed.addAll(state.milestones(player));
        owed.addAll(build(book));
        if (currentHome != null) owed.addAll(state.homeMilestones(currentHome));
        return owed;
    }
}
