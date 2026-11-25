package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.rift.RiftClaimView;
import com.cyberday1.riftfall.rift.RiftRecap;

import java.util.List;

/**
 * Which line the recap screen draws for each fact, and with what arguments. Pure, so the plurals and
 * the reduced-XP wording are plain JUnit against the generated English.
 */
final class RecapText {

    static final String PREFIX = "screen." + RiftfallCore.MODID + ".recap.";

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;
    private static final int TICKS_PER_SECOND = 20;

    /** A line under {@link #PREFIX}. A {@link Rarity} argument stands for a rarity's display name. */
    record Line(String key, List<Object> args) {
        static Line of(String key, Object... args) {
            return new Line(key, List.of(args));
        }
    }

    /** An index into {@code RarityLadder.RARITIES}, resolved to a name where the line is drawn. */
    record Rarity(int index) {
    }

    /** The names of the first {@link #NAMED} stacks that came back, joined where the line is drawn. */
    record Names() {
    }

    /** How many returned stacks the gear line names before "and N more". */
    static final int NAMED = 3;

    private RecapText() {
    }

    static String key(String line) {
        return PREFIX + line;
    }

    /** {@code base_none} for zero, {@code base_one} for one, {@code base} otherwise. */
    static Line counted(String base, int count) {
        if (count <= 0) {
            return Line.of(base + "_none");
        }
        return count == 1 ? Line.of(base + "_one", count) : Line.of(base, CharacterSheet.grouped(count));
    }

    static Line headline(RiftRecap recap) {
        return Line.of("outcome_" + recap.outcome().key());
    }

    /** The short outcome name on a history row. */
    static Line rowOutcome(RiftRecap recap) {
        return Line.of("row_" + recap.outcome().key());
    }

    static Line tierAndTime(RiftRecap recap) {
        return Line.of("tier_time", recap.tier(), duration(recap.elapsedTicks()));
    }

    static Line tier(RiftRecap recap) {
        return Line.of("tier", recap.tier());
    }

    /** {@code 4:07}, or {@code 1:02:03} from an hour up. */
    static String duration(int ticks) {
        int seconds = Math.max(0, ticks) / TICKS_PER_SECOND;
        int hours = seconds / 3600;
        int minutes = seconds % 3600 / 60;
        int rest = seconds % 60;
        return hours > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
                : String.format(java.util.Locale.ROOT, "%d:%02d", minutes, rest);
    }

    /** How long ago a run ended, in the largest whole unit. A clock set backwards reads as just now. */
    static Line age(long endedAt, long now) {
        long passed = now - endedAt;
        if (passed < MINUTE) {
            return Line.of("age_now");
        }
        if (passed < HOUR) {
            return unit("age_minutes", passed / MINUTE);
        }
        if (passed < DAY) {
            return unit("age_hours", passed / HOUR);
        }
        return unit("age_days", passed / DAY);
    }

    private static Line unit(String base, long count) {
        return count == 1 ? Line.of(base + "_one", count) : Line.of(base, CharacterSheet.grouped(count));
    }

    static String xp(long amount) {
        return CharacterSheet.grouped(Math.max(0L, amount));
    }

    /** The label of the kept-XP row: a plain total, or the reduced share of enemy XP. */
    static Line keptLabel(RiftRecap recap) {
        return recap.reduced() ? Line.of("xp_kept", recap.keptPercent()) : Line.of("xp_total");
    }

    /** Why the XP was reduced, or null when it was not. */
    static @org.jetbrains.annotations.Nullable Line reducedNote(RiftRecap recap) {
        if (!recap.reduced()) {
            return null;
        }
        return Line.of(recap.outcome() == RiftRecap.Outcome.LEFT_EARLY ? "note_early" : "note_lost",
                recap.keptPercent());
    }

    /** The clear bonus as a value, or the line saying there was none. */
    static Line clearValue(RiftRecap recap) {
        return recap.xpClear() > 0L ? Line.of("xp_amount", xp(recap.xpClear())) : Line.of("xp_clear_none");
    }

    static Line levels(RiftRecap recap) {
        int gained = recap.levelsGained();
        if (gained <= 0) {
            return Line.of("level_same", recap.levelAfter());
        }
        return Line.of(gained == 1 ? "level_up_one" : "level_up", gained, recap.levelBefore(), recap.levelAfter());
    }

    static Line kills(RiftRecap recap) {
        return counted("kills", recap.kills());
    }

    static Line boss(RiftRecap recap) {
        return Line.of(recap.bossDefeated() ? "boss_defeated" : "boss_not_defeated");
    }

    static Line barrels(RiftRecap recap) {
        return counted("barrels", recap.barrels());
    }

    static Line shards(RiftRecap recap) {
        return counted("shards", recap.shards());
    }

    static Line gear(RiftRecap recap) {
        return counted("gear", recap.gear());
    }

    /** The best rarity among the gear found, or null when there was none to name. */
    static @org.jetbrains.annotations.Nullable Line bestGear(RiftRecap recap) {
        return recap.gear() > 0 && recap.bestRarity() >= 0 ? Line.of("gear_best", new Rarity(recap.bestRarity())) : null;
    }

    /** The count under the history heading. */
    static Line recorded(int runs) {
        return counted("recorded", runs);
    }

    /** The held-gear line: what came back, naming the first few. */
    static Line claimBack(int total) {
        return total > NAMED ? Line.of("claim_back_more", new Names(), CharacterSheet.grouped(total - NAMED))
                : Line.of("claim_back", new Names());
    }

    /** What is still held, or, after a try that found no room, that there is still none. */
    static Line claimLeft(RiftClaimView claim) {
        String base = claim.noRoom() ? "claim_no_room" : "claim_left";
        return claim.left() == 1 ? Line.of(base + "_one", 1) : Line.of(base, CharacterSheet.grouped(claim.left()));
    }
}
