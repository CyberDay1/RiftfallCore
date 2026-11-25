package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.progression.RiftTier;
import com.cyberday1.riftfall.progression.RiftXp;
import com.cyberday1.riftfall.rift.RiftKeyItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * One look per rift key tier, picked by the base model's overrides on {@code riftfall:tier}.
 *
 * <p>
 * The property is the raw tier, not a 0..1 fraction: in 1.21.1 {@code ItemProperties.register} takes an
 * unclamped function. Overrides run 2..22 ascending, and Minecraft takes the last one that matches, so a
 * key above the top look keeps the top look and an untiered key (read as tier 1) keeps the base model.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class RiftKeyModels {

    /** How many looks ship: one per tier the level gate lets a player forge. */
    public static final int LOOKS = RiftTier.of(RiftXp.CEILING);

    public static final ResourceLocation TIER = RiftfallCore.id("tier");

    private RiftKeyModels() {
    }

    /** The look a key of this tier wears: its own, clamped to the ones that exist. */
    public static int look(int tier) {
        return Math.max(1, Math.min(LOOKS, tier));
    }

    @SubscribeEvent
    private static void register(FMLClientSetupEvent event) {
        // The property map is a plain HashMap, so off the parallel setup thread.
        event.enqueueWork(() -> ItemProperties.register(RiftfallItems.RIFT_KEY.get(), TIER,
                (stack, level, entity, seed) -> RiftKeyItem.tier(stack)));
    }
}
