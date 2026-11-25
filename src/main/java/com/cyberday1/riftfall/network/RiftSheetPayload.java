package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Tells a client how much XP it has earned, and what level cap that XP is being read against.
 *
 * <p>
 * Two stored numbers and no derived ones. Level, tier, the bar's fill and "XP to the next level"
 * are all computed on the client from {@code RiftXp} and {@code RiftTier}, which are pure and ship
 * on both sides &mdash; sending five derived numbers instead of two stored ones would be five
 * chances for the sheet to disagree with the server about arithmetic both of them can do.
 *
 * <p>
 * ⭐ The cap travels rather than being a client constant, because the level a total buys is a
 * function of both and the cap has not been ruled on yet. Sending it keeps the server the single
 * source of that answer on the day it is.
 */
public record RiftSheetPayload(long xp, int cap) implements CustomPacketPayload {

    public static final Type<RiftSheetPayload> TYPE = new Type<>(RiftfallCore.id("rift_sheet"));

    public static final StreamCodec<ByteBuf, RiftSheetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, RiftSheetPayload::xp,
            ByteBufCodecs.VAR_INT, RiftSheetPayload::cap,
            RiftSheetPayload::new);

    @Override
    public Type<RiftSheetPayload> type() {
        return TYPE;
    }
}
