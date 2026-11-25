package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The player's own Charge pool. {@code locked} is the server's answer to "would the held weapon be refused now":
 * the client's bar dims on it and its trigger is gated on it, since the client has no copy of a gun's cost.
 */
public record RangedChargePayload(double current, double max, boolean locked) implements CustomPacketPayload {

    public static final Type<RangedChargePayload> TYPE = new Type<>(RiftfallCore.id("ranged_charge"));

    public static final StreamCodec<ByteBuf, RangedChargePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, RangedChargePayload::current,
            ByteBufCodecs.DOUBLE, RangedChargePayload::max,
            ByteBufCodecs.BOOL, RangedChargePayload::locked,
            RangedChargePayload::new);

    @Override
    public Type<RangedChargePayload> type() {
        return TYPE;
    }
}
