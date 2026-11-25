package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.stat.StatBlock;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The sheet's Confirm: how many attribute points go on each primary, in {@link StatBlock.Primary} order.
 * Deltas only; the server reads the level, class and pool itself ({@code StatService}).
 */
public record StatAllocatePayload(List<Integer> deltas) implements CustomPacketPayload {

    public static final Type<StatAllocatePayload> TYPE = new Type<>(RiftfallCore.id("stat_allocate"));

    /** A primary's attribute range tops out here (RiftfallAttributes' PRIMARY_MAX), so no real delta is larger. */
    static final int MAX_DELTA = 1024;

    private static final int PRIMARIES = StatBlock.Primary.values().length;

    public StatAllocatePayload {
        if (deltas.size() != PRIMARIES) {
            throw new IllegalArgumentException(deltas.size() + " deltas for " + PRIMARIES + " primaries");
        }
        deltas = List.copyOf(deltas);
    }

    public static StatAllocatePayload of(Map<StatBlock.Primary, Integer> deltas) {
        List<Integer> out = new ArrayList<>();
        for (StatBlock.Primary primary : StatBlock.Primary.values()) {
            out.add(deltas.getOrDefault(primary, 0));
        }
        return new StatAllocatePayload(out);
    }

    public Map<StatBlock.Primary, Integer> asMap() {
        Map<StatBlock.Primary, Integer> out = new EnumMap<>(StatBlock.Primary.class);
        for (StatBlock.Primary primary : StatBlock.Primary.values()) {
            out.put(primary, deltas.get(primary.ordinal()));
        }
        return out;
    }

    public static final StreamCodec<ByteBuf, StatAllocatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> payload.deltas.forEach(delta -> writeVarInt(buf, delta)),
            buf -> {
                List<Integer> deltas = new ArrayList<>();
                for (int i = 0; i < PRIMARIES; i++) {
                    deltas.add(delta(readVarInt(buf)));
                }
                return new StatAllocatePayload(deltas);
            });

    /** Refused, not clamped: a negative or oversized delta is a malformed packet, as an unknown request kind is. */
    static int delta(int value) {
        if (value < 0 || value > MAX_DELTA) {
            throw new DecoderException("attribute delta out of range: " + value);
        }
        return value;
    }

    private static void writeVarInt(ByteBuf buf, int value) {
        ByteBufCodecs.VAR_INT.encode(buf, value);
    }

    private static int readVarInt(ByteBuf buf) {
        return ByteBufCodecs.VAR_INT.decode(buf);
    }

    @Override
    public Type<StatAllocatePayload> type() {
        return TYPE;
    }
}
