package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The Recovered Notes page asking for a fresh NotesPayload. */
public record NotesRequestPayload() implements CustomPacketPayload {
    public static final Type<NotesRequestPayload> TYPE = new Type<>(RiftfallCore.id("notes_request"));
    public static final NotesRequestPayload INSTANCE = new NotesRequestPayload();
    public static final StreamCodec<ByteBuf, NotesRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override public Type<NotesRequestPayload> type() { return TYPE; }
}
