package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** The Recovered Notes page: the notes this player has read and the tiles their home has restored. */
public record NotesPayload(List<String> found, List<String> known, boolean wild) implements CustomPacketPayload {
    public static final Type<NotesPayload> TYPE = new Type<>(RiftfallCore.id("notes"));
    public static final NotesPayload EMPTY = new NotesPayload(List.of(), List.of(), false);
    private static final int MAX = 512;

    public NotesPayload {
        found = List.copyOf(found);
        known = List.copyOf(known);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, NotesPayload> STREAM_CODEC = StreamCodec.of(NotesPayload::write, NotesPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, NotesPayload p) {
        buf.writeVarInt(p.found.size());
        p.found.forEach(id -> buf.writeUtf(id, 64));
        buf.writeVarInt(p.known.size());
        p.known.forEach(id -> buf.writeUtf(id, 64));
        buf.writeBoolean(p.wild);
    }

    private static NotesPayload read(RegistryFriendlyByteBuf buf) {
        var found = new ArrayList<String>();
        for (int i = 0, n = Math.min(buf.readVarInt(), MAX); i < n; i++) found.add(buf.readUtf(64));
        var known = new ArrayList<String>();
        for (int i = 0, n = Math.min(buf.readVarInt(), MAX); i < n; i++) known.add(buf.readUtf(64));
        return new NotesPayload(found, known, buf.readBoolean());
    }

    @Override public Type<NotesPayload> type() { return TYPE; }
}
