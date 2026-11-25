package com.cyberday1.riftfall.stat;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.skill.SkillBook;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where every player's progression is kept: their XP, their point allocation, and their skills.
 *
 * <p>
 * The overworld's storage, keyed by player UUID, which is the home {@link
 * com.cyberday1.riftfall.rift.RiftClaims} argued for and four other classes here already use. The
 * three things it has to survive are the same three: a rift level being deleted, the server
 * restarting, and the owner being offline for both.
 *
 * <p>
 * Storage only. Nothing here applies a modifier, awards XP, or knows what either buys.
 */
public final class RiftStats extends SavedData {

    /**
     * The layout of one player's record, written from the first commit.
     *
     * <p>
     * Per record rather than per file, because a migration lands per player and a half-migrated file
     * has to be a state that can be written down. Retrofitting this later would mean guessing what
     * unversioned data meant.
     *
     * <p>
     * Version 2 added {@code xp}. A version 1 record is read with the allocation it already had and
     * an XP of zero, and is rewritten as version 2 at the next save.
     *
     * <p>
     * Version 3 added {@code skills}, the {@link SkillBook}. An older record has no such key and reads
     * as an empty book, keeping its XP and allocation.
     *
     * <p>
     * Version 4 added {@code alloc}, the attribute points the player placed, and {@code unseated_bonus}.
     * {@code block} keeps meaning the class block. An older record reads with no allocation and no bonus.
     */
    public static final int VERSION = 4;

    /**
     * The oldest record layout this jar still understands, which is every one there has been.
     *
     * <p>
     * ⛔ This, not {@link #VERSION}, is what {@link #load} refuses below. Refusing at {@code VERSION}
     * is what the first draft did, and it is silently destructive: {@code SavedData} writes back what
     * it holds, so a record skipped on load is deleted at the next autosave. A version bump would
     * have taken every existing record with it.
     */
    public static final int FIRST_VERSION = 1;

    /**
     * The data file's name, which is also the key {@code DimensionDataStorage} caches it under.
     *
     * <p>
     * Public because the disk half of the round trip cannot be proved from inside this class: the
     * rig re-reads the written file through {@code readTagFromDisk} and loads it again, and it
     * needs the name to do that.
     */
    public static final String FILE = RiftfallCore.MODID + "_stats";
    private static final String STATS = "stats";
    private static final String PLAYER = "player";
    private static final String BLOCK = "block";
    private static final String XP = "xp";
    private static final String SKILLS = "skills";
    private static final String ALLOC = "alloc";
    private static final String UNSEATED_BONUS = "unseated_bonus";
    private static final String VERSION_KEY = "version";

    /**
     * One player's whole record.
     *
     * <p>
     * One value and not two maps, so that "does this player have a record" has a single answer and
     * neither half can be written without carrying the other. The defect that closes is the cheap
     * one: a setter for the XP that drops the allocation, or the reverse.
     */
    private record Kept(StatBlock block, long xp, SkillBook book, StatBlock alloc, boolean unseatedBonus) {

        Kept withBlock(StatBlock next) {
            return new Kept(next, xp, book, alloc, unseatedBonus);
        }

        Kept withXp(long next) {
            return new Kept(block, next, book, alloc, unseatedBonus);
        }

        Kept withBook(SkillBook next) {
            return new Kept(block, xp, next, alloc, unseatedBonus);
        }

        Kept withAlloc(StatBlock next) {
            return new Kept(block, xp, book, next, unseatedBonus);
        }

        Kept withUnseatedBonus() {
            return new Kept(block, xp, book, alloc, true);
        }
    }

    private static final Kept NOTHING = new Kept(StatBlock.empty(0), 0L, SkillBook.EMPTY, StatBlock.empty(0), false);

    private final Map<UUID, Kept> records = new HashMap<>();

    /**
     * Records written by a newer jar than this one, kept byte-for-byte.
     *
     * <p>
     * Skipping an unreadable record on load looks like the safe choice and is the opposite:
     * {@code SavedData} writes back what it holds, so anything dropped on load is deleted at the next
     * autosave. That would turn "booted an older jar once" into a permanently lost allocation.
     */
    private final List<CompoundTag> foreign = new ArrayList<>();

    // Package-private, like RiftReturns', so the round trip can be tested without a server.
    RiftStats() {
    }

    public static RiftStats of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RiftStats::new, RiftStats::load), FILE);
    }

    public static RiftStats load(CompoundTag tag, HolderLookup.Provider registries) {
        RiftStats stats = new RiftStats();
        ListTag list = tag.getList(STATS, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag record = list.getCompound(index);
            int version = record.getInt(VERSION_KEY);
            if (version > VERSION) {
                stats.foreign.add(record.copy());
                continue;
            }
            // A record with no version is not forward data and is not a version 1 record either --
            // `getInt` answers 0 for an absent key. Nothing has ever written one; the branch is here
            // so a truncated or hand-edited tag cannot be read as though it were current.
            if (version < FIRST_VERSION || !record.hasUUID(PLAYER)) {
                continue;
            }
            // The migration, and it is the whole of it: a version 1 record has no `xp` key, and an
            // absent key reads 0. ⛔ Deliberately not converted from `limit` -- a v1 limit is the
            // number of rifts that player cleared, so turning it into XP needs the per-clear rate,
            // which is a design call and not yet given. A zero is visibly a zero; an invented rate would
            // be invisible from the moment it was written.
            stats.records.put(record.getUUID(PLAYER),
                    new Kept(StatBlock.load(record.getCompound(BLOCK)),
                            // Clamped rather than refused, for `StatBlock.load`'s reason: a throw
                            // here propagates out of SavedData loading and takes the whole file.
                            Math.max(0L, record.getLong(XP)),
                            SkillBook.load(record.getCompound(SKILLS)),
                            // v4; absent before it, which reads as no allocation and no bonus.
                            StatBlock.load(record.getCompound(ALLOC)),
                            record.getBoolean(UNSEATED_BONUS)));
        }
        return stats;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        records.forEach((player, kept) -> {
            CompoundTag record = new CompoundTag();
            record.putInt(VERSION_KEY, VERSION);
            record.putUUID(PLAYER, player);
            record.put(BLOCK, kept.block().save());
            record.putLong(XP, kept.xp());
            record.put(SKILLS, kept.book().save());
            record.put(ALLOC, kept.alloc().save());
            record.putBoolean(UNSEATED_BONUS, kept.unseatedBonus());
            list.add(record);
        });
        foreign.forEach(record -> list.add(record.copy()));
        tag.put(STATS, list);
        return tag;
    }

    /** The class block this player carries ({@code ClassAlignment.reconcile} rewrites it). */
    public StatBlock blockFor(UUID player) {
        return kept(player).block();
    }

    /** The attribute points this player placed themselves, on top of the class block. */
    public StatBlock allocFor(UUID player) {
        return kept(player).alloc();
    }

    /** Replaces the player's own allocation, keeping everything else. */
    public void setAlloc(UUID player, StatBlock alloc) {
        records.put(player, kept(player).withAlloc(alloc));
        setDirty();
    }

    /** Whether this player has the Unseated's one-time attribute points. */
    public boolean hasUnseatedBonus(UUID player) {
        return kept(player).unseatedBonus();
    }

    /**
     * Grants the Unseated's attribute points, once. Idempotent: returns whether this call granted them,
     * so the caller tells the player only the first time. Survives death, unlike persistent data.
     */
    public boolean grantUnseatedBonus(UUID player) {
        Kept kept = kept(player);
        if (kept.unseatedBonus()) {
            return false;
        }
        records.put(player, kept.withUnseatedBonus());
        setDirty();
        return true;
    }

    /** Takes the bonus back. For the rig's cleanup only: no player path un-contains the Unseated. */
    public void forgetUnseatedBonus(UUID player) {
        Kept kept = kept(player);
        if (kept.unseatedBonus()) {
            records.put(player, new Kept(kept.block(), kept.xp(), kept.book(), kept.alloc(), false));
            setDirty();
        }
    }

    private Kept kept(UUID player) {
        return records.getOrDefault(player, NOTHING);
    }

    /** How much XP this player has earned in total, which today is nobody's anything. */
    public long xpFor(UUID player) {
        return kept(player).xp();
    }

    /** Replaces the class block, keeping the rest. */
    public void set(UUID player, StatBlock block) {
        records.put(player, kept(player).withBlock(block));
        setDirty();
    }

    /**
     * Replaces the XP total, keeping the allocation.
     *
     * <p>
     * Throws on a negative, which is the other side of {@link #load}'s clamp: a caller holding a
     * negative total in memory has a bug, and a bug that throws at the call site is cheap.
     */
    public void setXp(UUID player, long xp) {
        if (xp < 0L) {
            throw new IllegalArgumentException("negative XP total " + xp + " for " + player);
        }
        records.put(player, kept(player).withXp(xp));
        setDirty();
    }

    /** What this player has bought and slotted. */
    public SkillBook bookFor(UUID player) {
        return kept(player).book();
    }

    /** Replaces the purchases, keeping the XP and the allocation. */
    public void setBook(UUID player, SkillBook book) {
        records.put(player, kept(player).withBook(book));
        setDirty();
    }

    public boolean isEmpty() {
        return records.isEmpty() && foreign.isEmpty();
    }

    /** How many records this jar could not read and is carrying for a newer one. */
    public int foreignCount() {
        return foreign.size();
    }
}
