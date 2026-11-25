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
 * {@link RiftMana}'s current bar on disk: relogging used to refill it. The same shape as
 * {@link SkillCooldownStore}. Only a bar below full has an entry, so the file holds no max and a
 * player with no record is full.
 */
public final class RiftManaStore extends SavedData {

    public static final String FILE = RiftfallCore.MODID + "_mana";
    static final int VERSION = 1;
    private static final String PLAYERS = "players";
    private static final String PLAYER = "player";
    private static final String CURRENT = "current";
    private static final String VERSION_KEY = "version";

    private final Map<UUID, Double> current = new ConcurrentHashMap<>();

    RiftManaStore() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RiftManaStore::onStarting);
        NeoForge.EVENT_BUS.addListener(RiftManaStore::onStopped);
    }

    public static RiftManaStore of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(
                RiftManaStore::new, (tag, registries) -> load(tag)), FILE);
    }

    /** Makes {@code store} the live bar. What server start does. */
    public static void attach(RiftManaStore store) {
        RiftMana.use(store.current, store::setDirty);
    }

    public boolean attached() {
        return RiftMana.backedBy(current);
    }

    /** The stored value, or null for a full bar. Unclamped: the max is the player's, not the file's. */
    public Double stored(UUID player) {
        return current.get(player);
    }

    /** Never throws: a malformed entry is dropped (read as full), since a throw here takes the whole file. */
    public static RiftManaStore load(CompoundTag tag) {
        RiftManaStore store = new RiftManaStore();
        ListTag players = tag.getList(PLAYERS, Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag record = players.getCompound(index);
            if (!record.hasUUID(PLAYER) || !record.contains(CURRENT, Tag.TAG_DOUBLE)) {
                continue;
            }
            double value = record.getDouble(CURRENT);
            if (Double.isFinite(value)) {
                store.current.put(record.getUUID(PLAYER), Math.max(0.0, value));
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
        RiftMana.forget();
    }
}
