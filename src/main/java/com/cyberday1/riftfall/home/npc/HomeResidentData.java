package com.cyberday1.riftfall.home.npc;

import com.cyberday1.riftfall.block.marker.NpcRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Stored in the home dimension, so a permanent team shares exactly the same resident records. */
public final class HomeResidentData extends SavedData {
    public record Resident(NpcRole role, BlockPos anchor, Direction facing, int radius, UUID entityId) {
        public Resident {
            if (!supported(role) || !facing.getAxis().isHorizontal() || radius < 0 || radius > 8)
                throw new IllegalArgumentException("Invalid home resident");
            anchor = anchor.immutable();
        }
    }
    private final Map<NpcRole, Resident> residents = new EnumMap<>(NpcRole.class);
    // Station overlays applied in this level (StationOverlays): level and Master path per role, done chunk portions.
    private final Map<NpcRole, Integer> stationLevel = new EnumMap<>(NpcRole.class);
    private final Map<NpcRole, String> stationPath = new EnumMap<>(NpcRole.class);
    private final Set<String> stationDone = new HashSet<>();

    public static boolean supported(NpcRole role) {
        return role != null;
    }

    public static UUID identity(ResourceLocation home, NpcRole role) {
        return UUID.nameUUIDFromBytes((home + "/resident/" + role.id()).getBytes(StandardCharsets.UTF_8));
    }

    public static HomeResidentData of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(HomeResidentData::new, HomeResidentData::load),
                "riftfall_home_residents");
    }
    public Collection<Resident> residents() { return List.copyOf(residents.values()); }
    public Resident get(NpcRole role) { return residents.get(role); }

    /** Same anchor may refresh authoring settings; a second anchor never silently moves a service. */
    public boolean register(Resident resident) {
        var old = residents.get(resident.role());
        if (old != null && !old.anchor().equals(resident.anchor())) return false;
        if (!resident.equals(old)) { residents.put(resident.role(), resident); setDirty(); }
        return true;
    }

    public int stationLevel(NpcRole role) { return stationLevel.getOrDefault(role, 0); }
    public String stationPath(NpcRole role) { return stationPath.get(role); }
    public boolean stationDone(String key) { return stationDone.contains(key); }
    public void markStation(String key) { if (stationDone.add(key)) setDirty(); }
    public void forgetStation(String key) { if (stationDone.remove(key)) setDirty(); }
    /** Records a level fully applied; only raises. Level 3 also records the Master path it was built for. */
    public void stationApplied(NpcRole role, int level, String path) {
        boolean changed = level > stationLevel(role);
        if (changed) stationLevel.put(role, level);
        if (level >= 3 && !java.util.Objects.equals(path, stationPath.get(role))) {
            if (path == null) stationPath.remove(role); else stationPath.put(role, path);
            changed = true;
        }
        if (changed) setDirty();
    }
    /** A respec took the Master overlay down. */
    public void stationReverted(NpcRole role) {
        stationLevel.put(role, Math.min(2, stationLevel(role)));
        stationPath.remove(role);
        setDirty();
    }

    public static HomeResidentData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new HomeResidentData();
        var entries = tag.getList("residents", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            try {
                var role = NpcRole.valueOf(entry.getString("role"));
                var facing = Direction.byName(entry.getString("facing"));
                if (facing == null || !entry.hasUUID("entity")) continue;
                var resident = new Resident(role, BlockPos.of(entry.getLong("anchor")), facing,
                        entry.getInt("radius"), entry.getUUID("entity"));
                data.residents.putIfAbsent(role, resident);
            } catch (IllegalArgumentException ignored) { /* Unknown future roles do not create arbitrary mobs. */ }
        }
        var stations = tag.getList("stations", Tag.TAG_COMPOUND);
        for (int i = 0; i < stations.size(); i++) {
            var entry = stations.getCompound(i);
            try {
                var role = NpcRole.valueOf(entry.getString("role"));
                data.stationLevel.put(role, entry.getInt("level"));
                if (entry.contains("path")) data.stationPath.put(role, entry.getString("path"));
            } catch (IllegalArgumentException ignored) { /* A role this build does not know. */ }
        }
        for (var key : tag.getList("stationDone", Tag.TAG_STRING)) data.stationDone.add(key.getAsString());
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        for (var resident : residents.values()) {
            var entry = new CompoundTag();
            entry.putString("role", resident.role().name()); entry.putLong("anchor", resident.anchor().asLong());
            entry.putString("facing", resident.facing().getName()); entry.putInt("radius", resident.radius());
            entry.putUUID("entity", resident.entityId()); entries.add(entry);
        }
        tag.put("residents", entries);
        var stations = new ListTag();
        for (var role : NpcRole.values()) {
            if (!stationLevel.containsKey(role) && !stationPath.containsKey(role)) continue;
            var entry = new CompoundTag();
            entry.putString("role", role.name()); entry.putInt("level", stationLevel(role));
            if (stationPath.containsKey(role)) entry.putString("path", stationPath.get(role));
            stations.add(entry);
        }
        tag.put("stations", stations);
        var done = new ListTag();
        for (var key : stationDone) done.add(net.minecraft.nbt.StringTag.valueOf(key));
        tag.put("stationDone", done);
        return tag;
    }
}
