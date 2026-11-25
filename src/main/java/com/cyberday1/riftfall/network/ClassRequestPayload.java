package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What the class screen asks of the server: a fresh view, or one class by id. Carries nothing else;
 * the server checks the id against its own list and the player's own book.
 */
public record ClassRequestPayload(Kind kind, String id) implements CustomPacketPayload {

    public static final Type<ClassRequestPayload> TYPE = new Type<>(RiftfallCore.id("class_request"));
    private static final int TEXT = 64;

    public enum Kind {
        VIEW, CHOOSE
    }

    public static ClassRequestPayload view() {
        return new ClassRequestPayload(Kind.VIEW, "");
    }

    public static ClassRequestPayload choose(String id) {
        return new ClassRequestPayload(Kind.CHOOSE, id);
    }

    public static final StreamCodec<ByteBuf, ClassRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(ClassRequestPayload::kind, Kind::ordinal), ClassRequestPayload::kind,
            ByteBufCodecs.stringUtf8(TEXT), ClassRequestPayload::id,
            ClassRequestPayload::new);

    /** Refused, not wrapped, like {@link SkillRequestPayload}'s kind. */
    static Kind kind(int ordinal) {
        if (ordinal < 0 || ordinal >= Kind.values().length) {
            throw new DecoderException("unknown class request kind " + ordinal);
        }
        return Kind.values()[ordinal];
    }

    @Override
    public Type<ClassRequestPayload> type() {
        return TYPE;
    }
}
