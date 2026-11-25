package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.holycore.WorldCoreTarget;
import com.cyberday1.riftfall.network.CoreTouchPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Both hands empty, the crosshair on the orb and no block behind it in reach: vanilla sends nothing, so this sends the
 * aim. With an item, or a block behind the orb, WorldCoreAim answers the vanilla packets instead.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class WorldCoreTouch {
    private WorldCoreTouch() {}

    @SubscribeEvent
    public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        var client = Minecraft.getInstance();
        var player = client.player;
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || player == null || client.level == null
                || player.isSpectator() || !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()
                || (client.hitResult != null && client.hitResult.getType() != HitResult.Type.MISS)
                || WorldCoreTarget.find(client.level, player).isEmpty()) return;
        PacketDistributor.sendToServer(new CoreTouchPayload(player.getYRot(), player.getXRot()));
        event.setSwingHand(true);
        event.setCanceled(true);
    }
}
