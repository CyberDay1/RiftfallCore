package com.cyberday1.riftfall.worldgen.decorator;

import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import com.cyberday1.riftfall.theme.ThemeCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Lights a whole room until its floor spots reach {@code target} block light, darkest spot first.
 * Lights go flush into walls, floors and low ceilings, into wall alcoves, or hang under ceilings.
 *
 * <pre>
 * { "type": "riftfall:room_light", "target": 8, "wall": "minecraft:shroomlight",
 *   "floor": "minecraft:shroomlight", "alcove": "minecraft:lantern",
 *   "hanging": "minecraft:lantern[hanging=true]" }
 * </pre>
 *
 * <p>
 * Runs once over the room, not per position: {@code DecoratorDriver} calls {@link #light}.
 */
public final class RoomLightDecorator implements RiftDecorator {

    /** Every kind but UNKNOWN: props and the blender's faces are not rooms. */
    public static final Set<RoomKind> DEFAULT_ROOM_KINDS = EnumSet.of(RoomKind.APPROACH, RoomKind.CHAOS,
            RoomKind.STABLE, RoomKind.UNSTABLE, RoomKind.BOSS, RoomKind.PORTAL, RoomKind.ORE);
    public static final int DEFAULT_TARGET = 8;
    /** {@code -Driftfall.room_lights=off} skips the pass: the rig's "before" survey. */
    public static final String PROPERTY = "riftfall.room_lights";
    public static final boolean ENABLED = !"off".equals(System.getProperty(PROPERTY));
    /** At most one light per this many floor spots, plus {@link #MIN_LIGHTS}. */
    public static final int DEFAULT_SPOTS_PER_LIGHT = 10;
    static final int MIN_LIGHTS = 4;
    static final int MAX_LIGHTS = 512;
    /** How far from the dark spot (steps through open cells) to look for a site. */
    static final int SEARCH_STEPS = 1;
    /** Highest ceiling, above the feet, that a ceiling light or a hanging fixture is tried at. */
    static final int CEILING_REACH = 4;

    /** Site kinds, best-looking first; the weight scales a site's coverage score. */
    public enum Site {
        ALCOVE(1.5), HANGING(1.25), WALL(1.0), CEILING(0.9), FLOOR(0.6);

        final double weight;

        Site(double weight) {
            this.weight = weight;
        }
    }

    public static final MapCodec<RoomLightDecorator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(1, 15).optionalFieldOf("target", DEFAULT_TARGET).forGetter(RoomLightDecorator::target),
            ThemeCodecs.BLOCK_STATE.optionalFieldOf("wall").forGetter(RoomLightDecorator::wall),
            ThemeCodecs.BLOCK_STATE.optionalFieldOf("floor").forGetter(RoomLightDecorator::floor),
            ThemeCodecs.BLOCK_STATE.optionalFieldOf("ceiling").forGetter(RoomLightDecorator::ceiling),
            ThemeCodecs.BLOCK_STATE.optionalFieldOf("alcove").forGetter(RoomLightDecorator::alcove),
            ThemeCodecs.BLOCK_STATE.optionalFieldOf("hanging").forGetter(RoomLightDecorator::hanging),
            RoomKind.CODEC.listOf().optionalFieldOf("room_kinds", List.copyOf(DEFAULT_ROOM_KINDS))
                    .forGetter(d -> List.copyOf(d.roomKinds())),
            Codec.intRange(1, 4096).optionalFieldOf("spots_per_light", DEFAULT_SPOTS_PER_LIGHT)
                    .forGetter(RoomLightDecorator::spotsPerLight),
            Codec.LONG.optionalFieldOf("seed", 0x1A7E5L).forGetter(RoomLightDecorator::seed)
    ).apply(instance, RoomLightDecorator::new));

    private final int target;
    private final Optional<BlockState> wall, floor, ceiling, alcove, hanging;
    private final Set<RoomKind> roomKinds;
    private final int spotsPerLight;
    private final long seed;

    public RoomLightDecorator(int target, Optional<BlockState> wall, Optional<BlockState> floor,
            Optional<BlockState> ceiling, Optional<BlockState> alcove, Optional<BlockState> hanging,
            List<RoomKind> roomKinds, int spotsPerLight, long seed) {
        this.target = target;
        this.wall = wall;
        this.floor = floor;
        this.ceiling = ceiling;
        this.alcove = alcove;
        this.hanging = hanging;
        this.roomKinds = roomKinds.isEmpty() ? EnumSet.noneOf(RoomKind.class) : EnumSet.copyOf(roomKinds);
        this.spotsPerLight = spotsPerLight;
        this.seed = seed;
    }

    public int target() { return target; }
    public Optional<BlockState> wall() { return wall; }
    public Optional<BlockState> floor() { return floor; }
    public Optional<BlockState> ceiling() { return ceiling; }
    public Optional<BlockState> alcove() { return alcove; }
    public Optional<BlockState> hanging() { return hanging; }
    public Set<RoomKind> roomKinds() { return roomKinds; }
    public int spotsPerLight() { return spotsPerLight; }
    public long seed() { return seed; }

    public long salt() {
        return seed ^ 0x6C1A7E5L;
    }

    @Override
    public MapCodec<? extends RiftDecorator> codec() {
        return CODEC;
    }

    /** Whole-room pass only; see {@link #light}. */
    @Override
    public int decorate(DecoratorContext context, BlockState[] neighbourhood) {
        return 0;
    }

    // -------------------------------------------------------------------------------------------
    // The room pass
    // -------------------------------------------------------------------------------------------

    /**
     * Lights the room in place. {@code around} is the driver's six-neighbour index (-1 for none, which
     * is bedrock in a rift); {@code locked} cells carry NBT and are never written.
     *
     * @return the number of lights placed
     */
    public int light(BlockState[] states, BlockPos[] positions, int[] around, boolean[] locked, boolean[] changed,
            long worldSeed, RoomKind roomKind) {
        if (!ENABLED || !roomKinds.contains(roomKind == null ? RoomKind.UNKNOWN : roomKind) || states.length == 0) {
            return 0;
        }
        Room room = new Room(states, around, locked);
        int[] spots = room.spots();
        if (spots.length == 0) {
            return 0;
        }
        long mix = worldSeed ^ salt();
        int cap = Math.min(MAX_LIGHTS, MIN_LIGHTS + spots.length / spotsPerLight);
        boolean[] given = new boolean[states.length];
        long[] order = new long[spots.length];
        for (int k = 0; k < spots.length; k++) order[k] = hash(mix, positions[spots[k]]) >>> 8;
        int[] tried = new int[states.length];
        int placed = 0;
        int round = 0;
        while (placed < cap) {
            int dark = -1;
            long darkKey = Long.MAX_VALUE;
            for (int k = 0; k < spots.length; k++) {
                int s = spots[k];
                if (given[s] || room.light[s] >= target) continue;
                long key = ((long) room.light[s] << 56) | order[k];
                if (key < darkKey) {
                    darkKey = key;
                    dark = s;
                }
            }
            if (dark < 0) break;
            Candidate best = best(room, positions, dark, mix, tried, ++round);
            if (best == null) {
                given[dark] = true;
                continue;
            }
            room.place(best.cell, best.state);
            states[best.cell] = best.state;
            changed[best.cell] = true;
            placed++;
        }
        return placed;
    }

    private record Candidate(int cell, BlockState state, Site site, int covered, double score, long tie) {
    }

    private Candidate best(Room room, BlockPos[] positions, int dark, long mix, int[] tried, int round) {
        Candidate best = null;
        for (int spot : room.near(dark, SEARCH_STEPS)) {
            for (Candidate c : sites(room, spot)) {
                // A wall or ceiling cell is offered by several spots; score it once.
                if (tried[c.cell] == round && c.site != Site.ALCOVE) continue;
                tried[c.cell] = round;
                int covered = room.cover(c.cell, emission(c.state), target, dark);
                if (covered == 0) continue;
                Candidate scored = new Candidate(c.cell, c.state, c.site, covered, covered * c.site.weight,
                        hash(mix ^ c.site.ordinal(), positions[c.cell]));
                if (best == null || scored.score > best.score
                        || scored.score == best.score && scored.tie < best.tie) {
                    best = scored;
                }
            }
        }
        return best;
    }

    /** Every site this spot offers, before scoring. */
    private List<Candidate> sites(Room room, int spot) {
        List<Candidate> out = new ArrayList<>();
        int below = room.step(spot, Direction.DOWN);
        if (floor.isPresent() && room.replaceable(below)) out.add(site(below, floor.get(), Site.FLOOR));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int foot = room.step(spot, side);
            int head = room.step(room.step(spot, Direction.UP), side);
            if (wall.isPresent()) {
                if (room.replaceable(head)) out.add(site(head, wall.get(), Site.WALL));
                if (room.replaceable(foot)) out.add(site(foot, wall.get(), Site.WALL));
            }
            if (alcove.isPresent() && room.alcove(head, side)) out.add(site(head, alcove.get(), Site.ALCOVE));
        }
        int cell = room.step(spot, Direction.UP);
        for (int k = 2; k <= CEILING_REACH && cell >= 0; k++) {
            int next = room.step(cell, Direction.UP);
            if (next < 0) break;
            if (!room.open[next]) {
                if (ceiling.isPresent() && room.replaceable(next)) out.add(site(next, ceiling.get(), Site.CEILING));
                break;
            }
            // next is open and k cells above the feet; a fixture there leaves the two below clear.
            int roof = room.step(next, Direction.UP);
            if (hanging.isPresent() && room.states[next].isAir() && emission(room.states[next]) == 0
                    && roof >= 0 && !room.open[roof]
                    && DecoratorContext.isFaceSturdy(room.states[roof], Direction.DOWN)) {
                out.add(site(next, hanging.get(), Site.HANGING));
            }
            cell = next;
        }
        return out;
    }

    private static Candidate site(int cell, BlockState state, Site site) {
        return new Candidate(cell, state, site, 0, 0, 0);
    }

    static int emission(BlockState state) {
        return state.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    private static long hash(long mix, BlockPos pos) {
        long h = mix ^ pos.asLong() * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        return h ^ h >>> 33;
    }

    // -------------------------------------------------------------------------------------------
    // Geometry and the block light model
    // -------------------------------------------------------------------------------------------

    private static final Direction[] DIRECTIONS = Direction.values();

    /** The room as the pass sees it: openness, opacity and a block light map like the engine's. */
    public static final class Room {
        final BlockState[] states;
        final int[] around;
        final boolean[] locked;
        final boolean[] open;
        final int[] opacity;
        public final int[] light;
        private final boolean[] spot;
        private final boolean[] placed;
        private final int[] stamp;
        private final int[] scratch;
        private int generation;
        private final int[][] buckets = new int[16][];
        private final int[] sizes = new int[16];

        public Room(BlockState[] states, int[] around, boolean[] locked) {
            int n = states.length;
            this.states = states;
            this.around = around;
            this.locked = locked;
            this.open = new boolean[n];
            this.opacity = new int[n];
            this.light = new int[n];
            this.spot = new boolean[n];
            this.placed = new boolean[n];
            this.stamp = new int[n];
            this.scratch = new int[n];
            for (int i = 0; i < n; i++) {
                BlockState s = states[i];
                open[i] = s != null && s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()
                        && s.getFluidState().isEmpty();
                opacity[i] = s == null ? 15 : s.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            }
            for (int i = 0; i < n; i++) {
                int up = step(i, Direction.UP), down = step(i, Direction.DOWN);
                // Nobody stands on a barrier, so the cell above one is no floor to light.
                spot[i] = open[i] && up >= 0 && open[up] && down >= 0 && !open[down]
                        && (states[down] == null || !states[down].is(Blocks.BARRIER));
            }
            for (int b = 0; b < 16; b++) buckets[b] = new int[64];
            for (int i = 0; i < n; i++) {
                int e = states[i] == null ? 0 : emission(states[i]);
                if (e > light[i]) {
                    light[i] = e;
                    push(e, i);
                }
            }
            drain();
        }

        int step(int i, Direction d) {
            return i < 0 ? -1 : around[i * 6 + d.ordinal()];
        }

        /** Two open cells over something that is not open, as the riftlight probe counts a floor. */
        public int[] spots() {
            int count = 0;
            for (boolean s : spot) if (s) count++;
            int[] out = new int[count];
            for (int i = 0, k = 0; i < spot.length; i++) if (spot[i]) out[k++] = i;
            return out;
        }

        /** A plain full block nothing else claims: no NBT, no block entity, no marker, no light. */
        boolean replaceable(int i) {
            if (i < 0 || locked[i] || placed[i]) return false;
            BlockState s = states[i];
            return s != null && s.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    && !s.hasBlockEntity() && !RiftfallMarkers.isMarker(s) && emission(s) == 0
                    && !s.is(Blocks.BEDROCK);
        }

        /** A one-deep niche: solid behind, beside and above, and a sturdy block to stand on. */
        boolean alcove(int w, Direction into) {
            if (!replaceable(w)) return false;
            int below = step(w, Direction.DOWN);
            if (below < 0 || !DecoratorContext.isFaceSturdy(states[below], Direction.UP)) return false;
            return sealed(step(w, Direction.UP)) && sealed(step(w, into))
                    && sealed(step(w, into.getClockWise())) && sealed(step(w, into.getCounterClockWise()));
        }

        private boolean sealed(int i) {
            return i < 0 || states[i] != null && states[i].isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        }

        /** Spots within {@code steps} moves through open cells of {@code from}, itself included. */
        int[] near(int from, int steps) {
            int gen = ++generation;
            List<Integer> frontier = new ArrayList<>(List.of(from));
            List<Integer> found = new ArrayList<>();
            stamp[from] = gen;
            for (int depth = 0; depth <= steps && !frontier.isEmpty(); depth++) {
                List<Integer> next = new ArrayList<>();
                for (int i : frontier) {
                    if (spot[i]) found.add(i);
                    if (depth == steps) continue;
                    for (Direction d : DIRECTIONS) {
                        int j = step(i, d);
                        if (j >= 0 && open[j] && stamp[j] != gen) {
                            stamp[j] = gen;
                            next.add(j);
                        }
                    }
                }
                frontier = next;
            }
            return found.stream().mapToInt(Integer::intValue).toArray();
        }

        /**
         * How many spots under {@code target} a light of {@code emission} at {@code cell} lifts to it; 0 unless
         * {@code must} is one of them, so each light answers the spot it was placed for.
         */
        int cover(int cell, int emission, int target, int must) {
            if (emission < target) return 0;
            int gen = ++generation;
            stamp[cell] = gen;
            scratch[cell] = emission;
            push(emission, cell);
            int covered = 0;
            for (int level = 15; level >= target; level--) {
                while (sizes[level] > 0) {
                    int i = buckets[level][--sizes[level]];
                    if (scratch[i] != level) continue;
                    for (Direction d : DIRECTIONS) {
                        int j = step(i, d);
                        if (j < 0) continue;
                        int v = level - Math.max(1, opacity[j]);
                        // Below target it cannot cover; at or under the map it changes nothing further on.
                        if (v < target || v <= light[j]) continue;
                        if (stamp[j] == gen) {
                            if (scratch[j] >= v) continue;
                        } else {
                            stamp[j] = gen;
                            if (spot[j] && light[j] < target) covered++;
                        }
                        scratch[j] = v;
                        push(v, j);
                    }
                }
            }
            for (int level = target - 1; level >= 1; level--) sizes[level] = 0;
            return stamp[must] == gen ? covered : 0;
        }

        /** Puts a light in and updates the map. */
        void place(int cell, BlockState state) {
            states[cell] = state;
            placed[cell] = true;
            opacity[cell] = state.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            int e = emission(state);
            if (e > light[cell]) {
                light[cell] = e;
                push(e, cell);
                drain();
            }
        }

        /** Max-propagation from whatever is queued, into the real map. */
        private void drain() {
            for (int level = 15; level >= 1; level--) {
                while (sizes[level] > 0) {
                    int i = buckets[level][--sizes[level]];
                    if (light[i] != level) continue;
                    for (Direction d : DIRECTIONS) {
                        int j = step(i, d);
                        if (j < 0) continue;
                        int v = level - Math.max(1, opacity[j]);
                        if (v > light[j]) {
                            light[j] = v;
                            push(v, j);
                        }
                    }
                }
            }
        }

        private void push(int level, int i) {
            if (level < 1) return;
            int[] q = buckets[level];
            if (sizes[level] == q.length) buckets[level] = q = java.util.Arrays.copyOf(q, q.length * 2);
            q[sizes[level]++] = i;
        }
    }
}
