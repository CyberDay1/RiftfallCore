package com.cyberday1.riftfall.story;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Who has been given the quest book: one world-level record per character, like StarterKitLedger. */
public final class GuideBookLedger extends SavedData {
    public static final String FILE = RiftfallCore.MODID + "_guide_book";
    private static final String GIVEN = "given";

    private final Set<UUID> given = new HashSet<>();

    GuideBookLedger() {}

    public static GuideBookLedger of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(GuideBookLedger::new, GuideBookLedger::load), FILE);
    }

    static GuideBookLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        var ledger = new GuideBookLedger();
        for (Tag id : tag.getList(GIVEN, Tag.TAG_INT_ARRAY)) ledger.given.add(NbtUtils.loadUUID(id));
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (UUID id : given) list.add(NbtUtils.createUUID(id));
        tag.put(GIVEN, list);
        return tag;
    }

    public boolean has(UUID player) { return given.contains(player); }

    void record(UUID player) { if (given.add(player)) setDirty(); }

    /** For the rig, which needs fresh characters on a world that keeps its ledger between runs. */
    public void forget(UUID player) { if (given.remove(player)) setDirty(); }
}
