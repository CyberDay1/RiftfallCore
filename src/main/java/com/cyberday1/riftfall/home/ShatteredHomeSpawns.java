package com.cyberday1.riftfall.home;

import com.cyberday1.riftfall.RiftfallCore;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/** Personal homes have no mob spawning, even if a biome modifier adds spawn entries. */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class ShatteredHomeSpawns {
    private ShatteredHomeSpawns() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void placement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (ShatteredHomeLevels.isShatteredHome(event.getLevel().getLevel().dimension()))
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void finalizeSpawn(FinalizeSpawnEvent event) {
        if (ShatteredHomeLevels.isShatteredHome(event.getLevel().getLevel().dimension())) {
            // Canceling the event alone skips initialization but still allows the mob to spawn.
            event.setSpawnCancelled(true);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void breeding(BabyEntitySpawnEvent event) {
        var level = event.getParentA().level();
        if (!level.isClientSide && ShatteredHomeLevels.isShatteredHome(level.dimension()))
            event.setCanceled(true);
    }
}
