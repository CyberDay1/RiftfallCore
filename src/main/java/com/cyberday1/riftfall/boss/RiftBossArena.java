package com.cyberday1.riftfall.boss;

import com.cyberday1.riftfall.worldgen.level.RiftChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Where a rift's boss fight happens: the two pads and the gate the arena room's author marked, or
 * nothing.
 *
 * <p>
 * The same shape as {@code RiftLevels#wayOut}, and for the same reason &mdash; one answer, so that
 * every feature that wants the arena (the fight starting, a player rejoining after a relog) aims at the
 * one spot rather than each deriving its own. A level running on any other generator has no rooms to
 * look in.
 *
 * <p>
 * There are two of them because a boss and the party fighting it cannot start on the same block:
 * {@link #pad} is the boss's own footprint and {@link #entry} is the doorway a run walks in from.
 * A single position would have had every feature that moves a player either land them inside the boss
 * or invent its own offset, and those offsets would have disagreed.
 *
 * <p>
 * Nothing here moves anybody. Gathering a run on the entry, sealing the gate behind it and putting a
 * boss on the pad are separate features; this is only the lookup they will share.
 */
public final class RiftBossArena {

    private RiftBossArena() {
    }

    /**
     * Where the boss stands. Empty when the rift has no boss room, when the room that filled the
     * role carries no {@code boss_arena} marker, or when the level is not a rift at all.
     *
     * <p>
     * Plans the rift and generates the arena's chunks on the calling thread the first time it is
     * asked, and is cached from then on &mdash; finding the markers also consumes them.
     */
    public static Optional<BlockPos> pad(ServerLevel rift) {
        return rift.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator
                ? generator.bossPad(rift)
                : Optional.empty();
    }

    /**
     * Where a run arrives. Empty on the same three conditions as {@link #pad}, plus the room having
     * an arena pad but no {@code boss_entry} &mdash; which is left empty rather than falling back to
     * the pad, because the pad is precisely the block a party must not be put on.
     */
    public static Optional<BlockPos> entry(ServerLevel rift) {
        return rift.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator
                ? generator.bossEntry(rift)
                : Optional.empty();
    }

    /**
     * Where the way home opens when the boss dies. Empty on the same three conditions as
     * {@link #pad}, plus the arena's author having marked no {@code exit_portal}.
     *
     * <p>
     * Deliberately does not fall back to {@link #pad} or to wherever the boss fell. The author put
     * this marker on the far side of the arena from the fight, and a tear opened where the boss
     * happened to die can land inside geometry.
     */
    public static Optional<BlockPos> exit(ServerLevel rift) {
        return rift.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator
                ? generator.bossExit(rift)
                : Optional.empty();
    }

    /**
     * Every block of the doorway the gate fills, lowest row first, or empty on the same conditions as
     * {@link #pad} plus the author having marked no gate.
     *
     * <p>
     * A list rather than a position because a doorway is a rectangle: the author paints the opening
     * they want shut, and its size is theirs to choose rather than ours to assume.
     */
    public static List<BlockPos> gate(ServerLevel rift) {
        return rift.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator
                ? generator.bossGate(rift)
                : List.of();
    }

    /**
     * The arena room's own bounding box, or null on the same conditions as {@link #occupied}.
     *
     * <p>
     * Read from the plan and not from the blocks, so asking costs no generation. Exposed for the
     * probe: "a tear stands at the exit marker" and "a tear stands at the exit marker <em>and
     * nowhere else in the room</em>" are different claims, and only the second one rules out a tear
     * opened wherever the boss happened to fall. Answering the second needs the room's extent.
     */
    public static @Nullable BoundingBox room(ServerLevel rift) {
        return rift.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator
                ? generator.bossRoomBounds(rift)
                : null;
    }

    /**
     * Whether somebody is standing in the boss room right now. The cheap question, and the one every
     * feature here has to ask before it may ask any of the others.
     *
     * <p>
     * ⚠ {@link #pad}, {@link #entry} and {@link #gate} all walk the room for markers, and walking a
     * room reads blocks, and reading a block in a chunk nobody has built <em>builds</em> it on the
     * calling thread. Polling those every tick would generate every boss room in the game the moment
     * anybody lit a portal. This asks {@code bossRoomBounds} instead, which reads the plan and no
     * blocks, so a true answer means a player is inside those bounds &mdash; which means the chunks
     * are loaded, which is what makes the marker walk a read rather than a generation.
     *
     * <p>
     * Spectators do not count. A spectator is watching the room, not in it, and a fight that started
     * because somebody flew through in spectator is a fight nobody is having.
     */
    public static boolean occupied(ServerLevel rift) {
        if (!(rift.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator)) {
            return false;
        }
        BoundingBox room = generator.bossRoomBounds(rift);
        if (room == null) {
            return false;
        }
        for (ServerPlayer player : rift.players()) {
            if (!player.isSpectator() && room.isInside(player.blockPosition())) {
                return true;
            }
        }
        return false;
    }
}
