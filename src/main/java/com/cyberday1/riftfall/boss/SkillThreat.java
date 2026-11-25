package com.cyberday1.riftfall.boss;

/**
 * What a skill effect is worth on a threat table, as arithmetic.
 *
 * <p>
 * Split out of {@link ThreatEvents} for {@link ThreatTable}'s reason: nothing here touches a
 * Minecraft type, so the pricing rule can be tested without a world. The listener reads an amplifier
 * and a duration off the event and asks this.
 *
 * <p>
 * ⛔ Paid on the <em>increment</em>, never per event. {@code MobEffectEvent.Added} is posted before
 * the merge and fires even when the re-application changes nothing, so a ticking aura would otherwise
 * earn its caster twenty payments a second.
 */
public final class SkillThreat {

    /** Ticks in a second, so the unit below reads as seconds rather than as a tick count. */
    private static final double TICKS_PER_SECOND = 20.0;

    /**
     * The longest duration an effect is paid for, in ticks.
     *
     * <p>
     * It exists because an infinite effect reports {@code -1} ticks, which would price as either
     * nothing or everything depending on sign. 600 is E-9's target fight length: an effect that
     * outlasts the fight is worth the fight and no more.
     */
    static final int DURATION_CAP_TICKS = 600;

    /**
     * A dealer's threat per second, measured. Median boss, {@code SWORD} row, tier 8, median of three
     * rig boots; {@code ThreatEvents#THREAT_PER_DAMAGE} is 1.0, so damage per second is threat per
     * second directly.
     */
    static final double DEALER_THREAT_PER_SECOND = 4.59;

    /**
     * A caster's magnitude per second, measured off the pack's own spell files: 7.0 effect-seconds
     * per cast (median duration 5.0 s at median amplifier 0) over a 4.0 s cast cycle (median
     * status-effect cooldown 16 s across four such spells on a bar).
     */
    static final double CASTER_MAGNITUDE_PER_SECOND = 1.75;

    private SkillThreat() {
    }

    /**
     * What an effect instance is worth before weighting, in effect-seconds: how long it lasts times
     * how hard it is. Amplifier 0 is a real effect, hence the {@code + 1}.
     *
     * @param amplifier     the instance's amplifier, 0 for the first level
     * @param durationTicks its duration, or any negative value for an infinite one
     */
    public static double magnitude(int amplifier, int durationTicks) {
        int ticks = durationTicks < 0 ? DURATION_CAP_TICKS : Math.min(durationTicks, DURATION_CAP_TICKS);
        return (amplifier + 1) * ticks / TICKS_PER_SECOND;
    }

    /**
     * What an application adds to what was already there. Clamped at zero, so shortening or weakening
     * somebody's effect is worth nothing rather than a refund -- and a re-application that neither
     * extends nor strengthens is worth exactly nothing, which is what makes an aura harmless.
     *
     * @param now    the magnitude of the instance being applied
     * @param before the magnitude of the instance already held, zero if there was none
     */
    public static double increment(double now, double before) {
        return Math.max(0.0, now - before);
    }

    /**
     * Threat per effect-second, from the ratio the design is stated in.
     *
     * <p>
     * {@code ratio} is the quantity being ruled on: the threat rate of a player doing nothing but
     * skill effects over that of a player doing nothing but damage. It is not a weight, and turning
     * it into one needs both measured anchors above -- which is the whole reason they are named here
     * rather than folded into a single magic constant.
     *
     * <p>
     * ⛔ 1.0 is the measured worst value: it is the crossover, where hold share is a coin flip and
     * target-switch churn peaks. The shipped default sits in 1.5&ndash;2.5.
     */
    public static double weight(double ratio) {
        return ratio * DEALER_THREAT_PER_SECOND / CASTER_MAGNITUDE_PER_SECOND;
    }
}
