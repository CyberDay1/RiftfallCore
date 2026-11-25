package com.cyberday1.riftfall.client;

/**
 * The rip as a split in space rather than as a picture of one.
 *
 * <p>
 * Two walls meeting at the rim curve and receding to a jagged inner edge, one forward and one back.
 * The rim is the widest part; the local thickness is scaled by the local opening width, so the
 * tapered ends of the rip come to a point in three dimensions instead of a pinched pipe.
 *
 * <p>
 * Pure arithmetic on {@link RiftTearOutline}, with no rendering classes anywhere near it, so the
 * shape and its shading can be measured in a test rather than looked at.
 */
public final class RiftTearCrack {

    /**
     * ⭐ The size dial. One number: turn this and the whole picture grows about its own middle.
     *
     * <p>
     * It has to be one number rather than two. {@link #WIDTH} and {@link #HEIGHT} are half of a
     * contract &mdash; the other half is the frame size the generator writes into
     * {@code rift_tear.png.mcmeta}, currently 48&times;64 &mdash; and geometry whose aspect disagrees
     * with its texture's stretches the artwork without anything failing. Scaling the base 3&times;4
     * keeps that 3:4 by construction, so a re-tune cannot break it by hand.
     *
     * <p>
     * ⚠ Do not replace this with literal {@code 3.9F} / {@code 5.2F}. Those land the ratio at
     * 1.3333333 rather than 1.3333334, and the test that walks the ring across the sprite multiplies
     * it by the sprite width: 48 &times; the derived ratio is exactly 64.0, and 48 &times; the literal
     * one is 63.999996, which truncates a row short.
     */
    private static final float SCALE = 1.3F;

    /** The picture's size in blocks. Rev 14 took the base 3&times;4 to 1.3&times; on request. */
    public static final float WIDTH = 3.0F * SCALE;
    public static final float HEIGHT = 4.0F * SCALE;

    /**
     * Where the picture's middle sits, in blocks above the centre of the block that carries it.
     *
     * <p>
     * This, not {@link #DROP}, is the thing that is actually fixed. Growing a tear that hangs from a
     * fixed top drives its bottom into the floor and growing one that stands on a fixed bottom lifts
     * it off &mdash; either way the tear moves when the only thing asked for was that it get bigger.
     * Pinning the middle and deriving the hang means {@link #SCALE} is a pure size knob.
     *
     * <p>
     * ⭐ 2.1, not 1.0, and the number is derived rather than picked. The tear is carried on the
     * bottom block of a three-block column, which spans -0.5 to +2.5 in that block's own space, so
     * standing the picture on the column's floor means putting its bottom at exactly -0.5 &mdash;
     * which is {@code HEIGHT * 0.5F - 0.5F} above the carrying block's centre, or 2.1 at the shipped
     * {@link #SCALE}. At 1.0 the picture ran from -1.6 to +3.6: 1.1 blocks sunk into the floor,
     * which is what a player saw, and 1.1 wasted above the column in air, which nobody could.
     */
    private static final float CENTRE = 2.1F;

    /**
     * How far the picture hangs below its own block, derived so the middle stays at {@link #CENTRE}.
     *
     * <p>
     * {@link #up} puts the bottom at {@code -0.5 - DROP}, so holding the middle means the hang has to
     * take half of every increase in {@link #HEIGHT}. At the original 4 blocks this comes out at 0.5,
     * which is the number it replaces, to the bit.
     *
     * <p>
     * At the shipped {@link #CENTRE} this is exactly {@code +0.0F} &mdash; not near it: {@code 2.6F}
     * and {@code 2.1F} are within a factor of two of each other, so their subtraction is exact by
     * Sterbenz and lands on 0.5, and 0.5 - 0.5 is 0. Nothing hangs below the block any more. Kept as
     * a derived constant rather than deleted because it is still what {@link #CENTRE} is free to move.
     */
    public static final float DROP = HEIGHT * 0.5F - CENTRE - 0.5F;

    /**
     * ⭐ The depth dial. How thick the crack is, in blocks, at its thickest point. Reached, not aimed
     * at: the reach is normalised so this is the measurement rather than a ceiling.
     *
     * <p>
     * Split evenly about the rip's own plane, so half of it reaches either way. Rev 13 held this at
     * 0.5 so that half depth was exactly the block's own and a tear set flush in a wall could not push
     * into the neighbour; rev 14 doubled it on request and that reasoning does not survive,
     * because it was never load-bearing. Checked rather than assumed: the block's shape is
     * {@code Shapes.empty()} and collision is off in its properties, so there is nothing to intersect;
     * and the walls are real depth-writing geometry on {@code entityCutoutNoCull} rather than the old
     * stencil-less billboard, so a wall that crosses the block boundary is depth-tested against
     * whatever is there like any other surface. Poking into the next block is a thing you can see, not
     * a thing that can go wrong &mdash; which makes this a taste knob and free to turn.
     */
    public static final float DEPTH = 1.0F;

    private static final float REACH = DEPTH * 0.5F;

    /**
     * ⭐ The form dial. Which solid the two rings are joined into.
     *
     * <p>
     * Both forms use the same rim ring, the same inner ring and the same {@link #reach}; all that
     * changes is where the inner ring is put along Z and how many walls run to it. Kept as a dial
     * rather than replaced because the shape is a taste call not made yet, and
     * re-deriving the old one from a diff is the expensive way to answer it.
     */
    public enum Form {

        /**
         * Rev 14's solid: two walls, each running from the rim at {@code z = 0} to its own copy of
         * the inner ring, one at {@code -reach} and one at {@code +reach}. Biconvex &mdash; the
         * ragged inner points stick out at the viewer whichever side they stand on, which is the
         * "puffed up pillow" named twice in review.
         */
        LENS,

        /**
         * Rev 16: one wall, running from the rim at {@code z = 0} to a single throat ring on the far
         * side of the plane from the camera, capped by the core. Concave from wherever it is stood
         * in front of, because the side is chosen per frame rather than baked.
         */
        FUNNEL
    }

    /** ⭐ Which one is shipped. */
    public static final Form FORM = Form.FUNNEL;

    /**
     * How much of each row's half width the inner edge keeps.
     *
     * <p>
     * A single number here would be a scaled copy of the rim, and a scaled copy gives smooth conical
     * walls &mdash; a funnel, which is the thing this is not. The spread is what breaks the walls
     * into facets.
     */
    private static final float INSET_MIN = 0.26F;
    private static final float INSET_MAX = 0.62F;

    /**
     * How far the inner edge slips along the crack, as a fraction of the local half width.
     *
     * <p>
     * ⚠ It slips to another <em>row</em> of the rim rather than to an arbitrary height, and takes
     * that row's middle and half width with it. The rip does not run straight down: measured, a free
     * slip of this size lands outside the rim about once every eighty points, because the centreline
     * has moved further sideways over those rows than the inset kept back. Riding the row is what
     * makes containment hold by construction instead of by tuning.
     */
    private static final float WANDER = 0.40F;

    /** The shallowest a facet may be, as a fraction of its row's thickness. */
    private static final float JAG = 0.45F;

    /**
     * Points a wander cell spans, coarse and fine.
     *
     * <p>
     * The ring is 240 points over about seven blocks of rim, so one facet is a third of a final pixel
     * across and per-point noise would be fizz rather than shards. A cell of 16 is about half a block
     * and a cell of 6 about a fifth, which is a shard you can see.
     */
    private static final int COARSE = 16;
    private static final int FINE = 6;

    /** The baked light. Mostly in the rip's own plane, with enough Z to tell the two walls apart. */
    private static final float LIGHT_X = -0.48F;
    private static final float LIGHT_Y = 0.78F;
    private static final float LIGHT_Z = -0.40F;

    /** The darkest a facet gets before the throat is taken off it, and how much the throat takes. */
    private static final float AMBIENT = 0.45F;
    private static final float THROAT = 0.40F;

    private static final int SEED = 0x5CAB1E;

    private static RiftTearCrack crack;

    private final RiftTearOutline rim;
    private final int points;
    private final float[] innerU;
    private final float[] innerV;
    private final float[] reach;
    private final float[] nearShade;
    private final float[] farShade;
    private final float[] hollowShade;

    private RiftTearCrack(RiftTearOutline rim) {
        this.rim = rim;
        this.points = rim.points();
        this.innerU = new float[points];
        this.innerV = new float[points];
        this.reach = new float[points];
        this.nearShade = new float[points];
        this.farShade = new float[points];
        this.hollowShade = new float[points];

        int rows = rim.rows();
        float widest = 0.0F;
        for (int row = 0; row < rows; row++) {
            widest = Math.max(widest, rim.halfWidth(row));
        }

        float spacing = (rim.y(rows - 1) - rim.y(0)) / (rows - 1);
        for (int at = 0; at < points; at++) {
            int row = at < rows ? at : points - 1 - at;
            float side = at < rows ? 1.0F : -1.0F;
            float half = rim.halfWidth(row);

            // The slip is in half widths, which are across the picture, so it is turned into a step
            // along it. Scaled by the half width like everything else here, so the tips stay pointed.
            float slip = (noise(at, 2) * 2.0F - 1.0F) * half * WANDER * WIDTH / HEIGHT;
            int landed = Math.max(0, Math.min(rows - 1, row + Math.round(slip / spacing)));
            float inset = INSET_MIN + (INSET_MAX - INSET_MIN) * noise(at, 1);

            innerU[at] = rim.centreOf(landed) + side * rim.halfWidth(landed) * inset;
            innerV[at] = rim.y(landed);
            reach[at] = (half / widest) * (JAG + (1.0F - JAG) * noise(at, 3));
        }

        // The jag is a fraction, so the widest row does not have to be the one that draws it at
        // full. Normalising here is what makes DEPTH the thickness it says it is rather than an
        // upper bound the shape happens not to reach; measured before this, it landed at 0.39.
        float peak = 0.0F;
        for (int at = 0; at < points; at++) {
            peak = Math.max(peak, reach[at]);
        }
        for (int at = 0; at < points; at++) {
            reach[at] *= REACH / peak;
        }

        for (int at = 0; at < points; at++) {
            int next = (at + 1) % points;
            float through = (depthAt(at) + depthAt(next)) * 0.25F;
            nearShade[at] = facet(at, next, -1.0F, through, false);
            farShade[at] = facet(at, next, 1.0F, through, false);
            // ⭐ One array, not two, and that is the point: see hollowShade's own note.
            hollowShade[at] = facet(at, next, 1.0F, through, true);
        }
    }

    /** The shipped crack, built once off the shipped ring. */
    public static synchronized RiftTearCrack of() {
        if (crack == null) {
            crack = new RiftTearCrack(RiftTearOutline.mouth());
        }
        return crack;
    }

    public int points() {
        return points;
    }

    public RiftTearOutline outline() {
        return rim;
    }

    /** The inner edge, in the picture's own normalised coordinates, so it reads across to a UV. */
    public float innerU(int at) {
        return innerU[at];
    }

    public float innerV(int at) {
        return innerV[at];
    }

    /** Half the crack's thickness at this point, in blocks. Zero at the tips, by construction. */
    public float reach(int at) {
        return reach[at];
    }

    /** That thickness as a fraction of the deepest, which is how far down the wall's V runs. */
    public float depthAt(int at) {
        return reach[at] / REACH;
    }

    /** The flat shade of the facet from {@code at} to the next point, on the near and far walls. */
    public float nearShade(int at) {
        return nearShade[at];
    }

    public float farShade(int at) {
        return farShade[at];
    }

    /**
     * ⭐ The flat shade of the funnel's facet from {@code at} to the next point. One number per
     * facet, not one per facet per side, and that is a construction guarantee rather than a saving.
     *
     * <p>
     * The funnel's throat swaps sides when the camera crosses the plane. Everything about the wall
     * mirrors with it: the facet's own normal keeps its in-plane part and flips its Z. If the key
     * light stayed put, that flip would change every value on the tear at once &mdash; a brightness
     * pop laid on top of the shape flip, at the one moment there is no cover for it.
     *
     * <p>
     * So the key light rides the camera's side of the plane instead of the world's. Mirror the light
     * with the throat and {@code n·L} is invariant under the flip: the in-plane part is unchanged and
     * the Z part is a product of two things that both changed sign. Which means the two arrays would
     * have been equal to the bit, so there is one, and the flip cannot change a value it does not
     * index. This is only defensible because the tear is lit by nothing in the room &mdash; the
     * lightmap is full bright and this shade is baked form, not illumination.
     *
     * <p>
     * Measured in the throat-forward frame, so the caller passes no side at all.
     */
    public float hollowShade(int at) {
        return hollowShade[at];
    }

    /** The right one of the above for the form and, where the form cares, the side. */
    public float wallShade(Form form, int at, float sign) {
        if (form == Form.FUNNEL) {
            return hollowShade[at];
        }
        return sign < 0.0F ? nearShade[at] : farShade[at];
    }

    /**
     * The flat shade of the core, the plug over the inner ring.
     *
     * <p>
     * ⭐ For {@link Form#LENS} this is full bright, as it always was: the plug is the nearest point
     * of a bulge and a bulge's nearest point is its brightest.
     *
     * <p>
     * For {@link Form#FUNNEL} it is the opposite thing and has to read as the opposite thing. The
     * plug is now the <em>bottom of the tunnel</em>, the furthest surface from the camera, and it is
     * the single most legible concavity cue the tear has: a hole whose middle is darker than its rim
     * is a hole, and one whose middle is brightest is a dome. Derived rather than picked &mdash; it
     * is {@link #shade} of the plug's own camera-facing normal at full throat depth, which is the
     * same rule the walls are lit by, so the plug cannot drift away from the wall it caps.
     */
    public static float coreShade(Form form) {
        return form == Form.FUNNEL ? shade(0.0F, 0.0F, -1.0F, 1.0F) : 1.0F;
    }

    // -----------------------------------------------------------------------------------------
    // The picture's coordinates, in blocks
    // -----------------------------------------------------------------------------------------

    /** A normalised x on the picture, in the tear's own frame. */
    public static float across(float normalised) {
        return (normalised - 0.5F) * WIDTH;
    }

    /** A normalised y, which runs <em>down</em> from the top of the picture, in the tear's own. */
    public static float up(float normalised) {
        return -0.5F - DROP + (1.0F - normalised) * HEIGHT;
    }

    // -----------------------------------------------------------------------------------------
    // Shading
    // -----------------------------------------------------------------------------------------

    /**
     * How bright a facet with this normal, this far down the wall, is drawn.
     *
     * <p>
     * ⭐ Baked into the vertex colour rather than into the lightmap. The tear is a hole to somewhere
     * else and must not be shaded by the room it stands in, so the lightmap stays full bright; but
     * uniform full bright on real geometry reads flat again, because form is read off shading. These
     * are different things and the tear wants the first without the second.
     *
     * <p>
     * A wrapped lambert rather than a clamped one: the walls are drawn no-cull and both faces of
     * every facet are visible, so a facet turned away from the light has to land somewhere other than
     * black. The throat term is what darkens the depth, and it is scaled by the local thickness
     * upstream, which is why the tips do not read as two dark smudges.
     */
    public static float shade(float nx, float ny, float nz, float through) {
        float wrapped = 0.5F + 0.5F * (nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z);
        float value = AMBIENT + (1.0F - AMBIENT) * clamp(wrapped, 0.0F, 1.0F);
        return value * (1.0F - THROAT * clamp(through, 0.0F, 1.0F));
    }

    /** The brightest and darkest {@link #shade} can be, which is what a test pins. */
    public static float brightest() {
        return 1.0F;
    }

    public static float darkest() {
        return AMBIENT * (1.0F - THROAT);
    }

    private float facet(int at, int next, float sign, float through, boolean seen) {
        float[] n = normal(at, next, sign, seen);
        return shade(n[0], n[1], n[2], through);
    }

    /**
     * The facet's own normal, from its own corners. Flat, deliberately: interpolating between
     * neighbours averages an irregular surface back into something soft, and the point of the
     * irregularity is that the faces catch light at different values.
     *
     * <p>
     * ⭐ {@code seen} turns the raw cross product into the normal of the face the camera is actually
     * looking at, which for a funnel is the <em>inside</em> of the cone. That is the whole concavity
     * cue and it is worth being exact about: a crater and a dome under one light differ by which of
     * the two opposed normals you shade with, and nothing else. The walls are drawn no-cull, so both
     * faces carry this one flat value and picking the wrong one paints a dome on a hole.
     *
     * <p>
     * ⚠ Oriented by construction rather than by knowing which way the ring is wound. The camera sits
     * on the side the throat is not, so the face turned toward it is the one whose Z opposes
     * {@code sign}; the cross product is flipped when it does not. Get this from the winding instead
     * and it silently inverts the day the generator emits the ring the other way round.
     */
    private float[] normal(int at, int next, float sign, boolean seen) {
        float ax = across(rim.x(at));
        float ay = up(rim.y(at));
        float bx = across(rim.x(next));
        float by = up(rim.y(next));
        float cx = across(innerU[at]);
        float cy = up(innerV[at]);
        float cz = sign * reach[at];

        float ex = bx - ax;
        float ey = by - ay;
        float fx = cx - ax;
        float fy = cy - ay;
        float nx = ey * cz;
        float ny = -ex * cz;
        float nz = ex * fy - ey * fx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-9F) {
            // A tip facet, where the wall has no thickness and no area. It draws as nothing; the
            // normal only has to be finite.
            return new float[] {0.0F, 0.0F, seen ? -sign : sign};
        }
        float turn = seen && nz * sign > 0.0F ? -1.0F : 1.0F;
        return new float[] {turn * nx / length, turn * ny / length, turn * nz / length};
    }

    // -----------------------------------------------------------------------------------------
    // The wander
    // -----------------------------------------------------------------------------------------

    /**
     * Value noise round the ring, two octaves, wrapping at the seam.
     *
     * <p>
     * It wraps because the ring closes: a wander that did not would put one straight facet across the
     * join, in the middle of the tear's top tip. Two octaves because one gives either a smooth
     * meander or a fizz, and the walls want shards at about the size of a face.
     */
    private float noise(int at, int salt) {
        return 0.68F * lattice(at, COARSE, salt) + 0.32F * lattice(at, FINE, salt + 31);
    }

    private float lattice(int at, int span, int salt) {
        int cells = Math.max(1, points / span);
        float scaled = (float) at / points * cells;
        int cell = (int) Math.floor(scaled);
        float through = scaled - cell;
        float a = hash(Math.floorMod(cell, cells), salt);
        float b = hash(Math.floorMod(cell + 1, cells), salt);
        return a + (b - a) * through * through * (3.0F - 2.0F * through);
    }

    private static float hash(int cell, int salt) {
        int h = cell * 0x27D4EB2D + salt * 0x165667B1 + SEED;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / (float) (1 << 24);
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }
}
