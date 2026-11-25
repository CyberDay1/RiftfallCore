package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The recap screen's Claim the rest button. The server runs the same hand-over as the command. */
public record RiftClaimRequestPayload() implements CustomPacketPayload {
    public static final Type<RiftClaimRequestPayload> TYPE = new Type<>(RiftfallCore.id("rift_claim_request"));
    public static final RiftClaimRequestPayload INSTANCE = new RiftClaimRequestPayload();
    public static final StreamCodec<ByteBuf, RiftClaimRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<RiftClaimRequestPayload> type() {
        return TYPE;
    }
}
