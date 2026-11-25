package com.cyberday1.riftfall.worldgen.poi;

import com.cyberday1.riftfall.worldgen.space.JigsawInfo;
import com.cyberday1.riftfall.worldgen.space.TripleMirror;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Furnishes a room by resolving the props its builder anchored into it.
 *
 * <p>
 * The whole design rests on one decision made upstream and worth restating: <strong>nothing is
 * inferred from geometry.</strong> A builder places a {@code minecraft:jigsaw} block and types a
 * target pool such as {@code riftfall:rift/poi/inwall/7}, and that string is the entire declaration
 * &mdash; category and footprint both. There is no scan for flat ground, no "does this look like a
 * ceiling" heuristic, and so no way for the generator to disagree with the person who built the
 * room. This class only decides <em>which</em> piece fills each declared spot, and whether it fits.
 *
 * <p>
 * Recursion is the second half: a resolved prop carries its own jigsaws, so a bookshelf can anchor
 * a candle. Depth is bounded here where the reference implementation leaves it open &mdash; two
 * pools that can reach each other would otherwise recurse until the stack gives out, and a content
 * bug in a datapack should not be able to take the server down.
 *
 * <p>
 * Ported from Wanderers of the Rift's {@code RiftGeneratable.processGeneratable} (AGPL-3.0); see
 * {@code ATTRIBUTION.md}.
 */
public final class PoiRecursion {

    /** Deep enough for a prop on a prop on a prop, shallow enough that a pool cycle cannot hang. */
    public static final int DEFAULT_MAX_DEPTH = 8;

    /**
     * Vanilla's terminator pool. A jigsaw pointing at it is a socket &mdash; the thing a piece
     * offers so a parent can attach it &mdash; not an anchor asking to be filled, and a piece's
     * socket is still in its list when the recursion descends into it. Resolving it would find
     * nothing, so what matters is that finding nothing there is not reported as a content bug.
     */
    private static final ResourceLocation EMPTY_POOL = ResourceLocation.withDefaultNamespace("empty");

    private PoiRecursion() {
    }

    /**
     * A piece that has been drawn from a pool and is ready to position: its footprint and its own
     * anchors, both in the authored, untransformed frame.
     *
     * @param size the template's block extent, untransformed
     */
    public record Piece(ResourceLocation id, Vec3i size, List<JigsawInfo> jigsaws) {
        public Piece {
            jigsaws = List.copyOf(jigsaws);
        }
    }

    /** Draws one weighted piece from a pool, or null when the pool is empty. */
    @FunctionalInterface
    public interface PieceResolver {
        @Nullable
        Piece resolve(ResourceLocation pool, RandomSource random);
    }

    /** Receives every piece that survived collision, in the order it was accepted. */
    @FunctionalInterface
    public interface PlacementSink {
        void place(Piece piece, Vec3i shift, TripleMirror mirror);
    }

    /** Rewrites a piece's anchor list before any of it is resolved. See {@link JigsawFilters}. */
    @FunctionalInterface
    public interface JigsawListProcessor {
        void process(List<JigsawInfo> jigsaws, RandomSource random);
    }

    /**
     * @param placed   pieces handed to the sink, including the room itself
     * @param rejected anchors whose chosen piece collided with something already placed
     * @param unfilled anchors that resolved to nothing, or to a piece with no socket facing back.
     *                 Sockets &mdash; jigsaws targeting {@code minecraft:empty} &mdash; are not
     *                 counted, so a non-zero value here is a content bug worth chasing rather than
     *                 the ordinary business of pieces carrying their own attachment points.
     */
    public record Result(int placed, int rejected, int unfilled) {
    }

    /**
     * Furnishes one room.
     *
     * <p>
     * The room is placed unconditionally &mdash; it has already won its space in the layout pass, so
     * there is nothing for it to collide with. Its own footprint goes into the mask, so props cannot
     * clip into its walls, but only after its anchors have been resolved.
     *
     * @param shift the room's offset within the mask volume, in blocks
     */
    public static Result furnish(Piece room, Vec3i shift, TripleMirror mirror, PieceResolver resolver,
                                 List<JigsawListProcessor> processors, RandomSource random, PlacementSink sink) {
        return furnish(room, shift, mirror, resolver, processors, random, sink, new CollisionMask(),
                DEFAULT_MAX_DEPTH);
    }

    public static Result furnish(Piece room, Vec3i shift, TripleMirror mirror, PieceResolver resolver,
                                 List<JigsawListProcessor> processors, RandomSource random, PlacementSink sink,
                                 CollisionMask mask, int maxDepth) {
        int[] stats = new int[3];
        process(room, shift, mirror, false, resolver, processors, random, sink, mask, maxDepth, stats);
        return new Result(stats[0], stats[1], stats[2]);
    }

    /**
     * One node of the recursion.
     *
     * <p>
     * Order matters and is not the obvious one. The piece is collision-tested on entry, then its
     * children are resolved, and only <em>afterwards</em> is its own volume written into the mask.
     * So a child is positioned against a mask that does not yet contain its parent, which is what
     * lets a candle sit on the bookshelf that anchored it rather than being rejected for
     * overlapping it. Siblings still exclude each other, because each writes itself before the next
     * is considered.
     */
    private static void process(Piece piece, Vec3i shift, TripleMirror mirror, boolean test, PieceResolver resolver,
                                List<JigsawListProcessor> processors, RandomSource random, PlacementSink sink,
                                CollisionMask mask, int depthLeft, int[] stats) {
        Vec3i footprint = mirror.transformSize(piece.size());
        if (test && mask.collides(shift, footprint)) {
            stats[1]++;
            return;
        }

        if (depthLeft > 0) {
            List<JigsawInfo> anchors = new ArrayList<>(piece.jigsaws());
            for (JigsawListProcessor processor : processors) {
                processor.process(anchors, random);
            }
            for (JigsawInfo anchor : anchors) {
                resolveAnchor(piece, anchor, shift, mirror, resolver, processors, random, sink, mask, depthLeft, stats);
            }
        }

        mask.write(shift, footprint);
        sink.place(piece, shift, mirror);
        stats[0]++;
    }

    private static void resolveAnchor(Piece parent, JigsawInfo anchor, Vec3i shift, TripleMirror mirror,
                                      PieceResolver resolver, List<JigsawListProcessor> processors,
                                      RandomSource random, PlacementSink sink, CollisionMask mask, int depthLeft,
                                      int[] stats) {
        if (anchor.pool().equals(EMPTY_POOL)) {
            return;
        }

        Piece child = resolver.resolve(anchor.pool(), random);
        if (child == null) {
            stats[2]++;
            return;
        }

        Direction anchorFacing = mirror.applyToDirection(anchor.front());
        Direction wanted = anchorFacing.getOpposite();

        List<JigsawInfo> sockets = matchingSockets(child, anchor, anchorFacing);
        if (sockets.isEmpty()) {
            // A content bug, not a code path: the builder pointed at a pool whose pieces cannot
            // connect back. Counting it is how it reaches whoever authored the piece.
            stats[2]++;
            return;
        }
        JigsawInfo socket = sockets.get(random.nextInt(sockets.size()));

        TripleMirror childMirror = orientToward(socket, wanted, anchorFacing, anchor, mirror, random);
        if (childMirror == null) {
            stats[2]++;
            return;
        }

        Vec3i anchorPos = mirror.apply(anchor.pos(), parent.size());
        Vec3i socketPos = childMirror.apply(socket.pos(), child.size());
        Vec3i childShift = shift.offset(anchorPos).subtract(socketPos).relative(anchorFacing);

        process(child, childShift, childMirror, true, resolver, processors, random, sink, mask, depthLeft - 1, stats);
    }

    /**
     * The child's sockets that could connect back to {@code anchor}.
     *
     * <p>
     * A socket qualifies on name first: its own name must equal the anchor's target, which is how a
     * pool of mixed pieces declares which of them are wall-mountable. Then on direction. For a
     * horizontal anchor any horizontal socket qualifies, because the child can always be turned to
     * face the right way; for a vertical anchor the socket must already point the opposite way,
     * because {@link TripleMirror} never touches Y and no amount of turning will flip a
     * ceiling-hung piece onto the floor.
     */
    private static List<JigsawInfo> matchingSockets(Piece child, JigsawInfo anchor, Direction anchorFacing) {
        List<JigsawInfo> matches = new ArrayList<>();
        for (JigsawInfo socket : child.jigsaws()) {
            if (!socket.name().equals(anchor.target())) {
                continue;
            }
            Direction front = socket.front();
            boolean bothHorizontal = anchorFacing.getAxis().isHorizontal() && front.getAxis().isHorizontal();
            if (bothHorizontal || front == anchorFacing.getOpposite()) {
                matches.add(socket);
            }
        }
        return matches;
    }

    /**
     * Picks a random orientation for the child among those that actually point its socket back at
     * the anchor.
     *
     * <p>
     * Searching the eight elements is deliberate. The reference implementation derives the
     * correction by setting and clearing bits of the permutation index, which is faster and depends
     * entirely on the permutation list being in one exact order &mdash; a silent, seed-dependent
     * mis-orientation if that order ever changes. Filtering on
     * {@link TripleMirror#applyToDirection(Direction)} states the requirement instead of encoding
     * it, and eight iterations against a structure-template placement is free.
     *
     * <p>
     * For a horizontal socket exactly two of the eight qualify, so the child still gets a coin flip
     * of variety. For a vertical one, Y is fixed and the constraint moves to the socket's up-vector,
     * but only when both sides declared an {@code aligned} joint; a {@code rollable} ceiling prop is
     * free to spin.
     */
    @Nullable
    private static TripleMirror orientToward(JigsawInfo socket, Direction wanted, Direction anchorFacing,
                                             JigsawInfo anchor, TripleMirror anchorMirror, RandomSource random) {
        boolean vertical = anchorFacing.getAxis().isVertical();
        boolean aligned = anchor.joint() == JigsawBlockEntity.JointType.ALIGNED
                && socket.joint() == JigsawBlockEntity.JointType.ALIGNED;

        Direction wantedTop = vertical && aligned ? anchorMirror.applyToDirection(anchor.top()) : null;

        List<TripleMirror> valid = new ArrayList<>(8);
        for (TripleMirror candidate : TripleMirror.PERMUTATIONS) {
            if (!vertical && candidate.applyToDirection(socket.front()) != wanted) {
                continue;
            }
            if (wantedTop != null && candidate.applyToDirection(socket.top()) != wantedTop) {
                continue;
            }
            valid.add(candidate);
        }
        return valid.isEmpty() ? null : valid.get(random.nextInt(valid.size()));
    }
}
