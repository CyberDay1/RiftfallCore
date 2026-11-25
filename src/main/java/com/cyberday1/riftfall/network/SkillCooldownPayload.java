package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A loadout slot's skill ("" when empty), the world game tick it is usable again (0 means ready), and the
 * cooldown that timer runs for in ticks (0 when ready). Sent on cast, equip and login, never per tick: the
 * client draws the sweep from its own game time.
 */
public record SkillCooldownPayload(int slot, String skill, long readyAt, int duration) implements CustomPacketPayload {

    public static final Type<SkillCooldownPayload> TYPE = new Type<>(RiftfallCore.id("skill_cooldown"));

    public static final StreamCodec<ByteBuf, SkillCooldownPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SkillCooldownPayload::slot,
            ByteBufCodecs.STRING_UTF8, SkillCooldownPayload::skill,
            ByteBufCodecs.VAR_LONG, SkillCooldownPayload::readyAt,
            ByteBufCodecs.VAR_INT, SkillCooldownPayload::duration,
            SkillCooldownPayload::new);

    @Override
    public Type<SkillCooldownPayload> type() {
        return TYPE;
    }
}
