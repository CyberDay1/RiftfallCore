package com.cyberday1.riftfall.gear;

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
 * Who has had a starter kit, and which class's: one world-level record per character, so a kit is
 * given once whatever happens to the class afterwards (a reset does not clear it) and survives death,
 * logout and restart. Kept apart from {@code RiftStats} so the skill save needs no new version.
 */
public final class StarterKitLedger extends SavedData {

    public static final String FILE = RiftfallCore.MODID + "_starter_kits";
    private static final String GRANTED = "granted";
    private static final String PLAYER = "player";
    private static final String CLASS = "class";

    private final Map<UUID, String> granted = new HashMap<>();

    StarterKitLedger() {
    }

    public static StarterKitLedger of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StarterKitLedger::new, StarterKitLedger::load), FILE);
    }

    static StarterKitLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        StarterKitLedger ledger = new StarterKitLedger();
        ListTag list = tag.getList(GRANTED, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag record = list.getCompound(i);
            if (record.hasUUID(PLAYER)) {
                ledger.granted.put(record.getUUID(PLAYER), record.getString(CLASS));
            }
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        granted.forEach((player, skillClass) -> {
            CompoundTag record = new CompoundTag();
            record.putUUID(PLAYER, player);
            record.putString(CLASS, skillClass);
            list.add(record);
        });
        tag.put(GRANTED, list);
        return tag;
    }

    /** The class whose kit this character was given, or null if none yet. */
    public @Nullable String grantedTo(UUID player) {
        return granted.get(player);
    }

    void record(UUID player, String skillClass) {
        granted.put(player, skillClass);
        setDirty();
    }

    /** For the rig, which needs fresh characters on a world that keeps its ledger between runs. */
    public void forget(UUID player) {
        if (granted.remove(player) != null) {
            setDirty();
        }
    }
}
