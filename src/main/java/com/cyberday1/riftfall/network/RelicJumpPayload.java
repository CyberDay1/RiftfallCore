package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A relic movement the client already made: the server validates it ({@code RelicJump}, {@code RelicWallCling}). */
public record RelicJumpPayload(Move move) implements CustomPacketPayload {

    public enum Move {
        AIR_JUMP, WALL_KICK
    }

    public static final Type<RelicJumpPayload> TYPE = new Type<>(RiftfallCore.id("relic_jump"));

    public static final StreamCodec<ByteBuf, RelicJumpPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(RelicJumpPayload::decode, p -> p.move().ordinal());

    private static RelicJumpPayload decode(int ordinal) {
        Move[] moves = Move.values();
        if (ordinal < 0 || ordinal >= moves.length) throw new DecoderException("unknown relic move " + ordinal);
        return new RelicJumpPayload(moves[ordinal]);
    }

    @Override
    public Type<RelicJumpPayload> type() {
        return TYPE;
    }
}
