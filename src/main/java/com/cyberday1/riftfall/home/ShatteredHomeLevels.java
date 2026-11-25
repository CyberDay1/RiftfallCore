package com.cyberday1.riftfall.home;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.mixin.AccessorMinecraftServer;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Per-player persistent void dimensions with a 100x100 sanctuary. Unlike rifts these survive
 * server restarts: the level is saved to disk and reloaded on next boot.
 */
public final class ShatteredHomeLevels {

    private static final Logger LOGGER = LogUtils.getLogger();

    static final String PREFIX = "shattered_home/";

    private static final ChunkProgressListener UNWATCHED = new ChunkProgressListener() {
        @Override public void updateSpawnPos(ChunkPos pos) {}
        @Override public void onStatusChange(ChunkPos pos, @Nullable ChunkStatus status) {}
        @Override public void start() {}
        @Override public void stop() {}
    };

    private ShatteredHomeLevels() {}

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ShatteredHomeLevels::restoreHomes);
        HomeSkyLight.register();
    }

    /** Recreate saved dimension keys before player data resolves their previous dimension. */
    private static void restoreHomes(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        Path parent = DimensionType.getStorageFolder(key(new UUID(0, 0)),
                server.getWorldPath(LevelResource.ROOT)).getParent();
        if (!Files.isDirectory(parent)) return;
        try (var homes = Files.list(parent)) {
            for (Path folder : homes.filter(Files::isDirectory).toList()) {
                UUID owner;
                try { owner = UUID.fromString(folder.getFileName().toString()); }
                catch (IllegalArgumentException ignored) { continue; }
                getOrCreate(server, owner);
            }
        } catch (java.io.IOException exception) {
            LOGGER.error("Could not enumerate saved shattered homes", exception);
        }
    }

    public static ResourceKey<Level> key(UUID playerUuid) {
        return ResourceKey.create(Registries.DIMENSION, RiftfallCore.id(PREFIX + playerUuid));
    }

    public static boolean isShatteredHome(ResourceKey<Level> key) {
        return key.location().getNamespace().equals(RiftfallCore.MODID)
                && key.location().getPath().startsWith(PREFIX);
    }

    /** A supported, unobstructed position on the sanctuary's central approach. */
    public static Vec3 arrival(ServerLevel level) {
        return ShatteredHomeSpawn.find(level);
    }

    public static ServerLevel getOrCreate(MinecraftServer server, UUID playerUuid) {
        ResourceKey<Level> key = key(playerUuid);
        ServerLevel existing = server.getLevel(key);
        if (existing != null) {
            return existing;
        }
        return create(server, key);
    }

    @SuppressWarnings("deprecation")
    private static ServerLevel create(MinecraftServer server, ResourceKey<Level> key) {
        LevelStem stem = ShatteredHomeDimensions.stem(server.registryAccess());
        WorldData worldData = server.getWorldData();

        ServerLevel level = new ServerLevel(
                server,
                Util.backgroundExecutor(),
                ((AccessorMinecraftServer) server).getStorageSource(),
                new HomeLevelData(worldData, worldData.overworldData()),
                key,
                stem,
                UNWATCHED,
                false,
                0L,
                List.of(),
                false,
                new RandomSequences(server.getWorldData().worldGenOptions().seed()));

        // Persistent: do NOT set noSave. The level saves with the server like any other dimension.
        server.forgeGetWorldMap().put(key, level);
        server.markWorldsDirty();

        // Place sanctuary tile at origin as the starting ground.
        ShatteredHomeProgress progress = ShatteredHomeProgress.of(level);
        if (progress.unlockedTiles().isEmpty()) {
            var starter = new ShatteredHomeTiles.GridPos(0, 0);
            if (!ShatteredHomeTiles.placeTile(level, starter, ShatteredHomeLayout.starter().template())) {
                throw new IllegalStateException("Could not place shattered home sanctuary");
            }
            progress.recordPlacedTile(starter);
        }
        HomeSurfaceRepairs.repairBrook(level,progress);
        progress.applyBorder(level);

        // Eagerly generate chunks around origin so the player has ground on arrival.
        int chunkRadius = (ShatteredHomeTiles.TILE_SIZE >> 4) + 1;
        for (int cx = -chunkRadius; cx < chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz < chunkRadius; cz++) {
                level.getChunk(cx, cz, ChunkStatus.FULL, true);
            }
        }

        LOGGER.info("Created shattered home dimension {}", key.location());
        return level;
    }

    /** Whether a saved dimension folder exists on disk for this player's home. */
    static boolean existsOnDisk(MinecraftServer server, UUID playerUuid) {
        Path folder = DimensionType.getStorageFolder(key(playerUuid),
                server.getWorldPath(LevelResource.ROOT).normalize());
        return Files.isDirectory(folder);
    }

    /**
     * Sends the current world border state to a player who has just entered or logged into this
     * dimension. Vanilla only syncs the border on initial login to the overworld; per-player
     * dimensions created at runtime never get an automatic sync, so the client renders no barrier.
     */
    public static void syncBorderTo(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!isShatteredHome(level.dimension())) {
            return;
        }
        player.connection.send(new ClientboundInitializeBorderPacket(level.getWorldBorder()));
        HomeBoundary.sendTo(player);
    }
}
