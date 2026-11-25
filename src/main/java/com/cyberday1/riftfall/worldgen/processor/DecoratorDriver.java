package com.cyberday1.riftfall.worldgen.processor;

import com.cyberday1.riftfall.worldgen.decorator.AttachmentDecorator;
import com.cyberday1.riftfall.worldgen.decorator.DecoratorContext;
import com.cyberday1.riftfall.worldgen.decorator.GemVeinDecorator;
import com.cyberday1.riftfall.worldgen.decorator.OreChunkDecorator;
import com.cyberday1.riftfall.worldgen.decorator.RiftDecorator;
import com.cyberday1.riftfall.worldgen.decorator.RoomFacts;
import com.cyberday1.riftfall.worldgen.decorator.RoomKind;
import com.cyberday1.riftfall.worldgen.decorator.RoomLightDecorator;
import com.cyberday1.riftfall.worldgen.decorator.TrapPlateDecorator;
import com.cyberday1.riftfall.worldgen.decorator.VineDecorator;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs a theme's decorators over an already-substituted room.
 *
 * <p>
 * Decoration is a neighbourhood problem, not a per-block one — a vine has to know it is hanging off
 * the block above, a cobweb has to know it is wedged between two walls — so it runs in
 * {@code finalizeProcessing}, where the whole room is available, rather than in
 * {@code processBlock}, which sees one block with no context.
 *
 * <p>
 * <strong>Only positions present in the block list can be written.</strong> A structure template
 * saved from the world contains its air blocks, so the air a vine wants to grow into is normally
 * there; if the template was saved with air stripped, decorators simply find nothing to fill and
 * the room generates undecorated rather than wrong.
 *
 * <p>
 * Positions carrying block-entity NBT are never overwritten: replacing a chest with a cobweb would
 * silently eat authored loot.
 */
public final class DecoratorDriver {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int SIDES = DIRECTIONS.length;
    /** A grid is used while it is at most this many cells per block, and no wider than this. */
    private static final long GRID_SPARSITY = 8;
    private static final long GRID_FLOOR = 4096;
    private static final long GRID_SPAN = 1024;

    private DecoratorDriver() {
    }

    public static List<StructureTemplate.StructureBlockInfo> run(List<RiftDecorator> decorators,
            List<StructureTemplate.StructureBlockInfo> blocks, long worldSeed, RoomKind roomKind) {
        return run(decorators, blocks, worldSeed, roomKind, RoomFacts.NONE);
    }

    /** As above, with the rift's tier and the room's prop boxes. */
    public static List<StructureTemplate.StructureBlockInfo> run(List<RiftDecorator> decorators,
            List<StructureTemplate.StructureBlockInfo> blocks, long worldSeed, RoomKind roomKind, RoomFacts facts) {
        int count = blocks.size();
        if (count == 0) {
            return blocks;
        }

        BlockState[] states = new BlockState[count];
        BlockPos[] positions = new BlockPos[count];
        for (int i = 0; i < count; i++) {
            StructureTemplate.StructureBlockInfo info = blocks.get(i);
            states[i] = info.state();
            positions[i] = info.pos();
        }
        int[] around = neighbours(positions);

        DecoratorContext context = new DecoratorContext(worldSeed, roomKind, facts);
        BlockState[] neighbourhood = new BlockState[RiftDecorator.NEIGHBOURHOOD_SIZE];
        int[] targets = new int[RiftDecorator.NEIGHBOURHOOD_SIZE];
        boolean[] changed = new boolean[count];
        // Ore a whole-room pass set: later passes must not light or overwrite it.
        boolean[] ore = new boolean[count];

        for (RiftDecorator decorator : decorators) {
            if (decorator instanceof RoomLightDecorator light) {
                light.light(states, positions, around, locked(blocks, ore), changed, worldSeed, roomKind);
                continue;
            }
            if (decorator instanceof OreChunkDecorator chunks) {
                chunks.place(states, positions, around, locked(blocks, ore), changed, ore, worldSeed, roomKind,
                        context.facts());
                continue;
            }
            long salt = saltOf(decorator);
            for (int i = 0; i < count; i++) {
                neighbourhood[RiftDecorator.CENTRE] = states[i];
                targets[RiftDecorator.CENTRE] = i;
                int first = i * SIDES;
                for (int side = 0; side < SIDES; side++) {
                    int neighbour = around[first + side];
                    targets[side] = neighbour;
                    neighbourhood[side] = neighbour < 0 ? null : states[neighbour];
                }

                context.moveTo(positions[i], salt);
                int mask = decorator.decorate(context, neighbourhood);
                if (mask == 0) {
                    continue;
                }
                for (int slot = 0; slot < RiftDecorator.NEIGHBOURHOOD_SIZE; slot++) {
                    if ((mask & (1 << slot)) == 0) {
                        continue;
                    }
                    int target = targets[slot];
                    if (target < 0 || blocks.get(target).nbt() != null || ore[target]) {
                        continue;
                    }
                    states[target] = neighbourhood[slot];
                    changed[target] = true;
                }
            }
        }

        List<StructureTemplate.StructureBlockInfo> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            StructureTemplate.StructureBlockInfo info = blocks.get(i);
            result.add(changed[i]
                    ? new StructureTemplate.StructureBlockInfo(info.pos(), states[i], info.nbt())
                    : info);
        }
        return result;
    }

    private static boolean[] locked(List<StructureTemplate.StructureBlockInfo> blocks, boolean[] ore) {
        boolean[] locked = new boolean[blocks.size()];
        for (int i = 0; i < locked.length; i++) locked[i] = ore[i] || blocks.get(i).nbt() != null;
        return locked;
    }

    /**
     * For each position, the index of the position on each of its six sides in {@link Direction}
     * order, or -1 where the list has none. Where two entries share a position the later one wins.
     */
    public static int[] neighbours(BlockPos[] positions) {
        int count = positions.length;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        long sizeX = (long) maxX - minX + 1;
        long sizeY = (long) maxY - minY + 1;
        long sizeZ = (long) maxZ - minZ + 1;
        long volume = sizeX * sizeY * sizeZ;
        int[] around = new int[count * SIDES];
        if (sizeX > GRID_SPAN || sizeY > GRID_SPAN || sizeZ > GRID_SPAN
                || volume > Math.max(GRID_FLOOR, GRID_SPARSITY * count)) {
            // Too scattered for a grid: look each side up instead.
            Long2IntMap index = new Long2IntOpenHashMap(count);
            index.defaultReturnValue(-1);
            for (int i = 0; i < count; i++) {
                index.put(positions[i].asLong(), i);
            }
            BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
            for (int i = 0; i < count; i++) {
                for (Direction direction : DIRECTIONS) {
                    scratch.setWithOffset(positions[i], direction);
                    around[i * SIDES + direction.ordinal()] = index.get(scratch.asLong());
                }
            }
            return around;
        }

        int stepZ = (int) sizeX;
        int stepY = (int) (sizeX * sizeZ);
        int[] grid = new int[(int) volume];
        Arrays.fill(grid, -1);
        int[] cells = new int[count];
        for (int i = 0; i < count; i++) {
            BlockPos pos = positions[i];
            cells[i] = (pos.getY() - minY) * stepY + (pos.getZ() - minZ) * stepZ + (pos.getX() - minX);
            grid[cells[i]] = i;
        }
        for (int i = 0; i < count; i++) {
            BlockPos pos = positions[i];
            int cell = cells[i];
            int first = i * SIDES;
            around[first + Direction.DOWN.ordinal()] = pos.getY() > minY ? grid[cell - stepY] : -1;
            around[first + Direction.UP.ordinal()] = pos.getY() < maxY ? grid[cell + stepY] : -1;
            around[first + Direction.NORTH.ordinal()] = pos.getZ() > minZ ? grid[cell - stepZ] : -1;
            around[first + Direction.SOUTH.ordinal()] = pos.getZ() < maxZ ? grid[cell + stepZ] : -1;
            around[first + Direction.WEST.ordinal()] = pos.getX() > minX ? grid[cell - 1] : -1;
            around[first + Direction.EAST.ordinal()] = pos.getX() < maxX ? grid[cell + 1] : -1;
        }
        return around;
    }

    /**
     * How far from a block its decorated state can depend on the rest of the room, or -1 when no
     * bound is known. Placing a window of a room needs only the blocks within this distance of it.
     */
    public static int reach(List<RiftDecorator> decorators, RoomKind roomKind) {
        int reach = 0;
        for (RiftDecorator decorator : decorators) {
            int own = reachOf(decorator, roomKind == null ? RoomKind.UNKNOWN : roomKind);
            if (own < 0) {
                return -1;
            }
            reach += own;
        }
        return reach;
    }

    private static int reachOf(RiftDecorator decorator, RoomKind roomKind) {
        if (decorator instanceof AttachmentDecorator attachment) {
            if (!attachment.requiresUp() && !attachment.requiresDown() && attachment.requiresSides() == 0) {
                return 0;
            }
            // An attachment that can hold up the next one chains placements down the room.
            BlockState placed = attachment.blockState();
            boolean chains = attachment.requiresUp() && DecoratorContext.isFaceSturdy(placed, Direction.DOWN)
                    || attachment.requiresDown() && DecoratorContext.isFaceSturdy(placed, Direction.UP);
            if (attachment.requiresSides() > 0) {
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    chains |= DecoratorContext.isFaceSturdy(placed, side);
                }
            }
            return chains ? -1 : 1;
        }
        if (decorator instanceof VineDecorator) {
            // One for the block a vine hangs off, one more for that block's other faces: they
            // decide how many rolls it has spent before this one.
            return 2;
        }
        if (decorator instanceof TrapPlateDecorator trap) {
            return trap.roomKinds().contains(roomKind) ? -1 : 0;
        }
        if (decorator instanceof RoomLightDecorator light) {
            return light.roomKinds().contains(roomKind) ? -1 : 0;
        }
        if (decorator instanceof OreChunkDecorator chunks) {
            return chunks.roomKinds().contains(roomKind) ? -1 : 0;
        }
        if (decorator instanceof GemVeinDecorator) {
            return 0;
        }
        return -1;
    }

    /**
     * Per-decorator salt so two decorators in one theme do not roll in lockstep and therefore
     * always land on the same blocks. Decorators that want to control this expose their own; the
     * rest get their type's identity, which is stable across runs unlike a hash of the instance.
     */
    private static long saltOf(RiftDecorator decorator) {
        if (decorator instanceof AttachmentDecorator attachment) {
            return attachment.salt();
        }
        if (decorator instanceof VineDecorator vine) {
            return vine.salt();
        }
        if (decorator instanceof TrapPlateDecorator trap) {
            return trap.salt();
        }
        if (decorator instanceof RoomLightDecorator light) {
            return light.salt();
        }
        if (decorator instanceof GemVeinDecorator gems) {
            return gems.salt();
        }
        return decorator.getClass().getName().hashCode();
    }
}
