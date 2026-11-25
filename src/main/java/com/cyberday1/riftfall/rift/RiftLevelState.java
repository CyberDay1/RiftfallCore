package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What one rift has learned about itself that its blocks no longer say, kept in the rift's own
 * {@code data/} folder so it lives and dies with the level (G9).
 *
 * <p>
 * Two kinds of fact. The markers an author placed are <em>consumed</em> when they are first read
 * (the entry and the four boss-room markers), so a rift rebuilt after a restart would scan an empty
 * room and lose its arena, its gate and its way home. And three per-level flags that used to live in
 * static sets keyed by the level object: the boss has been spawned, the gate has shut, and the room
 * had no gate to shut.
 */
public final class RiftLevelState extends SavedData {

    private static final String FILE = RiftfallCore.MODID + "_rift";

    @Nullable
    private BlockPos entry;
    private boolean bossResolved;
    @Nullable
    private BlockPos bossPad;
    @Nullable
    private BlockPos bossEntry;
    @Nullable
    private BlockPos bossExit;
    private List<BlockPos> bossGate = List.of();
    private boolean bossSpawned;
    private boolean gateShut;
    private boolean gateless;
    private boolean bossDefeated;
    /** Roster members who died here during the boss fight; each still gets a boss roll. */
    private final Set<UUID> fallen = new LinkedHashSet<>();
    private int layoutVersion = com.cyberday1.riftfall.worldgen.layout.RiftScale.CURRENT_VERSION;

    RiftLevelState() {
        setDirty(); // Persist the sizing choice even if nobody reaches an entry marker before saving.
    }

    public int layoutVersion() { return layoutVersion; }

    /** This rift's state, made if it has none. Server thread only. */
    public static RiftLevelState of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RiftLevelState::new, RiftLevelState::load), FILE);
    }

    /** This rift's state if it has ever written any, without making an empty one. */
    @Nullable
    public static RiftLevelState peek(ServerLevel level) {
        return level.getDataStorage().get(new SavedData.Factory<>(RiftLevelState::new, RiftLevelState::load), FILE);
    }

    @Nullable
    public BlockPos entry() {
        return entry;
    }

    public void entry(BlockPos at) {
        entry = at.immutable();
        setDirty();
    }

    /** Whether the boss room's markers have been walked, which they can be only once. */
    public boolean bossResolved() {
        return bossResolved;
    }

    public Optional<BlockPos> bossPad() {
        return Optional.ofNullable(bossPad);
    }

    public Optional<BlockPos> bossEntry() {
        return Optional.ofNullable(bossEntry);
    }

    public Optional<BlockPos> bossExit() {
        return Optional.ofNullable(bossExit);
    }

    public List<BlockPos> bossGate() {
        return bossGate;
    }

    public void bossMarkers(Optional<BlockPos> pad, Optional<BlockPos> entry, Optional<BlockPos> exit,
            List<BlockPos> gate) {
        bossPad = pad.orElse(null);
        bossEntry = entry.orElse(null);
        bossExit = exit.orElse(null);
        bossGate = List.copyOf(gate);
        bossResolved = true;
        setDirty();
    }

    public boolean bossSpawned() {
        return bossSpawned;
    }

    public void bossSpawned(boolean spawned) {
        if (bossSpawned != spawned) {
            bossSpawned = spawned;
            setDirty();
        }
    }

    public boolean gateShut() {
        return gateShut;
    }

    /** The rift's boss has died (not despawned): the boss finder stays off from then on. */
    public boolean bossDefeated() {
        return bossDefeated;
    }

    public void bossDefeated(boolean defeated) {
        if (bossDefeated != defeated) {
            bossDefeated = defeated;
            setDirty();
        }
    }

    /** Who died here during the boss fight, in the order they fell. */
    public List<UUID> fallen() {
        return List.copyOf(fallen);
    }

    public void fell(UUID player) {
        if (fallen.add(player)) {
            setDirty();
        }
    }

    public boolean gateless() {
        return gateless;
    }

    public void gateShut(boolean gateless) {
        this.gateShut = true;
        this.gateless = gateless;
        setDirty();
    }

    static RiftLevelState load(CompoundTag tag, HolderLookup.Provider registries) {
        RiftLevelState state = new RiftLevelState();
        // A save from before the tag was written predates every later layout version.
        state.layoutVersion = tag.contains("layoutVersion") ? tag.getInt("layoutVersion") : 1;
        state.entry = NbtUtils.readBlockPos(tag, "entry").orElse(null);
        state.bossResolved = tag.getBoolean("bossResolved");
        state.bossPad = NbtUtils.readBlockPos(tag, "bossPad").orElse(null);
        state.bossEntry = NbtUtils.readBlockPos(tag, "bossEntry").orElse(null);
        state.bossExit = NbtUtils.readBlockPos(tag, "bossExit").orElse(null);
        List<BlockPos> gate = new ArrayList<>();
        ListTag list = tag.getList("bossGate", Tag.TAG_INT_ARRAY);
        for (int index = 0; index < list.size(); index++) {
            int[] xyz = list.getIntArray(index);
            if (xyz.length == 3) {
                gate.add(new BlockPos(xyz[0], xyz[1], xyz[2]));
            }
        }
        state.bossGate = List.copyOf(gate);
        state.bossSpawned = tag.getBoolean("bossSpawned");
        state.gateShut = tag.getBoolean("gateShut");
        state.gateless = tag.getBoolean("gateless");
        state.bossDefeated = tag.getBoolean("bossDefeated");
        for (Tag id : tag.getList("fallen", Tag.TAG_INT_ARRAY)) {
            state.fallen.add(NbtUtils.loadUUID(id));
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("layoutVersion", layoutVersion);
        if (entry != null) {
            tag.put("entry", NbtUtils.writeBlockPos(entry));
        }
        tag.putBoolean("bossResolved", bossResolved);
        if (bossPad != null) {
            tag.put("bossPad", NbtUtils.writeBlockPos(bossPad));
        }
        if (bossEntry != null) {
            tag.put("bossEntry", NbtUtils.writeBlockPos(bossEntry));
        }
        if (bossExit != null) {
            tag.put("bossExit", NbtUtils.writeBlockPos(bossExit));
        }
        ListTag gate = new ListTag();
        for (BlockPos pos : bossGate) {
            gate.add(NbtUtils.writeBlockPos(pos));
        }
        tag.put("bossGate", gate);
        tag.putBoolean("bossSpawned", bossSpawned);
        tag.putBoolean("gateShut", gateShut);
        tag.putBoolean("gateless", gateless);
        tag.putBoolean("bossDefeated", bossDefeated);
        ListTag fell = new ListTag();
        for (UUID id : fallen) {
            fell.add(NbtUtils.createUUID(id));
        }
        tag.put("fallen", fell);
        return tag;
    }
}
