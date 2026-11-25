package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Optional;
import java.util.UUID;

/**
 * The Party bookmark asking for something: a verb and, for the verbs that name somebody, who. The server
 * decides everything else; a target the verb does not take is dropped, and one it needs but lacks is refused.
 */
public record SocialRequestPayload(Kind kind, Optional<UUID> target) implements CustomPacketPayload {

    public static final Type<SocialRequestPayload> TYPE = new Type<>(RiftfallCore.id("social_request"));

    public enum Kind {
        /** The page opened: send the view now and keep it live until CLOSE. */
        VIEW(false),
        CLOSE(false),
        PARTY_CREATE(false),
        PARTY_INVITE(true),
        PARTY_ACCEPT(true),
        PARTY_DECLINE(true),
        PARTY_LEAVE(false),
        PARTY_REMOVE(true),
        PARTY_DISBAND(false),
        TEAM_CREATE(false),
        TEAM_INVITE(true),
        TEAM_JOIN(true),
        TEAM_LEAVE(false),
        TEAM_KICK(true),
        TEAM_DISBAND(false),
        TEAM_ALLY(true),
        TEAM_UNALLY(true),
        HOME_ALLY(true),
        HOME_UNALLY(true);

        private final boolean targeted;

        Kind(boolean targeted) {
            this.targeted = targeted;
        }

        public boolean targeted() {
            return targeted;
        }
    }

    public SocialRequestPayload {
        target = kind.targeted() ? target : Optional.empty();
    }

    public static SocialRequestPayload of(Kind kind) {
        return new SocialRequestPayload(kind, Optional.empty());
    }

    public static SocialRequestPayload of(Kind kind, UUID target) {
        return new SocialRequestPayload(kind, Optional.of(target));
    }

    static Kind kind(int ordinal) {
        if (ordinal < 0 || ordinal >= Kind.values().length) {
            throw new DecoderException("unknown social request kind " + ordinal);
        }
        return Kind.values()[ordinal];
    }

    public static final StreamCodec<ByteBuf, SocialRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(SocialRequestPayload::kind, Kind::ordinal), SocialRequestPayload::kind,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), SocialRequestPayload::target,
            SocialRequestPayload::new);

    @Override
    public Type<SocialRequestPayload> type() {
        return TYPE;
    }
}
