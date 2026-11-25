package com.cyberday1.riftfall.rift;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What one run came to for one entrant, as the recap screen shows it. Server facts, fixed when the
 * entrant's XP is banked; plain numbers so it reads the same from the wire and from the save.
 *
 * @param endedAt     wall-clock milliseconds when the recap was written
 * @param xpEnemies   what the credited kills were worth before any reduction
 * @param xpClear     the clear bonus this entrant banked, or 0
 * @param xpKept      what was actually banked for the run
 * @param keptPercent 100, or the reduced share of enemy XP kept after a loss or an early exit
 * @param bestRarity  an index into {@code RarityLadder.RARITIES}, or {@link RiftHaul#NO_RARITY}
 */
public record RiftRecap(Outcome outcome, int tier, int elapsedTicks, long endedAt, int kills, boolean bossDefeated,
                        long xpEnemies, long xpClear, long xpKept, int keptPercent, int levelBefore, int levelAfter,
                        int barrels, int shards, int gear, int bestRarity) {

    /** How the run ended for this entrant. */
    public enum Outcome {
        CLEARED,
        LEFT_EARLY,
        FELL,
        TIMED_OUT;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        static Outcome of(int ordinal) {
            Outcome[] all = values();
            return all[Math.clamp(ordinal, 0, all.length - 1)];
        }
    }

    /**
     * The outcome from what the run wrote down. The clock outranks a death because an expiry kills
     * whoever it catches; a wipe reads as a fall for those who were away when it happened.
     */
    public static Outcome outcome(RiftRun.Verdict verdict, boolean expired, boolean escaped, boolean died,
            boolean inside) {
        if (expired && inside && !escaped) {
            return Outcome.TIMED_OUT;
        }
        if (died) {
            return Outcome.FELL;
        }
        if (verdict == RiftRun.Verdict.CLEARED && escaped) {
            return Outcome.CLEARED;
        }
        return verdict == RiftRun.Verdict.WIPED ? Outcome.FELL : Outcome.LEFT_EARLY;
    }

    public boolean reduced() {
        return keptPercent < 100;
    }

    public int levelsGained() {
        return Math.max(0, levelAfter - levelBefore);
    }

    public static final StreamCodec<ByteBuf, RiftRecap> STREAM_CODEC = StreamCodec.of(RiftRecap::write, RiftRecap::read);

    private static void write(ByteBuf buf, RiftRecap recap) {
        ByteBufCodecs.VAR_INT.encode(buf, recap.outcome.ordinal());
        ByteBufCodecs.VAR_INT.encode(buf, recap.tier);
        ByteBufCodecs.VAR_INT.encode(buf, recap.elapsedTicks);
        ByteBufCodecs.VAR_LONG.encode(buf, recap.endedAt);
        ByteBufCodecs.VAR_INT.encode(buf, recap.kills);
        buf.writeBoolean(recap.bossDefeated);
        ByteBufCodecs.VAR_LONG.encode(buf, recap.xpEnemies);
        ByteBufCodecs.VAR_LONG.encode(buf, recap.xpClear);
        ByteBufCodecs.VAR_LONG.encode(buf, recap.xpKept);
        ByteBufCodecs.VAR_INT.encode(buf, recap.keptPercent);
        ByteBufCodecs.VAR_INT.encode(buf, recap.levelBefore);
        ByteBufCodecs.VAR_INT.encode(buf, recap.levelAfter);
        ByteBufCodecs.VAR_INT.encode(buf, recap.barrels);
        ByteBufCodecs.VAR_INT.encode(buf, recap.shards);
        ByteBufCodecs.VAR_INT.encode(buf, recap.gear);
        ByteBufCodecs.VAR_INT.encode(buf, recap.bestRarity);
    }

    private static RiftRecap read(ByteBuf buf) {
        return new RiftRecap(Outcome.of(ByteBufCodecs.VAR_INT.decode(buf)), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                buf.readBoolean(), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf));
    }

    private static final String OUTCOME = "outcome";
    private static final String TIER = "tier";
    private static final String ELAPSED = "elapsed";
    private static final String ENDED_AT = "endedAt";
    private static final String KILLS = "kills";
    private static final String BOSS = "boss";
    private static final String XP_ENEMIES = "xpEnemies";
    private static final String XP_CLEAR = "xpClear";
    private static final String XP_KEPT = "xpKept";
    private static final String KEPT_PERCENT = "keptPercent";
    private static final String LEVEL_BEFORE = "levelBefore";
    private static final String LEVEL_AFTER = "levelAfter";
    private static final String BARRELS = "barrels";
    private static final String SHARDS = "shards";
    private static final String GEAR = "gear";
    private static final String RARITY = "rarity";

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(OUTCOME, outcome.key());
        tag.putInt(TIER, tier);
        tag.putInt(ELAPSED, elapsedTicks);
        tag.putLong(ENDED_AT, endedAt);
        tag.putInt(KILLS, kills);
        tag.putBoolean(BOSS, bossDefeated);
        tag.putLong(XP_ENEMIES, xpEnemies);
        tag.putLong(XP_CLEAR, xpClear);
        tag.putLong(XP_KEPT, xpKept);
        tag.putInt(KEPT_PERCENT, keptPercent);
        tag.putInt(LEVEL_BEFORE, levelBefore);
        tag.putInt(LEVEL_AFTER, levelAfter);
        tag.putInt(BARRELS, barrels);
        tag.putInt(SHARDS, shards);
        tag.putInt(GEAR, gear);
        tag.putInt(RARITY, bestRarity);
        return tag;
    }

    /** An unknown outcome reads as an early exit, so one bad record does not take the list with it. */
    static RiftRecap load(CompoundTag tag) {
        Outcome outcome = Outcome.LEFT_EARLY;
        for (Outcome known : Outcome.values()) {
            if (known.key().equals(tag.getString(OUTCOME))) {
                outcome = known;
            }
        }
        return new RiftRecap(outcome, tag.getInt(TIER), tag.getInt(ELAPSED), tag.getLong(ENDED_AT), tag.getInt(KILLS),
                tag.getBoolean(BOSS), tag.getLong(XP_ENEMIES), tag.getLong(XP_CLEAR), tag.getLong(XP_KEPT),
                tag.getInt(KEPT_PERCENT), tag.getInt(LEVEL_BEFORE), tag.getInt(LEVEL_AFTER), tag.getInt(BARRELS),
                tag.getInt(SHARDS), tag.getInt(GEAR), tag.contains(RARITY) ? tag.getInt(RARITY) : RiftHaul.NO_RARITY);
    }
}
