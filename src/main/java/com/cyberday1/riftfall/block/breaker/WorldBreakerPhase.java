package com.cyberday1.riftfall.block.breaker;

import net.minecraft.util.StringRepresentable;

/**
 * Where a World Breaker is in its cycle: idle, a key turning, a tear standing on it, then a 30 s
 * cooldown whose first second is the shards flying home.
 *
 * <p>
 * Pure: every rule about which phase follows which, how long a timed phase lasts and what a key
 * gets told is here, with no level in sight, so the cycle is checked in a unit test and the block
 * only carries it out.
 */
public enum WorldBreakerPhase implements StringRepresentable {

    IDLE("idle", 0, 0),
    /** The key is in and turning. The tear opens when this runs out. */
    CHARGING("charging", 6, 60),
    /** A tear stands on the breaker. Lasts as long as the tear does, so it has no timer. */
    OPEN("open", 10, 0),
    /** The tear has gone; the floating shards fly back over their stumps. First 1 s of the cooldown. */
    HOMING("homing", 6, 20),
    COOLDOWN_3("cooldown_3", 6, 180),
    COOLDOWN_2("cooldown_2", 4, 200),
    COOLDOWN_1("cooldown_1", 2, 200);

    /** The whole cooldown, homing included: 30 s. */
    public static final int COOLDOWN_TICKS =
            HOMING.ticks + COOLDOWN_3.ticks + COOLDOWN_2.ticks + COOLDOWN_1.ticks;

    private final String name;
    private final int light;
    private final int ticks;

    WorldBreakerPhase(String name, int light, int ticks) {
        this.name = name;
        this.light = light;
        this.ticks = ticks;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The block light this phase gives off. */
    public int light() {
        return light;
    }

    /** How long this phase lasts before its scheduled tick, or 0 for one that waits on the world. */
    public int ticks() {
        return ticks;
    }

    /** True for the four phases a key is refused in because the breaker is cooling. */
    public boolean cooling() {
        return this == HOMING || this == COOLDOWN_3 || this == COOLDOWN_2 || this == COOLDOWN_1;
    }

    /**
     * Whether this phase has the charge store: from the key going in until the shards are home.
     *
     * <p>
     * Nothing else holds one, so an idle breaker, which is most of them, costs nothing per frame.
     */
    public boolean holdsKey() {
        return this == CHARGING || this == OPEN || this == HOMING;
    }

    /**
     * What a timed phase becomes when its tick comes round. {@link #CHARGING} is not here: whether
     * it becomes {@link #OPEN} depends on there being room, which only the block can find out.
     */
    public WorldBreakerPhase afterTimer() {
        return switch (this) {
            case HOMING -> COOLDOWN_3;
            case COOLDOWN_3 -> COOLDOWN_2;
            case COOLDOWN_2 -> COOLDOWN_1;
            case COOLDOWN_1 -> IDLE;
            default -> this;
        };
    }

    /**
     * What the block above says about this phase.
     *
     * <p>
     * ⭐ Every way a tear goes &mdash; a clear, an expiry, a wipe, an admin close, the restart sweep
     * &mdash; removes the block above, so the cooldown starting here is the ruled "anytime the rift is
     * destroyed" with no plumbing into any of them.
     */
    public WorldBreakerPhase withTearAbove(boolean tear) {
        if (tear) {
            return this == IDLE || this == CHARGING || this == OPEN ? OPEN : this;
        }
        return this == OPEN ? HOMING : this;
    }

    /** What a key used on a breaker in this phase is told. */
    public KeyAnswer answerKey() {
        if (this == IDLE) {
            return KeyAnswer.TAKE;
        }
        return cooling() ? KeyAnswer.COOLING : KeyAnswer.ALREADY_OPEN;
    }

    /** The three ways a breaker answers a key. Only {@link #TAKE} spends it. */
    public enum KeyAnswer {
        TAKE,
        ALREADY_OPEN,
        COOLING
    }
}
