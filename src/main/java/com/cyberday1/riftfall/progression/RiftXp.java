package com.cyberday1.riftfall.progression;

/**
 * Riftfall's XP-to-level curve, and nothing that awards XP or spends a level.
 *
 * <p>
 * Transcribed from the UE5 project's {@code ULevelProgressionLibrary}, which the Postgres side
 * reimplements with the same four constants and the same rounding. Two independent
 * implementations of one curve is the strongest design-of-record signal that project has, so this
 * is a transcription rather than a design &mdash; including the inner floor at the threshold,
 * which compounds the already-floored level-69 step and is wrong from level 70 on without it.
 *
 * <p>
 * Every method that needs a cap takes it as an argument. A player's level is read against
 * {@link #LEVEL_CAP}; {@link #UNCAPPED} is the curve's whole domain, for code asking about the
 * curve rather than about a player.
 */
public final class RiftXp {

    /** {@code BaseXPMultiplier}. */
    public static final double BASE = 100.0;

    /** {@code PreThresholdExponent}. */
    public static final double PRE_THRESHOLD_EXPONENT = 1.5;

    /** {@code ExponentialThreshold} &mdash; the first level costed by compounding. */
    public static final int EXPONENTIAL_THRESHOLD = 70;

    /** {@code PostThresholdExponent}, which is a base rather than an exponent. 12% a level. */
    public static final double POST_THRESHOLD_BASE = 1.12;

    /** The level a character starts at, and the floor of every domain here. */
    public static final int FIRST_LEVEL = 1;

    /**
     * The highest level this class will answer about. ⛔ Not a level cap and not a design number.
     *
     * <p>
     * The curve is computed in doubles, as the C++ and the SQL both are, and a double-computed
     * floor eventually stops agreeing with exact integer arithmetic. This is the measured level at
     * which it still does: every step from 1 to here is the same number computed either way, and
     * the next one is not. {@code RiftXpTest} asserts it in both directions against a
     * {@code BigInteger} oracle, so it is maximal by measurement rather than by choice.
     *
     * <p>
     * A {@code long} cumulative does not overflow until level 338, so determinacy is the binding
     * constraint by a wide margin.
     */
    public static final int CEILING = 211;

    /**
     * The curve's whole domain: what to pass when asking about the curve rather than a player.
     * ⛔ Not a player's cap; that is {@link #LEVEL_CAP}.
     */
    public static final int UNCAPPED = CEILING;

    /**
     * The design level cap, P-2 (ruled 2026-10-12: "yes" to 200). The one number every player-facing
     * level read passes.
     *
     * <p>
     * XP past it is still banked, not dropped: the level stops, the total does not. Respec pays from
     * progress into the current level, so a clamped total would leave a capped player unable to
     * respec, and a later cap raise converts banked XP into levels instead of having lost it.
     * ⚠ {@code RiftTier.of(200)} is 21, so tier 22 is out of reach under this cap.
     */
    public static final int LEVEL_CAP = 200;

    private RiftXp() {
    }

    /**
     * What one player's progress looks like at a total, which is everything a sheet needs.
     *
     * @param level the level this total buys, never above the cap it was asked with
     * @param total the XP handed in, unchanged
     * @param into  how much of {@code total} sits above {@link #totalFor(int)} for {@code level}
     * @param span  the cost of the next step, or 0 at the cap
     */
    public record Progress(int level, long total, long into, long span) {

        /** At the cap there is no next step, and {@link #into} is surplus rather than progress. */
        public boolean atCap() {
            return span == 0;
        }

        /**
         * How far along the step this is, in 0..1. One at the cap.
         *
         * <p>
         * One rather than zero because the bar is a picture of "how close to the next level", and
         * at the cap the honest picture is full. A zero bar would read as having just levelled.
         */
        public double fraction() {
            return span == 0 ? 1.0 : (double) into / span;
        }
    }

    /**
     * The cost of the step {@code level} &rarr; {@code level + 1}.
     *
     * <p>
     * {@code StrictMath} rather than {@code Math}: the latter is specified only to within an ulp,
     * so its result is a property of the JVM, and a curve whose values depend on which JVM read it
     * is not a design of record. Inside the determinate range the two were measured to agree
     * anyway, so this costs nothing and makes that a guarantee.
     */
    public static long required(int level) {
        checkLevel(level);
        if (level < EXPONENTIAL_THRESHOLD) {
            return (long) (BASE * StrictMath.pow(level, PRE_THRESHOLD_EXPONENT));
        }
        return (long) (thresholdStep()
                * StrictMath.pow(POST_THRESHOLD_BASE, level - (EXPONENTIAL_THRESHOLD - 1)));
    }

    /** The already-floored level-69 step the exponential branch compounds. 57,315. */
    private static long thresholdStep() {
        return (long) (BASE * StrictMath.pow(EXPONENTIAL_THRESHOLD - 1, PRE_THRESHOLD_EXPONENT));
    }

    /** The XP needed to <em>be</em> this level, which for the first level is none. */
    public static long totalFor(int level) {
        checkLevel(level);
        long total = 0L;
        for (int step = FIRST_LEVEL; step < level; step++) {
            total += required(step);
        }
        return total;
    }

    /**
     * The level this much XP buys, never above {@code cap}.
     *
     * <p>
     * Walked upward rather than inverted, which is what {@code GetLevelFromTotalXP} does and is at
     * most {@link #CEILING} additions. Inverting the first branch algebraically would need a
     * different inverse for the second one and a boundary where the two meet &mdash; three things
     * to get right instead of none.
     */
    public static int levelFor(long total, int cap) {
        checkCap(cap);
        if (total < 0L) {
            throw new IllegalArgumentException("negative XP total " + total);
        }
        long spent = 0L;
        int level = FIRST_LEVEL;
        while (level < cap) {
            long step = required(level);
            if (spent + step > total) {
                break;
            }
            spent += step;
            level++;
        }
        return level;
    }

    /** Everything a character sheet asks about one total, in one pass. */
    public static Progress progress(long total, int cap) {
        int level = levelFor(total, cap);
        return new Progress(level, total, total - totalFor(level),
                level >= cap ? 0L : required(level));
    }

    private static void checkLevel(int level) {
        if (level < FIRST_LEVEL || level > CEILING) {
            throw new IllegalArgumentException(
                    "level " + level + " is outside " + FIRST_LEVEL + ".." + CEILING);
        }
    }

    private static void checkCap(int cap) {
        if (cap < FIRST_LEVEL || cap > CEILING) {
            throw new IllegalArgumentException(
                    "level cap " + cap + " is outside " + FIRST_LEVEL + ".." + CEILING);
        }
    }
}
