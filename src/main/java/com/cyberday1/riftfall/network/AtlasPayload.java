package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The Cartographer page's list, decided on the server: every registered theme in id order with this player's atlas
 * entry, then the bosses offered for boss attunement. Row positions are the page's selections.
 */
public record AtlasPayload(List<Theme> themes, List<Boss> bosses) implements CustomPacketPayload {
    public static final Type<AtlasPayload> TYPE = new Type<>(RiftfallCore.id("atlas"));
    public static final AtlasPayload EMPTY = new AtlasPayload(List.of(), List.of());

    public record Theme(String id, int visits, int clears, int best, int bossKills, boolean crystal) {}
    public record Boss(String theme, String boss, int kills) {}

    public AtlasPayload {
        themes = List.copyOf(themes);
        bosses = List.copyOf(bosses);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AtlasPayload> STREAM_CODEC = StreamCodec.of(AtlasPayload::write, AtlasPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, AtlasPayload p) {
        buf.writeVarInt(p.themes.size());
        for (var t : p.themes) {
            buf.writeUtf(t.id); buf.writeVarInt(t.visits); buf.writeVarInt(t.clears); buf.writeVarInt(t.best);
            buf.writeVarInt(t.bossKills); buf.writeBoolean(t.crystal);
        }
        buf.writeVarInt(p.bosses.size());
        for (var b : p.bosses) { buf.writeUtf(b.theme); buf.writeUtf(b.boss); buf.writeVarInt(b.kills); }
    }

    private static AtlasPayload read(RegistryFriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 64);
        var themes = new ArrayList<Theme>();
        for (int i = 0; i < n; i++)
            themes.add(new Theme(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        int m = Math.min(buf.readVarInt(), 64);
        var bosses = new ArrayList<Boss>();
        for (int i = 0; i < m; i++) bosses.add(new Boss(buf.readUtf(), buf.readUtf(), buf.readVarInt()));
        return new AtlasPayload(themes, bosses);
    }

    @Override public Type<AtlasPayload> type() { return TYPE; }
}
