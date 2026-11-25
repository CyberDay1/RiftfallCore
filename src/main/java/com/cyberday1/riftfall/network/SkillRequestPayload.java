package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What the skill screen asks of the server: a fresh view, a rank of one skill, a slot change, a respec, or an ascension.
 * An empty {@code id} in {@link Kind#EQUIP} empties the slot. Carries no rank and no cost: the
 * server reads both from its own book, and every request goes through the same verbs as the command.
 */
public record SkillRequestPayload(Kind kind, int slot, String id) implements CustomPacketPayload {

    public static final Type<SkillRequestPayload> TYPE = new Type<>(RiftfallCore.id("skill_request"));
    private static final int TEXT = 64;

    public enum Kind {
        VIEW, SPEND, EQUIP, RESPEC_SKILLS, RESPEC_STATS, ASCEND
    }

    public static SkillRequestPayload view() {
        return new SkillRequestPayload(Kind.VIEW, 0, "");
    }

    public static SkillRequestPayload spend(String id) {
        return new SkillRequestPayload(Kind.SPEND, 0, id);
    }

    public static SkillRequestPayload equip(int slot, String id) {
        return new SkillRequestPayload(Kind.EQUIP, slot, id);
    }

    public static SkillRequestPayload respecSkills() {
        return new SkillRequestPayload(Kind.RESPEC_SKILLS, 0, "");
    }

    public static SkillRequestPayload respecStats() {
        return new SkillRequestPayload(Kind.RESPEC_STATS, 0, "");
    }

    /** The sheet's Ascend: {@code id} names the ascendancy. */
    public static SkillRequestPayload ascend(String name) {
        return new SkillRequestPayload(Kind.ASCEND, 0, name);
    }

    public static final StreamCodec<ByteBuf, SkillRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(SkillRequestPayload::kind, Kind::ordinal), SkillRequestPayload::kind,
            ByteBufCodecs.VAR_INT, SkillRequestPayload::slot,
            ByteBufCodecs.stringUtf8(TEXT), SkillRequestPayload::id,
            SkillRequestPayload::new);

    /** Refused, not wrapped: an unknown kind is a malformed packet, the lesson of {@code KeyForgeMenu}. */
    static Kind kind(int ordinal) {
        if (ordinal < 0 || ordinal >= Kind.values().length) {
            throw new DecoderException("unknown skill request kind " + ordinal);
        }
        return Kind.values()[ordinal];
    }

    @Override
    public Type<SkillRequestPayload> type() {
        return TYPE;
    }
}
