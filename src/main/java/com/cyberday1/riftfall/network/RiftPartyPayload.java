package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.shield.ShieldPool;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

/**
 * Everybody in the receiver's party, leader first, the receiver included; empty for no party. The
 * shield is the stored pool, which the client drains forward. An offline member carries zeros.
 */
public record RiftPartyPayload(List<Member> members) implements CustomPacketPayload {

    public static final Type<RiftPartyPayload> TYPE = new Type<>(RiftfallCore.id("party"));

    public static final RiftPartyPayload NONE = new RiftPartyPayload(List.of());

    public record Member(UUID id, String name, boolean online, float health, float maxHealth,
                         double shield, long shieldAt, double mana, double maxMana) {

        public ShieldPool shieldPool() {
            return new ShieldPool(Math.max(0.0, shield), shieldAt);
        }

        public static final StreamCodec<ByteBuf, Member> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Member decode(ByteBuf buf) {
                return new Member(UUIDUtil.STREAM_CODEC.decode(buf), ByteBufCodecs.stringUtf8(16).decode(buf),
                        buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readDouble(),
                        ByteBufCodecs.VAR_LONG.decode(buf), buf.readDouble(), buf.readDouble());
            }

            @Override
            public void encode(ByteBuf buf, Member member) {
                UUIDUtil.STREAM_CODEC.encode(buf, member.id());
                ByteBufCodecs.stringUtf8(16).encode(buf, member.name());
                buf.writeBoolean(member.online());
                buf.writeFloat(member.health());
                buf.writeFloat(member.maxHealth());
                buf.writeDouble(member.shield());
                ByteBufCodecs.VAR_LONG.encode(buf, member.shieldAt());
                buf.writeDouble(member.mana());
                buf.writeDouble(member.maxMana());
            }
        };
    }

    public static final StreamCodec<ByteBuf, RiftPartyPayload> STREAM_CODEC = Member.STREAM_CODEC
            .apply(ByteBufCodecs.list(64))
            .map(RiftPartyPayload::new, RiftPartyPayload::members);

    public RiftPartyPayload {
        members = List.copyOf(members);
    }

    @Override
    public Type<RiftPartyPayload> type() {
        return TYPE;
    }
}
