package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A fixed grapple anchor; the other end is rendered at the interpolated moving caster. */
public record TravelCuePayload(int entity, int kind, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<TravelCuePayload> TYPE = new Type<>(RiftfallCore.id("travel_cue"));
    public static final StreamCodec<ByteBuf, TravelCuePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TravelCuePayload::entity, ByteBufCodecs.INT, TravelCuePayload::kind,
            ByteBufCodecs.DOUBLE, TravelCuePayload::x, ByteBufCodecs.DOUBLE, TravelCuePayload::y,
            ByteBufCodecs.DOUBLE, TravelCuePayload::z, TravelCuePayload::new);
    @Override public Type<TravelCuePayload> type() { return TYPE; }
}
