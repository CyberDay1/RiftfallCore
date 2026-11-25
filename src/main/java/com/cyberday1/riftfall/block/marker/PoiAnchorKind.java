package com.cyberday1.riftfall.block.marker;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.processor.ProcessorSlot;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;

/**
 * The four ways a prop hangs, one block each.
 *
 * <p>
 * A prop anchor is a {@code minecraft:jigsaw} with six fields on it, five of which are the same for
 * every anchor in the library and the sixth of which is a pool path a builder has to type by hand.
 * These blocks are that jigsaw with the typing already done: place one, pick a size, and
 * {@link com.cyberday1.riftfall.worldgen.space.TemplateJigsaws#parse} turns it into the anchor at
 * parse time. Nothing downstream can tell one from an authored jigsaw.
 *
 * <p>
 * <strong>The socket faces away from its own body.</strong> That single fact is where all four
 * geometries come from: a {@code free} anchor's piece grows upward out of the cell above it, so the
 * socket points {@link Direction#UP}; a {@code ceiling} anchor's hangs below, so it points down; an
 * {@code inwall} anchor's is sunk into the wall behind it, so it points out of that wall.
 *
 * <p>
 * {@code halfway} is the reason these blocks are worth having at all. Its geometry is
 * {@code free}'s, but it is the one category authored {@code aligned} without exception (74 of 74),
 * so the piece's rotation is load-bearing against the terrain it is sunk into and nothing in a
 * template says which way is right. The guesser will not touch it. A builder placing the block
 * chooses that rotation by which way they were looking, which is the only place the answer exists.
 */
public enum PoiAnchorKind {

    /** Stands on the floor. The anchor cell is the floor under it. */
    FREE("poi_free", "POI Anchor (Free)", "free", false,
            RiftfallCore.id(ProcessorSlot.SLOT_3.familyId()), JigsawBlockEntity.JointType.ROLLABLE),

    /** Hangs from the ceiling. The anchor cell is the ceiling above it. */
    CEILING("poi_ceiling", "POI Anchor (Ceiling)", "ceiling", false,
            RiftfallCore.id(ProcessorSlot.SLOT_1.familyId()), JigsawBlockEntity.JointType.ROLLABLE),

    /** Set into a wall. Placed facing the way you want the niche to open. */
    INWALL("poi_inwall", "POI Anchor (In-Wall)", "inwall", true,
            ResourceLocation.withDefaultNamespace("air"), JigsawBlockEntity.JointType.ROLLABLE),

    /** Sunk into the floor, and rotated to match it. Placed facing the way you want it turned. */
    HALFWAY("poi_halfway", "POI Anchor (Halfway)", "halfway", true,
            RiftfallCore.id(ProcessorSlot.SLOT_3.familyId()), JigsawBlockEntity.JointType.ALIGNED);

    /** Every kind, in registration order. Cached because {@code values()} copies. */
    public static final PoiAnchorKind[] ALL = values();

    private final String blockId;
    private final String displayName;
    private final String poolGroup;
    private final boolean placementFacing;
    private final ResourceLocation finalState;
    private final JigsawBlockEntity.JointType joint;

    PoiAnchorKind(String blockId, String displayName, String poolGroup, boolean placementFacing,
                  ResourceLocation finalState, JigsawBlockEntity.JointType joint) {
        this.blockId = blockId;
        this.displayName = displayName;
        this.poolGroup = poolGroup;
        this.placementFacing = placementFacing;
        this.finalState = finalState;
        this.joint = joint;
    }

    /** The registry path, which is also the model and blockstate file name. */
    public String blockId() {
        return blockId;
    }

    /** en_us name. */
    public String displayName() {
        return displayName;
    }

    /** Whether the block carries a horizontal facing taken from how it was placed. */
    public boolean hasPlacementFacing() {
        return placementFacing;
    }

    /**
     * What the anchor cell becomes once the prop is stamped. A jigsaw's {@code final_state}, and the
     * mode of what builders put on their own anchors of this category: a floor under a
     * free-standing piece, a ceiling over a hanging one, and air behind a niche.
     */
    public ResourceLocation finalState() {
        return finalState;
    }

    /** Rollable everywhere but {@code halfway} &mdash; see the class comment. */
    public JigsawBlockEntity.JointType joint() {
        return joint;
    }

    /** The pool a piece of this category and footprint is drawn from. */
    public ResourceLocation pool(PoiAnchorSize size) {
        return RiftfallCore.id("rift/poi/" + poolGroup + "/" + size.blocks());
    }

    /**
     * The jigsaw orientation this anchor stands for, given the horizontal direction the block was
     * placed facing.
     *
     * <p>
     * Front is where the piece grows; top is which way up it is. For a vertical front the top is
     * only meaningful when the joint is {@code aligned}, which is why {@code halfway} spends the
     * placement facing on the top and everything else pins it to north.
     */
    public FrontAndTop orientation(Direction facing) {
        return switch (this) {
            case FREE -> FrontAndTop.fromFrontAndTop(Direction.UP, Direction.NORTH);
            case CEILING -> FrontAndTop.fromFrontAndTop(Direction.DOWN, Direction.NORTH);
            case INWALL -> FrontAndTop.fromFrontAndTop(facing, Direction.UP);
            case HALFWAY -> FrontAndTop.fromFrontAndTop(Direction.UP, facing);
        };
    }
}
