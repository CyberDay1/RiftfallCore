package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.home.ShatteredHomeTiles.GridPos;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public record HomeBoundaryPayload(ResourceLocation dimension, List<GridPos> tiles) implements CustomPacketPayload {
    public static final Type<HomeBoundaryPayload> TYPE = new Type<>(RiftfallCore.id("home_boundary"));
    private static final StreamCodec<ByteBuf, GridPos> TILE = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GridPos::x, ByteBufCodecs.VAR_INT, GridPos::z, GridPos::new);
    public static final StreamCodec<ByteBuf, HomeBoundaryPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, HomeBoundaryPayload::dimension,
            TILE.apply(ByteBufCodecs.list()), HomeBoundaryPayload::tiles, HomeBoundaryPayload::new);
    public HomeBoundaryPayload { tiles = List.copyOf(tiles); }
    @Override public Type<HomeBoundaryPayload> type() { return TYPE; }
}
