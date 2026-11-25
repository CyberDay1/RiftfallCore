package com.cyberday1.riftfall.worldgen.decorator;

import com.cyberday1.riftfall.block.marker.MarkerKind;
import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import com.cyberday1.riftfall.worldgen.ore.RiftOres;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Fills an ore room with mineable mass: riftstone mounds, wall banks and pillars veined with ore, leaving
 * a walkable path between every pair of doorways the room connects. Written only into cells the room
 * authored as air, so the shell and every authored block stay, and mining it all gives the room back.
 *
 * <pre>
 * { "type": "riftfall:ore_chunk", "home": "alexandrite", "host": "stone", "variant": "gloomslate" }
 * </pre>
 *
 * <p>
 * Only in an ore room ({@link com.cyberday1.riftfall.worldgen.ore.OreRooms}), which reaches this pass as
 * {@link RoomFacts#oreRoom()}. Runs once over the room, before the lights.
 */
public final class OreChunkDecorator implements RiftDecorator {

    public static final Set<RoomKind> DEFAULT_ROOM_KINDS = EnumSet.of(RoomKind.ORE, RoomKind.CHAOS, RoomKind.STABLE,
            RoomKind.UNSTABLE);
    public static final int DEFAULT_CLEARING_RADIUS = 4;
    public static final int DEFAULT_HEADROOM = 5;
    /** The tallest a mound stands, as a share of the open height above its floor. */
    public static final float DEFAULT_MOUND_SHARE = 0.75F;
    public static final int DEFAULT_BANK_DEPTH = 5;
    public static final float DEFAULT_PILLAR_CHANCE = 0.012F;
    /** Cells either side of a path kept clear, then a low shoulder. */
    public static final int DEFAULT_PATH_HALF_WIDTH = 1;
    public static final float DEFAULT_CRYSTAL_SCALE = 1F;
    /** Distances past this are not tracked; nothing below needs them. */
    static final int FAR = 16;

    public static final OreSource POOLS = (home, variant, host, tier, random) ->
            RiftOres.pick(RiftOres.roomPool(home, variant, host), tier, random);

    public static final MapCodec<OreChunkDecorator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("home").forGetter(d -> Optional.ofNullable(d.home)),
            RiftOres.Variant.CODEC.optionalFieldOf("variant", RiftOres.Variant.GLOOMSLATE)
                    .forGetter(OreChunkDecorator::variant),
            RiftOres.Host.CODEC.optionalFieldOf("host", RiftOres.Host.STONE).forGetter(OreChunkDecorator::host),
            Codec.intRange(3, 8).optionalFieldOf("clearing_radius", DEFAULT_CLEARING_RADIUS)
                    .forGetter(OreChunkDecorator::clearingRadius),
            Codec.intRange(4, 16).optionalFieldOf("headroom", DEFAULT_HEADROOM).forGetter(OreChunkDecorator::headroom),
            Codec.floatRange(0F, 1F).optionalFieldOf("mound_share", DEFAULT_MOUND_SHARE)
                    .forGetter(OreChunkDecorator::moundShare),
            Codec.intRange(0, 12).optionalFieldOf("bank_depth", DEFAULT_BANK_DEPTH).forGetter(OreChunkDecorator::bankDepth),
            Codec.floatRange(0F, 0.2F).optionalFieldOf("pillar_chance", DEFAULT_PILLAR_CHANCE)
                    .forGetter(OreChunkDecorator::pillarChance),
            Codec.intRange(0, 4).optionalFieldOf("path_half_width", DEFAULT_PATH_HALF_WIDTH)
                    .forGetter(OreChunkDecorator::pathHalfWidth),
            Codec.floatRange(0F, 16F).optionalFieldOf("crystal_scale", DEFAULT_CRYSTAL_SCALE)
                    .forGetter(OreChunkDecorator::crystalScale),
            RoomKind.CODEC.listOf().optionalFieldOf("room_kinds", List.copyOf(DEFAULT_ROOM_KINDS))
                    .forGetter(d -> List.copyOf(d.roomKinds())),
            Codec.LONG.optionalFieldOf("seed", 0x0C4C7L).forGetter(OreChunkDecorator::seed)
    ).apply(instance, (home, variant, host, radius, headroom, mound, bank, pillar, path, crystals, kinds, seed) ->
            new OreChunkDecorator(home.orElse(null), variant, host, radius, headroom, mound, bank, pillar, path,
                    crystals, kinds, seed, POOLS)));

    private final String home;
    private final RiftOres.Variant variant;
    private final RiftOres.Host host;
    private final int clearingRadius, headroom, bankDepth, pathHalfWidth;
    private final float moundShare, pillarChance, crystalScale;
    private final Set<RoomKind> roomKinds;
    private final long seed;
    private final OreSource source;

    /** Where the mass's blocks come from; tests swap in vanilla stand-ins for the modded ones. */
    public interface OreSource {
        Optional<BlockState> ore(String home, RiftOres.Variant variant, RiftOres.Host host, int tier, RandomSource random);

        /** Riftstone in the theme's host rock. */
        default Optional<BlockState> filler(RiftOres.Host host) {
            return RiftOres.riftstone(host);
        }

        default Optional<BlockState> crown() {
            return RiftOres.crown();
        }

        /** Whether an ore counts as a crystal for {@link #crystalShare}. */
        default boolean crystal(BlockState ore) {
            return RiftOres.isCrystalOre(ore);
        }
    }

    public OreChunkDecorator(String home, RiftOres.Variant variant, RiftOres.Host host, int clearingRadius, int headroom, float moundShare,
            int bankDepth, float pillarChance, int pathHalfWidth, float crystalScale, List<RoomKind> roomKinds,
            long seed, OreSource source) {
        this.crystalScale = crystalScale;
        this.home = home;
        this.source = source;
        this.variant = variant;
        this.host = host;
        this.clearingRadius = clearingRadius;
        this.headroom = headroom;
        this.moundShare = moundShare;
        this.bankDepth = bankDepth;
        this.pillarChance = pillarChance;
        this.pathHalfWidth = pathHalfWidth;
        this.roomKinds = roomKinds.isEmpty() ? EnumSet.noneOf(RoomKind.class) : EnumSet.copyOf(roomKinds);
        this.seed = seed;
    }

    /** All defaults, gloomslate, no home crystal. */
    public static OreChunkDecorator defaults() {
        return new OreChunkDecorator(null, RiftOres.Variant.GLOOMSLATE, RiftOres.Host.STONE, DEFAULT_CLEARING_RADIUS,
                DEFAULT_HEADROOM,
                DEFAULT_MOUND_SHARE, DEFAULT_BANK_DEPTH, DEFAULT_PILLAR_CHANCE, DEFAULT_PATH_HALF_WIDTH,
                DEFAULT_CRYSTAL_SCALE, List.copyOf(DEFAULT_ROOM_KINDS), 0x0C4C7L, POOLS);
    }

    public String home() { return home; }
    public RiftOres.Variant variant() { return variant; }
    public RiftOres.Host host() { return host; }
    public int clearingRadius() { return clearingRadius; }
    public int headroom() { return headroom; }
    public float moundShare() { return moundShare; }
    public int bankDepth() { return bankDepth; }
    public float pillarChance() { return pillarChance; }
    public int pathHalfWidth() { return pathHalfWidth; }
    public float crystalScale() { return crystalScale; }
    public Set<RoomKind> roomKinds() { return roomKinds; }
    public long seed() { return seed; }

    public OreChunkDecorator withSource(OreSource source) {
        return new OreChunkDecorator(home, variant, host, clearingRadius, headroom, moundShare, bankDepth, pillarChance,
                pathHalfWidth, crystalScale, List.copyOf(roomKinds), seed, source);
    }

    /** The same config with another clearing radius, for the census. */
    public OreChunkDecorator withRadius(int radius) {
        return new OreChunkDecorator(home, variant, host, radius, headroom, moundShare, bankDepth, pillarChance,
                pathHalfWidth, crystalScale, List.copyOf(roomKinds), seed, source);
    }

    /** The same config with another {@code crystal_scale}. */
    public OreChunkDecorator withCrystalScale(float scale) {
        return new OreChunkDecorator(home, variant, host, clearingRadius, headroom, moundShare, bankDepth, pillarChance,
                pathHalfWidth, scale, List.copyOf(roomKinds), seed, source);
    }

    /**
     * The share of crystal veins this room lays at a tier: {@code crystal_scale} times the tier curve
     * ({@link RiftOres#crystalCurve}), capped at 1. The one place crystal output is decided: a later
     * modifier that raises gem amounts multiplies in here.
     */
    public double crystalShare(int tier) {
        return Math.min(1.0, crystalScale * RiftOres.crystalCurve(tier));
    }

    public long salt() {
        return seed ^ 0x0C4C7B0L;
    }

    @Override
    public MapCodec<? extends RiftDecorator> codec() {
        return CODEC;
    }

    /** Whole-room pass only; see {@link #place}. */
    @Override
    public int decorate(DecoratorContext context, BlockState[] neighbourhood) {
        return 0;
    }

    /**
     * What one pass did. {@code cells} counts every block written; {@code air} the room's open cells before
     * it ran; {@code doors} the doorway floor cells found and {@code path} the cells kept clear between them.
     */
    public record Report(int air, int floor, int covered, int doors, int path, int cells, int ores, int filler,
                         int crowns) {
        public static final Report NONE = new Report(0, 0, 0, 0, 0, 0, 0, 0, 0);

        /** The share of the room's floor spots the mass stands on. */
        public double floorShare() {
            return floor == 0 ? 0 : covered / (double) floor;
        }

        /** The share of the room's open volume the pass filled. */
        public double massShare() {
            return air == 0 ? 0 : cells / (double) air;
        }
    }

    /**
     * Fills the room in place. {@code around} is the driver's six-neighbour index; {@code locked} cells
     * carry NBT. Every cell written is flagged in {@code placed}.
     */
    public Report place(BlockState[] states, BlockPos[] positions, int[] around, boolean[] locked, boolean[] changed,
            boolean[] placed, long worldSeed, RoomKind roomKind, RoomFacts facts) {
        if (!RiftOres.ENABLED || states.length == 0 || !facts.oreRoom()
                || !roomKinds.contains(roomKind == null ? RoomKind.UNKNOWN : roomKind)) return Report.NONE;
        Optional<BlockState> stone = source.filler(host);
        if (stone.isEmpty()) return Report.NONE;
        int n = states.length;
        long mix = worldSeed ^ salt();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int air = 0;
        boolean[] open = new boolean[n];
        for (int i = 0; i < n; i++) {
            open[i] = open(states[i]);
            if (states[i].isAir()) air++;
            minX = Math.min(minX, positions[i].getX());
            maxX = Math.max(maxX, positions[i].getX());
            minZ = Math.min(minZ, positions[i].getZ());
            maxZ = Math.max(maxZ, positions[i].getZ());
        }
        boolean[] walk = walkable(states, around, open);
        boolean[] boundary = new boolean[n];
        List<Integer> doors = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            BlockPos p = positions[i];
            boundary[i] = p.getX() == minX || p.getX() == maxX || p.getZ() == minZ || p.getZ() == maxZ;
            if (walk[i] && boundary[i]) doors.add(i);
        }
        // By position, not by list order, so the paths do not depend on how the room's blocks arrive; and
        // one cell per doorway, or a 3-wide hole would lay three side-by-side paths.
        doors.sort((a, b) -> Long.compare(positions[a].asLong(), positions[b].asLong()));
        List<Integer> oneEach = new ArrayList<>();
        for (int d : doors) {
            boolean near = false;
            for (int e : oneEach) near |= positions[d].distManhattan(positions[e]) <= 4;
            if (!near) oneEach.add(d);
        }
        doors = oneEach;
        boolean[] path = paths(states, around, walk, doors);
        int pathCells = 0;
        for (boolean b : path) if (b) pathCells++;
        int[] toPath = distances(around, walk, path);
        boolean[] wallSide = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (!walk[i]) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int j = step(around, i, d);
                if (j < 0 || !open[j]) {
                    wallSide[i] = true;
                    break;
                }
            }
        }
        int[] toWall = distances(around, walk, wallSide);

        List<Integer> body = new ArrayList<>();
        int floorSpots = 0, covered = 0;
        for (int i = 0; i < n; i++) {
            if (walk[i] && !boundary[i]) floorSpots++;
            int below = step(around, i, Direction.DOWN);
            // Masses stand on sturdy dry floor, never on the doorway plane or a path.
            if (!walk[i] || boundary[i] || toPath[i] <= pathHalfWidth || below < 0
                    || !DecoratorContext.isFaceSturdy(states[below], Direction.UP)) continue;
            int free = 0;
            for (int c = i; c >= 0 && !locked[c] && states[c].isAir() && !boundary[c]; c = step(around, c, Direction.UP)) {
                free++;
            }
            int h = Math.min(free, height(positions[i], free, toPath[i], toWall[i], mix));
            if (h > 0) covered++;
            for (int k = 0, c = i; k < h && c >= 0; k++, c = step(around, c, Direction.UP)) {
                states[c] = stone.get();
                open[c] = false;
                changed[c] = true;
                placed[c] = true;
                body.add(c);
            }
        }
        body.sort((a, b) -> Long.compare(positions[a].asLong(), positions[b].asLong()));
        RandomSource random = RandomSource.create(mix);
        int ores = veins(body, states, open, placed, around, facts.tier(), random);
        int crowns = 0;
        Optional<BlockState> crown = source.crown();
        for (int i : body) {
            int up = step(around, i, Direction.UP);
            if (crown.isEmpty() || up < 0 || placed[up] || locked[up] || !states[up].isAir() || boundary[up]) continue;
            if (random.nextDouble() >= RiftOres.crownChance(facts.tier())) continue;
            states[up] = crown.get();
            open[up] = false;
            changed[up] = true;
            placed[up] = true;
            crowns++;
        }
        return new Report(air, floorSpots, covered, doors.size(), pathCells, body.size() + crowns, ores,
                body.size() - ores, crowns);
    }

    /**
     * How high the mass stands at a floor spot with {@code free} open cells above it: a course of rubble at
     * least, noise mounds up to {@code mound_share} of that, banks rising to the roof against the walls, a low
     * shoulder beside the paths, and now and then a pillar to the roof.
     */
    int height(BlockPos p, int free, int toPath, int toWall, long mix) {
        double broad = noise(mix, p.getX(), p.getZ(), 7.0);
        double fine = noise(mix ^ 0x5EEDL, p.getX(), p.getZ(), 2.5);
        // Never bare: at least a course of rubble everywhere off the paths.
        double h = Math.max(1, free * moundShare * Math.min(1, Math.max(0, broad * 1.5 - 0.25)) + fine * 1.5);
        if (toWall < bankDepth) h = Math.max(h, free * (1 - toWall / (double) bankDepth) * (0.8 + 0.2 * fine));
        if (toPath == pathHalfWidth + 1) h = Math.min(h, 1 + fine * 2);
        else if (toPath == pathHalfWidth + 2) h = Math.min(h, 3 + fine * 2);
        long roll = hash(mix ^ 0x9111A2L, p);
        if (toPath > pathHalfWidth + 3 && (roll >>> 11) * 0x1.0p-53 < pillarChance) return free;
        return Math.max(0, (int) Math.round(h));
    }

    /** A cell a player can stand in: open with open above, on a block with collision, no fluid, no barrier. */
    static boolean[] walkable(BlockState[] states, int[] around, boolean[] open) {
        boolean[] walk = new boolean[states.length];
        for (int i = 0; i < states.length; i++) {
            int below = step(around, i, Direction.DOWN), up = step(around, i, Direction.UP);
            walk[i] = open[i] && up >= 0 && open[up] && below >= 0 && !open[below]
                    && !states[below].is(Blocks.BARRIER) && states[below].getFluidState().isEmpty();
        }
        return walk;
    }

    /** Walkable neighbours: level, one step up (with headroom) or one step down. */
    static int[] next(int[] around, boolean[] walk, int i, int[] out) {
        int k = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int j = step(around, i, d);
            if (j < 0) continue;
            if (walk[j]) {
                out[k++] = j;
                continue;
            }
            int up = step(around, j, Direction.UP);
            if (up >= 0 && walk[up]) {
                out[k++] = up;
                continue;
            }
            int down = step(around, j, Direction.DOWN);
            if (down >= 0 && walk[down]) out[k++] = down;
        }
        return Arrays.copyOf(out, k);
    }

    /**
     * Cells kept clear: a walk joining every doorway to the ones the room already joins it to, preferring
     * the authored track; every rail, so a mine track stays a track; and the floor beside each spawner.
     */
    static boolean[] paths(BlockState[] states, int[] around, boolean[] walk, List<Integer> doors) {
        int n = states.length;
        boolean[] rail = new boolean[n];
        for (int i = 0; i < n; i++) rail[i] = walk[i] && states[i].getBlock() instanceof BaseRailBlock;
        // A tree grown door by door: each doorway walks to the nearest cell already on it, with a step
        // along a track costing nothing, so walking paths ride the tracks instead of running beside them.
        boolean[] path = new boolean[n];
        int[] cost = new int[n], parent = new int[n], buffer = new int[4];
        boolean[] reached = new boolean[n];
        for (int door : doors) {
            if (path[door]) continue;
            boolean empty = true;
            for (boolean b : path) if (b) { empty = false; break; }
            if (empty) {
                path[door] = true;
                continue;
            }
            Arrays.fill(cost, Integer.MAX_VALUE);
            Arrays.fill(reached, false);
            cost[door] = 0;
            parent[door] = -1;
            ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(door));
            int hit = -1;
            while (!queue.isEmpty()) {
                int i = queue.removeFirst();
                if (reached[i]) continue;
                reached[i] = true;
                if (path[i]) {
                    hit = i;
                    break;
                }
                for (int j : next(around, walk, i, buffer)) {
                    int c = cost[i] + (rail[j] ? 0 : 1);
                    if (c >= cost[j]) continue;
                    cost[j] = c;
                    parent[j] = i;
                    if (rail[j]) queue.addFirst(j);
                    else queue.addLast(j);
                }
            }
            // A doorway the room does not join to the others keeps only its own threshold.
            if (hit < 0) {
                path[door] = true;
                continue;
            }
            for (int i = hit; i >= 0; i = parent[i]) path[i] = true;
        }
        for (int i = 0; i < n; i++) {
            if (rail[i]) path[i] = true;
            // A spawner keeps its standing room: the mass never buries one.
            if (states[i].is(RiftfallMarkers.block(MarkerKind.RIFT_MOB_SPAWNER))) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    int j = step(around, i, d);
                    if (j >= 0 && walk[j]) path[j] = true;
                }
            }
        }
        return path;
    }

    /** Walking distance from the nearest flagged cell, capped at {@link #FAR}. */
    static int[] distances(int[] around, boolean[] walk, boolean[] from) {
        int[] dist = new int[walk.length];
        Arrays.fill(dist, FAR);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < walk.length; i++) {
            if (walk[i] && from[i]) {
                dist[i] = 0;
                queue.add(i);
            }
        }
        int[] buffer = new int[4];
        while (!queue.isEmpty()) {
            int i = queue.removeFirst();
            if (dist[i] + 1 >= FAR) continue;
            for (int j : next(around, walk, i, buffer)) {
                if (dist[j] <= dist[i] + 1) continue;
                dist[j] = dist[i] + 1;
                queue.addLast(j);
            }
        }
        return dist;
    }

    /**
     * Plans {@link RiftOres#ORE_SHARE} of the mass as ore: most as small veins (half seeded on the surface),
     * the rest as long seams running straight through it. A crystal seam or vein is laid only at
     * {@link #crystalShare}; the rest stay riftstone. Returns the ore cells set.
     */
    private int veins(List<Integer> body, BlockState[] states, boolean[] open, boolean[] placed, int[] around,
            int tier, RandomSource random) {
        if (body.isEmpty()) return 0;
        Set<Integer> stone = new LinkedHashSet<>(body);
        List<Integer> surface = new ArrayList<>();
        for (int i : body) {
            for (Direction d : Direction.values()) {
                int j = step(around, i, d);
                if (d != Direction.DOWN && j >= 0 && open[j] && !placed[j]) {
                    surface.add(i);
                    break;
                }
            }
        }
        if (surface.isEmpty()) surface = body;
        int budget = (int) Math.round(body.size() * RiftOres.ORE_SHARE);
        int seamBudget = (int) Math.round(budget * RiftOres.SEAM_SHARE);
        double crystals = crystalShare(tier);
        // `planned` counts every cell a seam or vein took, laid or not, so dropping crystals never shifts
        // the budget onto the other ores; `ores` counts what was laid.
        int planned = 0, ores = 0;
        // Seams: a straight run through the mass, sometimes two thick, one ore its whole length.
        int guard = 0;
        while (planned < seamBudget && !stone.isEmpty() && guard++ < 4 * seamBudget + 16) {
            int start = body.get(random.nextInt(body.size()));
            if (!stone.contains(start)) continue;
            Optional<BlockState> ore = source.ore(home, variant, host, tier, random);
            if (ore.isEmpty()) break;
            boolean lay = !source.crystal(ore.get()) || random.nextDouble() < crystals;
            Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            int length = RiftOres.SEAM_MIN + random.nextInt(RiftOres.SEAM_MAX - RiftOres.SEAM_MIN + 1);
            boolean thick = random.nextBoolean();
            int c = start;
            for (int k = 0; k < length && c >= 0; k++) {
                if (stone.remove(c)) {
                    planned++;
                    if (lay) {
                        states[c] = ore.get();
                        ores++;
                    }
                }
                int up = step(around, c, Direction.UP);
                if (thick && up >= 0 && stone.remove(up)) {
                    planned++;
                    if (lay) {
                        states[up] = ore.get();
                        ores++;
                    }
                }
                int ahead = step(around, c, d);
                if (ahead < 0 || !stone.contains(ahead)) {
                    // Wander a step up or down to stay inside the mass, else the seam ends.
                    int alt = step(around, ahead < 0 ? c : ahead, random.nextBoolean() ? Direction.UP : Direction.DOWN);
                    ahead = alt >= 0 && stone.contains(alt) ? alt : -1;
                }
                c = ahead;
            }
        }
        double mean = (RiftOres.VEIN_MIN + RiftOres.VEIN_MAX) / 2.0;
        int count = (int) Math.round((budget - planned) / mean);
        int exposed = (int) Math.round(count * RiftOres.EXPOSED_VEINS);
        for (int v = 0; v < count && !stone.isEmpty(); v++) {
            List<Integer> from = v < exposed ? surface : body;
            int start = -1;
            for (int k = 0; k < 6 && start < 0; k++) {
                int pick = from.get(random.nextInt(from.size()));
                if (stone.contains(pick)) start = pick;
            }
            if (start < 0) continue;
            Optional<BlockState> ore = source.ore(home, variant, host, tier, random);
            if (ore.isEmpty()) continue;
            boolean lay = !source.crystal(ore.get()) || random.nextDouble() < crystals;
            int size = RiftOres.VEIN_MIN + random.nextInt(RiftOres.VEIN_MAX - RiftOres.VEIN_MIN + 1);
            List<Integer> vein = new ArrayList<>(List.of(start));
            stone.remove(start);
            while (vein.size() < size) {
                List<Integer> grow = new ArrayList<>();
                for (int i : vein) {
                    for (Direction d : Direction.values()) {
                        int j = step(around, i, d);
                        if (j >= 0 && stone.contains(j) && !grow.contains(j)) grow.add(j);
                    }
                }
                if (grow.isEmpty()) break;
                int j = grow.get(random.nextInt(grow.size()));
                stone.remove(j);
                vein.add(j);
            }
            // A crystal vein left out stays riftstone: the mass keeps its size and look.
            if (!lay) continue;
            for (int i : vein) states[i] = ore.get();
            ores += vein.size();
        }
        return ores;
    }

    /** The room's clearing centres at the configured radius, by cell index: what the census counts. */
    public List<Integer> clearings(BlockState[] states, int[] around, boolean[] open) {
        int n = states.length;
        boolean[] tall = new boolean[n];
        for (int i = 0; i < n; i++) open[i] = open(states[i]);
        for (int i = 0; i < n; i++) {
            int down = step(around, i, Direction.DOWN);
            if (!open[i] || down < 0 || open[down] || states[down].is(Blocks.BARRIER)
                    || !states[down].getFluidState().isEmpty()
                    || !DecoratorContext.isFaceSturdy(states[down], Direction.UP)) continue;
            int c = i;
            boolean ok = true;
            for (int k = 1; k < headroom && ok; k++) {
                c = step(around, c, Direction.UP);
                ok = c >= 0 && open[c];
            }
            tall[i] = ok;
        }
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) if (tall[i] && square(around, tall, i, clearingRadius)) out.add(i);
        return out;
    }

    private static boolean square(int[] around, boolean[] tall, int centre, int r) {
        int corner = centre;
        for (int k = 0; k < r && corner >= 0; k++) corner = step(around, corner, Direction.WEST);
        for (int k = 0; k < r && corner >= 0; k++) corner = step(around, corner, Direction.NORTH);
        int row = corner;
        for (int x = 0; x <= 2 * r; x++) {
            if (row < 0) return false;
            int cell = row;
            for (int z = 0; z <= 2 * r; z++) {
                if (cell < 0 || !tall[cell]) return false;
                cell = step(around, cell, Direction.SOUTH);
            }
            row = step(around, row, Direction.EAST);
        }
        return true;
    }

    /** No collision and no fluid, as the room-light pass counts open. */
    public static boolean open(BlockState s) {
        return s != null && s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()
                && s.getFluidState().isEmpty();
    }

    private static int step(int[] around, int i, Direction d) {
        return i < 0 ? -1 : around[i * 6 + d.ordinal()];
    }

    /** Smooth value noise in [0, 1). */
    static double noise(long mix, int x, int z, double scale) {
        double fx = x / scale, fz = z / scale;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = lattice(mix, i, j) + (lattice(mix, i + 1, j) - lattice(mix, i, j)) * tx;
        double b = lattice(mix, i, j + 1) + (lattice(mix, i + 1, j + 1) - lattice(mix, i, j + 1)) * tx;
        return a + (b - a) * tz;
    }

    private static double lattice(long mix, int i, int j) {
        return (hash(mix, new BlockPos(i, 0, j)) >>> 11) * 0x1.0p-53;
    }

    static long hash(long mix, BlockPos pos) {
        long h = mix ^ pos.asLong() * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        return h ^ h >>> 33;
    }
}
