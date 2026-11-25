package com.cyberday1.riftfall.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets a probe's stand-in take a hit. A fresh {@code ServerPlayer} refuses damage for its first 60
 * ticks and a fake player never ticks, so without this no lethal hit ever lands on one.
 */
@Mixin(ServerPlayer.class)
public interface AccessorServerPlayer {

    @Accessor("spawnInvulnerableTime")
    void riftfall$setSpawnInvulnerableTime(int ticks);
}
