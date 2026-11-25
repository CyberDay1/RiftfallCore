package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The player's own relic stacks (Berserker Rage, Soul Stacks, Momentum's steps), and the game tick Spore Burst and
 * Ground Pound are ready again, 0 when not cooling. Each is 0 unless its signature is active. Sent only when one
 * moves (a cooldown starts or ends), and on login and respawn; the client counts a cooldown down itself.
 */
public record RelicStacksPayload(int rage, int souls, int momentum, long sporeReady, long poundReady)
        implements CustomPacketPayload {

    public static final RelicStacksPayload NONE = new RelicStacksPayload(0, 0, 0, 0L, 0L);

    public static final Type<RelicStacksPayload> TYPE = new Type<>(RiftfallCore.id("relic_stacks"));

    public static final StreamCodec<ByteBuf, RelicStacksPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RelicStacksPayload::rage,
            ByteBufCodecs.VAR_INT, RelicStacksPayload::souls,
            ByteBufCodecs.VAR_INT, RelicStacksPayload::momentum,
            ByteBufCodecs.VAR_LONG, RelicStacksPayload::sporeReady,
            ByteBufCodecs.VAR_LONG, RelicStacksPayload::poundReady,
            RelicStacksPayload::new);

    /** A cooldown this payload shows has run out by {@code now}. */
    public boolean lapsed(long now) {
        return (sporeReady != 0L && now >= sporeReady) || (poundReady != 0L && now >= poundReady);
    }

    @Override
    public Type<RelicStacksPayload> type() {
        return TYPE;
    }
}
