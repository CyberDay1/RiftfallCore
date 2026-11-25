package com.cyberday1.riftfall.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.concurrent.CompletableFuture;

/** Validates the sender when queued work executes, so removal and repeated packets cannot bypass it. */
public final class LegendaryHelmetTeleport {
    private static final ResourceLocation HELMET = ResourceLocation.parse("legendary_monsters:annihilator_helmet");

    private LegendaryHelmetTeleport() { }

    public static CompletableFuture<Void> enqueue(IPayloadContext context, Runnable action) {
        return context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) return;
            var helmet = player.getItemBySlot(EquipmentSlot.HEAD);
            if (helmet.isEmpty() || !HELMET.equals(BuiltInRegistries.ITEM.getKey(helmet.getItem()))) return;
            if (player.getCooldowns().isOnCooldown(helmet.getItem())) return;
            action.run();
        });
    }
}
