package com.cyberday1.riftfall.home;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Home tile templates for Riftfall's own placement, kept out of the structure manager's cache. A crafted tile is read
 * without its air entries and keeps them as a bit mask, so a stray block where the tile has air is still cleared.
 * Wilderness tiles stay dense (their processor writes into the air) and leave the manager's cache once placed.
 */
public final class HomeTileTemplates {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final FileToIdConverter FILES = new FileToIdConverter("structure", ".nbt");
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** One prefetched tile per home the preloader serves, with room to spare. */
    static final int MAX_PENDING = 8;
    private static final Map<Key, CompletableFuture<Prepared>> PENDING = new LinkedHashMap<>();

    private HomeTileTemplates() {}

    record Key(@Nullable ResourceKey<Level> level, ResourceLocation id) {}

    /**
     * A template ready to place. {@code air} is null when nothing was stripped; {@code managed} when it lives in the
     * structure manager's cache; {@code source} is the resource manager it was read from.
     */
    public record Prepared(ResourceLocation id, StructureTemplate template, @Nullable AirMask air, boolean managed,
                           @Nullable Object source) {}

    /** The authored air cells, in template-local coordinates. */
    public record AirMask(int sizeX, int sizeY, int sizeZ, BitSet cells) {
        int index(int x, int y, int z) { return (y * sizeZ + z) * sizeX + x; }
        public int count() { return cells.cardinality(); }
        void position(int index, BlockPos origin, BlockPos.MutableBlockPos into) {
            into.set(origin.getX() + index % sizeX, origin.getY() + index / (sizeX * sizeZ),
                    origin.getZ() + index / sizeX % sizeZ);
        }
    }

    /** The tag with its plain-air entries moved into a mask: entries before and after. */
    record Stripped(CompoundTag tag, AirMask air, int before, int after) {}

    /** Strips {@code tag} in place; null for a template this cannot handle (several palettes, odd size). */
    static @Nullable Stripped strip(CompoundTag tag) {
        if (tag.contains("palettes", Tag.TAG_LIST) || !tag.contains("palette", Tag.TAG_LIST)) return null;
        var size = tag.getList("size", Tag.TAG_INT);
        if (size.size() != 3) return null;
        int sx = size.getInt(0), sy = size.getInt(1), sz = size.getInt(2);
        if (sx <= 0 || sy <= 0 || sz <= 0 || (long) sx * sy * sz > Integer.MAX_VALUE) return null;
        var palette = tag.getList("palette", Tag.TAG_COMPOUND);
        var airStates = new BitSet(palette.size());
        for (int i = 0; i < palette.size(); i++) {
            var state = palette.getCompound(i);
            if ("minecraft:air".equals(state.getString("Name")) && !state.contains("Properties")) airStates.set(i);
        }
        var blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        var kept = new ListTag();
        var mask = new AirMask(sx, sy, sz, new BitSet(sx * sy * sz));
        for (int i = 0; i < blocks.size(); i++) {
            var block = blocks.getCompound(i);
            var pos = block.getList("pos", Tag.TAG_INT);
            int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
            boolean inside = pos.size() == 3 && x >= 0 && x < sx && y >= 0 && y < sy && z >= 0 && z < sz;
            if (inside && airStates.get(block.getInt("state")) && !block.contains("nbt")) mask.cells().set(mask.index(x, y, z));
            else kept.add(block);
        }
        tag.put("blocks", kept);
        return new Stripped(tag, mask, blocks.size(), kept.size());
    }

    /**
     * Reads a tile the way the manager would, minus its air. Wilderness tiles, a world's generated/ copy and a tile no
     * pack has as a file are left to the manager, dense; so is everything when there is no server to read from.
     */
    static @Nullable Prepared load(ServerLevel level, ResourceLocation id) throws IOException {
        var manager = level.getStructureManager();
        var server = level.getServer();
        var resources = server == null ? null : server.getResourceManager();
        if (resources == null || ShatteredHomeExpansion.wildernessTemplates().contains(id) || generated(manager, id))
            return managed(manager, id, resources);
        CompoundTag tag;
        try (var in = new BufferedInputStream(resources.open(FILES.idToFile(id)))) {
            tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        } catch (FileNotFoundException missing) {
            return managed(manager, id, resources);
        }
        var stripped = strip(tag);
        return new Prepared(id, manager.readStructure(tag), stripped == null ? null : stripped.air(), false, resources);
    }

    private static @Nullable Prepared managed(StructureTemplateManager manager, ResourceLocation id, @Nullable Object resources) {
        return manager.get(id).map(t -> new Prepared(id, t, null, true, resources)).orElse(null);
    }

    /** The manager reads a world's generated/ copy first; leave such a tile to it. */
    private static boolean generated(StructureTemplateManager manager, ResourceLocation id) {
        try {
            return Files.isRegularFile(manager.createAndValidatePathToGeneratedStructure(id, ".nbt"));
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    /** Starts reading {@code id} for {@code level}'s next unlock; a no-op if it is already pending. */
    public static void prefetch(ServerLevel level, ResourceLocation id, Executor worker) {
        var key = new Key(level.dimension(), id);
        synchronized (PENDING) {
            if (PENDING.containsKey(key)) return;
            if (PENDING.size() >= MAX_PENDING) PENDING.remove(PENDING.keySet().iterator().next());
            PENDING.put(key, CompletableFuture.supplyAsync(() -> {
                try { return load(level, id); } catch (IOException e) { throw new CompletionException(e); }
            }, worker));
        }
    }

    /** The prefetched template if it is still current, otherwise a fresh read. Empty when the tile is missing. */
    public static Optional<Prepared> take(ServerLevel level, ResourceLocation id) {
        CompletableFuture<Prepared> pending;
        synchronized (PENDING) { pending = PENDING.remove(new Key(level.dimension(), id)); }
        if (pending != null) {
            try {
                var ready = pending.join();
                var server = level.getServer();
                if (ready != null && ready.source() == (server == null ? null : server.getResourceManager())) return Optional.of(ready);
            } catch (CompletionException | java.util.concurrent.CancellationException failed) {
                LOGGER.warn("Prefetch of home tile {} failed; reading it now", id, failed.getCause());
            }
        }
        try {
            return Optional.ofNullable(load(level, id));
        } catch (IOException | RuntimeException failed) {
            LOGGER.error("Couldn't load home tile {}", id, failed);
            return Optional.empty();
        }
    }

    /** Drops a prefetched tile nobody will place (the player left, or the next unlock changed). */
    public static void forget(ServerLevel level, ResourceLocation id) {
        CompletableFuture<Prepared> pending;
        synchronized (PENDING) { pending = PENDING.remove(new Key(level.dimension(), id)); }
        if (pending != null && pending.isDone() && !pending.isCompletedExceptionally()) {
            var ready = pending.join();
            if (ready != null) release(level, ready);
        }
    }

    /** After placement: a dense template leaves the manager's cache (the next home reads it again). */
    public static void release(ServerLevel level, Prepared prepared) {
        if (prepared.managed()) level.getStructureManager().remove(prepared.id());
    }

    public static void clear() { synchronized (PENDING) { PENDING.clear(); } }

    /** What the air entries would do once the void is not empty: clear a stray block where the tile has air. */
    static int clearStrays(ServerLevel level, BlockPos origin, @Nullable AirMask air) {
        if (air == null) return 0;
        var at = new BlockPos.MutableBlockPos();
        int cleared = 0;
        for (int i = air.cells().nextSetBit(0); i >= 0; i = air.cells().nextSetBit(i + 1)) {
            air.position(i, origin, at);
            if (level.getBlockState(at).is(Blocks.AIR)) continue;
            BlockPos pos = at.immutable();
            if (TilePlacementGuard.write(() -> level.setBlock(pos, AIR, 2), () -> level.setBlock(pos, AIR, 2 | 16),
                    () -> "stray block at " + pos.toShortString())) cleared++;
        }
        return cleared;
    }
}
