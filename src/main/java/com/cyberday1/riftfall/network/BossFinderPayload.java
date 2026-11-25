package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Where the HUD arrow points: the rift, the boss room's arrival point and the room's box (the arrow hides inside
 * it). Empty clears the arrow: not in a rift, no boss room, or the boss is dead.
 */
public record BossFinderPayload(Optional<Target> target) implements CustomPacketPayload {

    public static final Type<BossFinderPayload> TYPE = new Type<>(RiftfallCore.id("boss_finder"));
    public static final BossFinderPayload NONE = new BossFinderPayload(Optional.empty());

    public record Target(ResourceLocation dimension, BlockPos pos, BlockPos roomMin, BlockPos roomMax) {

        public static final StreamCodec<ByteBuf, Target> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Target::dimension,
                BlockPos.STREAM_CODEC, Target::pos,
                BlockPos.STREAM_CODEC, Target::roomMin,
                BlockPos.STREAM_CODEC, Target::roomMax,
                Target::new);

        /** Whether a block position is inside the room's box, ends included. */
        public boolean inRoom(int x, int y, int z) {
            return x >= roomMin.getX() && x <= roomMax.getX() && y >= roomMin.getY() && y <= roomMax.getY()
                    && z >= roomMin.getZ() && z <= roomMax.getZ();
        }
    }

    public static final StreamCodec<ByteBuf, BossFinderPayload> STREAM_CODEC =
            ByteBufCodecs.optional(Target.STREAM_CODEC).map(BossFinderPayload::new, BossFinderPayload::target);

    public static BossFinderPayload of(Target target) {
        return new BossFinderPayload(Optional.of(target));
    }

    @Override
    public Type<BossFinderPayload> type() {
        return TYPE;
    }
}
