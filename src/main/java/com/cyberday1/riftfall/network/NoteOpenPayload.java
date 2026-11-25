package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Tells the client to show a story note in the book view (a used note, or one read off a frame). */
public record NoteOpenPayload(String id) implements CustomPacketPayload {
    public static final Type<NoteOpenPayload> TYPE = new Type<>(RiftfallCore.id("note_open"));
    public static final StreamCodec<ByteBuf, NoteOpenPayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(64).map(NoteOpenPayload::new, NoteOpenPayload::id);

    @Override public Type<NoteOpenPayload> type() { return TYPE; }
}
