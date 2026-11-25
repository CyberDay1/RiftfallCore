package com.cyberday1.riftfall.home.npc.depth;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.lore.ResidentStory;
import com.cyberday1.riftfall.story.StoryLines;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * The greeting line on a landing. Priority: a resident's first words to a player they have never spoken to (Penk's,
 * story S2), a recognition line, rank-up, request done, boss, tier, deaths, absence, then a plain greeting. Each event fires once per player per resident; the line then stays until the player's next
 * run. Keys are {@code resident.riftfall.<role>.<trigger>.<n>}, falling back to {@code resident.riftfall.any...};
 * once the resident knows, the {@code known} variants come first (see {@link #keys}).
 */
public final class ResidentDialogue {
    public enum Trigger {
        GREETING, RANK_UP, REQUEST, BOSS, TIER, DEATHS, ABSENCE,
        /** A recognition line from ResidentStory; its variant is the line's index. */
        NOTE,
        /** A resident's first words to this player (StoryLines#meeting); only residents who have them. */
        MEET;
        public String id() { return name().toLowerCase(Locale.ROOT); }
        public static Trigger byId(String id) {
            for (var t : values()) if (t.id().equals(id)) return t;
            return null;
        }
    }

    public static final int VARIANTS = 2, ABSENCE_RUNS = 10, DEATH_STREAK = 2;
    private static final String PREFIX = "resident." + RiftfallCore.MODID + ".";

    private ResidentDialogue() {}

    public static String key(String scope, Trigger trigger, int variant) {
        return PREFIX + scope + "." + trigger.id() + "." + variant;
    }
    public static String key(NpcRole role, Trigger trigger, int variant) { return key(role.id(), trigger, variant); }
    public static String fallback(Trigger trigger, int variant) { return key("any", trigger, variant); }

    /**
     * Lookup order for a line: once the resident knows, their own {@code known} line, their own line, the shared
     * {@code known} line, then the shared line. A recognition line has only its own key.
     */
    public static List<String> keys(NpcRole role, Trigger trigger, int variant, boolean known) {
        if (trigger == Trigger.NOTE) return List.of(ResidentStory.noteKey(role, variant));
        if (trigger == Trigger.MEET) return StoryLines.meeting(role).map(List::of).orElse(List.of());
        var keys = new ArrayList<String>(4);
        if (known) keys.add(ResidentStory.knownKey(role.id(), trigger.id(), variant));
        keys.add(key(role, trigger, variant));
        if (known) keys.add(ResidentStory.knownKey("any", trigger.id(), variant));
        keys.add(fallback(trigger, variant));
        return keys;
    }

    /** Stable for one player, resident, trigger and run, so reopening shows the same variant. */
    static int variant(UUID player, NpcRole role, Trigger trigger, int run) {
        return Math.floorMod(Objects.hash(player, role.ordinal(), trigger.ordinal(), run), VARIANTS);
    }

    /**
     * Picks this visit's line and records what it consumed. {@code bossId} turns a boss into the number the client
     * resolves back to its name.
     */
    public static ResidentProgress.Line pick(UUID id, ResidentProgress.Player player, NpcRole role, int rank,
                                             int completed, ToIntFunction<ResourceLocation> bossId) {
        return pick(id, player, role, rank, completed, bossId, 0);
    }

    /** As above; {@code homeRank} (tiles restored) opens rank-gated recognition lines. */
    public static ResidentProgress.Line pick(UUID id, ResidentProgress.Player player, NpcRole role, int rank,
                                             int completed, ToIntFunction<ResourceLocation> bossId, int homeRank) {
        var seen = player.seen(role);
        int run = player.runsSettled();
        if (seen.visit < 0 && StoryLines.meeting(role).isPresent()) {
            // The first visit ever: the introduction; everything else waits for the next visit.
            seen.visit = run;
            return seen.line = new ResidentProgress.Line(Trigger.MEET, 0, 0, run);
        }
        int note = ResidentStory.next(role, seen.noted, player.notes(), homeRank);
        if (note >= 0) {
            // Other pending events wait for the next visit.
            seen.noted |= 1 << note;
            seen.visit = run;
            return seen.line = new ResidentProgress.Line(Trigger.NOTE, note, 0, run);
        }
        Trigger fired = null;
        int arg = 0;
        var last = player.last().orElse(null);
        if (rank > seen.rank) {
            fired = Trigger.RANK_UP; arg = rank;
            seen.rank = rank;
        } else if (completed > seen.completed) {
            fired = Trigger.REQUEST;
            seen.completed = completed;
        } else if (last != null && last.id() > seen.report) {
            if (last.boss().isPresent()) { fired = Trigger.BOSS; arg = bossId.applyAsInt(last.boss().get()); }
            else if (last.cleared() && last.newBest()) { fired = Trigger.TIER; arg = last.tier(); }
            else if (player.deathStreak() >= DEATH_STREAK) fired = Trigger.DEATHS;
            seen.report = last.id();
        }
        if (fired == null && seen.visit >= 0 && run - seen.visit >= ABSENCE_RUNS) fired = Trigger.ABSENCE;
        seen.visit = run;
        if (fired != null) {
            seen.line = new ResidentProgress.Line(fired, variant(id, role, fired, run), arg, run);
        } else if (seen.line == null || seen.line.at() != run) {
            seen.line = new ResidentProgress.Line(Trigger.GREETING, variant(id, role, Trigger.GREETING, run), 0, run);
        }
        return seen.line;
    }
}
