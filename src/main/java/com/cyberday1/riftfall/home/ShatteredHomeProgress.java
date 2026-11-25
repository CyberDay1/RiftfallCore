package com.cyberday1.riftfall.home;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The home owns its restoration progress, even if its controller is removed and rebuilt. */
public final class ShatteredHomeProgress extends SavedData {
    public static final int RELICS_PER_PHASE = 20;
    public static final int MAX_PHASE = 4;
    public static final int INITIAL_DIAMETER = 20;
    public static final int DIAMETER_PER_SHARD = 10;
    public static final int SHARDS_PER_TILE = 5;
    private static final int MAX_SHARDS = 5_999_994;
    private int shards;
    private int layoutVersion = ShatteredHomeLayout.VERSION;
    private final List<ShatteredHomeTiles.GridPos> unlockedTiles = new ArrayList<>();
    private int tilesPlaced;
    private boolean brookBankRepaired;
    private final java.util.Set<String> repairedRoadChunks=new java.util.HashSet<>();
    private final java.util.Set<String> repairedForgeWallChunks=new java.util.HashSet<>();
    private final java.util.Set<String> repairedForgeDisplays=new java.util.HashSet<>();
    /** Story notes already placed or back-filled, keyed by NotePlacement#key; each is tried once. */
    private final java.util.Set<String> notesPlaced=new java.util.HashSet<>();
    /** Wilderness pool notes this home has already dropped, so none repeats. */
    private final List<String> wildNotes=new ArrayList<>();

    public static ShatteredHomeProgress of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(ShatteredHomeProgress::new,
                ShatteredHomeProgress::load), "riftfall_home_progress");
    }
    public int shards() { return shards; }
    public int layoutVersion() { return layoutVersion; }
    public int tilesUnlocked() { return 1 + shards / SHARDS_PER_TILE; }
    public List<ShatteredHomeTiles.GridPos> unlockedTiles() { return Collections.unmodifiableList(unlockedTiles); }
    public int tilesPlaced() { return tilesPlaced; }
    public boolean brookBankRepaired() { return brookBankRepaired; }
    public void markBrookBankRepaired() { brookBankRepaired=true;setDirty(); }
    public boolean roadChunkRepaired(String key) { return repairedRoadChunks.contains(key); }
    public void markRoadChunkRepaired(String key) { if (repairedRoadChunks.add(key)) setDirty(); }
    public boolean forgeWallChunkRepaired(String key) { return repairedForgeWallChunks.contains(key); }
    public void markForgeWallChunkRepaired(String key) { if (repairedForgeWallChunks.add(key)) setDirty(); }
    public boolean forgeDisplayRepaired(String key) { return repairedForgeDisplays.contains(key); }
    public void markForgeDisplayRepaired(String key) { if (repairedForgeDisplays.add(key)) setDirty(); }
    public boolean notePlaced(String key) { return notesPlaced.contains(key); }
    public void markNotePlaced(String key) { if (notesPlaced.add(key)) setDirty(); }
    public List<String> wildNotes() { return Collections.unmodifiableList(wildNotes); }
    public void recordWildNote(String id) { if (!wildNotes.contains(id)) { wildNotes.add(id); setDirty(); } }
    public static int phase(int count) { return Math.min(MAX_PHASE, Math.max(0, count) / RELICS_PER_PHASE); }
    public int phase() { return phase(shards); }
    /** Border encompasses all unlocked tiles; falls back to legacy formula if no tiles tracked. */
    public double diameter() {
        if (unlockedTiles.isEmpty()) return INITIAL_DIAMETER + (double) shards * DIAMETER_PER_SHARD;
        return ShatteredHomeTiles.borderDiameter(unlockedTiles);
    }
    public boolean canInsertShard() { return shards < MAX_SHARDS; }
    public boolean insertShard() {
        if (shards >= MAX_SHARDS) return false;
        shards++;
        setDirty();
        return true;
    }
    /** Raises the shard count to {@code count}, never lowers it, and places no terrain. */
    public void raiseShards(int count) {
        int migrated = Math.min(MAX_SHARDS, Math.max(shards, count));
        if (migrated != shards) {
            shards = migrated;
            setDirty();
        }
    }
    public void applyBorder(ServerLevel level) {
        // Vanilla only supports squares. Exact collision and rendering use the tile perimeter.
        if (level.getWorldBorder().getSize() != 59_999_968.0) {
            level.getWorldBorder().setCenter(0, 0);
            level.getWorldBorder().setSize(59_999_968.0);
        }
        HomeBoundary.update(level, unlockedTiles);
    }

    /** Commit only after the structure was successfully placed. */
    public void recordPlacedTile(ShatteredHomeTiles.GridPos tile) {
        if (!unlockedTiles.contains(tile)) unlockedTiles.add(tile);
        tilesPlaced = unlockedTiles.size();
        setDirty();
    }
    public static ShatteredHomeProgress load(CompoundTag tag, HolderLookup.Provider registries) {
        ShatteredHomeProgress progress = new ShatteredHomeProgress();
        progress.layoutVersion = tag.contains("layout_version", Tag.TAG_INT)
                ? tag.getInt("layout_version") : ShatteredHomeLayout.VERSION;
        progress.shards = Math.max(0, Math.min(MAX_SHARDS, tag.getInt("shards")));
        progress.tilesPlaced = Math.max(0, tag.getInt("tiles_placed"));
        progress.brookBankRepaired = tag.getBoolean("brook_bank_repaired_v1");
        var roadChunks=tag.getList("road_chunks_repaired_v1",Tag.TAG_STRING);
        for (int i=0;i<roadChunks.size();i++) progress.repairedRoadChunks.add(roadChunks.getString(i));
        var forgeChunks=tag.getList("forge_wall_chunks_repaired_v1",Tag.TAG_STRING);
        for (int i=0;i<forgeChunks.size();i++) progress.repairedForgeWallChunks.add(forgeChunks.getString(i));
        var forgeDisplays=tag.getList("forge_displays_repaired_v1",Tag.TAG_STRING);
        for (int i=0;i<forgeDisplays.size();i++) progress.repairedForgeDisplays.add(forgeDisplays.getString(i));
        var notes=tag.getList("notes_placed_v1",Tag.TAG_STRING);
        for (int i=0;i<notes.size();i++) progress.notesPlaced.add(notes.getString(i));
        var wild=tag.getList("wild_notes",Tag.TAG_STRING);
        for (int i=0;i<wild.size();i++) progress.wildNotes.add(wild.getString(i));
        ListTag tileList = tag.getList("unlocked_tiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < tileList.size(); i++) {
            CompoundTag t = tileList.getCompound(i);
            progress.unlockedTiles.add(new ShatteredHomeTiles.GridPos(t.getInt("x"), t.getInt("z")));
        }
        return progress;
    }
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("shards", shards);
        tag.putInt("layout_version", layoutVersion);
        tag.putInt("tiles_placed", tilesPlaced);
        tag.putBoolean("brook_bank_repaired_v1",brookBankRepaired);
        var roadChunks=new ListTag();
        repairedRoadChunks.stream().sorted().forEach(key -> roadChunks.add(net.minecraft.nbt.StringTag.valueOf(key)));
        tag.put("road_chunks_repaired_v1",roadChunks);
        var forgeChunks=new ListTag();
        repairedForgeWallChunks.stream().sorted().forEach(key -> forgeChunks.add(net.minecraft.nbt.StringTag.valueOf(key)));
        tag.put("forge_wall_chunks_repaired_v1",forgeChunks);
        var forgeDisplays=new ListTag();
        repairedForgeDisplays.stream().sorted().forEach(key -> forgeDisplays.add(net.minecraft.nbt.StringTag.valueOf(key)));
        tag.put("forge_displays_repaired_v1",forgeDisplays);
        var notes=new ListTag();
        notesPlaced.stream().sorted().forEach(key -> notes.add(net.minecraft.nbt.StringTag.valueOf(key)));
        tag.put("notes_placed_v1",notes);
        var wild=new ListTag();
        wildNotes.forEach(id -> wild.add(net.minecraft.nbt.StringTag.valueOf(id)));
        tag.put("wild_notes",wild);
        ListTag tileList = new ListTag();
        for (ShatteredHomeTiles.GridPos g : unlockedTiles) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", g.x());
            t.putInt("z", g.z());
            tileList.add(t);
        }
        tag.put("unlocked_tiles", tileList);
        return tag;
    }
}
