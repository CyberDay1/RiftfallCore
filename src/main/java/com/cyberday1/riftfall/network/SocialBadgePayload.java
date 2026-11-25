package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Invitations waiting (expedition party plus home team), for the Party bookmark's badge. Sent to everyone. */
public record SocialBadgePayload(int partyInvites, int teamInvites) implements CustomPacketPayload {

    public static final Type<SocialBadgePayload> TYPE = new Type<>(RiftfallCore.id("social_badge"));
    public static final SocialBadgePayload NONE = new SocialBadgePayload(0, 0);

    public static final StreamCodec<ByteBuf, SocialBadgePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SocialBadgePayload::partyInvites,
            ByteBufCodecs.VAR_INT, SocialBadgePayload::teamInvites,
            SocialBadgePayload::new);

    public int total() {
        return Math.max(0, partyInvites) + Math.max(0, teamInvites);
    }

    @Override
    public Type<SocialBadgePayload> type() {
        return TYPE;
    }
}
