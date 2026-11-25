package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.lore.LostNoteItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** A lost note looks like what it is (book, letter, card, map, carved stick, clock): {@code riftfall:look} is the Look ordinal. */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class LostNoteModels {
    public static final ResourceLocation LOOK = RiftfallCore.id("look");

    private LostNoteModels() {}

    @SubscribeEvent
    private static void register(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(RiftfallItems.LOST_NOTE.get(), LOOK,
                (stack, level, entity, seed) -> LostNoteItem.note(stack).map(n -> n.look().ordinal()).orElse(0)));
    }
}
