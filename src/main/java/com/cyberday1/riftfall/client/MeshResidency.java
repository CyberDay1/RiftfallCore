package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.boss.UnseatedEntity;
import com.cyberday1.riftfall.boss.UnseatedPreviewEntity;
import com.cyberday1.riftfall.home.ShatteredHomeLevels;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Reads the big meshes before they are seen and lets them go once idle; see {@link LazyMesh}. */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class MeshResidency {
    /** How often the loaded entities are checked for an Unseated. */
    static final int SCAN_TICKS = 20;
    private static int ticks;

    private MeshResidency() {}

    static boolean wantsUnseated(Entity entity) {
        return entity instanceof UnseatedEntity || entity instanceof UnseatedPreviewEntity;
    }

    @SubscribeEvent
    static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && wantsUnseated(event.getEntity())) UnseatedPreviewRenderer.MESH.prefetch();
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level != null && ShatteredHomeLevels.isShatteredHome(level.dimension())) WorldCoreMesh.INSTANCE.prefetch();
        if (level != null && ++ticks % SCAN_TICKS == 0) {
            for (var entity : level.entitiesForRendering()) {
                if (wantsUnseated(entity)) { UnseatedPreviewRenderer.MESH.prefetch(); break; }
            }
        }
        WorldCoreMesh.INSTANCE.tick();
        UnseatedPreviewRenderer.MESH.tick();
    }
}
