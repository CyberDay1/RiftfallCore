package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.mixin.AccessorMinecraftServer;
import com.cyberday1.riftfall.worldgen.barrel.BarrelRules;
import com.cyberday1.riftfall.worldgen.level.RiftChunkGenerator;
import com.cyberday1.riftfall.worldgen.level.RiftDimensions;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * One rift, one world, and the world only lasts as long as the rift does. Every entry gets a
 * {@link ServerLevel} of its own, built from the same {@link LevelStem} the shipped
 * {@code riftfall:rift} dimension uses but carrying its own seed and tier.
 *
 * <p>
 * Nothing is registered. The {@code LEVEL_STEM} registry is exactly what the server writes to
 * {@code level.dat}, so registering a rift would make a temporary world permanent and would resurrect
 * at the next start every rift a crash stopped us deleting. A rift goes straight into the server's
 * live level map, which is not persisted.
 *
 * <p>
 * ⭐ The one exception is a rift a run still holds when the server stops (G9, ruled 2026-10-11:
 * a paused run survives a restart). Its folder is kept, and {@link RiftRuns} brings it back under
 * the same key with {@link #restore}. It is still never registered: the run record is what makes
 * it come back, so a rift nobody's run names is still swept at boot.
 *
 * <p>
 * The client is told nothing and needs nothing.
 */
public final class RiftLevels {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The id prefix, and therefore the folder: a rift lives at {@code riftfall:rifts/<uuid>}, on disk
     * at {@code dimensions/riftfall/rifts/<uuid>}.
     *
     * <p>
     * Plural, and deliberately not {@code rift/}: the shipped fixed dimension {@code riftfall:rift}
     * owns {@code dimensions/riftfall/rift}, and {@link #sweep} deletes this folder wholesale. Nesting
     * the temporary worlds inside the permanent one would put that sweep one careless line from
     * deleting a dimension it must not touch.
     */
    private static final String PREFIX = "rifts/";

    /**
     * Nobody is watching a rift load. The boot listener exists so the console and the loading screen
     * can show overworld spawn-chunk progress; a rift is generated around a player already in world.
     */
    private static final ChunkProgressListener UNWATCHED = new ChunkProgressListener() {
        @Override
        public void updateSpawnPos(ChunkPos pos) {
        }

        @Override
        public void onStatusChange(ChunkPos pos, @Nullable ChunkStatus status) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    };

    /**
     * Rifts whose players are gone and whose levels come down once their chunk work has drained ({@link RiftDrain}).
     * A key stays here from {@link #close} until the level is gone, so nobody is sent into it meanwhile.
     */
    private static final Set<ResourceKey<Level>> CLOSING = new HashSet<>();

    /** How far each closing rift has got with draining. */
    private static final Map<ResourceKey<Level>, RiftDrain> DRAINING = new HashMap<>();

    /**
     * Rifts no run holds at a stop. Vanilla's stop drains, saves and closes them with every other level, and their
     * folders go once it has ({@link #forgetAll}).
     */
    private static final Set<ResourceKey<Level>> DOOMED = new HashSet<>();

    // Synchronized, these four: the chunk generator reads them from worldgen workers. Not
    // ConcurrentHashMap: it throws on a null key, which is what a mocked level in a test asks with.
    /**
     * The difficulty each open rift was opened at. Beside the level rather than in it, because a
     * rift's tier is a fact about the key that lit the frame rather than about the terrain, and a rift
     * is never reloaded from disk. Emptied by {@link #tearDown} so it cannot outlive its rift.
     */
    private static final Map<ResourceKey<Level>, Integer> TIERS = Collections.synchronizedMap(new HashMap<>());

    /**
     * How generous each open rift is, taken from the config the moment it was made. Beside the level
     * for the reason its tier is, and taken once for a reason of its own: a rift generates over the
     * minutes a party spends walking through it, and a setting edited halfway would otherwise make
     * the far half of a dungeon richer than the near half. Emptied by {@link #tearDown}.
     */
    private static final Map<ResourceKey<Level>, BarrelRules> BARRELS = Collections.synchronizedMap(new HashMap<>());

    /** The seed each open rift was built from, which is what rebuilds it after a restart. */
    private static final Map<ResourceKey<Level>, Long> SEEDS = Collections.synchronizedMap(new HashMap<>());

    /** What the key that opened each rift was attuned to, for the boss pad and the run record. */
    private static final Map<ResourceKey<Level>, RiftAttunement> ATTUNEMENTS = Collections.synchronizedMap(new HashMap<>());

    private RiftLevels() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RiftLevels::tearDownClosing);
        NeoForge.EVENT_BUS.addListener(RiftLevels::closeAll);
        NeoForge.EVENT_BUS.addListener(RiftLevels::forgetAll);
    }

    /**
     * Whether a level key names a rift instance rather than a world someone lives in. Only an instance
     * is a run, and only an instance is deleted: {@link #openRifts} and {@link #close} must never see
     * the shipped {@code riftfall:rift}. For "does this level play as a rift", ask {@link #isRiftWorld}.
     */
    public static boolean isRift(ResourceKey<Level> key) {
        return key.location().getNamespace().equals(RiftfallCore.MODID)
                && key.location().getPath().startsWith(PREFIX);
    }

    /**
     * Whether a level runs on Riftfall's rift generator: an instance, the shipped {@code riftfall:rift},
     * or a datapack dimension built on it. Server-side only; the client cannot see the generator.
     */
    public static boolean isRiftWorld(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof RiftChunkGenerator;
    }

    /**
     * Whether a rift is already on its way down. Between {@link #close} and the teardown that follows (the drain
     * can take a few seconds), the level is still in the server's map and still answers {@code getLevel}, so
     * anything that would send somebody into it must ask this too.
     */
    public static boolean isClosing(ResourceKey<Level> key) {
        return CLOSING.contains(key);
    }

    /** Every rift currently open, in no particular order. */
    public static List<ServerLevel> openRifts(MinecraftServer server) {
        List<ServerLevel> rifts = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            if (isRift(level.dimension())) {
                rifts.add(level);
            }
        }
        return rifts;
    }

    /**
     * Makes a rift and brings it up, ready to be walked into. The arguments mirror the non-overworld
     * branch of {@code MinecraftServer#createLevels}, departing in three ways that are the point of
     * the class: the stem carries {@code seed} so this dungeon is this dungeon, it carries
     * {@code tier} so a harder key buys a bigger dungeon and not only a harder one, and the random
     * sequences are the rift's own so loot drawn here does not advance a sequence another rift reads.
     */
    // forgeGetWorldMap/markWorldsDirty are deprecated as Forge-internal. Suppressed knowingly: adding
    // a level after boot is the one legitimate use of them and there is no other way to do it. The
    // warning is right that the map has rules; tearDown() keeps them.
    public static ServerLevel create(MinecraftServer server, long seed, int tier) {
        return create(server, seed, tier, RiftAttunement.NONE);
    }

    /** As above, for a key a Cartographer attuned. */
    public static ServerLevel create(MinecraftServer server, long seed, int tier, RiftAttunement attunement) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                RiftfallCore.id(PREFIX + UUID.randomUUID()));
        ServerLevel level = open(server, key, seed, tier, BarrelRules.fromConfig(), attunement);
        LOGGER.info("Opened rift {} at tier {} with seed {}{}", key.location(), tier, seed,
                attunement.none() ? "" : " attuned to " + attunement.themes() + attunement.boss().map(b -> " boss " + b).orElse(""));
        return level;
    }

    /**
     * Brings back a rift a run still held when the server last stopped, under its old key so its
     * folder's chunks load and so a player saved standing in it lands back in it. The seed and tier
     * rebuild the same plan; the barrel rules are the ones it was opened under, not today's config.
     */
    static ServerLevel restore(MinecraftServer server, ResourceKey<Level> key, long seed, int tier,
            BarrelRules barrels, RiftAttunement attunement) {
        ServerLevel level = open(server, key, seed, tier, barrels, attunement);
        LOGGER.info("Restored rift {} at tier {} with seed {}", key.location(), tier, seed);
        return level;
    }

    @SuppressWarnings("deprecation")
    private static ServerLevel open(MinecraftServer server, ResourceKey<Level> key, long seed, int tier,
            BarrelRules barrels, RiftAttunement attunement) {
        LevelStem stem = RiftDimensions.stem(server.registryAccess(), seed, tier, attunement.themes().stream()
                .map(theme -> ResourceKey.create(com.cyberday1.riftfall.init.RiftfallRegistries.RIFT_THEMES, theme)).toList());
        WorldData worldData = server.getWorldData();

        ServerLevel level = new ServerLevel(
                server,
                Util.backgroundExecutor(),
                ((AccessorMinecraftServer) server).getStorageSource(),
                new DerivedLevelData(worldData, worldData.overworldData()),
                key,
                stem,
                UNWATCHED,
                false, // isDebug
                0L,    // biomeZoomSeed — unused: the biome source is fixed
                List.of(), // no custom spawners; nothing wanders into a rift
                false, // tickTime
                new RandomSequences(seed));

        server.forgeGetWorldMap().put(key, level);
        server.markWorldsDirty();
        TIERS.put(key, tier);
        BARRELS.put(key, barrels);
        SEEDS.put(key, seed);
        ATTUNEMENTS.put(key, attunement);
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
        RiftWarmup.warm(level);
        return level;
    }

    /** Whether this class built a level under this key and it is still open. */
    static boolean knows(ResourceKey<Level> key) {
        return SEEDS.containsKey(key);
    }

    /** The seed a rift was built from, or zero for a level this class did not build. */
    static long seed(ResourceKey<Level> key) {
        return SEEDS.getOrDefault(key, 0L);
    }

    /** As above, for a live level; names a shard's world (ShardOrigin). */
    public static long seed(ServerLevel level) {
        return seed(level.dimension());
    }

    /** What a rift's key was attuned to, or {@link RiftAttunement#NONE}. */
    public static RiftAttunement attunement(ResourceKey<Level> key) {
        return ATTUNEMENTS.getOrDefault(key, RiftAttunement.NONE);
    }

    /** The tier a rift was opened at, by key, for the run record. */
    static int tier(ResourceKey<Level> key) {
        return TIERS.getOrDefault(key, RiftKeyItem.DEFAULT_TIER);
    }

    /** The barrel rules a rift was opened under, by key, for the run record. */
    static BarrelRules barrelRules(ResourceKey<Level> key) {
        BarrelRules rules = BARRELS.get(key);
        return rules != null ? rules : BarrelRules.fromConfig();
    }

    /** The tier a rift was opened at, or {@link RiftKeyItem#DEFAULT_TIER} for anything that is not one. */
    public static int tier(ServerLevel level) {
        return TIERS.getOrDefault(level.dimension(), RiftKeyItem.DEFAULT_TIER);
    }

    /**
     * The barrel rules a rift was opened under. The fallback reads the config, which is what a level
     * that came up some other way should get; there is no case in which a rift ought to have none.
     */
    public static BarrelRules barrelRules(ServerLevel level) {
        BarrelRules rules = BARRELS.get(level.dimension());
        return rules != null ? rules : BarrelRules.fromConfig();
    }

    /**
     * Where to put somebody arriving in a rift. One answer for both ways in, so the portal and
     * {@code /riftfall rift open} cannot drift apart -- and they had, the command arriving in the root
     * room's bottom corner. A rift running on any other generator has no rooms to aim at and gets the
     * origin.
     *
     * <p>
     * A few blocks off {@link #wayOut}, and a different few each time: the way out is a tear standing
     * in the middle of the room, and arriving on top of it would send the player straight back.
     */
    public static Vec3 arrival(ServerLevel rift) {
        return rift.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator
                ? generator.arrival(rift).getBottomCenter()
                : BlockPos.ZERO.getBottomCenter();
    }

    /**
     * Where the way out of a rift stands: the middle of the root room, on the floor. Fixed for the
     * life of the level, unlike {@link #arrival}, because the tear is written once.
     */
    public static BlockPos wayOut(ServerLevel rift) {
        return rift.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator
                ? generator.entry(rift)
                : BlockPos.ZERO;
    }

    /**
     * Sends everyone in a rift home now, and schedules the level itself for demolition once its chunk
     * work has drained.
     *
     * <p>
     * The split is load-bearing. Callers are inside a world tick, and the server ticks a snapshot array
     * of its levels taken at the top of the tick, so closing a level here would leave the rest of that
     * tick running against a shut chunk source. Eviction cannot wait, though: a player left standing in
     * a level that is about to come down is the one state this must never produce. And the demolition
     * cannot hurry: see {@link RiftDrain} for the park a level closed mid-generation leaves behind.
     */
    public static void close(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (!isRift(level.dimension()) || !CLOSING.add(level.dimension())) {
            return;
        }
        evict(server, level);
    }

    private static void evict(MinecraftServer server, ServerLevel level) {
        // Copied first: changeDimension removes the player from this list as it goes.
        for (ServerPlayer player : List.copyOf(level.players())) {
            try {
                RiftEvacuation.returnPlayer(server, player);
            } catch (IllegalStateException unavailable) {
                LOGGER.warn("Cannot return {} from closing rift {}; keeping the level open",
                        player.getUUID(), level.dimension().location(), unavailable);
            }
        }
    }

    /**
     * Each tick, every closing rift lets go of its tickets again and is looked at: still chunk work, it waits; quiet
     * for {@link RiftDrain#QUIET_TICKS} ticks, it comes down. Nothing here blocks on worldgen.
     */
    private static void tearDownClosing(ServerTickEvent.Post event) {
        if (CLOSING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        // A copy, so that a rift closed by something this teardown sets off -- an unload listener, say --
        // is picked up on the next tick rather than while the set is being walked.
        for (ResourceKey<Level> key : List.copyOf(CLOSING)) {
            ServerLevel level = server.getLevel(key);
            if (level == null) {
                CLOSING.remove(key);
                DRAINING.remove(key);
                continue;
            }
            RiftDrain drain = DRAINING.computeIfAbsent(key, ignored -> {
                RiftWarmup.release(level);
                return new RiftDrain();
            });
            // Whoever got in since the close goes home too, and whatever ticket anything added since goes. The same
            // two moves vanilla's stop makes on every level before it waits for the chunk work to drain.
            evict(server, level);
            // Another mod may refuse travel. Never delete a dimension with a player still in it.
            if (!level.players().isEmpty()) continue;
            level.getChunkSource().removeTicketsOnClosing();
            if (drain.observe(level.getChunkSource().chunkMap.hasWork()) == RiftDrain.Step.TEAR_DOWN) {
                CLOSING.remove(key);
                DRAINING.remove(key);
                tearDown(server, level);
            } else if (drain.overdue()) {
                LOGGER.warn("Rift {} has been draining for {} ticks and still has chunk work; it stays open until it"
                        + " drains or the server stops", key.location(), drain.waited());
            }
        }
    }

    /**
     * Unregister, close, delete -- in that order, and the order is not negotiable. Only ever called on a level
     * with no chunk work left ({@link #tearDownClosing}), so the close has nothing to generate and nothing to save.
     *
     * <p>
     * The level has to leave the server's map before it is closed, or the next tick ticks a shut
     * chunk source. It has to be closed before the directory is deleted, because the chunk and entity
     * storage hold open file handles and Windows will not delete a file that is open. And
     * {@code noSave} goes up first so that nothing between here and the delete bothers writing to a
     * folder that is on its way out. The warm chunks are handed back at the top for the same reason
     * they are the first thing the level was given: a ticket outliving its level is a ticket nothing
     * can ever release.
     */
    @SuppressWarnings("deprecation") // See create(): the same two Forge-internal map methods.
    private static void tearDown(MinecraftServer server, ServerLevel level) {
        ResourceKey<Level> key = level.dimension();
        level.noSave = true;
        RiftWarmup.release(level);

        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
        server.forgeGetWorldMap().remove(key);
        server.markWorldsDirty();
        TIERS.remove(key);
        BARRELS.remove(key);
        SEEDS.remove(key);
        ATTUNEMENTS.remove(key);

        try {
            level.close();
        } catch (IOException e) {
            LOGGER.error("Failed to close rift {}; leaving its folder in place", key.location(), e);
            return;
        }

        deleteRecursively(folder(server, key));
        LOGGER.info("Closed rift {}", key.location());
    }

    /**
     * At a stop no rift is closed here. Every rift's warm tickets go (the stop waits for every level's chunk work to
     * drain, and a rift still generating its entrance would hold the shutdown for it), and then vanilla's stop drains,
     * saves and closes each one like any other level, running the server's queued tasks while it drains. A rift a live
     * run holds keeps its players and its folder, and the run record brings it back. One no run holds (or one already
     * closing) is emptied now, and its folder deleted once the server has stopped.
     *
     * <p>
     * ⛔ Closing a rift here, as this once did, is the stop half of the teardown freeze: the level shut while its generation was in flight, and the POI updates that generation had queued ran
     * later, in the stop's own drain loop, against shut storage, and parked the server thread for good.
     */
    private static void closeAll(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : openRifts(server)) {
            ResourceKey<Level> key = level.dimension();
            RiftWarmup.release(level);
            if (RiftDrain.keptAtStop(CLOSING.contains(key), RiftRuns.in(key) != null)) {
                continue;
            }
            evict(server, level);
            DOOMED.add(key);
        }
        CLOSING.clear();
        DRAINING.clear();
    }

    /**
     * Deletes the folders of the rifts the stop took down, once every level is closed and so holds no file open, then
     * drops the per-rift records; a singleplayer client reuses the JVM. A folder that will not go is the boot sweep's.
     */
    private static void forgetAll(ServerStoppedEvent event) {
        MinecraftServer server = event.getServer();
        for (ResourceKey<Level> key : DOOMED) {
            deleteRecursively(folder(server, key));
            LOGGER.info("Closed rift {} at the stop", key.location());
        }
        DOOMED.clear();
        TIERS.clear();
        BARRELS.clear();
        SEEDS.clear();
        ATTUNEMENTS.clear();
        CLOSING.clear();
        DRAINING.clear();
    }

    /**
     * Deletes every rift folder at startup that no saved run names, and answers how many went.
     *
     * <p>
     * Anything else under {@code dimensions/riftfall/rifts} is the remains of a rift that was open
     * when the process died, or one nobody's run held at the stop. Called by {@link RiftRuns} once it
     * has read the run records and before any rift is restored, so it cannot race a live level.
     */
    static int sweep(MinecraftServer server, Set<String> keep) {
        Path rifts = server.getWorldPath(LevelResource.ROOT)
                .normalize()
                .resolve("dimensions")
                .resolve(RiftfallCore.MODID)
                .resolve(PREFIX.substring(0, PREFIX.length() - 1));
        if (!Files.isDirectory(rifts)) {
            return 0;
        }
        int removed = 0;
        try (Stream<Path> folders = Files.list(rifts)) {
            for (Path folder : folders.toList()) {
                if (!keep.contains(folder.getFileName().toString())) {
                    deleteRecursively(folder);
                    removed++;
                }
            }
        } catch (IOException | UncheckedIOException e) {
            LOGGER.warn("Could not sweep {}", rifts, e);
        }
        if (removed > 0) {
            LOGGER.info("Removed {} rift folder(s) no saved run holds; kept {}", removed, keep.size());
        }
        return removed;
    }

    /** The folder name a rift key sweeps under, which is the part after {@code rifts/}. */
    static String folderName(ResourceKey<Level> key) {
        return key.location().getPath().substring(PREFIX.length());
    }

    private static Path folder(MinecraftServer server, ResourceKey<Level> key) {
        return DimensionType.getStorageFolder(key, server.getWorldPath(LevelResource.ROOT).normalize());
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            // Not fatal: a leftover folder costs disk and nothing else, and the next start sweeps
            // whatever is still here. Worth a line in the log so it does not accumulate unnoticed.
            LOGGER.warn("Could not delete {}", root, e);
        }
    }
}
