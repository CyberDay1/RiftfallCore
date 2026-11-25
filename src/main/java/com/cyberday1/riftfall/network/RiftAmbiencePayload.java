package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Tells a client which rift theme it is standing in, or that it is standing in none.
 *
 * <p>
 * A packet rather than a lookup because the theme registry is deliberately unsynced -- see
 * {@code RiftfallRegistries} -- and because a client cannot work rift-ness out for itself anyway:
 * the shipped {@code riftfall:rift} dimension is a rift and its id does not say so, and the answer
 * lives in the chunk generator, which is server-side.
 *
 * <p>
 * Only the theme's id travels, not the theme, so this stays a name the client looks a sound up by.
 */
public record RiftAmbiencePayload(Optional<ResourceLocation> theme) implements CustomPacketPayload {

    public static final Type<RiftAmbiencePayload> TYPE = new Type<>(RiftfallCore.id("rift_ambience"));

    public static final StreamCodec<ByteBuf, RiftAmbiencePayload> STREAM_CODEC =
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC)
                    .map(RiftAmbiencePayload::new, RiftAmbiencePayload::theme);

    /** Absent means "not in a rift", which is also what a client is told on the way out. */
    public static final RiftAmbiencePayload NONE = new RiftAmbiencePayload(Optional.empty());

    public static RiftAmbiencePayload of(ResourceLocation theme) {
        return new RiftAmbiencePayload(Optional.of(theme));
    }

    @Override
    public Type<RiftAmbiencePayload> type() {
        return TYPE;
    }
}
