package com.cyberday1.riftfall.data;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.PoiAnchorKind;
import com.cyberday1.riftfall.worldgen.processor.ThemeSubstitutionProcessor;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Generates the {@code riftfall:} template pools and their processor lists.
 *
 * <p>Membership is scanned off the {@code .nbt} tree at datagen rather than hand-written: a directory
 * holding templates becomes a pool, and adding a file is the whole workflow. Six exclusion rules,
 * nineteen of them hand vetoes, decide by what a file contains and never by what it is called; the
 * veto list is the escape hatch for the ones that need an eye. A room still carrying builder wool
 * fails the scan.
 */
public final class TemplatePoolProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Root of the piece library inside the mod's data namespace. */
    private static final String STRUCTURE_ROOT = "data/" + RiftfallCore.MODID + "/structure";

    /** Rule 1: palette swatches, jigsaw worked examples and blank guide boxes. 74 files. */
    private static final List<String> AUTHORING_AIDS = List.of("guides", "templates");

    /**
     * Blocks that are structure plumbing rather than build. Air and structure void are the absence
     * of a block; jigsaw and structure blocks are the connectors and are stripped at placement.
     */
    private static final Set<String> SCAFFOLD = scaffold();

    private static Set<String> scaffold() {
        Set<String> names = new TreeSet<>(Set.of(
                "minecraft:air", "minecraft:cave_air", "minecraft:void_air",
                "minecraft:structure_void", "minecraft:jigsaw", "minecraft:structure_block"));
        // A POI anchor is a jigsaw written short and is stripped at placement the same way, so a
        // room built out of nothing but anchors is as empty as one built out of nothing but jigsaws.
        for (PoiAnchorKind kind : PoiAnchorKind.ALL) {
            names.add(RiftfallCore.MODID + ":" + kind.blockId());
        }
        return Set.copyOf(names);
    }

    /**
     * The library's builder wool: a two-high plug inside every ring of a blank start, and the guide
     * line a builder carves a path along. Discounted by rules 2 and 4; a room still carrying any of it
     * fails the scan. Props may use it as material (a tank, a vineyard, an awning).
     */
    private static final String VOLUME_MARKER = "minecraft:green_wool";

    /**
     * Terminators are exempt from rule 2, and have to be: {@code rift/terminator/poi/empty.nbt} is a
     * 1x3x1 of pure jigsaw, and dropping it as "no content" empties the fallback pool that seals
     * every socket nothing else filled.
     */
    private static final String TERMINATOR_PREFIX = "rift/terminator/";

    /** Rooms are joined ring-to-ring, so this prefix decides whether rules 3 and 4 apply. */
    private static final String ROOM_PREFIX = "rift/room/";

    /**
     * Rule 6: pieces struck off by hand after being seen in a live rift. Nothing measurable is wrong
     * with any of them, so no scan can find them. Exact locations rather than a pattern, and
     * {@code scan} throws if one goes missing, so a rename cannot quietly put a room back.
     */
    private static final Set<String> VETOED = Set.of(
            // 2026-11-24: the labyrinth is removed for now. The stand-in
            // stable/333/topdown_cave_standin takes its slot; drop this line and the stand-in to restore it.
            "rift/room/stable/333/topdown_labrynth_1_grimm",
            // 2026-11-24: hub-hub is an ore room only. unstable/323/hub-hub-bub-4-mine (the
            // mine-dressed copy, an ore room) takes its slot; drop this line and the copy to restore it.
            "rift/room/unstable/323/hub-hub-bub-4-mal",
            "rift/room/stable/333/shell",
            "rift/room/stable/333/topdown_1_grimm",
            "rift/room/stable/333/bridges",
            "rift/room/stable/333/opoen_cave-1-puzzledfartman",
            "rift/room/chaos/312/cascades-1-mal",
            "rift/room/unstable/313/hub_hub",
            "rift/room/unstable/313/hub_hub_0",
            "rift/room/unstable/313/hub_hub_template",
            "rift/room/unstable/313/mine-1-rachaelrose",
            "rift/room/unstable/313/mine-1-rachaelroses",
            "rift/room/unstable/313/mine_1_rachaelrose",
            "rift/room/unstable/333/main_feat-number-signature",
            "rift/room/unstable/333/glow-1-rachaelrose",
            "rift/room/portal/333/portal_flat-1-pat",
            "rift/room/chaos/322/chicken-1-rachaelrose",
            "rift/room/chaos/111/greebles-1-jyu",
            "rift/poi/halfway/11/greeble-1-jyu",
            // Progress saves of winding_path-1-mal, still carrying the green guide wool.
            "rift/room/chaos/333/winding_path-1-pat",
            "rift/room/chaos/333/winding_pat-1-team");

    /** Vanilla's do-nothing pool, named by hand to avoid touching {@code net.minecraft.data.worldgen.Pools}. */
    private static final ResourceKey<StructureTemplatePool> EMPTY_POOL =
            ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.withDefaultNamespace("empty"));

    public static final ResourceKey<StructureProcessorList> ROOM_THEME = processorList("room_theme");
    public static final ResourceKey<StructureProcessorList> POI_THEME = processorList("poi_theme");

    /** Fallback for a POI-shaped socket: a 1x3x1 column of air carrying a {@code riftfall:poi} jigsaw. */
    private static final ResourceKey<StructureTemplatePool> TERMINATOR_POI = pool("rift/terminator/poi");

    /** Fallback for a ring connection: the flat wall slice carrying a {@code riftfall:ring} jigsaw. */
    private static final ResourceKey<StructureTemplatePool> TERMINATOR_PASSAGE = pool("rift/terminator/passage");

    private TemplatePoolProvider() {
    }

    public static void processorLists(BootstrapContext<StructureProcessorList> context) {
        // The same single processor today, kept apart because they are the two places a per-category
        // tweak would land and repointing one category is a one-line change only if the seam exists.
        context.register(ROOM_THEME, themeOnly());
        context.register(POI_THEME, themeOnly());
    }

    public static void pools(BootstrapContext<StructureTemplatePool> context) {
        HolderGetter<StructureProcessorList> processors = context.lookup(Registries.PROCESSOR_LIST);
        HolderGetter<StructureTemplatePool> pools = context.lookup(Registries.TEMPLATE_POOL);

        Library library = Library.scan();

        for (var entry : library.membersByPool().entrySet()) {
            String poolPath = entry.getKey();
            ResourceKey<StructureTemplatePool> key = pool(poolPath);
            Holder<StructureProcessorList> processorList = processors.getOrThrow(processorListFor(poolPath));

            List<Pair<Function<StructureTemplatePool.Projection, ? extends StructurePoolElement>, Integer>> elements =
                    new ArrayList<>();
            for (String location : entry.getValue()) {
                // Equal weights: nothing in the library carries a rarity hint (no naming convention,
                // no marker block, no sidecar metadata), so any weighting here would be invented.
                elements.add(Pair.of(StructurePoolElement.single(location, processorList), 1));
            }

            context.register(key, new StructureTemplatePool(
                    pools.getOrThrow(fallbackFor(poolPath)),
                    elements,
                    StructureTemplatePool.Projection.RIGID));
        }

        LOGGER.info("Riftfall template pools: {} pools from {} pieces ({} pool ids had no directory and are empty)",
                library.membersByPool().size(), library.pieceCount(), library.emptyPoolCount());
    }

    private static StructureProcessorList themeOnly() {
        // No `theme` argument: RiftChunkGenerator swaps this unbound processor for one naming the
        // theme its rift rolled. Absent is still a working answer -- the codec reads it as
        // riftfall:default -- so placing these pools outside a rift gives a material, not a hole.
        return new StructureProcessorList(List.of(new ThemeSubstitutionProcessor(Optional.empty())));
    }

    /**
     * Rooms get their own processor list; everything else — POI, anomaly, objective, exit,
     * ring, terminator — shares the POI one.
     */
    private static ResourceKey<StructureProcessorList> processorListFor(String poolPath) {
        return poolPath.startsWith("rift/room/") ? ROOM_THEME : POI_THEME;
    }

    /**
     * What a pool degrades to when it is exhausted or every candidate is filtered out. Rooms get
     * {@code minecraft:empty} because pooled rooms come in eighteen footprints, so no single piece
     * could be the right size, and because nothing reaches a room pool through a jigsaw anyway.
     */
    private static ResourceKey<StructureTemplatePool> fallbackFor(String poolPath) {
        if (poolPath.startsWith("rift/ring")) {
            return TERMINATOR_PASSAGE;
        }
        if (poolPath.startsWith("rift/room/") || poolPath.startsWith("rift/terminator")) {
            return EMPTY_POOL;
        }
        return TERMINATOR_POI;
    }

    private static ResourceKey<StructureTemplatePool> pool(String path) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, id(path));
    }

    private static ResourceKey<StructureProcessorList> processorList(String path) {
        return ResourceKey.create(Registries.PROCESSOR_LIST, id(path));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(RiftfallCore.MODID, path);
    }

    /**
     * The piece library on disk, for the other providers that read it. Shared rather than copied: a
     * second copy of {@link Library#locate()} would fail by generating nothing, not by throwing.
     */
    static Path structureRoot() {
        return Library.locate();
    }

    /**
     * The rooms a synthetic anchor could ever reach: the ones that made it into a pool, plus the
     * ones only rule 6 holds back, since lifting a veto is a one-line edit to {@link #VETOED}.
     *
     * <p>Rules 3 and 4 exclude rooms for reasons no amount of dressing fixes — no doorway, or a
     * whole face never built — so proposing props for those would be writing entries that can never
     * be read. Derived from the same scan rather than re-tested here, so the two cannot disagree.
     */
    static Set<String> dressableRooms() {
        Set<String> rooms = new TreeSet<>();
        Library.scan().membersByPool().forEach((pool, members) -> {
            if (pool.startsWith(ROOM_PREFIX)) {
                members.forEach(id -> rooms.add(id.substring(RiftfallCore.MODID.length() + 1)));
            }
        });
        VETOED.stream().filter(location -> location.startsWith(ROOM_PREFIX)).forEach(rooms::add);
        return rooms;
    }

    /** Every piece that made it into any pool, as {@code riftfall:...}. */
    static Set<String> pooledPieces() {
        Set<String> pieces = new TreeSet<>();
        Library.scan().membersByPool().values().forEach(pieces::addAll);
        return pieces;
    }

    /** Every room that made it into a room pool, as {@code riftfall:rift/room/...}. */
    static Set<String> pooledRooms() {
        Set<String> rooms = new TreeSet<>();
        Library.scan().membersByPool().forEach((pool, members) -> {
            if (pool.startsWith(ROOM_PREFIX)) {
                rooms.addAll(members);
            }
        });
        return rooms;
    }

    /**
     * The scan result: pool path -> the element locations that belong to it, in a stable order so
     * the committed datagen output has an empty diff when nothing changed.
     *
     * @param membersByPool every pool that must exist, mapped to its members (possibly none)
     * @param pieceCount    how many {@code .nbt} files landed in a pool
     * @param emptyPoolCount how many pools were created from a jigsaw reference with no directory
     */
    private record Library(TreeMap<String, TreeSet<String>> membersByPool, int pieceCount, int emptyPoolCount) {

        static Library scan() {
            Path root = locate();
            TreeMap<String, TreeSet<String>> members = new TreeMap<>();
            TreeSet<String> referenced = new TreeSet<>();
            // Per pool, the content digests already claimed, so rule 5 can spot the second copy.
            TreeMap<String, TreeMap<String, String>> seenByPool = new TreeMap<>();
            int pieces = 0;
            int shells = 0;
            int unplaceable = 0;
            int halfBuilt = 0;
            int duplicates = 0;
            int vetoed = 0;
            List<String> woolly = new ArrayList<>();

            try (Stream<Path> walk = Files.walk(root)) {
                List<Path> files = walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".nbt"))
                        .sorted()
                        .toList();

                for (Path file : files) {
                    String relative = relativise(root, file);
                    if (isAuthoringAid(relative)) {
                        continue;
                    }
                    String poolPath = relative.substring(0, relative.lastIndexOf('/'));
                    String location = relative.substring(0, relative.length() - ".nbt".length());
                    // Rule 6, run first: a vetoed piece is never placed and so can never ask for a
                    // pool, so leaving its jigsaw targets out of `referenced` is the point.
                    if (VETOED.contains(location)) {
                        LOGGER.debug("Riftfall pools: skipping {} — on the veto list", relative);
                        vetoed++;
                        continue;
                    }
                    Piece piece = readPiece(file);

                    // Rule 2: only scaffold and volume markers means a blank starting template, one
                    // of which was seeded into nearly every content folder. 46 files.
                    if (piece.content() == 0 && !relative.startsWith(TERMINATOR_PREFIX)) {
                        LOGGER.debug("Riftfall pools: skipping {} — starting template, no build in it", relative);
                        shells++;
                        continue;
                    }
                    // Rule 3: a room with no ring jigsaw has no doorway and can never be reached.
                    if (relative.startsWith(ROOM_PREFIX) && !piece.hasRing()) {
                        LOGGER.warn("Riftfall pools: skipping {} — a room with no ring jigsaw cannot be"
                                + " connected to anything", relative);
                        unplaceable++;
                        continue;
                    }
                    // Rule 4: a whole face with no build on it is a room saved before it had walls.
                    // Not a hole in the dungeon any more -- the generator backs unmatched faces with
                    // filler -- but a half-drawn build with rock against it, which looks worse.
                    if (relative.startsWith(ROOM_PREFIX) && !piece.enclosed()) {
                        LOGGER.warn("Riftfall pools: skipping {} — a whole face of this room has no"
                                + " build anywhere on it, so it was saved half-finished", relative);
                        halfBuilt++;
                        continue;
                    }
                    // Rule 5: the same bytes twice in one pool is double weight nobody asked for.
                    // Byte-identical only, deliberately: a near-match threshold would be the veto
                    // list's judgement applied to all 480 survivors instead. 16 files.
                    TreeMap<String, String> seen = seenByPool.computeIfAbsent(poolPath, unused -> new TreeMap<>());
                    String rival = seen.get(piece.digest());
                    if (rival != null) {
                        String loser = preferredName(rival, relative).equals(rival) ? relative : rival;
                        String winner = loser.equals(relative) ? rival : relative;
                        LOGGER.debug("Riftfall pools: {} is byte-identical to {} — keeping {}",
                                loser, winner, winner);
                        members.get(poolPath).remove(RiftfallCore.MODID + ":" + stripExtension(loser));
                        members.get(poolPath).add(RiftfallCore.MODID + ":" + stripExtension(winner));
                        seen.put(piece.digest(), winner);
                        duplicates++;
                        continue;
                    }
                    seen.put(piece.digest(), relative);

                    if (relative.startsWith(ROOM_PREFIX) && piece.markers() > 0) {
                        woolly.add(location + " (" + piece.markers() + ")");
                    }
                    pieces++;
                    members.computeIfAbsent(poolPath, unused -> new TreeSet<>())
                            .add(RiftfallCore.MODID + ":" + location);
                    referenced.addAll(piece.targets());
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not scan the Riftfall piece library at " + root, e);
            }

            if (pieces == 0) {
                throw new IllegalStateException("Found no placeable pieces under " + root
                        + " — the pool generator would silently emit nothing, so this is fatal.");
            }
            if (vetoed != VETOED.size()) {
                // A veto that matches nothing reads like the piece is gone while the piece keeps
                // generating, so a rename has to break the build rather than undo the veto.
                throw new IllegalStateException("The veto list names " + VETOED.size() + " pieces but only "
                        + vetoed + " were found under " + root + " — an entry has been renamed or moved.");
            }
            if (!woolly.isEmpty()) {
                // Nothing strips it at placement, so a pooled room would generate with it.
                throw new IllegalStateException("Pooled rooms still carry builder wool (" + VOLUME_MARKER
                        + "): " + woolly + ". Veto the piece or clean the .nbt.");
            }
            LOGGER.info("Riftfall pools: excluded {} starting templates, {} unplaceable rooms,"
                    + " {} half-built rooms, {} in-pool duplicates, {} vetoed by hand",
                    shells, unplaceable, halfBuilt, duplicates, vetoed);

            // Any pool a jigsaw can ask for must exist, even empty, or the reference dangles at
            // runtime. Five do today, the rift/ring_* set; they lean entirely on their fallback.
            int empty = 0;
            for (String reference : referenced) {
                if (members.putIfAbsent(reference, new TreeSet<>()) == null) {
                    empty++;
                }
            }
            return new Library(members, pieces, empty);
        }

        private static boolean isAuthoringAid(String relative) {
            return AUTHORING_AIDS.stream().anyMatch(dir -> relative.startsWith(dir + "/"));
        }

        private static String stripExtension(String relative) {
            return relative.substring(0, relative.length() - ".nbt".length());
        }

        /**
         * Of two byte-identical files, the one whose name to keep: whichever is not the placeholder.
         * When neither is or both are, the earlier name wins, only so the datagen diff stays empty.
         */
        private static String preferredName(String a, String b) {
            boolean placeholderA = isPlaceholderName(a);
            boolean placeholderB = isPlaceholderName(b);
            if (placeholderA != placeholderB) {
                return placeholderA ? b : a;
            }
            return a.compareTo(b) <= 0 ? a : b;
        }

        /**
         * The library's {@code <feature>-<number>-<signature>} placeholder, in any spelling it
         * actually appears in. Tie-break only: several finished rooms still wear this name, so it
         * must never exclude anything on its own.
         */
        private static boolean isPlaceholderName(String relative) {
            String stem = relative.substring(relative.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            return stem.contains("feat") && stem.contains("num") && stem.contains("sign");
        }

        private static Piece readPiece(Path file) throws IOException {
            byte[] raw = Files.readAllBytes(file);
            CompoundTag root;
            try (InputStream in = new ByteArrayInputStream(raw)) {
                root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            }

            List<String> palette = new ArrayList<>();
            for (Tag tag : root.getList("palette", Tag.TAG_COMPOUND)) {
                palette.add(((CompoundTag) tag).getString("Name"));
            }
            ListTag extent = root.getList("size", Tag.TAG_INT);

            int content = 0;
            int markers = 0;
            boolean hasRing = false;
            // One flag per face of the box, in the order -X, +X, -Y, +Y, -Z, +Z.
            boolean[] built = new boolean[6];
            List<String> targets = new ArrayList<>();
            for (Tag tag : root.getList("blocks", Tag.TAG_COMPOUND)) {
                CompoundTag block = (CompoundTag) tag;
                int state = block.getInt("state");
                String name = state >= 0 && state < palette.size() ? palette.get(state) : "";
                if (VOLUME_MARKER.equals(name)) {
                    markers++;
                } else if (!SCAFFOLD.contains(name)) {
                    content++;
                    markFaces(built, block.getList("pos", Tag.TAG_INT), extent);
                }
                if (!block.contains("nbt", Tag.TAG_COMPOUND)) {
                    continue;
                }
                CompoundTag blockEntity = block.getCompound("nbt");
                if (!"minecraft:jigsaw".equals(blockEntity.getString("id"))) {
                    continue;
                }
                String target = blockEntity.getString("pool");
                // The target pool's PATH is the whole discriminator between a doorway and a prop
                // anchor: rift/ring_* goes through a wall, rift/poi/* hangs a decoration.
                if (target.startsWith(RiftfallCore.MODID + ":")) {
                    String path = target.substring(RiftfallCore.MODID.length() + 1);
                    targets.add(path);
                    hasRing |= path.startsWith("rift/ring");
                }
            }
            boolean enclosed = true;
            for (boolean face : built) {
                enclosed &= face;
            }
            return new Piece(content, markers, hasRing, enclosed, targets, digest(raw));
        }

        /**
         * Notes which faces of the box a block lies on, so rule 4 can spot a face nothing was built
         * on. Must only ever be called for blocks that count as build: a face whose only content is
         * builder wool or its own jigsaws is not a wall.
         *
         * <p>A whole empty face rather than a gap threshold, because every pooled room has gaps —
         * measured, all 114 have holes in at least two faces. There is no defensible threshold.
         */
        private static void markFaces(boolean[] built, ListTag pos, ListTag extent) {
            for (int axis = 0; axis < 3; axis++) {
                if (pos.getInt(axis) == 0) {
                    built[axis * 2] = true;
                }
                if (pos.getInt(axis) == extent.getInt(axis) - 1) {
                    built[axis * 2 + 1] = true;
                }
            }
        }

        private static String digest(byte[] raw) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is required by every JVM", e);
            }
        }

        private static String relativise(Path root, Path file) {
            return root.relativize(file).toString().replace('\\', '/');
        }

        /**
         * Finds the piece library on disk. The source tree must win over the classpath copy: that
         * copy is a {@code processResources} output and can lag behind an {@code .nbt} just dropped
         * in, generating a pool quietly missing its newest piece.
         */
        private static Path locate() {
            Path here = Paths.get("").toAbsolutePath();
            for (int i = 0; i < 6 && here != null; i++, here = here.getParent()) {
                Path candidate = here.resolve("src/main/resources").resolve(STRUCTURE_ROOT);
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
            URL resource = TemplatePoolProvider.class.getClassLoader().getResource(STRUCTURE_ROOT);
            if (resource != null && "file".equals(resource.getProtocol())) {
                try {
                    Path candidate = Paths.get(resource.toURI());
                    if (Files.isDirectory(candidate)) {
                        return candidate;
                    }
                } catch (URISyntaxException ignored) {
                    // fall through to the hard failure below
                }
            }
            throw new IllegalStateException("Could not find " + STRUCTURE_ROOT
                    + " in the source tree or on the classpath; pool membership is scanned from it,"
                    + " so generating without it would produce empty pools.");
        }
    }

    /**
     * One template, reduced to the six things pool membership turns on.
     *
     * @param content  how many blocks are actual build — scaffold and volume markers set aside
     * @param markers  how many blocks are {@link #VOLUME_MARKER}
     * @param hasRing  whether any jigsaw targets a ring pool, i.e. whether the piece has a doorway
     * @param enclosed whether every face of the box has some build on it
     * @param targets  the {@code riftfall:} pool paths this piece's jigsaws name
     * @param digest   the file's bytes, for spotting the same piece saved twice
     */
    private record Piece(int content, int markers, boolean hasRing, boolean enclosed, List<String> targets, String digest) {
    }
}
