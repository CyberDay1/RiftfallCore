package com.cyberday1.riftfall.worldgen.decorator;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Per-position state handed to a {@link RiftDecorator}: where we are, which room we are in, and a
 * deterministic random source for rarity rolls.
 *
 * <p>
 * The random source is reseeded from the world seed and the absolute block position for every
 * position visited, so a room decorates identically however many times it is generated and
 * regardless of which order the blocks were processed in. Carrying one stream across the whole room
 * would make the result depend on iteration order, which is exactly the kind of thing that turns
 * into an unreproducible bug report.
 *
 * <p>
 * The room kind is constant for a whole run rather than per position — it belongs to the placement,
 * not to the block — but it lives here because this is already the object every decorator is handed
 * and the alternative was widening {@link RiftDecorator#decorate} so that every decorator had to
 * accept an argument only one of them reads.
 */
public final class DecoratorContext {

    private final long worldSeed;
    private final RoomKind roomKind;
    private final RoomFacts facts;
    private final RandomSource random = RandomSource.create();
    private BlockPos position = BlockPos.ZERO;

    public DecoratorContext(long worldSeed, RoomKind roomKind) {
        this(worldSeed, roomKind, RoomFacts.NONE);
    }

    public DecoratorContext(long worldSeed, RoomKind roomKind, RoomFacts facts) {
        this.worldSeed = worldSeed;
        this.roomKind = roomKind == null ? RoomKind.UNKNOWN : roomKind;
        this.facts = facts == null ? RoomFacts.NONE : facts;
    }

    /**
     * Called by the driver before each position; not part of the decorator-facing surface.
     *
     * <p>
     * {@code Mth.getSeed} is deprecated in vanilla but not replaced, and it is the position-mixing
     * function the rest of worldgen uses. Reimplementing it here would only guarantee a different —
     * and no better — distribution.
     */
    @SuppressWarnings("deprecation")
    public void moveTo(BlockPos pos, long decoratorSalt) {
        this.position = pos;
        this.random.setSeed(Mth.getSeed(pos) ^ worldSeed ^ decoratorSalt);
    }

    /** Absolute world position of the neighbourhood centre. */
    public BlockPos position() {
        return position;
    }

    /**
     * Which {@code rift/room/<kind>/} directory the room being decorated came from, or
     * {@link RoomKind#UNKNOWN} for a placement that is not one of the dungeon's rooms.
     */
    public RoomKind roomKind() {
        return roomKind;
    }

    /** The rift's tier, and the props this placement must leave room for. */
    public RoomFacts facts() {
        return facts;
    }

    /** A fresh roll in {@code [0, 1)}. */
    public float roll() {
        return random.nextFloat();
    }

    public RandomSource random() {
        return random;
    }

    /**
     * Whether {@code state} presents a full, sturdy face towards {@code direction} — i.e. whether
     * something can be hung on it.
     *
     * <p>
     * Evaluated against {@link EmptyBlockGetter} because a structure processor sees blocks before
     * they exist in the world. Vanilla answers this from {@code BlockStateBase}'s precomputed shape
     * cache for every block whose shape does not depend on its surroundings, which is all of them
     * that matter here.
     */
    public static boolean isFaceSturdy(BlockState state, Direction direction) {
        // A barrier is an invisible wall: anything hung on it floats in mid-air.
        if (state == null || state.isAir() || state.is(Blocks.BARRIER)) {
            return false;
        }
        return state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, direction);
    }
}
