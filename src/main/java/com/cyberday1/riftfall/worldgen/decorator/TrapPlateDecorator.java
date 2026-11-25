package com.cyberday1.riftfall.worldgen.decorator;

import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Scatters debuff pressure plates across open floor, mixed in with inert lookalikes.
 *
 * <pre>
 * { "type": "riftfall:trap_plate",
 *   "traps": ["darkutils:slowness_plate", "darkutils:weakness_plate"],
 *   "decoy": "minecraft:stone_pressure_plate",
 *   "room_kinds": ["approach", "chaos", "stable", "unstable", "boss"],
 *   "rarity": 0.006, "trap_share": 0.34 }
 * </pre>
 *
 * <h2>Concealment is the decoys, not invisibility</h2>
 * Dark Utilities plates have a {@code hidden} blockstate, and it is not usable here: it maps to a
 * model with no parent and no elements, so a hidden plate is not subtle, it is <em>gone</em>. There
 * is no partial state between the two. So the plate stays visible and the doubt comes from company:
 * most of what is scattered is a plain pressure plate that does nothing, and a player who has been
 * bitten once has to decide about every plate after it.
 *
 * <h2>Every trap has a way around it</h2>
 * A plate is only placed where all four horizontal neighbours are air. That single test is what
 * keeps traps out of doorways, corridors and chokepoints: a one-wide passage has walls on two
 * opposite sides, a two-wide one has a wall on one side, so the first position that passes is in the
 * interior of something at least three wide — somewhere a player can see the plate and step around
 * it. Being unavoidable is what would make this feel like a tax rather than a hazard.
 *
 * <p>
 * Nothing is placed within one block of a Riftfall marker either, which is how the boss's own spawn
 * point, the arena's entry, its gate and the exit portal stay clear without any of them having to be
 * looked up by position.
 *
 * <h2>Not a record</h2>
 * Unlike its two siblings this needs a field the codec does not carry: the resolved blockstates.
 * They cannot be resolved at decode time in the type's own codec without making a theme file fail to
 * load when Dark Utilities is absent, and they must not be resolved per position, because that is a
 * registry lookup on every floor tile of every room. So the ids are kept as authored and resolved
 * once, lazily, exactly as {@code ThemeSubstitutionProcessor} caches its theme resolution.
 */
public final class TrapPlateDecorator implements RiftDecorator {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The five kinds the dungeon puts traps in. Portal is excluded; so is everything not a room. */
    public static final Set<RoomKind> DEFAULT_ROOM_KINDS =
            Set.of(RoomKind.APPROACH, RoomKind.CHAOS, RoomKind.STABLE, RoomKind.UNSTABLE, RoomKind.BOSS);

    /**
     * Chance that an eligible floor position gets a plate at all.
     *
     * <p>
     * Measured rather than guessed: across the shipped library a 47x47x47 room offers roughly 1300
     * (boss arena) to 3900 (the most open stable rooms) eligible positions, and the median is about
     * 900. At this rate that is a handful of plates in a big room and usually none in a small one,
     * which is the conservative end on purpose — this is a first look, and too few is a nudge where
     * too many is a minefield.
     */
    public static final float DEFAULT_RARITY = 0.006F;

    /** Fraction of placed plates that actually bite: two decoys per trap. */
    public static final float DEFAULT_TRAP_SHARE = 0.34F;

    /** How many of the four horizontal neighbours must be air for a position to be avoidable. */
    public static final int DEFAULT_OPEN_SIDES = 4;

    public static final MapCodec<TrapPlateDecorator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.listOf().fieldOf("traps")
                            .forGetter(TrapPlateDecorator::traps),
                    ResourceLocation.CODEC.optionalFieldOf("decoy")
                            .forGetter(TrapPlateDecorator::decoy),
                    RoomKind.CODEC.listOf()
                            .optionalFieldOf("room_kinds", List.copyOf(DEFAULT_ROOM_KINDS))
                            .forGetter(decorator -> List.copyOf(decorator.roomKinds())),
                    Codec.floatRange(0.0F, 1.0F).optionalFieldOf("rarity", DEFAULT_RARITY)
                            .forGetter(TrapPlateDecorator::rarity),
                    Codec.floatRange(0.0F, 1.0F).optionalFieldOf("trap_share", DEFAULT_TRAP_SHARE)
                            .forGetter(TrapPlateDecorator::trapShare),
                    Codec.intRange(0, 4).optionalFieldOf("open_sides", DEFAULT_OPEN_SIDES)
                            .forGetter(TrapPlateDecorator::openSides),
                    Codec.LONG.optionalFieldOf("seed", 0x7A4F9L).forGetter(TrapPlateDecorator::seed)
            ).apply(instance, TrapPlateDecorator::new));

    private final List<ResourceLocation> traps;
    private final Optional<ResourceLocation> decoy;
    private final Set<RoomKind> roomKinds;
    private final float rarity;
    private final float trapShare;
    private final int openSides;
    private final long seed;

    /** Resolved once on first use; see the class note on why this is not a record. */
    private volatile Palette palette;

    public TrapPlateDecorator(List<ResourceLocation> traps, Optional<ResourceLocation> decoy,
            List<RoomKind> roomKinds, float rarity, float trapShare, int openSides, long seed) {
        this.traps = List.copyOf(traps);
        this.decoy = decoy;
        this.roomKinds = roomKinds.isEmpty() ? EnumSet.noneOf(RoomKind.class) : EnumSet.copyOf(roomKinds);
        this.rarity = rarity;
        this.trapShare = trapShare;
        this.openSides = openSides;
        this.seed = seed;
    }

    public List<ResourceLocation> traps() {
        return traps;
    }

    public Optional<ResourceLocation> decoy() {
        return decoy;
    }

    public Set<RoomKind> roomKinds() {
        return roomKinds;
    }

    public float rarity() {
        return rarity;
    }

    public float trapShare() {
        return trapShare;
    }

    public int openSides() {
        return openSides;
    }

    public long seed() {
        return seed;
    }

    @Override
    public MapCodec<? extends RiftDecorator> codec() {
        return CODEC;
    }

    /** Keeps two trap decorators in one theme from rolling in lockstep, as the siblings' salts do. */
    public long salt() {
        return seed ^ traps.hashCode();
    }

    // -------------------------------------------------------------------------------------------
    // Placement
    // -------------------------------------------------------------------------------------------

    @Override
    public int decorate(DecoratorContext context, BlockState[] neighbourhood) {
        if (!roomKinds.contains(context.roomKind())) {
            return 0;
        }
        Palette resolved = palette();
        if (resolved.isEmpty()) {
            return 0;
        }
        if (!isOpenFloor(resolved, neighbourhood)) {
            return 0;
        }
        // Rolled after the geometry, unlike AttachmentDecorator, and the difference is deliberate:
        // rarity here should mean "of the floor a player can walk on and step around", which is a
        // small, meaningful set. Rolling first would make it "of air", so the same number would give
        // a cavern ten times the traps of a tight room for no reason anyone chose.
        if (context.roll() >= rarity) {
            return 0;
        }

        BlockState plate = resolved.pick(context, trapShare);
        if (plate == null) {
            return 0;
        }
        neighbourhood[CENTRE] = plate;
        return CENTRE_BIT;
    }

    /**
     * Whether a plate here would sit on the floor, under headroom, with a way around it.
     *
     * <p>
     * A {@code null} horizontal neighbour is refused rather than ignored. Null means "outside this
     * room's block list", so it is precisely the case where we cannot show the position is
     * avoidable — and a room edge is where a doorway is.
     */
    private boolean isOpenFloor(Palette resolved, BlockState[] neighbourhood) {
        BlockState centre = neighbourhood[CENTRE];
        if (centre == null || !centre.isAir()) {
            return false;
        }
        BlockState above = neighbourhood[Direction.UP.ordinal()];
        if (above == null || !above.isAir()) {
            return false;
        }
        BlockState below = neighbourhood[Direction.DOWN.ordinal()];
        if (!DecoratorContext.isFaceSturdy(below, Direction.UP)) {
            return false;
        }

        int open = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState neighbour = neighbourhood[side.ordinal()];
            // A plate this decorator already placed counts as open, and that is not a detail. The
            // driver writes into the array it is still walking, so a neighbour may or may not have
            // been visited yet depending on the order the template stored its blocks. Reading a
            // placed plate as "blocked" would make the room a function of that order; reading it as
            // "open" -- as the air it replaced -- gives the same answer either way. The cost is that
            // two plates may sit side by side, which at these densities is rare and reads fine.
            if (neighbour != null && (neighbour.isAir() || resolved.contains(neighbour))) {
                open++;
            }
        }
        if (open < openSides) {
            return false;
        }

        // Authored fixtures: the boss's spawn point, the arena entry, the gate the room shuts, the
        // exit portal. Standing a trap on any of them is the one placement the room's own author
        // would not have wanted, and checking the whole neighbourhood catches "beside" as well as
        // "on top of" without needing to know where any of them are.
        for (BlockState state : neighbourhood) {
            if (state != null && RiftfallMarkers.isMarker(state)) {
                return false;
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------------------------
    // Resolution
    // -------------------------------------------------------------------------------------------

    /**
     * The blockstates behind the authored ids, resolved once.
     *
     * <p>
     * Ids are kept unresolved through the codec on the same terms {@code ThemeMaterial} keeps its
     * block ids unresolved: Dark Utilities is a pack mod and not a dependency, so resolving in the
     * codec would turn "that mod is not installed" into "this theme file does not parse", which
     * would take the whole theme's materials down with it. A missing trap is dropped and logged; if
     * every trap is missing the decorator places the decoys and nothing else, and if the decoy is
     * missing too it places nothing at all.
     */
    private Palette palette() {
        Palette current = palette;
        if (current == null) {
            synchronized (this) {
                current = palette;
                if (current == null) {
                    current = resolve();
                    palette = current;
                }
            }
        }
        return current;
    }

    private Palette resolve() {
        List<BlockState> states = new ArrayList<>(traps.size());
        for (ResourceLocation id : traps) {
            BlockState state = lookUp(id, "trap");
            if (state != null) {
                states.add(state);
            }
        }
        BlockState decoyState = decoy.map(id -> lookUp(id, "decoy")).orElse(null);
        return new Palette(List.copyOf(states), decoyState);
    }

    private static BlockState lookUp(ResourceLocation id, String role) {
        // getOptional, never get: the block registry is defaulted, so the obvious call would hand
        // back air and cheerfully carve a hole in the floor instead of placing a plate.
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null || block.defaultBlockState().isAir()) {
            LOGGER.info("Riftfall traps: {} block '{}' is not registered; skipping it.", role, id);
            return null;
        }
        return block.defaultBlockState();
    }

    /**
     * The resolved plates. A pure memo of registry lookups, so it holds no per-position state and
     * cannot make the result depend on how often or in what order a room is decorated.
     */
    private record Palette(List<BlockState> traps, BlockState decoy) {

        boolean isEmpty() {
            return traps.isEmpty() && decoy == null;
        }

        /** Whether a state is one this decorator could have written. */
        boolean contains(BlockState state) {
            return state == decoy || traps.contains(state);
        }

        /**
         * A trap with probability {@code trapShare}, otherwise the decoy. Falls through to whichever
         * side actually resolved, so a theme that named only traps still places, and one whose traps
         * are all missing degrades to plain floor detail rather than to nothing.
         *
         * <p>
         * Both rolls are always taken in the same order for a given position, and the context's
         * stream is reseeded per position, so the answer is a function of position and seed alone.
         */
        BlockState pick(DecoratorContext context, float trapShare) {
            boolean wantTrap = context.roll() < trapShare;
            float which = context.roll();
            if (wantTrap && !traps.isEmpty()) {
                return traps.get(Math.min((int) (which * traps.size()), traps.size() - 1));
            }
            if (decoy != null) {
                return decoy;
            }
            return traps.isEmpty()
                    ? null
                    : traps.get(Math.min((int) (which * traps.size()), traps.size() - 1));
        }
    }
}
