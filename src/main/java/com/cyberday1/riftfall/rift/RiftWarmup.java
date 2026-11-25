package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.worldgen.level.RiftChunkGenerator;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The dungeon a lit frame already has, and the part of it kept ready for somebody to walk into.
 *
 * <p>
 * A rift used to be built by the first body to touch the portal, which put a whole dungeon's worth of
 * planning and a chunk generation on the tick a player was mid-stride. Lighting the frame is what
 * triggers it now, so the walk from the forge to the frame is the loading screen.
 *
 * <p>
 * The arrival neighbourhood is held loaded and specifically not ticking: no block entity ticks, so the
 * one-shot spawners stay loaded and unfired, and no entity ticks, so whatever is already there stays
 * where it was put. A player's own tickets start all of it on arrival.
 *
 * <p>
 * ⚠ NeoForge's {@code TicketController.forceChunk(..., ticking = false)} is <em>not</em> this. It is a
 * region ticket at radius 2, which is level 31, which is {@code ENTITY_TICKING}; the flag only stops
 * {@code ServerChunkCache} random-ticking and natural-spawning a chunk with nobody near it. Block
 * entities and entities in such a chunk tick exactly as they always did.
 *
 * <p>
 * Three ways a lit frame stops having a dungeon: somebody walks in and {@link #claim} hands it to the
 * run, the frame is broken and {@link #abandon} takes it down, or nobody comes and {@link #IDLE_TICKS}
 * closes it. The last is affordable only because {@link RiftPortals} saves the seed, which makes the
 * level a cache of a dungeon the frame can describe rather than the only copy.
 */
public final class RiftWarmup {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Holds a chunk at {@code FULL} and no higher. Region, because only a region ticket is levelled. */
    private static final TicketType<ChunkPos> WARM =
            TicketType.create("riftfall:warm", Comparator.comparingLong(ChunkPos::toLong));

    /**
     * The radius is the whole of the trick. A region ticket's level is {@code 33 - radius}, and 33 is
     * the boundary between a chunk that is loaded and one that is ticking, so zero is the only radius
     * that means what this class is for.
     */
    private static final int WARM_RADIUS = 0;

    /**
     * How long the dungeon behind a frame nobody has walked into is kept, in ticks: five minutes. Long
     * enough for the walk back from the forge, short enough that a frame lit and forgotten is not a
     * live level for the session. The frame stays lit and enterable either way.
     */
    private static final int IDLE_TICKS = 6000;

    /** What each open rift is holding, so that it can be handed back. Chunk positions, packed. */
    private static final Map<ResourceKey<Level>, Set<Long>> HELD = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> PREPARING = new HashMap<>();
    private static final Set<ResourceKey<Level>> READY = new java.util.HashSet<>();

    /** The dungeon each lit frame already has, for the frames nobody has walked into yet. */
    private static final Map<RiftRun.Frame, Waiting> PENDING = new HashMap<>();

    /** One lit frame's rift, and how long it has been standing there unvisited. */
    private static final class Waiting {

        private final ResourceKey<Level> rift;
        private int idle;

        private Waiting(ResourceKey<Level> rift) {
            this.rift = rift;
        }
    }

    private RiftWarmup() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RiftWarmup::expire);
        NeoForge.EVENT_BUS.addListener(RiftWarmup::forgetAll);
    }

    /**
     * Builds the dungeon a frame has just been lit onto.
     *
     * <p>
     * Relighting a frame that was never walked into drops the old one first. Two levels behind one
     * frame is the leak this whole class is one mistake away from, and a fresh key is entitled to a
     * fresh dungeon.
     */
    public static void open(ServerLevel level, BlockPos corner, int tier, long seed) {
        open(level, corner, tier, seed, RiftAttunement.NONE);
    }

    /** As above, for a frame lit with an attuned key. */
    public static void open(ServerLevel level, BlockPos corner, int tier, long seed, RiftAttunement attunement) {
        MinecraftServer server = level.getServer();
        RiftRun.Frame frame = new RiftRun.Frame(level.dimension(), corner);
        drop(server, PENDING.remove(frame));
        PENDING.put(frame, new Waiting(RiftLevels.create(server, seed, tier, attunement).dimension()));
    }

    /**
     * The dungeon behind a frame somebody is walking into, made now if it is not already there.
     *
     * <p>
     * Missing is ordinary rather than exceptional: the frame may have been lit before this server
     * started, or lit so long ago that {@link #IDLE_TICKS} let the level go. Either way the seed says
     * which dungeon it is, so what gets built is the dungeon the key bought and not a second one.
     */
    @org.jetbrains.annotations.Nullable
    public static ServerLevel claim(ServerLevel level, BlockPos corner, int tier, long seed) {
        return claim(level, corner, tier, seed, RiftAttunement.NONE);
    }

    @org.jetbrains.annotations.Nullable
    public static ServerLevel claim(ServerLevel level, BlockPos corner, int tier, long seed, RiftAttunement attunement) {
        MinecraftServer server = level.getServer();
        RiftRun.Frame frame = new RiftRun.Frame(level.dimension(), corner);
        Waiting waiting = PENDING.get(frame);
        if (waiting != null && !RiftLevels.isClosing(waiting.rift)) {
            ServerLevel warm = server.getLevel(waiting.rift);
            if (warm != null) {
                if (!ready(warm)) return null;
                PENDING.remove(frame);
                return warm;
            }
        }
        open(level, corner, tier, seed, attunement);
        return null;
    }

    /** A ticket is a request, not proof that generation and entry-marker resolution finished. */
    public static boolean ready(ServerLevel rift) {
        return READY.contains(rift.dimension());
    }

    public static boolean pending(ServerLevel level, BlockPos corner) {
        Waiting waiting = PENDING.get(new RiftRun.Frame(level.dimension(), corner));
        return waiting != null && !RiftLevels.isClosing(waiting.rift)
                && level.getServer().getLevel(waiting.rift) != null;
    }

    /** Takes down the dungeon behind a frame that has been broken before anybody used it. */
    static void abandon(ServerLevel level, BlockPos corner) {
        drop(level.getServer(), PENDING.remove(new RiftRun.Frame(level.dimension(), corner)));
    }

    /**
     * Starts holding a rift's arrival neighbourhood open. Every rift gets this however it was made,
     * because {@code /riftfall rift open} wants a walkable entrance as much as a portal does.
     *
     * <p>
     * The plan is built on the background executor because the tick that reaches this is a player
     * placing a block, and building one grows a few hundred rooms. Adding a ticket generates nothing
     * inline; the chunk system takes it from there over the ticks that follow, which is the window
     * this feature is trying to spend.
     */
    static void warm(ServerLevel rift) {
        if (!(rift.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator)) {
            return;
        }
        MinecraftServer server = rift.getServer();
        ResourceKey<Level> key = rift.dimension();
        CompletableFuture.supplyAsync(() -> generator.entranceColumns(rift), Util.backgroundExecutor())
                .thenAcceptAsync(columns -> hold(server, key, columns), server)
                .exceptionally(error -> {
                    // Not fatal: an unwarmed rift is the rift this feature replaced, generated under
                    // the arriving player instead of ahead of them.
                    LOGGER.error("Could not warm {}", key.location(), error);
                    return null;
                });
    }

    /** Hands back every chunk a rift was holding. Called before the level is closed, never after. */
    static void release(ServerLevel rift) {
        READY.remove(rift.dimension());
        PREPARING.remove(rift.dimension());
        Set<Long> held = HELD.remove(rift.dimension());
        if (held == null) {
            return;
        }
        for (long packed : held) {
            ChunkPos column = new ChunkPos(packed);
            rift.getChunkSource().removeRegionTicket(WARM, column, WARM_RADIUS, column);
        }
    }

    /** How many chunks a rift is holding open, for {@code /riftfall rift list}. */
    public static int held(ServerLevel rift) {
        Set<Long> held = HELD.get(rift.dimension());
        return held == null ? 0 : held.size();
    }

    private static void hold(MinecraftServer server, ResourceKey<Level> key, Set<ChunkPos> columns) {
        ServerLevel rift = server.getLevel(key);
        if (rift == null || RiftLevels.isClosing(key)) {
            // The rift came down while its plan was being built. Nothing to hold and nobody to tell.
            return;
        }
        Set<Long> held = HELD.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
        for (ChunkPos column : columns) {
            if (held.add(column.toLong())) {
                rift.getChunkSource().addRegionTicket(WARM, column, WARM_RADIUS, column);
            }
        }
        LOGGER.info("Holding {} chunks around the way into {}", held.size(), key.location());
        PREPARING.put(key, System.nanoTime());
    }

    /** Poll only already-loaded chunks. Minecraft's getChunkFuture blocks when called on the server thread. */
    private static void finishReady(MinecraftServer server) {
        var iterator = PREPARING.entrySet().iterator();
        while (iterator.hasNext()) {
            var pending = iterator.next();
            var key = pending.getKey();
            ServerLevel rift = server.getLevel(key);
            Set<Long> held = HELD.get(key);
            if (rift == null || RiftLevels.isClosing(key) || held == null) { iterator.remove(); continue; }
            boolean loaded = held.stream().allMatch(p -> rift.getChunkSource()
                    .getChunkNow(ChunkPos.getX(p), ChunkPos.getZ(p)) != null);
            if (!loaded) continue;
            RiftChunkGenerator generator = (RiftChunkGenerator) rift.getChunkSource().getGenerator();
            long entryStart = System.nanoTime();
            generator.entry(rift);
            READY.add(key);
            if (Boolean.getBoolean("riftfall.hashRift")) LOGGER.info("Rift blocks {}: {}", key.location(), blockHash(rift, held));
            if (System.getProperty("riftfall.dumpRift") != null) dumpBlocks(rift, held);
            LOGGER.info("Rift entrance ready in {} ms: {}", (System.nanoTime() - pending.getValue()) / 1_000_000, key.location());
            LOGGER.info("Rift load {}: {} held, entry {} ms, {}", key.location(), held.size(),
                    (System.nanoTime() - entryStart) / 1_000_000, generator.loadStats().report());
            iterator.remove();
        }
    }

    /** Every block state and block entity position of the held columns, in a fixed order. Rig check only. */
    private static String blockHash(ServerLevel rift, Set<Long> held) {
        long hash = 1125899906842597L;
        long solid = 0;
        for (long packed : held.stream().sorted().toList()) {
            var chunk = rift.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
            var sections = chunk.getSections();
            for (int s = 0; s < sections.length; s++) {
                if (sections[s].hasOnlyAir()) continue;
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    int id = net.minecraft.world.level.block.Block.getId(sections[s].getBlockState(x, y, z));
                    if (id == 0) continue;
                    solid++;
                    hash = 31 * hash + ((((packed * 31 + s) * 16 + y) * 16 + z) * 16 + x);
                    hash = 31 * hash + id;
                }
            }
            for (BlockPos pos : chunk.getBlockEntitiesPos().stream().sorted().toList()) {
                hash = 31 * hash + pos.asLong();
            }
        }
        return Long.toHexString(hash) + " over " + solid + " blocks in " + held.size() + " columns";
    }

    /** Writes the held columns' block states under the folder named by riftfall.dumpRift. Rig check only. */
    private static void dumpBlocks(ServerLevel rift, Set<Long> held) {
        java.nio.file.Path file = java.nio.file.Path.of(System.getProperty("riftfall.dumpRift"),
                RiftLevels.seed(rift.dimension()) + "-" + System.nanoTime() + ".bin");
        Map<Integer, String> names = new java.util.TreeMap<>();
        try (var out = new java.io.DataOutputStream(new java.io.BufferedOutputStream(
                java.nio.file.Files.newOutputStream(file)))) {
            out.writeInt(held.size());
            for (long packed : held.stream().sorted().toList()) {
                var chunk = rift.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
                var sections = chunk.getSections();
                out.writeInt(ChunkPos.getX(packed));
                out.writeInt(ChunkPos.getZ(packed));
                out.writeInt(chunk.getMinSection());
                out.writeInt(sections.length);
                for (var section : sections) {
                    for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        var state = section.getBlockState(x, y, z);
                        int id = net.minecraft.world.level.block.Block.getId(state);
                        names.putIfAbsent(id, state.toString());
                        out.writeInt(id);
                    }
                }
            }
            out.writeInt(names.size());
            for (var name : names.entrySet()) {
                out.writeInt(name.getKey());
                out.writeUTF(name.getValue());
            }
        } catch (java.io.IOException error) {
            LOGGER.error("Could not dump {}", file, error);
        }
    }

    /**
     * Lets go of the dungeons behind frames nobody has come to. Counted on the tick rather than off
     * the game time so that a rift is measured from when it was lit, not from when the world began.
     */
    private static void expire(ServerTickEvent.Post event) {
        finishReady(event.getServer());
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<RiftRun.Frame, Waiting>> entries = PENDING.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<RiftRun.Frame, Waiting> entry = entries.next();
            Waiting waiting = entry.getValue();
            if (++waiting.idle > IDLE_TICKS) {
                entries.remove();
                LOGGER.info("Letting {} go: its frame at {} has been lit for {} ticks and nobody has walked in",
                        waiting.rift.location(), entry.getKey().corner().toShortString(), IDLE_TICKS);
                drop(server, waiting);
            }
        }
    }

    private static void drop(MinecraftServer server, Waiting waiting) {
        if (waiting == null) {
            return;
        }
        ServerLevel rift = server.getLevel(waiting.rift);
        if (rift != null) {
            RiftLevels.close(rift);
        }
    }

    /**
     * Drops both maps when the server stops, for the reason {@link RiftRuns#forgetAll} gives: they are
     * static, and a singleplayer client starts a new server per world. The levels themselves are taken
     * down by {@link RiftLevels#closeAll}, which sweeps every rift in the server's map and so covers
     * the ones no run ever claimed. After the stop rather than at it, so {@code closeAll} can still
     * hand back the tickets of a rift it keeps.
     */
    private static void forgetAll(ServerStoppedEvent event) {
        PENDING.clear();
        HELD.clear();
        READY.clear();
        PREPARING.clear();
    }
}
