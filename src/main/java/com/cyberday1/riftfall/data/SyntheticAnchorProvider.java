package com.cyberday1.riftfall.data;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.MarkerKind;
import com.cyberday1.riftfall.block.marker.PoiAnchorKind;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Proposes POI anchors for rooms that carry none, and writes them to a data file.
 *
 * <p>Thirty rooms in the library have zero {@code riftfall:rift/poi/*} jigsaws: thirteen struck off
 * by hand for being bare, seventeen still in the pool. Nothing is inferred for a room that already
 * has anchors — a room the builder dressed is left exactly as they left it.
 *
 * <p>Nor for a room we authored ourselves, told apart by the markers only an author places. That
 * rule arrived after the 08-30 run put 17 anchors through the boss arena, floor and ceiling.
 *
 * <p><strong>Nothing reads the output.</strong> A room generates with the anchors its builder placed
 * and no others; this stays because the proposals and the rules behind them are worth keeping, not
 * because generation consults them.
 */
public final class SyntheticAnchorProvider implements DataProvider {

    /** Matches the block-family loader's shape: {@code data/<ns>/riftfall/<thing>}. */
    public static final String DIRECTORY = RiftfallCore.MODID + "/poi_anchors";

    /** The id the mod's own generated file is written under. */
    public static final String GENERATED_FILE_NAME = "generated";

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String ROOM_PREFIX = "rift/room/";
    private static final String POI_PREFIX = RiftfallCore.MODID + ":rift/poi/";

    /** The four anchor blocks, by registry name: a jigsaw written short, with no block entity. */
    private static final Set<String> ANCHOR_BLOCKS = anchorBlocks();

    /** Not a block: air, structure void, and the connectors that are stripped at placement. */
    private static final Set<String> PLUMBING = plumbing();

    /**
     * The markers that mean a human laid this room out, so nothing may be added to it.
     *
     * <p>
     * Deliberately <em>four</em> of the nine {@link MarkerKind}s, not all nine.
     * {@link MarkerKind}'s own javadoc draws the line: the exit portal and the three boss-room
     * fixtures "are ours, placed only by rooms we author", while {@code rift_chest},
     * {@code spring_block}, {@code quest_hub}, {@code objective_block} and
     * {@code rift_mob_spawner} came in with the imported library and are scattered through rooms
     * nobody here has ever opened. Testing on all nine would silently stop dressing imported rooms
     * that want dressing &mdash; measured: 5 library rooms carry an imported marker, and one of
     * them is dressed today and should stay that way.
     */
    private static final Set<String> AUTHORED_MARKERS = Set.of(
            RiftfallCore.MODID + ":" + MarkerKind.EXIT_PORTAL.blockId(),
            RiftfallCore.MODID + ":" + MarkerKind.BOSS_ARENA.blockId(),
            RiftfallCore.MODID + ":" + MarkerKind.BOSS_ENTRY.blockId(),
            RiftfallCore.MODID + ":" + MarkerKind.BOSS_GATE.blockId());

    private static Set<String> anchorBlocks() {
        Set<String> names = new TreeSet<>();
        for (PoiAnchorKind kind : PoiAnchorKind.ALL) {
            names.add(RiftfallCore.MODID + ":" + kind.blockId());
        }
        return Set.copyOf(names);
    }

    private static Set<String> plumbing() {
        Set<String> names = new TreeSet<>(Set.of(
                "minecraft:air", "minecraft:cave_air", "minecraft:void_air",
                "minecraft:structure_void", "minecraft:jigsaw", "minecraft:structure_block"));
        names.addAll(ANCHOR_BLOCKS);
        return Set.copyOf(names);
    }

    /** Every POI piece box, measured: the five footprints are the same in all four categories. */
    private static final int[][] BOX = {
            {3, 4, 3}, {5, 6, 5}, {7, 7, 7}, {9, 8, 9}, {11, 9, 11}};

    private static final int[] SIZES = {3, 5, 7, 9, 11};

    /** How many candidates a single category keeps in hand. Reservoir-sampled, so bounded but even. */
    private static final int SHORTLIST = 2048;

    private static final String[] HORIZONTAL = {"north", "south", "east", "west"};

    /**
     * Anchors per 1000 cells of room box. The median across the 93 rooms builders dressed; the
     * clamp keeps a 47-cubed room from being handed three times what any of them got.
     */
    private static final double DENSITY = 0.168 / 1000.0;
    private static final int FEWEST = 3;
    private static final int MOST = 24;

    private final PackOutput.PathProvider path;

    public SyntheticAnchorProvider(PackOutput output) {
        this.path = output.createPathProvider(PackOutput.Target.DATA_PACK, DIRECTORY);
    }

    /**
     * One category of mount. {@code contact} is the least solid the mounting face may be, and
     * {@code bodyLow}/{@code bodyHigh} bound how much of the piece's own volume is already solid —
     * which is the only thing separating a free-standing prop from a niche cut into rock.
     *
     * <p>All five numbers are the 5th or 10th percentile of what builders themselves tolerated,
     * measured over their 841 anchors, so the rule is no fussier than the people it imitates.
     *
     * <p>{@code weights} is how many anchors builders placed at each of the five footprints, in
     * {@link #SIZES} order. Used as the draw, so both the mix of footprints and the mix of
     * categories come out as they did by hand rather than largest-first — which produced a room of
     * seventeen 11s where a builder would have placed mostly 3s and 5s.
     */
    private record Mount(String group, String facing, int contact, int bodyLow, int bodyHigh,
                         String joint, String finalState, int[] weights) {
    }

    /**
     * {@code halfway} is deliberately absent. Its geometry is a free mount's, but it is the one
     * category authored {@code aligned} without exception (74 of 74), meaning the piece's rotation
     * is load-bearing against the terrain it is sunk into — and nothing in the template says which
     * way is right. A guess there is only visible in-world, so it is not guessed.
     */
    private static final List<Mount> MOUNTS = List.of(
            new Mount("free", "up", 88, 0, 25, "rollable", "riftfall:processor_block_3",
                    new int[]{150, 195, 112, 38, 19}),
            new Mount("ceiling", "down", 84, 0, 53, "rollable", "riftfall:processor_block_1",
                    new int[]{19, 33, 13, 5, 4}),
            new Mount("inwall", "?", 67, 50, 100, "rollable", "minecraft:air",
                    new int[]{63, 51, 31, 13, 13}));

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Path root = TemplatePoolProvider.structureRoot();
        Set<String> dressable = TemplatePoolProvider.dressableRooms();
        TreeMap<String, List<Anchor>> proposals = new TreeMap<>();
        int bare = 0;
        int dressed = 0;
        int authored = 0;

        try (Stream<Path> walk = Files.walk(root.resolve("rift").resolve("room"))) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".nbt"))
                    .sorted()
                    .toList();
            for (Path file : files) {
                String location = root.relativize(file).toString().replace('\\', '/');
                location = location.substring(0, location.length() - ".nbt".length());
                // Ore rooms are bare on purpose: the mass and the mine dressing are what they are for.
                if (!dressable.contains(location) || com.cyberday1.riftfall.worldgen.ore.OreRooms.isOreRoom(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(RiftfallCore.MODID, location))) {
                    continue;
                }
                Template template = read(file);
                // Asked before `anchored`, and reported separately, because the two are different
                // answers: a dressed room already said where its props go, an authored one is
                // saying there are none to place.
                if (template.authored()) {
                    authored++;
                    continue;
                }
                if (template.anchored()) {
                    dressed++;
                    continue;
                }
                bare++;
                List<Anchor> found = propose(template, location);
                if (!found.isEmpty()) {
                    proposals.put(RiftfallCore.MODID + ":" + location, found);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not scan the Riftfall piece library at " + root, e);
        }

        int total = proposals.values().stream().mapToInt(List::size).sum();
        LOGGER.info("Riftfall synthetic anchors: of the rooms that can generate, {} carry none of"
                + " their own, {} are already dressed and {} were authored by hand and left alone;"
                + " proposed {} anchors across {} of them",
                bare, dressed, authored, total, proposals.size());

        JsonObject json = new JsonObject();
        proposals.forEach((location, anchors) -> {
            JsonArray array = new JsonArray();
            anchors.forEach(anchor -> array.add(anchor.toJson()));
            json.add(location, array);
        });
        return DataProvider.saveStable(cache, json,
                path.json(RiftfallCore.id(GENERATED_FILE_NAME)));
    }

    @Override
    public String getName() {
        return "Riftfall synthetic POI anchors";
    }

    private record Anchor(int x, int y, int z, String pool, String orientation, String joint,
                          String finalState) {

        JsonObject toJson() {
            JsonObject object = new JsonObject();
            JsonArray pos = new JsonArray();
            pos.add(x);
            pos.add(y);
            pos.add(z);
            object.add("pos", pos);
            object.addProperty("pool", pool);
            object.addProperty("orientation", orientation);
            object.addProperty("joint", joint);
            object.addProperty("final_state", finalState);
            return object;
        }
    }

    /**
     * A room reduced to what site selection needs: which cells are solid, which are already spoken
     * for by a jigsaw, and whether the builder placed any POI anchor at all.
     *
     * <p>{@code prefix} is an inclusive-exclusive summed volume over {@code solid}, so the solid
     * count of any box is four adds and four subtracts rather than a walk. A 47-cubed room is
     * 110k cells and every candidate cell asks about several boxes.
     */
    private static final class Template {

        private final int width;
        private final int height;
        private final int depth;
        private final boolean[] solid;
        private final boolean[] jigsaw;
        private final boolean anchored;
        private final boolean authored;
        private final int[] prefix;

        Template(int width, int height, int depth, boolean[] solid, boolean[] jigsaw, boolean anchored,
                 boolean authored) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.solid = solid;
            this.jigsaw = jigsaw;
            this.anchored = anchored;
            this.authored = authored;
            this.prefix = new int[(width + 1) * (height + 1) * (depth + 1)];
            for (int x = 1; x <= width; x++) {
                for (int y = 1; y <= height; y++) {
                    for (int z = 1; z <= depth; z++) {
                        int here = solid[index(x - 1, y - 1, z - 1)] ? 1 : 0;
                        prefix[p(x, y, z)] = here
                                + prefix[p(x - 1, y, z)] + prefix[p(x, y - 1, z)] + prefix[p(x, y, z - 1)]
                                - prefix[p(x - 1, y - 1, z)] - prefix[p(x - 1, y, z - 1)]
                                - prefix[p(x, y - 1, z - 1)]
                                + prefix[p(x - 1, y - 1, z - 1)];
                    }
                }
            }
        }

        boolean anchored() {
            return anchored;
        }

        /**
         * Whether a person laid this room out, which is a stronger claim than {@link #anchored}.
         *
         * <p>
         * A dressed room says "the props go here". An authored room says nothing about props at
         * all, and that silence is the instruction: a boss arena is empty because the fight needs
         * the floor, so filling it with the library's furniture is not a default, it is damage.
         */
        boolean authored() {
            return authored;
        }

        int volume() {
            return width * height * depth;
        }

        private int index(int x, int y, int z) {
            return (y * depth + z) * width + x;
        }

        private int p(int x, int y, int z) {
            return (y * (depth + 1) + z) * (width + 1) + x;
        }

        boolean inside(int x, int y, int z) {
            return x >= 0 && y >= 0 && z >= 0 && x < width && y < height && z < depth;
        }

        boolean contains(int x0, int y0, int z0, int x1, int y1, int z1) {
            return inside(x0, y0, z0) && inside(x1 - 1, y1 - 1, z1 - 1);
        }

        /** Solid cells in the half-open box, which the caller has already bounds-checked. */
        int solidIn(int x0, int y0, int z0, int x1, int y1, int z1) {
            return prefix[p(x1, y1, z1)] - prefix[p(x0, y1, z1)] - prefix[p(x1, y0, z1)]
                    - prefix[p(x1, y1, z0)] + prefix[p(x0, y0, z1)] + prefix[p(x0, y1, z0)]
                    + prefix[p(x1, y0, z0)] - prefix[p(x0, y0, z0)];
        }

        boolean hasJigsaw(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x < x1; x++) {
                for (int y = y0; y < y1; y++) {
                    for (int z = z0; z < z1; z++) {
                        if (jigsaw[index(x, y, z)]) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    private static Template read(Path file) throws IOException {
        CompoundTag root;
        try (InputStream in = new ByteArrayInputStream(Files.readAllBytes(file))) {
            root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }

        List<String> palette = new ArrayList<>();
        for (Tag tag : root.getList("palette", Tag.TAG_COMPOUND)) {
            palette.add(((CompoundTag) tag).getString("Name"));
        }
        ListTag extent = root.getList("size", Tag.TAG_INT);
        int width = extent.getInt(0);
        int height = extent.getInt(1);
        int depth = extent.getInt(2);

        boolean[] solid = new boolean[width * height * depth];
        boolean[] jigsaw = new boolean[solid.length];
        boolean anchored = false;
        boolean authored = false;

        for (Tag tag : root.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) tag;
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            int x = pos.getInt(0);
            int y = pos.getInt(1);
            int z = pos.getInt(2);
            if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
                continue;
            }
            int at = (y * depth + z) * width + x;
            int state = block.getInt("state");
            String name = state >= 0 && state < palette.size() ? palette.get(state) : "";
            solid[at] = !PLUMBING.contains(name);
            authored |= AUTHORED_MARKERS.contains(name);
            // An anchor block says the same thing a POI jigsaw says, and says it without a block
            // entity: the room is dressed by hand, and this cell is already spoken for.
            if (ANCHOR_BLOCKS.contains(name)) {
                jigsaw[at] = true;
                anchored = true;
                continue;
            }
            if (!block.contains("nbt", Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag blockEntity = block.getCompound("nbt");
            if (!"minecraft:jigsaw".equals(blockEntity.getString("id"))) {
                continue;
            }
            jigsaw[at] = true;
            anchored |= blockEntity.getString("pool").startsWith(POI_PREFIX);
        }
        return new Template(width, height, depth, solid, jigsaw, anchored, authored);
    }

    /**
     * The proposal pass. Every cell is offered every mount at every footprint, largest first, and a
     * site is taken only if it clears its category's limits and overlaps nothing already taken.
     *
     * <p>Seeded off the template's own name: datagen output is committed, so the same library has
     * to produce the same file every time it is generated.
     */
    private static List<Anchor> propose(Template template, String location) {
        int budget = Math.clamp(Math.round(template.volume() * DENSITY), FEWEST, MOST);
        List<Anchor> taken = new ArrayList<>();
        List<int[]> claimed = new ArrayList<>();
        Random random = new Random(location.hashCode());

        // One shortlist per category-and-footprint, then draw between them by how often builders
        // used each. Filling largest-first instead put seventeen 11s in a room and nothing else.
        List<List<int[]>> shortlists = new ArrayList<>();
        List<Mount> owners = new ArrayList<>();
        List<Integer> footprints = new ArrayList<>();
        int[] weights = new int[MOUNTS.size() * SIZES.length];
        int bucket = 0;
        for (Mount mount : MOUNTS) {
            for (int s = 0; s < SIZES.length; s++) {
                shortlists.add(shortlist(template, BOX[s], mount, random));
                owners.add(mount);
                footprints.add(SIZES[s]);
                weights[bucket++] = mount.weights()[s];
            }
        }

        int[] cursor = new int[shortlists.size()];
        while (taken.size() < budget) {
            int total = 0;
            for (int i = 0; i < weights.length; i++) {
                if (cursor[i] < shortlists.get(i).size()) {
                    total += weights[i];
                }
            }
            if (total == 0) {
                break;
            }
            int roll = random.nextInt(total);
            int pick = -1;
            for (int i = 0; i < weights.length; i++) {
                if (cursor[i] >= shortlists.get(i).size()) {
                    continue;
                }
                roll -= weights[i];
                if (roll < 0) {
                    pick = i;
                    break;
                }
            }
            Mount mount = owners.get(pick);
            int size = footprints.get(pick);
            int[] box = BOX[indexOfSize(size)];
            List<int[]> candidates = shortlists.get(pick);
            // Walk this bucket forward until one lands or it is spent, so a crowded footprint
            // retires rather than being redrawn forever.
            while (cursor[pick] < candidates.size()) {
                int[] candidate = candidates.get(cursor[pick]++);
                String facing = candidate[3] < 0 ? mount.facing() : HORIZONTAL[candidate[3]];
                int[] volume = occupies(candidate[0], candidate[1], candidate[2], box, facing);
                if (overlapsAny(claimed, volume)) {
                    continue;
                }
                claimed.add(volume);
                taken.add(new Anchor(candidate[0], candidate[1], candidate[2],
                        RiftfallCore.MODID + ":rift/poi/" + mount.group() + "/" + size,
                        orientation(facing), mount.joint(), mount.finalState()));
                break;
            }
        }
        return taken;
    }

    /**
     * Every cell that would take this mount at this footprint, capped by reservoir sampling.
     *
     * <p>A 47-cubed room offers a small footprint tens of thousands of sites and only ever spends
     * seventeen. Truncating the scan would bias the whole room toward low {@code y}; sampling keeps
     * the shortlist even over the room and bounded at the same time.
     */
    private static List<int[]> shortlist(Template template, int[] box, Mount mount, Random random) {
        List<int[]> kept = new ArrayList<>();
        int seen = 0;
        for (int y = 0; y < template.height; y++) {
            for (int x = 0; x < template.width; x++) {
                for (int z = 0; z < template.depth; z++) {
                    for (int side = 0; side < HORIZONTAL.length; side++) {
                        boolean any = "?".equals(mount.facing());
                        if (!any && side > 0) {
                            break;
                        }
                        String facing = any ? HORIZONTAL[side] : mount.facing();
                        if (!fits(template, x, y, z, box, mount, facing)) {
                            continue;
                        }
                        int[] candidate = {x, y, z, any ? side : -1};
                        if (kept.size() < SHORTLIST) {
                            kept.add(candidate);
                        } else {
                            int slot = random.nextInt(seen + 1);
                            if (slot < SHORTLIST) {
                                kept.set(slot, candidate);
                            }
                        }
                        seen++;
                    }
                }
            }
        }
        shuffle(kept, random);
        return kept;
    }

    private static int indexOfSize(int size) {
        for (int i = 0; i < BOX.length; i++) {
            if (BOX[i][0] == size) {
                return i;
            }
        }
        throw new IllegalArgumentException("No POI piece box is " + size + " wide");
    }

    /**
     * The box a piece would fill, as {@code x0 y0 z0 x1 y1 z1}. The socket faces away from its own
     * body, so the body always grows one step along the anchor's facing and then onward.
     */
    private static int[] occupies(int x, int y, int z, int[] box, String facing) {
        int w = box[0];
        int h = box[1];
        int d = box[2];
        int rx = w / 2;
        int rz = d / 2;
        return switch (facing) {
            case "up" -> new int[]{x - rx, y + 1, z - rz, x + rx + 1, y + 1 + h, z + rz + 1};
            case "down" -> new int[]{x - rx, y - h, z - rz, x + rx + 1, y, z + rz + 1};
            case "north" -> new int[]{x - rx, y - h / 2, z - d, x + rx + 1, y + h - h / 2, z};
            case "south" -> new int[]{x - rx, y - h / 2, z + 1, x + rx + 1, y + h - h / 2, z + 1 + d};
            case "west" -> new int[]{x - d, y - h / 2, z - rz, x, y + h - h / 2, z + rz + 1};
            case "east" -> new int[]{x + 1, y - h / 2, z - rz, x + 1 + d, y + h - h / 2, z + rz + 1};
            default -> throw new IllegalArgumentException("Not a facing: " + facing);
        };
    }

    /** The single layer the piece rests against, in the same {@code x0 y0 z0 x1 y1 z1} form. */
    private static int[] face(int x, int y, int z, int[] box, String facing) {
        int[] volume = occupies(x, y, z, box, facing);
        return switch (facing) {
            case "up" -> new int[]{volume[0], y + 1, volume[2], volume[3], y + 2, volume[5]};
            case "down" -> new int[]{volume[0], y - 1, volume[2], volume[3], y, volume[5]};
            case "north" -> new int[]{volume[0], volume[1], z - 1, volume[3], volume[4], z};
            case "south" -> new int[]{volume[0], volume[1], z + 1, volume[3], volume[4], z + 2};
            case "west" -> new int[]{x - 1, volume[1], volume[2], x, volume[4], volume[5]};
            case "east" -> new int[]{x + 1, volume[1], volume[2], x + 2, volume[4], volume[5]};
            default -> throw new IllegalArgumentException("Not a facing: " + facing);
        };
    }

    /**
     * A wall mount faces into the wall from the cell a player stands in, so that cell has to be
     * open. Contact and body alone do not say so: a room with no air anywhere passes them at every
     * cell and ends up with its wall pieces buried in rock. 95% of the authored inwall anchors
     * clear this. Floor and ceiling mounts are exempt because they are sunk into the surface by
     * design, and only 9% of the authored ones clear it.
     */
    private static boolean approachOpen(Template template, int x, int y, int z, String facing) {
        int bx = x;
        int bz = z;
        switch (facing) {
            case "north" -> bz = z + 1;
            case "south" -> bz = z - 1;
            case "west" -> bx = x + 1;
            case "east" -> bx = x - 1;
            default -> {
                return true;
            }
        }
        // Off the edge is not a standing spot either: an anchor on the outer skin faces its body
        // inward and would be viewed from outside the room. No authored anchor does that.
        if (!template.contains(bx, y, bz, bx + 1, y + 1, bz + 1)) {
            return false;
        }
        return !template.hasJigsaw(bx, y, bz, bx + 1, y + 1, bz + 1)
                && template.solidIn(bx, y, bz, bx + 1, y + 1, bz + 1) == 0;
    }

    private static boolean fits(Template template, int x, int y, int z, int[] box, Mount mount,
                                String facing) {
        int[] volume = occupies(x, y, z, box, facing);
        if (!template.contains(volume[0], volume[1], volume[2], volume[3], volume[4], volume[5])) {
            return false;
        }
        int[] surface = face(x, y, z, box, facing);
        if (!template.contains(surface[0], surface[1], surface[2], surface[3], surface[4], surface[5])) {
            return false;
        }
        // A doorway is the one thing in these rooms that must stay walkable.
        if (template.hasJigsaw(volume[0], volume[1], volume[2], volume[3], volume[4], volume[5])) {
            return false;
        }
        if (!approachOpen(template, x, y, z, facing)) {
            return false;
        }
        int surfaceCells = cells(surface);
        int contact = template.solidIn(surface[0], surface[1], surface[2],
                surface[3], surface[4], surface[5]) * 100 / surfaceCells;
        if (contact < mount.contact()) {
            return false;
        }
        // The mounting face sits inside the body box for a vertical mount, so it is discounted
        // rather than counted twice: a floor is not evidence that the space above it is full.
        int bodyCells = cells(volume) - surfaceCells;
        if (bodyCells <= 0) {
            return false;
        }
        int bodySolid = template.solidIn(volume[0], volume[1], volume[2], volume[3], volume[4], volume[5])
                - template.solidIn(surface[0], surface[1], surface[2],
                        surface[3], surface[4], surface[5]);
        int body = Math.max(0, bodySolid) * 100 / bodyCells;
        return body >= mount.bodyLow() && body <= mount.bodyHigh();
    }

    private static int cells(int[] volume) {
        return (volume[3] - volume[0]) * (volume[4] - volume[1]) * (volume[5] - volume[2]);
    }

    private static boolean overlapsAny(List<int[]> claimed, int[] volume) {
        for (int[] other : claimed) {
            if (volume[0] < other[3] && other[0] < volume[3]
                    && volume[1] < other[4] && other[1] < volume[4]
                    && volume[2] < other[5] && other[2] < volume[5]) {
                return true;
            }
        }
        return false;
    }

    /** {@code <front>_<top>}. A vertical front needs a horizontal top and the other way round. */
    private static String orientation(String facing) {
        return "up".equals(facing) || "down".equals(facing) ? facing + "_north" : facing + "_up";
    }

    private static void shuffle(List<int[]> list, Random random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int[] swap = list.get(i);
            list.set(i, list.get(j));
            list.set(j, swap);
        }
    }
}
