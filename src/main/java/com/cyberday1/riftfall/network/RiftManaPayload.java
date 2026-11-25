package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The player's own Riftfall mana bar (V2). {@code max} is {@code riftfall:max_mana} as the server
 * reads it, so the HUD never shows a ceiling nothing grants.
 */
public record RiftManaPayload(double current, double max) implements CustomPacketPayload {

    public static final Type<RiftManaPayload> TYPE = new Type<>(RiftfallCore.id("mana"));

    public static final StreamCodec<ByteBuf, RiftManaPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, RiftManaPayload::current,
            ByteBufCodecs.DOUBLE, RiftManaPayload::max,
            RiftManaPayload::new);

    @Override
    public Type<RiftManaPayload> type() {
        return TYPE;
    }
}
