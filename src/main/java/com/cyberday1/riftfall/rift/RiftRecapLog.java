package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every player's past run recaps, newest first, and who has one they have not been shown yet.
 * On the overworld's storage, keyed by player, for the reason {@link RiftClaims} is: it outlives the
 * rift, the process and the owner being offline.
 */
public final class RiftRecapLog extends SavedData {

    /** How many recaps are kept per player; the oldest is dropped past it. */
    public static final int HISTORY_CAP = 50;

    private static final String FILE = RiftfallCore.MODID + "_recaps";
    private static final String PLAYERS = "players";
    private static final String PLAYER = "player";
    private static final String RUNS = "runs";
    private static final String UNSEEN = "unseen";

    private final Map<UUID, List<RiftRecap>> runs = new HashMap<>();
    private final Set<UUID> unseen = new HashSet<>();

    // Package-private, like RiftTolls', so the round trip can be tested without a server.
    RiftRecapLog() {
    }

    public static RiftRecapLog of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RiftRecapLog::new, RiftRecapLog::load), FILE);
    }

    static RiftRecapLog load(CompoundTag tag, HolderLookup.Provider registries) {
        RiftRecapLog log = new RiftRecapLog();
        ListTag players = tag.getList(PLAYERS, Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag record = players.getCompound(index);
            if (!record.hasUUID(PLAYER)) {
                continue;
            }
            UUID player = record.getUUID(PLAYER);
            ListTag saved = record.getList(RUNS, Tag.TAG_COMPOUND);
            List<RiftRecap> history = new ArrayList<>();
            for (int run = 0; run < saved.size() && history.size() < HISTORY_CAP; run++) {
                history.add(RiftRecap.load(saved.getCompound(run)));
            }
            if (!history.isEmpty()) {
                log.runs.put(player, history);
                if (record.getBoolean(UNSEEN)) {
                    log.unseen.add(player);
                }
            }
        }
        return log;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        runs.forEach((player, history) -> {
            CompoundTag record = new CompoundTag();
            record.putUUID(PLAYER, player);
            ListTag saved = new ListTag();
            history.forEach(recap -> saved.add(recap.save()));
            record.put(RUNS, saved);
            record.putBoolean(UNSEEN, unseen.contains(player));
            players.add(record);
        });
        tag.put(PLAYERS, players);
        return tag;
    }

    /** Files a recap at the front of a player's history and marks it as not yet shown. */
    public void add(UUID player, RiftRecap recap) {
        List<RiftRecap> history = runs.computeIfAbsent(player, key -> new ArrayList<>());
        history.add(0, recap);
        while (history.size() > HISTORY_CAP) {
            history.remove(history.size() - 1);
        }
        unseen.add(player);
        setDirty();
    }

    /** A player's recaps, newest first. */
    public List<RiftRecap> history(UUID player) {
        return List.copyOf(runs.getOrDefault(player, List.of()));
    }

    /** Whether this player has a recap that has not been sent to them yet. */
    public boolean unseen(UUID player) {
        return unseen.contains(player);
    }

    public boolean anyUnseen() {
        return !unseen.isEmpty();
    }

    public void seen(UUID player) {
        if (unseen.remove(player)) {
            setDirty();
        }
    }
}
