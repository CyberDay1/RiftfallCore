package com.cyberday1.riftfall.home;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Home grants survive restarts and are independent of dungeon-party membership. */
public final class HomeAccessData extends SavedData {
    private final Map<UUID, Set<UUID>> allies = new HashMap<>();
    private final Map<UUID, UUID> teamHomes = new HashMap<>();

    public UUID teamHome(UUID team, UUID founder) {
        UUID existing = teamHomes.get(team);
        if (existing != null) return existing;
        teamHomes.put(team, founder);
        setDirty();
        return founder;
    }

    public static HomeAccessData of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(HomeAccessData::new, HomeAccessData::load), "riftfall_home_access");
    }

    public boolean allied(UUID owner, UUID guest) {
        return allies.getOrDefault(owner, Set.of()).contains(guest);
    }

    public boolean setAlly(UUID owner, UUID guest, boolean allow) {
        boolean changed;
        if (allow) changed = allies.computeIfAbsent(owner, id -> new HashSet<>()).add(guest);
        else {
            var grants = allies.get(owner);
            changed = grants != null && grants.remove(guest);
            if (grants != null && grants.isEmpty()) allies.remove(owner);
        }
        if (changed) setDirty();
        return changed;
    }

    public Set<UUID> allies(UUID owner) { return Set.copyOf(allies.getOrDefault(owner, Set.of())); }

    static HomeAccessData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new HomeAccessData();
        var homes = tag.getList("teamHomes", Tag.TAG_COMPOUND);
        for (int i = 0; i < homes.size(); i++) {
            var entry = homes.getCompound(i);
            if (entry.hasUUID("team") && entry.hasUUID("home"))
                data.teamHomes.put(entry.getUUID("team"), entry.getUUID("home"));
        }
        var entries = tag.getList("allies", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            if (entry.hasUUID("owner") && entry.hasUUID("guest"))
                data.allies.computeIfAbsent(entry.getUUID("owner"), id -> new HashSet<>()).add(entry.getUUID("guest"));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        allies.forEach((owner, guests) -> guests.forEach(guest -> {
            var entry = new CompoundTag();
            entry.putUUID("owner", owner); entry.putUUID("guest", guest); entries.add(entry);
        }));
        tag.put("allies", entries);
        var homes = new ListTag();
        teamHomes.forEach((team, home) -> {
            var entry = new CompoundTag();
            entry.putUUID("team", team); entry.putUUID("home", home); homes.add(entry);
        });
        tag.put("teamHomes", homes);
        return tag;
    }
}
