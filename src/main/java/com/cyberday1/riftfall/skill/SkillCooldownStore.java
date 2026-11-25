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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * {@link SkillCooldowns}' timers on disk (CD1): relogging used to reset every cooldown. Ready-at
 * ticks are world game time, which a restart keeps, so they are stored as they are. Overworld
 * storage, keyed by player UUID, like {@code RiftStats}; a timer already past is dropped on save.
 */
public final class SkillCooldownStore extends SavedData {

    public static final String FILE = RiftfallCore.MODID + "_cooldowns";
    static final int VERSION = 1;
    private static final String PLAYERS = "players";
    private static final String PLAYER = "player";
    private static final String TIMERS = "timers";
    private static final String VERSION_KEY = "version";

    private final Map<UUID, Map<String, Long>> readyAt = new ConcurrentHashMap<>();
    private final LongSupplier now;

    SkillCooldownStore(LongSupplier now) {
        this.now = now;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SkillCooldownStore::onStarting);
        NeoForge.EVENT_BUS.addListener(SkillCooldownStore::onStopped);
    }

    public static SkillCooldownStore of(MinecraftServer server) {
        LongSupplier now = () -> server.overworld().getGameTime();
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(
                () -> new SkillCooldownStore(now), (tag, registries) -> load(tag, now)), FILE);
    }

    /** Makes {@code store} the live timers. What server start does; the rig calls it with a disk read. */
    public static void attach(SkillCooldownStore store) {
        SkillCooldowns.use(store.readyAt, store::setDirty);
    }

    /** Whether the live timers are this store's. */
    public boolean attached() {
        return SkillCooldowns.backedBy(readyAt);
    }

    /** The stored ready-at tick, or 0. */
    public long readyAt(UUID player, String nodeId) {
        Map<String, Long> mine = readyAt.get(player);
        Long at = mine == null ? null : mine.get(nodeId);
        return at == null ? 0L : at;
    }

    /** Never throws: a malformed entry is dropped, since a throw here takes the whole file. */
    public static SkillCooldownStore load(CompoundTag tag, LongSupplier now) {
        SkillCooldownStore store = new SkillCooldownStore(now);
        ListTag players = tag.getList(PLAYERS, Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag record = players.getCompound(index);
            if (!record.hasUUID(PLAYER)) {
                continue;
            }
            CompoundTag timers = record.getCompound(TIMERS);
            Map<String, Long> mine = new HashMap<>();
            for (String node : timers.getAllKeys()) {
                if (timers.contains(node, Tag.TAG_LONG)) {
                    mine.put(node, timers.getLong(node));
                }
            }
            if (!mine.isEmpty()) {
                store.readyAt.put(record.getUUID(PLAYER), mine);
            }
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long at = now.getAsLong();
        ListTag players = new ListTag();
        readyAt.forEach((player, mine) -> {
            CompoundTag timers = new CompoundTag();
            mine.forEach((node, ready) -> {
                if (ready > at) {
                    timers.putLong(node, ready);
                }
            });
            if (!timers.isEmpty()) {
                CompoundTag record = new CompoundTag();
                record.putUUID(PLAYER, player);
                record.put(TIMERS, timers);
                players.add(record);
            }
        });
        tag.putInt(VERSION_KEY, VERSION);
        tag.put(PLAYERS, players);
        return tag;
    }

    private static void onStarting(ServerStartingEvent event) {
        attach(of(event.getServer()));
    }

    private static void onStopped(ServerStoppedEvent event) {
        SkillCooldowns.forget();
    }
}
