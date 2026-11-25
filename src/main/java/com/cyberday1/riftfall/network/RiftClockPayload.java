package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The run's clock: the game time it runs out at and the whole limit in ticks. The client counts down from its
 * own level clock, so the server sends on arrival and on a slow resync only. A zero limit is no clock.
 */
public record RiftClockPayload(long endsAt, int limitTicks) implements CustomPacketPayload {

    public static final Type<RiftClockPayload> TYPE = new Type<>(RiftfallCore.id("rift_clock"));
    public static final RiftClockPayload NONE = new RiftClockPayload(0L, 0);

    public static final StreamCodec<ByteBuf, RiftClockPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, RiftClockPayload::endsAt,
            ByteBufCodecs.VAR_INT, RiftClockPayload::limitTicks,
            RiftClockPayload::new);

    public boolean running() {
        return limitTicks > 0;
    }

    @Override
    public Type<RiftClockPayload> type() {
        return TYPE;
    }
}
