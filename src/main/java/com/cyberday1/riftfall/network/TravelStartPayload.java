package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** One server-approved route. Modes: 0 ground, 1 leap, 2 grapple, 3 ground with precise arrival.
 * The owning client drives normal collision movement along it. */
public record TravelStartPayload(int mode, Vec3 start, Vec3 end) implements CustomPacketPayload {
    public static final Type<TravelStartPayload> TYPE = new Type<>(RiftfallCore.id("travel_start"));
    private static final StreamCodec<ByteBuf, Vec3> VECTOR = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, v -> v.x, ByteBufCodecs.DOUBLE, v -> v.y,
            ByteBufCodecs.DOUBLE, v -> v.z, Vec3::new);
    public static final StreamCodec<ByteBuf, TravelStartPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TravelStartPayload::mode, VECTOR, TravelStartPayload::start,
            VECTOR, TravelStartPayload::end, TravelStartPayload::new);
    @Override public Type<TravelStartPayload> type() { return TYPE; }
}
