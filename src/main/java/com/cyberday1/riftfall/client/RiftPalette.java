package com.cyberday1.riftfall.client;

/**
 * The colours running through a rift tear, and through anything thrown off one.
 *
 * <p>
 * The tear is a baked animation: the wheel is painted into the frames by {@code
 * scratch/riftfall-tear/generate_tear.py}, because a block colour handler bakes into the chunk mesh
 * and only re-evaluates when the chunk is rebuilt. Its embers are not baked &mdash; they are
 * particles, tinted once each at birth &mdash; so the same wheel has to exist a second time, here,
 * in a second language.
 *
 * <p>
 * Nothing connects the two copies, which is the whole hazard: change one and the sparks quietly stop
 * being the colour of the thing they came off, with no error and nothing in the log. {@code
 * RiftTearStatesTest.everyPaletteStopIsActuallyInTheTexture} is what closes that, by going looking
 * for each stop below in the shipped PNG.
 *
 * <h2>Which surface the sparks come off</h2>
 * The generator paints two surfaces from two wheels now, and they are no longer the same wheel. The
 * aperture is {@code --palette void}: a hole, all but black, and deliberately so. The tube wall is
 * {@code --wall-palette rimlit}: the torn edge of space, lit by the rim. A spark is a piece of the
 * edge that came loose, so it takes the wall's wheel &mdash; the void one would give near-black
 * sparks, and {@code RiftEmberParticle}'s floor would then lift them to a grey that is not a colour
 * the tear is anywhere.
 *
 * <h2>Rate</h2>
 * The generator lays {@link #CYCLES_PER_BLOCK} of the wheel over each block of the rip's height and
 * advances one whole turn every {@link #TICKS_PER_CYCLE} ticks of the strip. An ember reading {@link
 * #at} with the same two numbers drifts at the same rate and in the same direction as the rip it
 * left.
 *
 * <p>
 * One turn per <em>cycle</em>, not one per strip: the strip runs several times that length, because
 * it carries a different set of cracks on each pass. What the two numbers here have to match is the
 * rate, so the only thing the strip's length owes them is to be a whole number of cycles &mdash;
 * which {@code RiftTearStatesTest.theEmbersTurnAtTheRateTheStripPlays} is what checks.
 *
 * <p>
 * It does not drift <em>in step</em> with it, and cannot: Minecraft's sprite ticker counts from when
 * the atlas was stitched, not from world time, so the texture's phase at any given tick is not a
 * thing Java can ask for. Matching the palette and the rate is the honest goal; matching the frame
 * is not available.
 */
public final class RiftPalette {

    /**
     * The wheel, mirrored from {@code PALETTES["rimlit"]} in the generator.
     *
     * <p>
     * Cyclic &mdash; the last stop interpolates back to the first &mdash; and the first is almost
     * black on purpose. A wheel at even brightness reads as a novelty; it is the dark rung that
     * keeps the thing looking like a hole rather than a light.
     *
     * <p>
     * Violet all the way round, out through indigo to lavender and back through orchid. The way up
     * and the way down are different hues deliberately: a ramp that retraces itself puts two stops
     * of near-equal luminance a few units apart, which is a flat rung the phase crawls over, and
     * {@code RiftTearStatesTest.theWheelIsAllDistinctStops} refuses it.
     */
    private static final int[] STOPS = {
            0x1C0A5C, 0x5C26DA, 0x9450FA, 0xB87EFC, 0xC49EFA, 0xC870E2, 0x9A3CC4, 0x541A8C,
    };

    /** Turns of the wheel per block of rip height: the generator's {@code --cycles} over its body. */
    public static final float CYCLES_PER_BLOCK = 0.9F / 3.0F;

    /** Ticks for one whole turn: the generator's 32 frames per crack set at a {@code frametime} of 2. */
    public static final float TICKS_PER_CYCLE = 64.0F;

    private RiftPalette() {
    }

    /** How many stops there are, for the test that goes looking for them. */
    public static int size() {
        return STOPS.length;
    }

    /** Stop {@code index}, packed {@code 0xRRGGBB}, for the test that goes looking for them. */
    public static int stop(int index) {
        return STOPS[index];
    }

    /**
     * The colour at {@code phase}, as three channels in 0..1.
     *
     * <p>
     * {@code phase} is wrapped rather than clamped, so callers may hand it any real number &mdash;
     * the useful ones are built out of a world height and a game time, and both run off in both
     * directions.
     */
    public static float[] at(float phase) {
        float scaled = (phase - (float) Math.floor(phase)) * STOPS.length;
        int index = (int) scaled;
        // Java's floor-based wrap cannot reach STOPS.length the way a naive modulo can, but a phase
        // a hair below a whole number rounds to it in float, so the index is pinned anyway.
        index = Math.min(index, STOPS.length - 1);
        float fraction = scaled - index;

        int from = STOPS[index];
        int to = STOPS[(index + 1) % STOPS.length];
        return new float[] {
                mix(from >> 16, to >> 16, fraction),
                mix(from >> 8, to >> 8, fraction),
                mix(from, to, fraction),
        };
    }

    private static float mix(int from, int to, float fraction) {
        return ((from & 0xFF) * (1.0F - fraction) + (to & 0xFF) * fraction) / 255.0F;
    }
}
