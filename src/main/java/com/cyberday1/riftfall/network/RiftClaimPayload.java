package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.rift.RiftClaimView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Held gear handed back, for the recap screen. {@code fresh} is sent just before a fresh recap and
 * starts its gear line over; otherwise the view adds to the line already showing.
 */
public record RiftClaimPayload(RiftClaimView view, boolean fresh) implements CustomPacketPayload {

    public static final Type<RiftClaimPayload> TYPE = new Type<>(RiftfallCore.id("rift_claim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RiftClaimPayload> STREAM_CODEC = StreamCodec.composite(
            RiftClaimView.STREAM_CODEC, RiftClaimPayload::view,
            ByteBufCodecs.BOOL, RiftClaimPayload::fresh,
            RiftClaimPayload::new);

    @Override
    public Type<RiftClaimPayload> type() {
        return TYPE;
    }
}
