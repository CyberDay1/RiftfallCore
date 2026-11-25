package com.cyberday1.riftfall.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The one thing a rift needs from the server that the server does not offer.
 *
 * <p>
 * Building a {@link net.minecraft.server.level.ServerLevel} after boot means supplying the same
 * twelve arguments {@code MinecraftServer#createLevels} supplies, and eleven of them are reachable
 * without touching anything private:
 *
 * <ul>
 *   <li>the executor is unconditionally {@code Util.backgroundExecutor()};</li>
 *   <li>the level data is a {@code DerivedLevelData} over the public {@code getWorldData()};</li>
 *   <li>the chunk progress listener is a four-method interface we can no-op, because nothing is
 *       watching a rift load the way the boot screen watches the overworld;</li>
 *   <li>the rest are constants, or things we choose.</li>
 * </ul>
 *
 * <p>
 * {@code storageSource} is the exception. It is {@code protected} and it is not handed out anywhere:
 * {@code getWorldPath} returns a resolved {@link java.nio.file.Path} rather than the access object,
 * and {@code ChunkMap} does not retain the one it was given. Opening a second
 * {@link LevelStorageSource.LevelStorageAccess} over the same save is not a way around it either --
 * the first one holds {@code session.lock}, so a second would either fail or, worse, take a lock the
 * server believes it owns.
 *
 * <p>
 * An accessor rather than reflection, so that a rename in a future Minecraft is a mixin-apply
 * failure at startup with the field name in the log, instead of a player walking into a portal and
 * getting a stack trace.
 */
@Mixin(MinecraftServer.class)
public interface AccessorMinecraftServer {

    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess getStorageSource();
}
