package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link RangedCharge}'s pool on disk, the shape of {@link RiftManaStore}: a relog, a death or a restart must not be
 * a free refill. Only a pool below full has an entry, and an overdrawn one is kept as it is. The bank is the player's
 * attribute and is not known here, so a value is kept as written and every read holds it inside the bank.
 */
public final class RangedChargeStore extends SavedData {

    public static final String FILE = RiftfallCore.MODID + "_ranged_charge";
    static final int VERSION = 1;
    private static final String PLAYERS = "players";
    private static final String PLAYER = "player";
    private static final String CURRENT = "current";
    private static final String VERSION_KEY = "version";

    private final Map<UUID, Double> current = new ConcurrentHashMap<>();

    RangedChargeStore() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RangedChargeStore::onStarting);
        NeoForge.EVENT_BUS.addListener(RangedChargeStore::onStopped);
    }

    public static RangedChargeStore of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(
                RangedChargeStore::new, (tag, registries) -> load(tag)), FILE);
    }

    /** Makes {@code store} the live pool. What server start does. */
    public static void attach(RangedChargeStore store) {
        RangedCharge.use(store.current, store::setDirty);
    }

    public boolean attached() {
        return RangedCharge.backedBy(current);
    }

    /** The stored value, or null for a full pool. */
    public Double stored(UUID player) {
        return current.get(player);
    }

    /** Never throws: a malformed entry is dropped (read as full), since a throw here takes the whole file. */
    public static RangedChargeStore load(CompoundTag tag) {
        RangedChargeStore store = new RangedChargeStore();
        ListTag players = tag.getList(PLAYERS, Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag record = players.getCompound(index);
            if (!record.hasUUID(PLAYER) || !record.contains(CURRENT, Tag.TAG_DOUBLE)) {
                continue;
            }
            double value = record.getDouble(CURRENT);
            if (Double.isFinite(value)) {
                store.current.put(record.getUUID(PLAYER), value);
            }
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        current.forEach((player, value) -> {
            CompoundTag record = new CompoundTag();
            record.putUUID(PLAYER, player);
            record.putDouble(CURRENT, value);
            players.add(record);
        });
        tag.putInt(VERSION_KEY, VERSION);
        tag.put(PLAYERS, players);
        return tag;
    }

    private static void onStarting(ServerStartingEvent event) {
        attach(of(event.getServer()));
    }

    private static void onStopped(ServerStoppedEvent event) {
        RangedCharge.forget();
    }
}
