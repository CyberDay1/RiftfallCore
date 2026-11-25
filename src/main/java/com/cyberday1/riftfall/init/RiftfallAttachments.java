package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.shield.ShieldPool;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Entity data attachments for Riftfall Core. */
public final class RiftfallAttachments {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, RiftfallCore.MODID);

    /** The shield pool. Not copied on death, so a respawn starts with no shields; an empty pool is not saved. */
    public static final Supplier<AttachmentType<ShieldPool>> SHIELDS = ATTACHMENTS.register("shields",
            () -> AttachmentType.builder(() -> ShieldPool.EMPTY)
                    .serialize(new IAttachmentSerializer<CompoundTag, ShieldPool>() {
                        @Override
                        public ShieldPool read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                            return ShieldPool.load(tag);
                        }

                        @Override
                        public CompoundTag write(ShieldPool pool, HolderLookup.Provider provider) {
                            return pool.isEmpty() ? null : pool.save();
                        }
                    })
                    .build());

    private RiftfallAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
