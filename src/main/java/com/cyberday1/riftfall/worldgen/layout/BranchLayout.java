package com.cyberday1.riftfall.worldgen.layout;

import com.cyberday1.riftfall.worldgen.space.RiftSpace;
import com.cyberday1.riftfall.worldgen.space.RoomSocket;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Branch-growth packing of rooms into a volume of chunk sections. Grow a tree from one origin room,
 * matching socket to socket across a section face; hang the boss room off the deepest archway that
 * will take it ({@link #placeBoss}) and a handful of deliberate dead ends off the rest
 * ({@link #growStubs}); then add every join the tree left
 * on the table ({@link #findJoins}) and record every socket that met nothing ({@link #findBlanks}).
 * Rooms abut directly, with no corridor piece; the doorway itself is carved by a later pass from
 * {@link Result}. Depth alternates the role &mdash; {@code depth % mainRoomInterval == 0} draws a main
 * room, which fans out several branches, otherwise a connector, which fans out exactly one.
 */
public final class BranchLayout {

    private BranchLayout() {
    }

    public enum RoomRole {
        ORIGIN,
        MAIN,
        CONNECTOR,
        STUB,
        BOSS,
        /** The stair piece between an ordinary room and a boss room entered above floor level. */
        APPROACH,
        /** An ore room, hung off a free archway until the rift has its quota ({@link #growOreRooms}). */
        ORE
    }

    /** One stub attempted per this many rooms of the cap, so the count curves with the tier. */
    private static final int ROOMS_PER_STUB = 8;

    /**
     * @param maxDepth         branches at this depth place no children
     * @param branchRate       children per main room; the fraction is the chance of one extra
     * @param mainRoomInterval every Nth depth draws main rooms instead of connectors
     * @param originBranches   children hung off the origin room
     * @param maxRooms         hard cap, so a pathological pool cannot run forever
     * @param stubRooms        dead ends wanted on top of {@code maxRooms}; see {@link #growStubs}
     * @param oreRooms         ore rooms the rift must hold, topped up after growth; see {@link #growOreRooms}
     */
    public record Config(int maxDepth, double branchRate, int mainRoomInterval, int originBranches, int maxRooms,
                         int stubRooms, int oreRooms) {
        /** No ore quota: the version-1 shape. */
        public Config(int maxDepth, double branchRate, int mainRoomInterval, int originBranches, int maxRooms,
                      int stubRooms) {
            this(maxDepth, branchRate, mainRoomInterval, originBranches, maxRooms, stubRooms, 0);
        }

        public Config {
            if (oreRooms < 0) {
                throw new IllegalArgumentException("oreRooms must be >= 0");
            }
            if (maxDepth < 0) {
                throw new IllegalArgumentException("maxDepth must be >= 0");
            }
            if (branchRate < 0.0) {
                throw new IllegalArgumentException("branchRate must be >= 0");
            }
            if (mainRoomInterval < 1) {
                throw new IllegalArgumentException("mainRoomInterval must be >= 1");
            }
            if (maxRooms < 1) {
                throw new IllegalArgumentException("maxRooms must be >= 1");
            }
            if (stubRooms < 0) {
                throw new IllegalArgumentException("stubRooms must be >= 0");
            }
        }

        /**
         * A config of the given size. The four numbers filled in here are the same at every
         * difficulty on purpose: a harder rift is a longer walk, not a differently shaped one.
         * The stub count is the one exception and only in appearance &mdash; it is a fixed
         * <em>proportion</em>, so the shape holds while the absolute number follows the cap.
         */
        public static Config sized(int maxDepth, int maxRooms) {
            return sized(maxDepth, maxRooms, 0);
        }

        /** As above, with an ore-room quota. */
        public static Config sized(int maxDepth, int maxRooms, int oreRooms) {
            return new Config(maxDepth, 2.5, 2, 4, maxRooms, maxRooms / ROOMS_PER_STUB, oreRooms);
        }
    }

    /** Candidate orientations, already sized and socketed. The layout owns all the geometry. */
    @FunctionalInterface
    public interface RoomSupply {
        List<RiftSpace> rooms(RoomRole role, RandomSource random);

        /** Whether a placed room counts toward the ore quota. */
        default boolean ore(RiftSpace room) {
            return false;
        }
    }

    /**
     * One placed room and the join that got it there.
     *
     * @param parentIndex  index into {@link Result#placements()}, or -1 for the origin
     * @param parentSocket the parent socket consumed; {@code socket} is the one that met it. Both
     *                     null for the origin.
     */
    public record Placement(RiftSpace space, int parentIndex, @Nullable RoomSocket parentSocket,
                            @Nullable RoomSocket socket, int depth) {
    }

    /**
     * A doorway between two already-placed rooms. {@code fromSocket} belongs to {@code fromIndex} and
     * points at {@code toIndex}, the same shape {@link Placement} records a parent join in.
     */
    public record Join(int fromIndex, int toIndex, RoomSocket fromSocket) {
    }

    /**
     * An archway that leads nowhere: a hole the room's author already cut in its own wall, standing
     * open onto the filler or onto a neighbour's blank back. {@code facesRoom} decides nothing; it
     * is recorded only so the two cases can be counted apart.
     */
    public record Blank(int roomIndex, RoomSocket socket, boolean facesRoom) {
    }

    /**
     * @param placements index 0 is always the origin
     * @param joins      doorways beyond the parent edges; {@code blanks} are archways that met nothing
     * @param considered placements evaluated, of which {@code rejected} failed overlap or envelope;
     *                   {@code deadEnds} counts branch steps that found no legal placement at all
     * @param stubs      how many of {@code placements} came from {@link #growStubs} rather than growth.
     *                   They are the last {@code stubs} entries, since the pass runs after growth and
     *                   appends; growth's own placements keep the indices they had.
     * @param boss       index into {@code placements} of the one boss room, or -1 if none was placed
     */
    public record Result(List<Placement> placements, List<Join> joins, List<Blank> blanks, int considered,
                         int rejected, int deadEnds, int stubs, int boss) {
        public Result {
            placements = List.copyOf(placements);
            joins = List.copyOf(joins);
            blanks = List.copyOf(blanks);
        }

        public int roomCount() {
            return placements.size();
        }

        public int maxDepth() {
            int max = 0;
            for (Placement placement : placements) {
                max = Math.max(max, placement.depth());
            }
            return max;
        }
    }

    /** A candidate join, before it is committed. */
    public record Candidate(RiftSpace space, RoomSocket parentSocket, RoomSocket socket) {
    }

    /** Grows a layout around {@code centre}, the section the origin room is centred on. */
    public static Result generate(Config config, RoomSupply supply, RiftShape shape, Vec3i centre,
                                  RandomSource random) {
        SectionOccupancy occupancy = new SectionOccupancy();
        List<Placement> placements = new ArrayList<>();
        int[] stats = new int[3]; // considered, rejected, deadEnds

        RiftSpace origin = placeOrigin(supply, shape, centre, random, occupancy);
        if (origin == null) {
            return new Result(List.of(), List.of(), List.of(), stats[0], stats[1], stats[2], 0, -1);
        }
        placements.add(new Placement(origin, -1, null, null, 0));

        Deque<Branch> queue = new ArrayDeque<>();
        queue.add(new Branch(0, config.originBranches(), 0));

        while (!queue.isEmpty() && placements.size() < config.maxRooms()) {
            Branch branch = queue.removeFirst();
            if (branch.depth() >= config.maxDepth()) {
                continue;
            }
            boolean main = branch.depth() % config.mainRoomInterval() == 0;
            RoomRole role = main ? RoomRole.MAIN : RoomRole.CONNECTOR;

            for (int i = 0; i < branch.branches() && placements.size() < config.maxRooms(); i++) {
                RiftSpace parent = placements.get(branch.placementIndex()).space();
                List<Candidate> candidates =
                        findPlacements(parent, supply.rooms(role, random), occupancy, shape, stats);
                if (candidates.isEmpty()) {
                    stats[2]++;
                    break;
                }
                Candidate chosen = choose(candidates, occupancy, random);
                occupancy.place(chosen.space());
                int childIndex = placements.size();
                placements.add(new Placement(chosen.space(), branch.placementIndex(), chosen.parentSocket(),
                        chosen.socket(), branch.depth() + 1));
                queue.add(new Branch(childIndex, childBranches(config, main, random), branch.depth() + 1));
            }
        }

        int grown = placements.size();
        // Before the stubs, so the deepest archways are still unspoken for when the boss room picks.
        int boss = placeBoss(supply, shape, placements, occupancy, random, stats);

        // After the boss, so the arena keeps first pick of the deep archways; before the stubs, so the
        // stub count and the "stubs are the last placements" contract are unchanged.
        growOreRooms(config, supply, shape, placements, grown, occupancy, random, stats);

        // Before parentFaces, so a stub's own doorway is claimed like any other parent edge and
        // neither pass below touches it.
        int stubs = growStubs(config, supply, shape, placements, occupancy, random, stats);

        // Order matters, in one direction only: findJoins fills `claimed` with every face that is
        // going to become a doorway, and findBlanks walls up whatever is left. The other way round
        // bricks up half the rift's doors.
        Set<Face> claimed = parentFaces(placements);
        List<Join> joins = findJoins(placements, occupancy, claimed);
        return new Result(placements, joins, findBlanks(placements, occupancy, claimed),
                stats[0], stats[1], stats[2], stubs, boss);
    }

    /**
     * Hangs the one boss room as deep as the layout reached. The same shape as {@link #growStubs} and
     * for the same reasons &mdash; snapshot the archways that lead nowhere, then take one &mdash; with
     * two differences: the candidates are ordered by parent depth rather than shuffled flat, and it
     * stops at the first that lands.
     *
     * <p>Deepest <em>reachable</em>, not deepest full stop: a boss room is three sections on a side, so
     * the very last archway of a tightly packed rift often has nothing to offer it, and the walk
     * carries on outwards until one does. {@link #quietest} keeps the arena terminal for the same
     * reason it keeps a stub terminal.
     *
     * <h2>Two ways in</h2>
     * An archway is tried directly first, and only then through an approach room. The order is the
     * point: a boss room whose doorway is at the elevation the rest of the library joins at is placed
     * exactly as it always was, so nothing about a rift changes for having taught the layout this.
     *
     * <p>The second way exists for the boss room that is <em>not</em> at that elevation &mdash; an
     * arena entered on a balcony rather than at floor level. Sockets only pair at matching heights, so
     * such a room can never meet an ordinary one; what it can meet is an approach, the piece authored
     * with its two doorways deliberately at different heights, whose far end is back at the library's.
     * That makes the boss the one room reached through a fixed two-piece chain rather than off any
     * archway going.
     *
     * @return its index in {@code placements}, or -1 when no archway anywhere would take one
     */
    private static int placeBoss(RoomSupply supply, RiftShape shape, List<Placement> placements,
                                 SectionOccupancy occupancy, RandomSource random, int[] stats) {
        List<int[]> eligible = new ArrayList<>();
        for (int index = 0; index < placements.size(); index++) {
            List<RoomSocket> sockets = placements.get(index).space().sockets();
            for (int socket = 0; socket < sockets.size(); socket++) {
                if (sockets.get(socket).direction().getAxis().isVertical()) {
                    continue;
                }
                Vec3i beyond = placements.get(index).space().connectingSection(sockets.get(socket));
                if (occupancy.isFree(beyond) && shape.isSectionValid(beyond)) {
                    eligible.add(new int[] {index, socket});
                }
            }
        }
        shuffle(eligible, random);
        // Stable, so the shuffle above survives as the tie-break within one depth.
        eligible.sort(Comparator.comparingInt((int[] pair) -> placements.get(pair[0]).depth()).reversed());

        List<RiftSpace> arenas = supply.rooms(RoomRole.BOSS, random);
        List<RiftSpace> approaches = supply.rooms(RoomRole.APPROACH, random);
        for (int[] pair : eligible) {
            Placement parent = placements.get(pair[0]);
            RoomSocket parentSocket = parent.space().sockets().get(pair[1]);
            List<Candidate> candidates = placementsFrom(parent.space(), parentSocket, arenas,
                    occupancy, shape, stats);
            if (!candidates.isEmpty()) {
                return commit(placements, occupancy, quietest(candidates, occupancy, random),
                        pair[0], parentSocket, parent.depth() + 1);
            }
            int viaApproach = throughApproach(shape, placements, occupancy, random, stats, pair[0],
                    parentSocket, parent.depth(), approaches, arenas);
            if (viaApproach >= 0) {
                return viaApproach;
            }
        }
        return -1;
    }

    /**
     * One archway's worth of the two-piece chain: stand an approach room in the gap, then look for an
     * arena on the far side of it.
     *
     * <p>
     * The approach has to be claimed before the arena can be looked for &mdash; the arena must not be
     * allowed to occupy the sections the approach is standing in &mdash; so every candidate that does
     * not pan out is given straight back. A stairwell with nothing at the top of it is worse than no
     * boss room at all, which is why this never leaves one behind.
     *
     * <p>
     * Approaches are tried quietest-first for the same reason arenas are: an approach that opens
     * further doorways into the rest of the dungeon is a back door into the boss fight.
     *
     * @return the arena's index in {@code placements}, or -1 if no approach here led to one
     */
    private static int throughApproach(RiftShape shape, List<Placement> placements, SectionOccupancy occupancy,
                                       RandomSource random, int[] stats, int parentIndex,
                                       RoomSocket parentSocket, int parentDepth,
                                       List<RiftSpace> approaches, List<RiftSpace> arenas) {
        List<Candidate> landings = placementsFrom(placements.get(parentIndex).space(), parentSocket,
                approaches, occupancy, shape, stats);
        while (!landings.isEmpty()) {
            Candidate landing = quietest(landings, occupancy, random);
            landings.remove(landing);
            occupancy.place(landing.space());

            for (RoomSocket onward : landing.space().sockets()) {
                if (onward.equals(landing.socket()) || onward.direction().getAxis().isVertical()) {
                    continue;
                }
                List<Candidate> candidates = placementsFrom(landing.space(), onward, arenas,
                        occupancy, shape, stats);
                if (candidates.isEmpty()) {
                    continue;
                }
                int approachIndex = placements.size();
                placements.add(new Placement(landing.space(), parentIndex, parentSocket, landing.socket(),
                        parentDepth + 1));
                return commit(placements, occupancy, quietest(candidates, occupancy, random),
                        approachIndex, onward, parentDepth + 2);
            }
            occupancy.release(landing.space());
        }
        return -1;
    }

    /** Claims a chosen candidate and records it, answering with the index it landed at. */
    private static int commit(List<Placement> placements, SectionOccupancy occupancy, Candidate chosen,
                              int parentIndex, RoomSocket parentSocket, int depth) {
        occupancy.place(chosen.space());
        placements.add(new Placement(chosen.space(), parentIndex, parentSocket, chosen.socket(), depth));
        return placements.size() - 1;
    }

    /**
     * Hangs dead ends off archways that would otherwise have been bricked up, so that some of what a
     * player follows ends in a room with nothing beyond it. Deliberate: {@code findBlanks} closes
     * every unmatched archway, which leaves a rift with no dead ends at all, and a dungeon wants
     * some.
     *
     * <p>Three things make the count a count rather than a lottery. It runs after growth, so it is
     * not competing with {@link Config#maxRooms} &mdash; growth fills that cap, and a stub taken out
     * of it would never be placed. Eligibility is a <em>snapshot</em> taken before the first stub
     * lands, so a stub is never a stub's parent and they cannot chain. And it takes sockets until
     * the target is met rather than rolling a chance per socket: there are on the order of a
     * thousand filler-facing archways in a rift against a target in the tens, so a rate would have
     * been a knob nobody could read.
     *
     * <p><strong>The dead end is usual, not guaranteed.</strong> These are ordinary library pieces,
     * so nothing stops them carrying further archways, and {@link #findJoins} opens any that lands
     * against a neighbour facing back. {@link #quietest} is what keeps that rare &mdash; it is
     * {@link #choose} inverted, taking the placement that opens the <em>fewest</em> further
     * doorways. A piece authored with a single socket would make it exact, and needs no code.
     *
     * @return how many were actually placed, which is below {@link Config#stubRooms()} only when the
     *         layout ran out of room to put them in
     */
    private static int growStubs(Config config, RoomSupply supply, RiftShape shape, List<Placement> placements,
                                 SectionOccupancy occupancy, RandomSource random, int[] stats) {
        if (config.stubRooms() <= 0) {
            return 0;
        }
        List<int[]> eligible = new ArrayList<>();
        for (int index = 0; index < placements.size(); index++) {
            List<RoomSocket> sockets = placements.get(index).space().sockets();
            for (int socket = 0; socket < sockets.size(); socket++) {
                // The same two skips as findBlanks, for the same reason: no authored aperture in a
                // floor, and a section the room already owns is an interior wall.
                if (sockets.get(socket).direction().getAxis().isVertical()) {
                    continue;
                }
                Vec3i beyond = placements.get(index).space().connectingSection(sockets.get(socket));
                if (occupancy.isFree(beyond) && shape.isSectionValid(beyond)) {
                    eligible.add(new int[] {index, socket});
                }
            }
        }
        shuffle(eligible, random);

        int placed = 0;
        for (int[] pair : eligible) {
            if (placed >= config.stubRooms()) {
                break;
            }
            Placement parent = placements.get(pair[0]);
            RoomSocket parentSocket = parent.space().sockets().get(pair[1]);
            // Re-asked rather than trusted: an earlier stub this pass may have taken the section,
            // and two archways of the same room can face the same one.
            if (!occupancy.isFree(parent.space().connectingSection(parentSocket))) {
                continue;
            }
            List<Candidate> candidates = placementsFrom(parent.space(), parentSocket,
                    supply.rooms(RoomRole.STUB, random), occupancy, shape, stats);
            if (candidates.isEmpty()) {
                continue;
            }
            Candidate chosen = quietest(candidates, occupancy, random);
            occupancy.place(chosen.space());
            placements.add(new Placement(chosen.space(), pair[0], parentSocket, chosen.socket(),
                    parent.depth() + 1));
            placed++;
        }
        return placed;
    }

    /**
     * Tops the rift up to {@link Config#oreRooms()} ore rooms: the ones growth already drew count, and the
     * rest are hung off free archways the way {@link #growStubs} hangs dead ends (a shuffled snapshot,
     * quietest placement). With a quota of 0 it draws nothing, so a version-1 layout is unchanged.
     *
     * @return ore rooms placed by this pass
     */
    static int growOreRooms(Config config, RoomSupply supply, RiftShape shape, List<Placement> placements,
                            int grown, SectionOccupancy occupancy, RandomSource random, int[] stats) {
        if (config.oreRooms() <= 0) {
            return 0;
        }
        int have = 0;
        for (Placement placement : placements) {
            if (supply.ore(placement.space())) have++;
        }
        int wanted = config.oreRooms() - have;
        if (wanted <= 0) {
            return 0;
        }
        // Grown rooms only: never off the boss arena or its approach, which would be a back door into the fight.
        List<int[]> eligible = new ArrayList<>();
        for (int index = 0; index < grown; index++) {
            List<RoomSocket> sockets = placements.get(index).space().sockets();
            for (int socket = 0; socket < sockets.size(); socket++) {
                if (sockets.get(socket).direction().getAxis().isVertical()) {
                    continue;
                }
                Vec3i beyond = placements.get(index).space().connectingSection(sockets.get(socket));
                if (occupancy.isFree(beyond) && shape.isSectionValid(beyond)) {
                    eligible.add(new int[] {index, socket});
                }
            }
        }
        shuffle(eligible, random);
        int placed = 0;
        for (int[] pair : eligible) {
            if (placed >= wanted) {
                break;
            }
            Placement parent = placements.get(pair[0]);
            RoomSocket parentSocket = parent.space().sockets().get(pair[1]);
            if (!occupancy.isFree(parent.space().connectingSection(parentSocket))) {
                continue;
            }
            List<Candidate> candidates = placementsFrom(parent.space(), parentSocket,
                    supply.rooms(RoomRole.ORE, random), occupancy, shape, stats);
            if (candidates.isEmpty()) {
                continue;
            }
            Candidate chosen = quietest(candidates, occupancy, random);
            occupancy.place(chosen.space());
            placements.add(new Placement(chosen.space(), pair[0], parentSocket, chosen.socket(),
                    parent.depth() + 1));
            placed++;
        }
        return placed;
    }

    /** {@link #choose} inverted: the placement that opens the fewest further doorways, ties at random. */
    private static Candidate quietest(List<Candidate> candidates, SectionOccupancy occupancy, RandomSource random) {
        List<Candidate> best = new ArrayList<>();
        int bestScore = Integer.MAX_VALUE;
        for (Candidate candidate : candidates) {
            int score = extraMatches(candidate.space(), candidate.socket(), occupancy);
            if (score < bestScore) {
                bestScore = score;
                best.clear();
            }
            if (score == bestScore) {
                best.add(candidate);
            }
        }
        return best.get(random.nextInt(best.size()));
    }

    private static void shuffle(List<int[]> entries, RandomSource random) {
        for (int index = entries.size() - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            int[] held = entries.get(index);
            entries.set(index, entries.get(swap));
            entries.set(swap, held);
        }
    }

    /**
     * Which legal placement to take: a room drawn evenly, then put wherever it meets the most.
     * {@link #findPlacements} returns one entry per parent socket per orientation per socket facing
     * back, so drawing from the flat list would favour socket-rich rooms in proportion to their
     * socket count, inflating the very thing being measured.
     */
    private static Candidate choose(List<Candidate> candidates, SectionOccupancy occupancy, RandomSource random) {
        List<Candidate> room = drawRoom(candidates, random);

        List<Candidate> best = new ArrayList<>();
        int bestScore = -1;
        for (Candidate candidate : room) {
            int score = extraMatches(candidate.space(), candidate.socket(), occupancy);
            if (score > bestScore) {
                bestScore = score;
                best.clear();
            }
            if (score == bestScore) {
                best.add(candidate);
            }
        }
        return best.get(random.nextInt(best.size()));
    }

    /**
     * One authored room's worth of the candidate list, drawn evenly over the rooms present. Keyed on
     * the template, so all eight orientations of a room count as the one room they are.
     * Insertion-ordered: the group is picked by index and a rift must be a function of its seed.
     */
    private static List<Candidate> drawRoom(List<Candidate> candidates, RandomSource random) {
        Map<ResourceLocation, List<Candidate>> byTemplate = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            byTemplate.computeIfAbsent(candidate.space().template(), key -> new ArrayList<>()).add(candidate);
        }
        if (byTemplate.size() < 2) {
            return candidates;
        }
        List<List<Candidate>> groups = new ArrayList<>(byTemplate.values());
        return groups.get(random.nextInt(groups.size()));
    }

    /**
     * How many doorways this placement opens beyond the one that puts it there. The arriving socket is
     * excluded: matched by construction, so counting it ranks nothing.
     */
    private static int extraMatches(RiftSpace room, @Nullable RoomSocket attachedBy, SectionOccupancy occupancy) {
        int matches = 0;
        for (RoomSocket socket : room.sockets()) {
            if (socket.equals(attachedBy) || socket.direction().getAxis().isVertical()) {
                continue;
            }
            Vec3i beyond = room.connectingSection(socket);
            RiftSpace neighbour = occupancy.occupant(beyond);
            if (neighbour != null && opens(neighbour, beyond, socket)) {
                matches++;
            }
        }
        return matches;
    }

    /**
     * Every doorway the growth pass left on the table: a socket facing an occupied section whose
     * occupant declares a socket facing back, which by the library's convention is two authors
     * saying there is a door here. Growth alone is a tree, so tight packing leaves most matched
     * pairs unjoined. Taken unconditionally, because a refused pair leaves two walled archways a
     * block apart facing each other. Keyed on the face rather than the room pair, so two rooms
     * meeting along a long wall get every doorway.
     */
    private static List<Join> findJoins(List<Placement> placements, SectionOccupancy occupancy, Set<Face> claimed) {
        Map<RiftSpace, Integer> indices = new IdentityHashMap<>(placements.size());
        for (int index = 0; index < placements.size(); index++) {
            indices.put(placements.get(index).space(), index);
        }

        List<Join> joins = new ArrayList<>();
        for (int index = 0; index < placements.size(); index++) {
            RiftSpace room = placements.get(index).space();
            for (RoomSocket socket : room.sockets()) {
                // No authored aperture in a floor to line a doorway up with. Same skip in findBlanks
                // and in RiftPlan#seamOf.
                if (socket.direction().getAxis().isVertical()) {
                    continue;
                }
                Vec3i beyond = room.connectingSection(socket);
                RiftSpace neighbour = occupancy.occupant(beyond);
                if (neighbour == null || neighbour == room) {
                    continue;
                }
                Integer other = indices.get(neighbour);
                if (other == null || !opens(neighbour, beyond, socket)) {
                    continue;
                }
                if (claimed.add(Face.of(room, socket))) {
                    joins.add(new Join(index, other, socket));
                }
            }
        }
        return joins;
    }

    /** The doorways growth itself made, one per room bar the origin, named by the face they occupy. */
    private static Set<Face> parentFaces(List<Placement> placements) {
        Set<Face> faces = new HashSet<>();
        for (Placement placement : placements) {
            if (placement.parentSocket() != null && placement.parentIndex() >= 0) {
                faces.add(Face.of(placements.get(placement.parentIndex()).space(), placement.parentSocket()));
            }
        }
        return faces;
    }

    /**
     * Every archway that is going to lead nowhere, so something can be built in front of it: the
     * other half of {@link #findJoins}, running on what that pass left. Two skips, both the same
     * rule &mdash; the doorway convention holds only at a room's outer boundary. A vertical socket
     * has no authored aperture in a floor; a socket whose neighbouring section is the room's own is
     * an interior wall, and building there would wall the room in half.
     */
    private static List<Blank> findBlanks(List<Placement> placements, SectionOccupancy occupancy, Set<Face> claimed) {
        List<Blank> blanks = new ArrayList<>();
        for (int index = 0; index < placements.size(); index++) {
            RiftSpace room = placements.get(index).space();
            for (RoomSocket socket : room.sockets()) {
                if (socket.direction().getAxis().isVertical()) {
                    continue;
                }
                RiftSpace neighbour = occupancy.occupant(room.connectingSection(socket));
                if (neighbour == room) {
                    continue;
                }
                if (claimed.add(Face.of(room, socket))) {
                    blanks.add(new Blank(index, socket, neighbour != null));
                }
            }
        }
        return blanks;
    }

    /**
     * Whether {@code room} declares a doorway out of {@code section} that {@code against} could
     * actually be joined to: facing back, and at the same height. Two rooms whose openings miss each
     * other vertically are not a doorway, and opening the seam between them would leave a hole onto
     * an unbroken wall.
     */
    private static boolean opens(RiftSpace room, Vec3i section, RoomSocket against) {
        for (RoomSocket socket : room.sockets()) {
            if (against.pairsWith(socket) && socket.section(room.origin()).equals(section)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The face two rooms meet across, named from the low section and the axis rather than the socket:
     * one face is an east socket to one room and a west socket to the other, and a doorway must not be
     * built twice for being described twice.
     */
    private record Face(Vec3i lowSection, Direction.Axis axis) {
        static Face of(RiftSpace room, RoomSocket socket) {
            Vec3i section = socket.section(room.origin());
            Direction direction = socket.direction();
            return new Face(direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? section
                    : section.relative(direction), direction.getAxis());
        }
    }

    /**
     * Every legal way to hang one of {@code candidates} off {@code parent}: for each free
     * neighbouring section and each candidate socket facing back, offset the candidate so the two
     * socket sections land either side of the shared face, then test overlap and envelope. Sockets
     * pair on <em>opposing</em> directions, because a jigsaw points out of its own piece.
     */
    public static List<Candidate> findPlacements(RiftSpace parent, List<RiftSpace> candidates,
                                                 SectionOccupancy occupancy, RiftShape shape) {
        return findPlacements(parent, candidates, occupancy, shape, new int[3]);
    }

    private static List<Candidate> findPlacements(RiftSpace parent, List<RiftSpace> candidates,
                                                  SectionOccupancy occupancy, RiftShape shape, int[] stats) {
        List<Candidate> found = new ArrayList<>();
        for (RoomSocket parentSocket : parent.sockets()) {
            found.addAll(placementsFrom(parent, parentSocket, candidates, occupancy, shape, stats));
        }
        return found;
    }

    /** One parent socket's worth of the above, which is all {@link #growStubs} ever wants. */
    private static List<Candidate> placementsFrom(RiftSpace parent, RoomSocket parentSocket,
                                                  List<RiftSpace> candidates, SectionOccupancy occupancy,
                                                  RiftShape shape, int[] stats) {
        Vec3i connecting = parent.connectingSection(parentSocket);
        if (!occupancy.isFree(connecting) || !shape.isSectionValid(connecting)) {
            return List.of();
        }
        List<Candidate> found = new ArrayList<>();
        for (RiftSpace candidate : candidates) {
            for (RoomSocket socket : candidate.sockets()) {
                if (!parentSocket.pairsWith(socket)) {
                    continue;
                }
                stats[0]++;
                RiftSpace placed = candidate.at(connecting.subtract(socket.position()));
                if (occupancy.canPlace(placed, shape)) {
                    found.add(new Candidate(placed, parentSocket, socket));
                } else {
                    stats[1]++;
                }
            }
        }
        return found;
    }

    @Nullable
    private static RiftSpace placeOrigin(RoomSupply supply, RiftShape shape, Vec3i centre, RandomSource random,
                                         SectionOccupancy occupancy) {
        List<RiftSpace> candidates = new ArrayList<>(supply.rooms(RoomRole.ORIGIN, random));
        while (!candidates.isEmpty()) {
            RiftSpace candidate = candidates.remove(random.nextInt(candidates.size()));
            Vec3i size = candidate.size();
            RiftSpace placed = candidate.at(centre.subtract(new Vec3i(size.getX() / 2, size.getY() / 2, size.getZ() / 2)));
            if (occupancy.canPlace(placed, shape)) {
                occupancy.place(placed);
                return placed;
            }
        }
        return null;
    }

    private static int childBranches(Config config, boolean main, RandomSource random) {
        if (!main) {
            return 1;
        }
        int whole = (int) Math.floor(config.branchRate());
        double fraction = config.branchRate() - whole;
        return fraction > 0.0 && random.nextDouble() < fraction ? whole + 1 : whole;
    }

    private record Branch(int placementIndex, int branches, int depth) {
    }
}
