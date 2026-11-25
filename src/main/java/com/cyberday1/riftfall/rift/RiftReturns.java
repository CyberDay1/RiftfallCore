package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The way home for every player currently standing in a rift.
 *
 * <p>
 * A rift is deleted when its run ends, and a player who is offline at that moment is not there to be
 * sent anywhere: {@link RiftLevels#close} evicts the players it can see, and somebody who logged out
 * inside is not one of them. Their saved dimension still names the level. Vanilla's own answer to an
 * unknown respawn dimension is to log a warning and drop them into the overworld at whatever
 * coordinates they had, which in a rift means somewhere near the origin and quite possibly inside
 * rock. That is the failure this exists to prevent.
 *
 * <h2>Written on the way in, not on the way out</h2>
 * The debt is recorded when a player steps into a rift rather than when they log out of one, because
 * a crash is exactly the case that matters and a crash gives no notice. Recording at entry means the
 * record is already on disk at the next autosave, and the only window left is between walking in and
 * that save. Recording at logout would have been tidier and would have covered nothing.
 *
 * <p>
 * It is cleared when the player is next seen outside a rift, which {@link RiftRuns} sweeps for
 * periodically rather than hanging off any one way of leaving. There are several &mdash; the run
 * ending, an operator's {@code /riftfall rift close}, a death and respawn, a {@code /tp}, and the way
 * out that a later slice will build &mdash; and a debt left behind by one of them would teleport the
 * player away from wherever they were at their next login. A sweep cannot miss a case; a list of
 * events can.
 *
 * <p>
 * Lives on the overworld's data storage rather than beside the frame, because it is keyed by player
 * and a player has one of these at a time whichever level they entered from.
 */
public final class RiftReturns extends SavedData {

    private static final String FILE = RiftfallCore.MODID + "_returns";
    private static final String RETURNS = "returns";
    private static final String PLAYER = "player";
    private static final String ENTRY = "entry";

    private final Map<UUID, RiftEntry> owed = new HashMap<>();
    // Session-only retry state. The owed entry persists; login re-arms recovery after a restart.
    private final java.util.Set<UUID> recovering = new java.util.HashSet<>();

    // Package-private, like RiftPortals', so the round trip can be tested without a server.
    RiftReturns() {
    }

    public static RiftReturns of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RiftReturns::new, RiftReturns::load), FILE);
    }

    static RiftReturns load(CompoundTag tag, HolderLookup.Provider registries) {
        RiftReturns returns = new RiftReturns();
        ListTag list = tag.getList(RETURNS, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag record = list.getCompound(index);
            RiftEntry entry = RiftEntry.load(record.getCompound(ENTRY));
            if (entry != null) {
                returns.owed.put(record.getUUID(PLAYER), entry);
            }
        }
        return returns;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        owed.forEach((player, entry) -> {
            CompoundTag record = new CompoundTag();
            record.putUUID(PLAYER, player);
            record.put(ENTRY, entry.save());
            list.add(record);
        });
        tag.put(RETURNS, list);
        return tag;
    }

    /**
     * Records a way home for a player who is walking into a rift.
     *
     * <p>
     * An existing debt wins. Being owed a way home already means the player is believed to be inside
     * a rift, so the doorstep on file is the older one and therefore the one further out; overwriting
     * it with a doorstep inside a dungeon would be aiming the rescue at a level that is about to be
     * deleted.
     */
    public void owe(UUID player, RiftEntry entry) {
        if (owed.putIfAbsent(player, entry) == null) {
            setDirty();
        }
    }

    /** Drops a player's debt, because they are demonstrably somewhere they can stand. */
    public void forgive(UUID player) {
        recovering.remove(player);
        if (owed.remove(player) != null) {
            setDirty();
        }
    }

    /** Where this player should be put if the rift they were in is gone, or null if nothing is owed. */
    @Nullable
    public RiftEntry owed(UUID player) {
        return owed.get(player);
    }

    public boolean isEmpty() {
        return owed.isEmpty();
    }

    void recover(UUID player) {
        if (owed.containsKey(player)) recovering.add(player);
    }

    boolean recovering(UUID player) { return recovering.contains(player); }
}
