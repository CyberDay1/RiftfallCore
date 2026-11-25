package com.cyberday1.riftfall.worldgen.layout;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.worldgen.space.PoolElements;
import com.cyberday1.riftfall.worldgen.space.RiftSpace;
import com.cyberday1.riftfall.worldgen.space.TemplateJigsaws;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The rooms a layout may draw from, read once out of the template pools and kept for the life of a
 * level.
 *
 * <p>
 * The pool registry is the source and the template directory is not, even though the directory is
 * easier to read. {@code TemplatePoolProvider} excludes a quarter of the room files at datagen time
 * &mdash; blank starting templates, rooms with no doorway, the same build saved twice &mdash; and
 * that curation only exists inside the pools. Walking the templates directly would generate 25 rooms
 * that are an empty box or a doubled weight.
 *
 * <h2>Roles</h2>
 * The origin is drawn from the portal rooms and the boss room from the boss rooms, which is what
 * those two directories mean and the only roles the library carries evidence for. Main rooms and
 * connectors both draw from every room <em>except</em> those two, because nothing else in the
 * library distinguishes them &mdash; the
 * remaining directories are themed (chaos, stable, unstable), not roled &mdash; and a split invented
 * here would be a content decision nobody made. The layout still tells main rooms and connectors apart
 * by how far they branch, which is where the difference is really expressed.
 *
 * <p>
 * The portal rooms are held back because they are the way in. A measured rift placed
 * {@code rift/room/portal/333/portal-1-grimm} at depth 6, four rooms deep past the entrance, which
 * reads as a second front door in the middle of the dungeon. No portal room sits in any other pool, so
 * withholding them costs the main supply nothing. The boss rooms are held back because one arena per
 * rift is the point; loose in the main supply they would turn up anywhere and repeatedly.
 *
 * <p>
 * The approach rooms are the fourth, and they are held back for a reason of geometry rather than
 * pacing: an approach is the one piece in the library authored with its two doorways at
 * <em>different</em> heights, so it is the only thing that can carry a party from the elevation the
 * rest of the library joins at up to a boss room that does not. Loose in the main supply it would
 * land anywhere with one of its two doors permanently bricked. Unlike the other three this list is
 * allowed to stay empty and does not fall back: with no approach authored, the boss room is hung
 * straight off its parent as it always was.
 */
public final class RiftRoomLibrary {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String ROOM_PREFIX = "rift/room/";
    private static final String ORIGIN_PREFIX = "rift/room/portal/";
    private static final String BOSS_PREFIX = "rift/room/boss/";
    private static final String APPROACH_PREFIX = "rift/room/approach/";
    private static final String ORE_PREFIX = com.cyberday1.riftfall.worldgen.ore.OreRooms.CAVERN_PREFIX;

    /**
     * How many rooms one branch step gets to choose between.
     *
     * <p>
     * The whole library is 124 rooms in 8 orientations, and {@link BranchLayout#findPlacements} is
     * quadratic in candidates against sockets, so handing it everything would cost a few million
     * socket pairings per rift to pick one room. A sample is not a compromise here: the layout picks
     * uniformly from whatever comes back, so sampling first and picking after is the same
     * distribution, reached sooner.
     */
    private static final int CANDIDATES_PER_STEP = 32;

    /** Which supply a pooled room is held for. */
    private enum Held {
        MAIN, ORIGIN, BOSS, APPROACH, ORE;

        /** A room listed in two pools takes the more specific role. */
        static Held merge(Held a, Held b) {
            return a.ordinal() >= b.ordinal() ? a : b;
        }
    }

    private final List<RiftSpace> origins;
    private final List<RiftSpace> main;
    private final List<RiftSpace> stubs;
    private final List<RiftSpace> bosses;
    private final List<RiftSpace> approaches;
    private final List<RiftSpace> ores;
    private final List<RiftSpace> all;
    private final Map<ResourceLocation, Vec3i> sizes;

    private RiftRoomLibrary(List<RiftSpace> origins, List<RiftSpace> main, List<RiftSpace> stubs,
                            List<RiftSpace> bosses, List<RiftSpace> approaches, List<RiftSpace> ores,
                            List<RiftSpace> all, Map<ResourceLocation, Vec3i> sizes) {
        this.origins = List.copyOf(origins);
        this.main = List.copyOf(main);
        this.stubs = List.copyOf(stubs);
        this.bosses = List.copyOf(bosses);
        this.approaches = List.copyOf(approaches);
        this.ores = List.copyOf(ores);
        this.all = List.copyOf(all);
        this.sizes = Map.copyOf(sizes);
    }

    /**
     * Reads every {@code riftfall:rift/room/...} pool and expands its members into placeable
     * orientations. A room the template manager cannot load is skipped with a warning rather than
     * thrown on: a datapack removing one piece should cost that piece, not the dimension.
     */
    public static RiftRoomLibrary load(Registry<StructureTemplatePool> pools, StructureTemplateManager templates) {
        return load(pools, templates, RoomIndex.EMPTY);
    }

    /** As above, reading a room's blocks only when {@code index} does not describe it. */
    public static RiftRoomLibrary load(Registry<StructureTemplatePool> pools, StructureTemplateManager templates,
                                       RoomIndex index) {
        // Deduplicated because pools list a member once per unit of weight, and a room in two pools
        // must not get two chances at the same draw.
        Map<ResourceLocation, Held> members = new LinkedHashMap<>();
        RandomSource fixed = RandomSource.create(0L);

        // Sorted, and it has to be. Registry iteration order is not stable across JVM runs:
        // MappedRegistry holds its entries in a HashMap keyed by ResourceKey, and ResourceKey
        // interns its instances rather than overriding hashCode, so the buckets fall out in
        // identity-hash order and that differs every launch. The order reaches the dungeon --
        // it fixes the order of `all` and `origins` below, and `sample` indexes into those lists
        // -- so an unsorted walk makes the layout a function of the JVM run as well as the seed.
        // That is not a variety problem (variety comes from the seed and is unaffected); it is a
        // persistence problem. Chunks generate lazily and are saved, while the plan is rebuilt on
        // every server start, so a rift explored across a restart would grow a second, different
        // dungeon into the chunks it had already committed to disk.
        List<Map.Entry<ResourceKey<StructureTemplatePool>, StructureTemplatePool>> ordered =
                new ArrayList<>(pools.entrySet());
        ordered.sort(Comparator.comparing(entry -> entry.getKey().location()));

        List<StructureTemplatePool> orePools = new ArrayList<>();
        for (var entry : ordered) {
            ResourceLocation poolId = entry.getKey().location();
            if (!poolId.getNamespace().equals(RiftfallCore.MODID) || !poolId.getPath().startsWith(ROOM_PREFIX)) {
                continue;
            }
            // Walked after, on their own random: an ore pool drawing from `fixed` here would reshuffle
            // every pool after it and replan every version-1 rift.
            if (poolId.getPath().startsWith(ORE_PREFIX)) {
                orePools.add(entry.getValue());
                continue;
            }
            Held held = poolId.getPath().startsWith(APPROACH_PREFIX) ? Held.APPROACH
                    : poolId.getPath().startsWith(BOSS_PREFIX) ? Held.BOSS
                    : poolId.getPath().startsWith(ORIGIN_PREFIX) ? Held.ORIGIN : Held.MAIN;
            // `fixed` is deliberately one RandomSource across every pool, not one per pool, so each
            // pool's shuffle depends on how many draws the pools before it consumed. That is what
            // makes the sort above load-bearing twice: it settles the order pools are visited AND,
            // through this, the shuffle within each one. Giving each pool its own RandomSource
            // looks equivalent and is not -- it would change every existing world's layout.
            for (StructurePoolElement element : entry.getValue().getShuffledTemplates(fixed)) {
                ResourceLocation location = PoolElements.locationOf(element);
                if (location != null) {
                    members.merge(location, held, Held::merge);
                }
            }
        }

        RandomSource oreOrder = RandomSource.create(0L);
        for (StructureTemplatePool pool : orePools) {
            for (StructurePoolElement element : pool.getShuffledTemplates(oreOrder)) {
                ResourceLocation location = PoolElements.locationOf(element);
                if (location != null) {
                    members.merge(location, Held.ORE, Held::merge);
                }
            }
        }

        List<RiftSpace> origins = new ArrayList<>();
        List<RiftSpace> main = new ArrayList<>();
        List<RiftSpace> stubs = new ArrayList<>();
        List<RiftSpace> bosses = new ArrayList<>();
        List<RiftSpace> approaches = new ArrayList<>();
        List<RiftSpace> ores = new ArrayList<>();
        List<RiftSpace> all = new ArrayList<>();
        Map<ResourceLocation, Vec3i> sizes = new HashMap<>();
        int missing = 0;
        int doorless = 0;

        int read = 0;
        int stale = 0;
        int checked = 0;
        boolean verify = Boolean.getBoolean("riftfall.verifyRoomIndex");
        for (var member : members.entrySet()) {
            RoomIndex.Entry known = index.get(member.getKey());
            if (known == null || verify) {
                Optional<StructureTemplate> template = templates.get(member.getKey());
                if (template.isEmpty()) {
                    LOGGER.warn("Riftfall rooms: no template for pooled element {}", member.getKey());
                    missing++;
                    continue;
                }
                RoomIndex.Entry actual = RoomIndex.Entry.of(template.get());
                if (known != null) {
                    checked++;
                    if (!known.equals(actual)) {
                        LOGGER.error("Riftfall rooms: the room index is wrong about {}", member.getKey());
                        stale++;
                    }
                }
                known = actual;
                read++;
            }
            List<RiftSpace> orientations =
                    TemplateJigsaws.orientations(member.getKey(), known.size(), known.sockets());
            // A room with no ring jigsaw has no doorway, so the layout can neither reach it nor
            // leave it. Datagen already drops these, but a datapack can add one back.
            if (orientations.getFirst().sockets().isEmpty()) {
                LOGGER.warn("Riftfall rooms: {} declares no ring jigsaw and cannot be connected to"
                        + " anything", member.getKey());
                doorless++;
                continue;
            }

            sizes.put(member.getKey(), known.size());
            // Kept out of `all`: the caverns exist only for the ore quota, never as a fallback room.
            if (member.getValue() == Held.ORE) {
                ores.addAll(orientations);
                continue;
            }
            all.addAll(orientations);
            // The listed existing open halls stay in the main draw and also stand in the ore supply.
            if (com.cyberday1.riftfall.worldgen.ore.OreRooms.isOreRoom(member.getKey())) {
                ores.addAll(orientations);
            }
            switch (member.getValue()) {
                case ORIGIN -> origins.addAll(orientations);
                case BOSS -> bosses.addAll(orientations);
                case APPROACH -> approaches.addAll(orientations);
                case MAIN -> {
                    main.addAll(orientations);
                    orientations.stream().filter(RiftRoomLibrary::isCorridor).forEach(stubs::add);
                }
                case ORE -> { }
            }
        }

        LOGGER.info("Riftfall rooms: {} rooms in {} orientations, {} of them origins, {} arenas,"
                + " {} approaches, {} corridors ({} missing, {} doorless)", sizes.size(), all.size(),
                origins.size(), bosses.size(), approaches.size(), stubs.size(), missing, doorless);
        if (verify) {
            LOGGER.info("Riftfall rooms: checked {} index entries against their files, {} wrong",
                    checked, stale);
        } else {
            LOGGER.debug("Riftfall rooms: {} of {} read from their files", read, members.size());
        }
        return new RiftRoomLibrary(origins, main, stubs, bosses, approaches, ores, all, sizes);
    }

    /**
     * Something to walk along rather than somewhere to fight: at most one axis longer than a section.
     * Read off sections rather than blocks, because sections are what a room states before it is
     * placed. The layout draws its stubs from these; they stay in {@code main} as well.
     */
    private static boolean isCorridor(RiftSpace room) {
        int wide = 0;
        wide += room.size().getX() > 1 ? 1 : 0;
        wide += room.size().getY() > 1 ? 1 : 0;
        wide += room.size().getZ() > 1 ? 1 : 0;
        return wide <= 1;
    }

    /** True when no room could be loaded at all, which makes a rift impossible rather than sparse. */
    public boolean isEmpty() {
        return all.isEmpty();
    }

    /**
     * A supply over this library. Every role falls back when its own list is empty: a rift with an
     * ordinary room at its centre, or an ordinary room at the end of it, is still a rift, and so is
     * one built out of portal rooms, whereas refusing to generate would turn a missing piece into a
     * missing dimension.
     *
     * <p>
     * Stubs draw from the corridor-shaped rooms, which are also still in {@code main}. Holding them
     * back from the main supply would have been the tidier split and is the wrong one: the corridors
     * are 19 of the 106 pooled rooms and the layout has been threading rifts with them since the
     * first one generated, so removing them changes the shape of every existing world.
     */
    public BranchLayout.RoomSupply supply() {
        return new BranchLayout.RoomSupply() {
            @Override
            public List<RiftSpace> rooms(BranchLayout.RoomRole role, RandomSource random) {
                return switch (role) {
                    case ORIGIN -> sample(origins.isEmpty() ? all : origins, random);
                    case MAIN, CONNECTOR -> sample(main.isEmpty() ? all : main, random);
                    case STUB -> sample(stubs.isEmpty() ? (main.isEmpty() ? all : main) : stubs, random);
                    case BOSS -> sample(bosses.isEmpty() ? (main.isEmpty() ? all : main) : bosses, random);
                    // The one role with no fallback: an approach is defined by the heights of its two
                    // doorways, so an ordinary room standing in for a missing one would be an approach that
                    // does not climb. An empty list is read as "none authored" and the boss hangs directly.
                    case APPROACH -> sample(approaches, random);
                    // No fallback either: an ordinary room is exactly what an ore room must not be.
                    case ORE -> sample(ores, random);
                };
            }

            @Override
            public boolean ore(RiftSpace room) {
                return com.cyberday1.riftfall.worldgen.ore.OreRooms.isOreRoom(room.template());
            }
        };
    }

    /** The authored, untransformed block extent of a room, or null if it is not in the library. */
    @Nullable
    public Vec3i blockSize(ResourceLocation template) {
        return sizes.get(template);
    }

    private static List<RiftSpace> sample(List<RiftSpace> from, RandomSource random) {
        if (from.size() <= CANDIDATES_PER_STEP) {
            return from;
        }
        List<RiftSpace> picked = new ArrayList<>(CANDIDATES_PER_STEP);
        for (int i = 0; i < CANDIDATES_PER_STEP; i++) {
            picked.add(from.get(random.nextInt(from.size())));
        }
        return picked;
    }
}
