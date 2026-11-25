package com.cyberday1.riftfall.worldgen.layout;

import com.cyberday1.riftfall.worldgen.space.RiftSpace;
import com.cyberday1.riftfall.worldgen.space.RoomSocket;
import com.cyberday1.riftfall.worldgen.space.TemplatePlacement;
import com.cyberday1.riftfall.worldgen.space.TripleMirror;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A finished layout, converted from chunk sections into block coordinates and indexed by chunk.
 * {@link BranchLayout} packs rooms as boxes of sections and never sees a block; a chunk generator
 * sees one 16&times;16 column at a time and needs the rooms reaching into it without scanning them
 * all. A room sits at the <em>minimum</em> corner of its section box, which is what makes matching
 * sockets by section alone correct. Built once from a seed, then read-only.
 */
public final class RiftPlan {

    private static final int SECTION_SHIFT = 4;

    /**
     * One room, ready to place.
     *
     * @param corner the minimum block corner of the room's section box
     * @param bounds the blocks the oriented template covers, which may be smaller than the section
     *               box on any axis. Shared with the chunk index, and {@code BoundingBox} is
     *               mutable, so read-only: {@code encapsulate} and {@code move} edit the plan.
     */
    public record PlacedRoom(ResourceLocation template, TripleMirror mirror, BlockPos anchor, Vec3i corner,
                             BoundingBox bounds, int depth) {
    }

    /**
     * Two rooms meeting across one section face, and the empty plane left between them. Rooms are
     * authored {@code 16n - 1} and anchored at their section box's minimum corner, so joined rooms
     * never touch: one empty plane sits between them, the low room's own maximum plane, and that is
     * what the doorway pass fills. No aperture size is stored.
     *
     * @param lowToHigh always {@code EAST} or {@code SOUTH}; normalised so a join reads the same
     *                  whichever end grew first, and horizontal because every ring jigsaw is
     * @param lowSection the section on the low side of the face, whose chunk column owns the seam
     * @param elevation  how far up the section the two rooms' openings sit, which both of them agree
     *                   on: the layout will not pair sockets that disagree. See
     *                   {@link com.cyberday1.riftfall.worldgen.space.RoomSocket#elevation()}
     */
    public record Seam(PlacedRoom low, PlacedRoom high, Direction lowToHigh, Vec3i lowSection, int elevation) {

        /** The coordinate of the empty plane on {@code lowToHigh}'s axis. */
        public int planeCoordinate() {
            return plane(lowSection, lowToHigh.getAxis());
        }
    }

    /**
     * One room's archway with nothing on the other side of it, and the plane that will close it.
     * {@code lowSection} names that plane from the low side, as a {@link Seam} does, so the column
     * owning it builds it: a west- or north-facing archway closes a plane in the next column over,
     * which may hold no room, and a chunk cannot write into its neighbour during decoration.
     *
     * @param outward the direction the archway faces, out of {@code room}. Not normalised, unlike
     *                {@link Seam#lowToHigh}: the closing wall is copied out of the room and pushed
     *                the way the archway points.
     * @param facesRoom see {@link BranchLayout.Blank#facesRoom}
     * @param elevation how far up the section the archway being closed sits
     */
    public record Blank(PlacedRoom room, Direction outward, Vec3i lowSection, boolean facesRoom, int elevation) {

        /** The coordinate of the plane on {@code outward}'s axis. */
        public int planeCoordinate() {
            return plane(lowSection, outward.getAxis());
        }
    }

    private static int plane(Vec3i lowSection, Direction.Axis axis) {
        int section = axis == Direction.Axis.X ? lowSection.getX() : lowSection.getZ();
        return ((section + 1) << SECTION_SHIFT) - 1;
    }

    private final List<PlacedRoom> rooms;
    private final List<Seam> seams;
    private final List<Blank> blanks;
    private final Map<Long, List<PlacedRoom>> byChunk;
    private final Map<Long, List<Seam>> seamsByChunk;
    private final Map<Long, List<Blank>> blanksByChunk;
    @Nullable
    private final PlacedRoom boss;

    private RiftPlan(List<PlacedRoom> rooms, List<Seam> seams, List<Blank> blanks,
                     Map<Long, List<PlacedRoom>> byChunk, Map<Long, List<Seam>> seamsByChunk,
                     Map<Long, List<Blank>> blanksByChunk, @Nullable PlacedRoom boss) {
        this.rooms = List.copyOf(rooms);
        this.seams = List.copyOf(seams);
        this.blanks = List.copyOf(blanks);
        this.byChunk = byChunk;
        this.seamsByChunk = seamsByChunk;
        this.blanksByChunk = blanksByChunk;
        this.boss = boss;
    }

    /**
     * Converts a packed layout into world coordinates. Rooms whose template is unknown are dropped
     * rather than guessed at: substituting the section box for a missing extent would turn a
     * datapack fault into a geometry bug several passes downstream.
     *
     * @param blockSize the authored, untransformed block extent of a template, or null if unknown
     */
    public static RiftPlan of(BranchLayout.Result layout, Function<ResourceLocation, Vec3i> blockSize) {
        List<PlacedRoom> rooms = new ArrayList<>(layout.placements().size());
        Map<Long, List<PlacedRoom>> byChunk = new HashMap<>();
        // Indexed by position in layout.placements(), with a null wherever a room was dropped below.
        // BranchLayout.Placement.parentIndex points into that list, so the seam pass cannot walk
        // `rooms` -- dropping one room shifts every index after it.
        PlacedRoom[] byPlacement = new PlacedRoom[layout.placements().size()];

        for (int index = 0; index < layout.placements().size(); index++) {
            BranchLayout.Placement placement = layout.placements().get(index);
            RiftSpace space = placement.space();
            ResourceLocation template = space.template();
            if (template == null) {
                continue;
            }
            Vec3i size = blockSize.apply(template);
            if (size == null) {
                continue;
            }

            // `size` must stay the authored extent. RiftSpace.size() has already had the
            // orientation applied; feeding that back in swaps x and z twice.
            Vec3i corner = new Vec3i(space.origin().getX() << SECTION_SHIFT,
                    space.origin().getY() << SECTION_SHIFT,
                    space.origin().getZ() << SECTION_SHIFT);

            BlockPos anchor = TemplatePlacement.anchor(size, space.transform(), corner);
            BoundingBox bounds = TemplatePlacement.boundsAt(size, space.transform(), corner);
            PlacedRoom room = new PlacedRoom(template, space.transform(), anchor, corner, bounds, placement.depth());
            rooms.add(room);
            byPlacement[index] = room;

            for (int cx = bounds.minX() >> SECTION_SHIFT; cx <= bounds.maxX() >> SECTION_SHIFT; cx++) {
                for (int cz = bounds.minZ() >> SECTION_SHIFT; cz <= bounds.maxZ() >> SECTION_SHIFT; cz++) {
                    byChunk.computeIfAbsent(ChunkPos.asLong(cx, cz), key -> new ArrayList<>()).add(room);
                }
            }
        }

        List<Seam> seams = new ArrayList<>();
        Map<Long, List<Seam>> seamsByChunk = new HashMap<>();
        for (int index = 0; index < layout.placements().size(); index++) {
            collect(seamOf(layout, byPlacement, index), seams, seamsByChunk);
        }
        // The doorways growth did not use are seams in every sense. See BranchLayout.findJoins.
        for (BranchLayout.Join join : layout.joins()) {
            collect(seamOf(layout, byPlacement, join), seams, seamsByChunk);
        }

        // The archways nothing met, which the doorway pass walls up rather than opens.
        List<Blank> blanks = new ArrayList<>();
        Map<Long, List<Blank>> blanksByChunk = new HashMap<>();
        for (BranchLayout.Blank blank : layout.blanks()) {
            PlacedRoom room = byPlacement[blank.roomIndex()];
            if (room == null || blank.socket().direction().getAxis().isVertical()) {
                continue;
            }
            Direction outward = blank.socket().direction();
            Vec3i section = blank.socket().section(layout.placements().get(blank.roomIndex()).space().origin());
            Vec3i low = outward.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? section
                    : section.relative(outward);
            Blank placed = new Blank(room, outward, low, blank.facesRoom(), blank.socket().elevation());
            blanks.add(placed);
            blanksByChunk.computeIfAbsent(ChunkPos.asLong(low.getX(), low.getZ()), key -> new ArrayList<>())
                    .add(placed);
        }

        byChunk.replaceAll((key, list) -> List.copyOf(list));
        seamsByChunk.replaceAll((key, list) -> List.copyOf(list));
        blanksByChunk.replaceAll((key, list) -> List.copyOf(list));
        // Named by index rather than by depth: the boss room is the deepest the layout could fit one
        // at, which is not always the deepest room there is.
        PlacedRoom boss = layout.boss() >= 0 ? byPlacement[layout.boss()] : null;
        return new RiftPlan(rooms, seams, blanks, Map.copyOf(byChunk), Map.copyOf(seamsByChunk),
                Map.copyOf(blanksByChunk), boss);
    }

    /** Files one seam, if there is one, under the chunk column that owns it. */
    private static void collect(@Nullable Seam seam, List<Seam> seams, Map<Long, List<Seam>> byChunk) {
        if (seam == null) {
            return;
        }
        seams.add(seam);
        byChunk.computeIfAbsent(ChunkPos.asLong(seam.lowSection().getX(), seam.lowSection().getZ()),
                key -> new ArrayList<>()).add(seam);
    }

    /** The join that put placement {@code index} where it is; null for the origin room. */
    @Nullable
    private static Seam seamOf(BranchLayout.Result layout, PlacedRoom[] byPlacement, int index) {
        BranchLayout.Placement placement = layout.placements().get(index);
        if (placement.parentSocket() == null || placement.parentIndex() < 0) {
            return null;
        }
        return seamOf(layout, byPlacement, placement.parentIndex(), index, placement.parentSocket());
    }

    /** The doorway one of {@link BranchLayout.Result#joins()} asks for. */
    @Nullable
    private static Seam seamOf(BranchLayout.Result layout, PlacedRoom[] byPlacement, BranchLayout.Join join) {
        return seamOf(layout, byPlacement, join.fromIndex(), join.toIndex(), join.fromSocket());
    }

    /**
     * One doorway, described from whichever room owns {@code socket}. Null when there is nothing to
     * build: either end may have been dropped, and a vertical join has no doorway convention.
     */
    @Nullable
    private static Seam seamOf(BranchLayout.Result layout, PlacedRoom[] byPlacement, int fromIndex, int toIndex,
                               RoomSocket socket) {
        PlacedRoom from = byPlacement[fromIndex];
        PlacedRoom to = byPlacement[toIndex];
        if (from == null || to == null) {
            return null;
        }
        Direction direction = socket.direction();
        if (direction.getAxis().isVertical()) {
            // Every ring jigsaw in the library faces sideways, so a vertical join means a datapack
            // added a socket with no authored aperture behind it. Leave the two rooms flush.
            return null;
        }

        // Normalised to the low side whichever room named it: the empty plane is the low room's own
        // last plane.
        Vec3i section = socket.section(layout.placements().get(fromIndex).space().origin());
        boolean fromIsLow = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        return new Seam(fromIsLow ? from : to, fromIsLow ? to : from,
                fromIsLow ? direction : direction.getOpposite(),
                fromIsLow ? section : section.relative(direction), socket.elevation());
    }

    /** Every room, in the order the layout placed them. */
    public List<PlacedRoom> rooms() {
        return rooms;
    }

    /**
     * The room a player arrives in, or null for a layout with nothing in it. Growth starts from one
     * origin, so depth zero names exactly one room.
     */
    @Nullable
    public PlacedRoom entrance() {
        for (PlacedRoom room : rooms) {
            if (room.depth() == 0) {
                return room;
            }
        }
        return null;
    }

    /** The one boss room, or null when the layout could not fit one. */
    @Nullable
    public PlacedRoom boss() {
        return boss;
    }

    /**
     * The chunk columns of the arrival neighbourhood: the entrance room, every room joined to it, and
     * the column owning each of those joins.
     *
     * <p>
     * The seam columns are not always covered by the two rooms they lie between. A room may be
     * authored shorter than the section box it was given, so the empty plane between it and its
     * neighbour can fall in a column neither room's bounds reach into &mdash; and that column is where
     * the doorway between them is cut.
     *
     * <p>
     * Ordered, so that the same plan asks for the same chunks in the same order every time.
     */
    public Set<ChunkPos> entranceColumns() {
        PlacedRoom entrance = entrance();
        if (entrance == null) {
            return Set.of();
        }
        Set<ChunkPos> columns = new LinkedHashSet<>();
        cover(entrance.bounds(), columns);
        // The arrival search checks a ring around the exit marker, including across room/chunk edges.
        // Those safety checks must not synchronously generate a new column on first contact.
        BoundingBox arrival = entrance.bounds();
        for (int x = (arrival.minX() >> SECTION_SHIFT) - 1; x <= (arrival.maxX() >> SECTION_SHIFT) + 1; x++) {
            for (int z = (arrival.minZ() >> SECTION_SHIFT) - 1; z <= (arrival.maxZ() >> SECTION_SHIFT) + 1; z++) {
                columns.add(new ChunkPos(x, z));
            }
        }
        for (Seam seam : seams) {
            PlacedRoom neighbour = seam.low().equals(entrance) ? seam.high()
                    : seam.high().equals(entrance) ? seam.low() : null;
            if (neighbour == null) {
                continue;
            }
            cover(neighbour.bounds(), columns);
            columns.add(new ChunkPos(seam.lowSection().getX(), seam.lowSection().getZ()));
        }
        return columns;
    }

    private static void cover(BoundingBox bounds, Set<ChunkPos> columns) {
        for (int cx = bounds.minX() >> SECTION_SHIFT; cx <= bounds.maxX() >> SECTION_SHIFT; cx++) {
            for (int cz = bounds.minZ() >> SECTION_SHIFT; cz <= bounds.maxZ() >> SECTION_SHIFT; cz++) {
                columns.add(new ChunkPos(cx, cz));
            }
        }
    }

    /** The rooms overlapping one chunk column, in layout order. Empty for chunks outside the rift. */
    public List<PlacedRoom> roomsIn(ChunkPos chunk) {
        return byChunk.getOrDefault(chunk.toLong(), Collections.emptyList());
    }

    /** Every join between two rooms, in layout order. */
    public List<Seam> seams() {
        return seams;
    }

    /**
     * The seams one chunk column is responsible for building. A seam belongs to exactly one column
     * and never straddles two, which is what makes it safe to build during decoration: the empty
     * plane is the last block of the low section's column, and the aperture is centred on a socket
     * well inside it.
     */
    public List<Seam> seamsIn(ChunkPos chunk) {
        return seamsByChunk.getOrDefault(chunk.toLong(), Collections.emptyList());
    }

    /** Every archway that leads nowhere, in layout order. */
    public List<Blank> blanks() {
        return blanks;
    }

    /**
     * The archways one chunk column is responsible for walling up. Unlike {@link #seamsIn}, this can
     * be non-empty for a column holding no room: a west- or north-facing archway closes a plane
     * belonging to the column next door, which may be nothing but filler.
     */
    public List<Blank> blanksIn(ChunkPos chunk) {
        return blanksByChunk.getOrDefault(chunk.toLong(), Collections.emptyList());
    }

    /** Chunk columns any room reaches into. */
    public int occupiedColumns() {
        return byChunk.size();
    }

    /**
     * The whole rift's extent in blocks, or null when nothing was placed. Deferred to vanilla
     * because {@code BoundingBox#encapsulate} mutates its receiver: folding it over the rooms
     * directly would widen the first room's own box, so asking a plan its size would corrupt it.
     */
    public BoundingBox bounds() {
        return BoundingBox.encapsulatingBoxes(rooms.stream().map(PlacedRoom::bounds).toList()).orElse(null);
    }
}
