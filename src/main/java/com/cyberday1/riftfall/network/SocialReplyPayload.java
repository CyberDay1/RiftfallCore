package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * What the command behind a Party bookmark button said, so the page can show it: chat is hidden while a
 * screen is open. The same lines also went to chat.
 */
public record SocialReplyPayload(List<Component> lines, boolean ok) implements CustomPacketPayload {

    public static final Type<SocialReplyPayload> TYPE = new Type<>(RiftfallCore.id("social_reply"));
    static final int MAX_LINES = 4;

    public SocialReplyPayload {
        lines = List.copyOf(lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SocialReplyPayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)), SocialReplyPayload::lines,
            ByteBufCodecs.BOOL, SocialReplyPayload::ok,
            SocialReplyPayload::new);

    @Override
    public Type<SocialReplyPayload> type() {
        return TYPE;
    }
}
