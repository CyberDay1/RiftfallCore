package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The character sheet's Rift Log link. The server sends the run history as {@code /riftfall recap} does. */
public record RiftRecapRequestPayload() implements CustomPacketPayload {
    public static final Type<RiftRecapRequestPayload> TYPE = new Type<>(RiftfallCore.id("rift_recap_request"));
    public static final RiftRecapRequestPayload INSTANCE = new RiftRecapRequestPayload();
    public static final StreamCodec<ByteBuf, RiftRecapRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<RiftRecapRequestPayload> type() {
        return TYPE;
    }
}
