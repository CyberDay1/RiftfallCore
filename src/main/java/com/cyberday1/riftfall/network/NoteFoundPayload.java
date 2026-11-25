package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A player read a story note for the first time: the client shows the "Note recovered" toast. */
public record NoteFoundPayload(String id) implements CustomPacketPayload {
    public static final Type<NoteFoundPayload> TYPE = new Type<>(RiftfallCore.id("note_found"));
    public static final StreamCodec<ByteBuf, NoteFoundPayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(64).map(NoteFoundPayload::new, NoteFoundPayload::id);

    @Override public Type<NoteFoundPayload> type() { return TYPE; }
}
