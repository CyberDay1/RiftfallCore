package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The landing's Requests, Orders and Path tabs for one resident, decided on the server: the home's board, this
 * player's orders there, and what a path choice or respec would answer. An empty string stands for "none".
 */
public record ResidentBoardPayload(int role, List<Request> requests, int rerollCost, int runsToRefresh, boolean member,
                                   List<Order> orders, String path, String pathState, String respecState, int respecShards,
                                   boolean owner, Station station) implements CustomPacketPayload {

    public static final Type<ResidentBoardPayload> TYPE = new Type<>(RiftfallCore.id("resident_board"));
    public static final ResidentBoardPayload EMPTY = new ResidentBoardPayload(-1, List.of(), 0, 0, false, List.of(), "", "", "", 0, false, Station.NONE);

    /** One posted request; {@code deliverable} is how many units this player could hand in now. */
    public record Request(String def, String kind, String theme, int target, int progress, int minRunTier, int tierOffset,
                          int minutes, int deliverable) {}

    /** One of this player's orders at this resident; {@code show} is what it makes or holds. */
    public record Order(UUID id, ItemStack show, int runsDone, int runs, int minutesDone, int minutes, boolean ready,
                        boolean cancellable) {}

    /** The station: levels paid, the highest with an overlay, the next level's price, and why it cannot be paid ("" if it can). */
    public record Station(int funded, int top, int slate, int shards, String state) {
        public static final Station NONE = new Station(0, 0, 0, 0, "");
    }

    public ResidentBoardPayload {
        requests = List.copyOf(requests);
        orders = List.copyOf(orders);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ResidentBoardPayload> STREAM_CODEC =
            StreamCodec.of(ResidentBoardPayload::write, ResidentBoardPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ResidentBoardPayload p) {
        buf.writeVarInt(p.role);
        buf.writeVarInt(p.requests.size());
        for (var r : p.requests) {
            buf.writeUtf(r.def); buf.writeUtf(r.kind); buf.writeUtf(r.theme);
            buf.writeVarInt(r.target); buf.writeVarInt(r.progress); buf.writeVarInt(r.minRunTier);
            buf.writeVarInt(r.tierOffset); buf.writeVarInt(r.minutes); buf.writeVarInt(r.deliverable);
        }
        buf.writeVarInt(p.rerollCost); buf.writeVarInt(p.runsToRefresh); buf.writeBoolean(p.member);
        buf.writeVarInt(p.orders.size());
        for (var o : p.orders) {
            buf.writeUUID(o.id);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, o.show);
            buf.writeVarInt(o.runsDone); buf.writeVarInt(o.runs); buf.writeVarInt(o.minutesDone); buf.writeVarInt(o.minutes);
            buf.writeBoolean(o.ready); buf.writeBoolean(o.cancellable);
        }
        buf.writeUtf(p.path); buf.writeUtf(p.pathState); buf.writeUtf(p.respecState);
        buf.writeVarInt(p.respecShards); buf.writeBoolean(p.owner);
        buf.writeVarInt(p.station.funded()); buf.writeVarInt(p.station.top()); buf.writeVarInt(p.station.slate());
        buf.writeVarInt(p.station.shards()); buf.writeUtf(p.station.state());
    }

    private static ResidentBoardPayload read(RegistryFriendlyByteBuf buf) {
        int role = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), 16);
        var requests = new ArrayList<Request>();
        for (int i = 0; i < n; i++)
            requests.add(new Request(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        int reroll = buf.readVarInt(), refresh = buf.readVarInt();
        boolean member = buf.readBoolean();
        int m = Math.min(buf.readVarInt(), 16);
        var orders = new ArrayList<Order>();
        for (int i = 0; i < m; i++)
            orders.add(new Order(buf.readUUID(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
        return new ResidentBoardPayload(role, requests, reroll, refresh, member, orders, buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readVarInt(), buf.readBoolean(),
                new Station(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf()));
    }

    /** A request definition's own line, if the pack ships one: {@code commission.<ns>.<path with dots>}. */
    public static String titleKey(String def) {
        var id = ResourceLocation.tryParse(def);
        return id == null ? "" : "commission." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    @Override public Type<ResidentBoardPayload> type() { return TYPE; }
}
