package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A cast key's press (V3): which loadout slot, and nothing else. The one field is the whole wire
 * format, so a rank or a cost cannot be sent at all; the server reads both from its own book. The
 * slot is not checked here: {@code SkillService#castSlot} refuses one out of range.
 */
public record SkillCastPayload(int slot) implements CustomPacketPayload {

    public static final Type<SkillCastPayload> TYPE = new Type<>(RiftfallCore.id("skill_cast"));

    public static final StreamCodec<ByteBuf, SkillCastPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(SkillCastPayload::new, SkillCastPayload::slot);

    @Override
    public Type<SkillCastPayload> type() {
        return TYPE;
    }
}
