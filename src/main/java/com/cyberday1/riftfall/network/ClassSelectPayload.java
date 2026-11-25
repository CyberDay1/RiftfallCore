package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the class screen shows, decided on the server: each class with its stat block, base
 * skills and ascendancies from the live table, and what choosing it would answer
 * ({@code SkillBook.Choose}, by name). {@code open} asks the client to show the screen, which the
 * server sets for a character with no class at login and after an admin reset. An empty string is "none".
 */
public record ClassSelectPayload(boolean open, String current, boolean inRift, String result, List<Option> options)
        implements CustomPacketPayload {

    public static final Type<ClassSelectPayload> TYPE = new Type<>(RiftfallCore.id("class_select"));
    public static final ClassSelectPayload EMPTY = new ClassSelectPayload(false, "", false, "", List.of());
    private static final int TEXT = 64;
    /** Generous: the catalogue has 13 classes, 10 base actives and 5 ascendancies each. */
    static final int MAX_LIST = 64;

    /** One class. {@code stats} are its primaries in {@code StatBlock.Primary} order. */
    public record Option(String id, List<Integer> stats, String status, List<String> actives, int passives,
                         List<String> ascendancies) {
        public Option {
            stats = List.copyOf(stats);
            actives = List.copyOf(actives);
            ascendancies = List.copyOf(ascendancies);
        }
    }

    public ClassSelectPayload {
        options = List.copyOf(options);
    }

    public boolean hasClass() {
        return !current.isEmpty();
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ClassSelectPayload> STREAM_CODEC =
            StreamCodec.of(ClassSelectPayload::write, ClassSelectPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ClassSelectPayload p) {
        buf.writeBoolean(p.open);
        buf.writeUtf(p.current, TEXT);
        buf.writeBoolean(p.inRift);
        buf.writeUtf(p.result, TEXT);
        buf.writeVarInt(p.options.size());
        for (Option o : p.options) {
            buf.writeUtf(o.id, TEXT);
            buf.writeVarInt(o.stats.size());
            o.stats.forEach(buf::writeVarInt);
            buf.writeUtf(o.status, TEXT);
            writeIds(buf, o.actives);
            buf.writeVarInt(o.passives);
            writeIds(buf, o.ascendancies);
        }
    }

    private static ClassSelectPayload read(RegistryFriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        String current = buf.readUtf(TEXT);
        boolean inRift = buf.readBoolean();
        String result = buf.readUtf(TEXT);
        int count = size(buf);
        List<Option> options = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(TEXT);
            int statCount = size(buf);
            List<Integer> stats = new ArrayList<>(statCount);
            for (int s = 0; s < statCount; s++) {
                stats.add(buf.readVarInt());
            }
            String status = buf.readUtf(TEXT);
            List<String> actives = readIds(buf);
            int passives = buf.readVarInt();
            options.add(new Option(id, stats, status, actives, passives, readIds(buf)));
        }
        return new ClassSelectPayload(open, current, inRift, result, options);
    }

    private static void writeIds(RegistryFriendlyByteBuf buf, List<String> ids) {
        buf.writeVarInt(ids.size());
        ids.forEach(id -> buf.writeUtf(id, TEXT));
    }

    private static List<String> readIds(RegistryFriendlyByteBuf buf) {
        int count = size(buf);
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(buf.readUtf(TEXT));
        }
        return ids;
    }

    /** A count off the wire is refused past {@link #MAX_LIST}, so a bad packet cannot ask for a huge list. */
    static int size(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_LIST) {
            throw new DecoderException("class view list of " + count);
        }
        return count;
    }

    @Override
    public Type<ClassSelectPayload> type() {
        return TYPE;
    }
}
