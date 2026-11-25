package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.shield.ShieldPool;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The player's own shield pool as last written: the amount and the game time it was drained to.
 * The client drains it forward itself, so only a grant, a hit or a reset is ever sent.
 */
public record RiftShieldPayload(double amount, long at) implements CustomPacketPayload {

    public static final Type<RiftShieldPayload> TYPE = new Type<>(RiftfallCore.id("shield"));

    public static final StreamCodec<ByteBuf, RiftShieldPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, RiftShieldPayload::amount,
            ByteBufCodecs.VAR_LONG, RiftShieldPayload::at,
            RiftShieldPayload::new);

    public static RiftShieldPayload of(ShieldPool pool) {
        return new RiftShieldPayload(pool.amount(), pool.updatedAt());
    }

    public ShieldPool pool() {
        return new ShieldPool(Math.max(0.0, amount), at);
    }

    @Override
    public Type<RiftShieldPayload> type() {
        return TYPE;
    }
}
