package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What each lit copper frame in a level knows across a restart: the tier it was lit at, and whether
 * it has already been spent on a run.
 *
 * <p>
 * A portal has to remember its tier because the key that set it is consumed at lighting time, and
 * because the frame is what a second player walks into an hour later. There is nowhere on the block
 * to put it: the tier has no agreed ceiling &mdash; the forge lets a player pick the number &mdash;
 * so it cannot be a block state, and a block entity per portal block would mean six of them for the
 * smallest legal frame.
 *
 * <p>
 * Frames are keyed by the bottom-left corner of their opening, which
 * {@link RiftPortalShape#bottomLeft()} recovers from any block inside them. It is the one point that
 * is the same whichever block of the portal is asking.
 *
 * <h2>What is not here any more</h2>
 * The frame-to-rift binding used to live here, deliberately unsaved so that a closed rift left the
 * frame free to open a new one. That is the behaviour a run replaces, so the binding moved to
 * {@link RiftRuns} where the rest of a run's state is and where it can be ended in one piece. Only
 * the two facts that have to survive the process are left, and both of them are saved.
 *
 * <h2>Why "spent" is worth a set of its own</h2>
 * A run cannot survive a restart, because the level it is a run through cannot: nothing about a rift
 * reaches {@code level.dat} and {@link RiftLevels#sweep} deletes whatever folders a crash left
 * behind. But the frame does survive, still lit and still remembering its tier, and a frame that
 * outlives its run is exactly the free rift this feature exists to close off. Marking the frame the
 * moment its run begins is what lets the portal recognise that case afterwards and put itself out.
 *
 * <p>
 * The mark is not consulted while the run is going &mdash; the run is right there to be asked &mdash;
 * so it costs nothing until it is the only thing left.
 *
 * <h2>Why the seed is here</h2>
 * A rift's layout is a pure function of its seed, so saving the seed beside the tier makes the
 * dungeon behind a lit frame <em>reproducible</em>. That turns the live level {@link RiftWarmup}
 * builds at lighting time into a cache rather than the only copy: it may be let go and rebuilt, and
 * a frame that was lit before a restart still leads to the dungeon it was lit onto rather than to
 * nothing. A frame already spent on a run is a different matter and stays expired &mdash; see
 * {@link RiftPortalBlock#getPortalDestination}.
 */
public final class RiftPortals extends SavedData {

    private static final String FILE = RiftfallCore.MODID + "_portals";
    private static final String PORTALS = "portals";
    private static final String POS = "pos";
    private static final String TIER = "tier";
    private static final String SPENT = "spent";
    private static final String SEED = "seed";
    private static final String ATTUNED = "attuned";

    /** Bottom-left corner of a lit frame's opening to the tier it was lit at. Saved. */
    private final Map<BlockPos, Integer> tiers = new HashMap<>();

    /** The same corners, for the frames whose run has already been started. Saved. */
    private final Set<BlockPos> spent = new HashSet<>();

    /** The same corners, to the seed the dungeon behind each one is built from. Saved. */
    private final Map<BlockPos, Long> seeds = new HashMap<>();

    /** The same corners, to what an attuned key asked of the rift. Saved; absent for a plain key. */
    private final Map<BlockPos, RiftAttunement> attunements = new HashMap<>();

    // The constructor and load are package-private rather than private so that the round trip can be
    // tested without a server. Nothing else can reach a RiftPortals except through of(ServerLevel).
    RiftPortals() {
    }

    public static RiftPortals of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RiftPortals::new, RiftPortals::load), FILE);
    }

    static RiftPortals load(CompoundTag tag, HolderLookup.Provider registries) {
        RiftPortals portals = new RiftPortals();
        ListTag list = tag.getList(PORTALS, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            BlockPos corner = BlockPos.of(entry.getLong(POS));
            portals.tiers.put(corner, entry.getInt(TIER));
            if (entry.getBoolean(SPENT)) {
                portals.spent.add(corner);
            }
            // Absent on any frame lit before the seed was saved at all, which is why seed() rolls one
            // rather than assuming zero: zero is a legal seed and every such frame would share it.
            if (entry.contains(SEED, Tag.TAG_LONG)) {
                portals.seeds.put(corner, entry.getLong(SEED));
            }
            if (entry.contains(ATTUNED, Tag.TAG_COMPOUND)) {
                portals.attunements.put(corner, RiftAttunement.load(entry.getCompound(ATTUNED)));
            }
        }
        return portals;
    }

    /**
     * Every corner any of the three maps knows, not just the ones with a tier.
     *
     * <p>
     * A frame lit by {@code /fill} rather than by a key is never remembered, so it has no tier &mdash;
     * but it can still be walked into, which spends it, and asking it for its dungeon rolls it a seed.
     * Writing only the frames with tiers would lose both of those on every restart, which is the free
     * rift the spent mark exists to close off and a different dungeon behind a frame that has not
     * moved.
     */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Set<BlockPos> corners = new LinkedHashSet<>(tiers.keySet());
        corners.addAll(spent);
        corners.addAll(seeds.keySet());

        ListTag list = new ListTag();
        for (BlockPos corner : corners) {
            CompoundTag entry = new CompoundTag();
            entry.putLong(POS, corner.asLong());
            entry.putInt(TIER, tier(corner));
            if (spent.contains(corner)) {
                entry.putBoolean(SPENT, true);
            }
            Long seed = seeds.get(corner);
            if (seed != null) {
                entry.putLong(SEED, seed);
            }
            RiftAttunement attunement = attunements.get(corner);
            if (attunement != null) {
                entry.put(ATTUNED, attunement.save());
            }
            list.add(entry);
        }
        tag.put(PORTALS, list);
        return tag;
    }

    /**
     * Records what a frame was just lit at.
     *
     * <p>
     * Relighting clears the spent mark. The only way to reach a frame that still carries one is to
     * break it, which forgets it entirely, so this is belt and braces rather than a path the game
     * has &mdash; but the two facts are about the same frame and leaving them able to disagree would
     * make a fresh key buy a portal that refuses to open.
     */
    public void remember(BlockPos corner, int tier, long seed) {
        remember(corner, tier, seed, RiftAttunement.NONE);
    }

    /** As above, keeping what an attuned key asked for until the frame is relit or broken. */
    public void remember(BlockPos corner, int tier, long seed, RiftAttunement attunement) {
        if (attunement.none()) attunements.remove(corner);
        else attunements.put(corner.immutable(), attunement);
        tiers.put(corner.immutable(), tier);
        seeds.put(corner.immutable(), seed);
        spent.remove(corner);
        setDirty();
    }

    /**
     * The seed the dungeon behind a frame is built from, rolled and kept on the first ask.
     *
     * <p>
     * Rolling here rather than refusing covers the frame that was lit before any of this existed, and
     * covers it in the only way that keeps the promise the seed is saved for: whatever number this
     * hands out, it hands out the same one every time afterwards, so the dungeon can be let go and
     * rebuilt without becoming a different dungeon.
     */
    public long seed(BlockPos corner, RandomSource fallback) {
        Long known = seeds.get(corner);
        if (known != null) {
            return known;
        }
        long rolled = fallback.nextLong();
        seeds.put(corner.immutable(), rolled);
        setDirty();
        return rolled;
    }

    /**
     * Marks a frame as having had its run started, which is the one use a lit frame gets.
     *
     * <p>
     * The tier is left alone. It is of no further use &mdash; the rift has been built and carries its
     * own copy &mdash; but dropping it would make {@code /riftfall} and the save file stop agreeing
     * about what a frame in the world was lit at, for no gain.
     */
    public void spend(BlockPos corner) {
        if (spent.add(corner.immutable())) {
            setDirty();
        }
    }

    /** Whether this frame has already been spent on a run. */
    public boolean isSpent(BlockPos corner) {
        return spent.contains(corner);
    }

    /**
     * The tier a frame was lit at.
     *
     * <p>
     * An unknown frame is a tier-1 frame rather than a broken one, for the same reason a key with no
     * tier component is: refusing to work would turn every path that can produce a portal we did not
     * see lit &mdash; a structure that ships one, a world upgraded from before this was saved &mdash;
     * into a dead frame with no way to tell why.
     */
    public int tier(BlockPos corner) {
        return tiers.getOrDefault(corner, RiftKeyItem.DEFAULT_TIER);
    }

    /** What the key that lit a frame was attuned to; {@link RiftAttunement#NONE} for a plain key. */
    public RiftAttunement attunement(BlockPos corner) {
        return attunements.getOrDefault(corner, RiftAttunement.NONE);
    }

    /** Drops everything known about a frame, called when its portal blocks go. */
    public void forget(BlockPos corner) {
        boolean known = tiers.remove(corner) != null;
        known |= attunements.remove(corner) != null;
        known |= seeds.remove(corner) != null;
        if (spent.remove(corner) || known) {
            setDirty();
        }
    }
}
