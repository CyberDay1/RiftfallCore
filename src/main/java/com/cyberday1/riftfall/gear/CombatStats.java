package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.boss.UnseatedArena;
import com.cyberday1.riftfall.init.RiftfallAttributes;
import com.cyberday1.riftfall.rift.RiftKeyItem;
import com.cyberday1.riftfall.rift.RiftLevels;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * The Riftfall gear stats that act at damage time: {@code resistance} and {@code guard} on the
 * victim, {@code true_damage} on the attacker.
 *
 * <h2>Why here, and why one listener</h2>
 * {@code LivingDamageEvent.Pre} is the last point at which the figure is both final and still
 * writable &mdash; armour, protection and every other reduction have run, absorption has not. Guard
 * belongs after all of them because it is a proportional reduction, and true damage belongs after
 * all of them because it is a floor under whatever they left. They are one listener because they are
 * one arithmetic: a floor applied before a reduction is not a floor.
 *
 * <h2>Mitigation composes sequentially, which is why there is no arbitrary cap</h2>
 * Each source reduces what the last one left, so gear ({@code riftfall:resistance} on the C3 tier
 * curve) then skills ({@code riftfall:damage_resistance}) then guard is
 * {@code 1 - (1-a)(1-b)(1-c)} and the stack approaches 1.0 without ever reaching it. That is the
 * answer to {@code puffish_attributes}' resistance having no ceiling: a linear sum of fractions hits
 * 1.0 and grants immunity, a product of remainders cannot.
 *
 * <h2>Guard is not partial blocking</h2>
 * A successful vanilla block already negates 100% of the hit, and Shield Expansion cancels a blocked
 * melee hit at the top of {@code hurt}, before the shield code runs at all. So there is no reduction
 * here to increase. Guard answers for the damage a raised shield <em>cannot</em> stop: the
 * {@code #bypasses_shield} sources, which in this pack is the whole spell surface, and hits from
 * outside the frontal arc. That also means it can never double-dip with Apotheosis or IronsJewelry,
 * both of which write to the vanilla block path this gate excludes by definition.
 *
 * <p>
 * The gate asks the game rather than reimplementing it: {@code isDamageSourceBlocked} already holds
 * the tag test, the piercing-arrow test and the 180&deg; arc test, and {@code isBlocking} is already
 * patched by Shield Expansion, so the two mods agree on when a shield is up.
 *
 * <h2>Ordering against the boss curve</h2>
 * {@code ThreatEvents#onBossHurt} writes to this same event at the same default priority. Both are
 * multiplicative, so they commute and neither needs a declared priority. The true-damage floor is
 * not multiplicative &mdash; but it reads {@code getOriginalDamage}, which no listener can change,
 * so it is order-free for a different reason. If either ever gains a flat term, both need priorities
 * and this paragraph is why.
 */
public final class CombatStats {

    /** The share of a hit that lands however the target is mitigating, when the stat is at its cap. */
    static final double MAX_TRUE_DAMAGE = 0.25;

    /** The share a shield can turn away at its cap. Under one, so guard can never buy immunity. */
    static final double MAX_GUARD = 0.9;

    /**
     * The most of one hit the resistance channel may turn away, whatever is stacked into it.
     *
     * <p>
     * Under one for the same reason as {@link #MAX_GUARD}, and applied to the fraction rather than to
     * the attribute so a future skill cannot be silently clipped before it is composed.
     */
    static final double MAX_RESISTANCE = 0.8;

    /**
     * The reference kit, in points: a full mythic kit's {@code riftfall:resistance} as measured after
     * the trinket fix ({@code 89de45d}). The curve is pinned so this kit
     * turns away {@link #share} of a hit at each tier; a lighter or heavier kit slides along it.
     */
    static final double REFERENCE_POINTS = 0.66;

    /** Rift tiers the curve is anchored at, and the share {@link #REFERENCE_POINTS} turns away there (§9b). */
    static final int[] ANCHOR_TIERS = {1, 8, 20};
    static final double[] ANCHOR_SHARES = {0.10, 0.30, 0.45};

    /**
     * The share the reference kit turns away at a rift tier: linear between the anchors, held flat
     * below the first and past the last. Held rather than extrapolated so the tier ride, which has no
     * ceiling, cannot keep buying mitigation (the D-1 ruling).
     */
    static double share(int tier) {
        if (tier <= ANCHOR_TIERS[0]) {
            return ANCHOR_SHARES[0];
        }
        for (int i = 1; i < ANCHOR_TIERS.length; i++) {
            if (tier <= ANCHOR_TIERS[i]) {
                double t = (double) (tier - ANCHOR_TIERS[i - 1]) / (ANCHOR_TIERS[i] - ANCHOR_TIERS[i - 1]);
                return ANCHOR_SHARES[i - 1] + t * (ANCHOR_SHARES[i] - ANCHOR_SHARES[i - 1]);
            }
        }
        return ANCHOR_SHARES[ANCHOR_SHARES.length - 1];
    }

    /**
     * K, the points that buy half the cap, at a rift tier. Solved so {@code saturating(REFERENCE_POINTS,
     * K, MAX_RESISTANCE)} is exactly {@link #share}: {@code K = R (cap / s - 1)}. The D-1 ruling: the
     * curve constant varies by tier, the resistance itself does not.
     */
    static double halfPoint(int tier) {
        return REFERENCE_POINTS * (MAX_RESISTANCE / share(tier) - 1.0);
    }

    /**
     * What {@code points} of {@code riftfall:resistance} turn away at a rift tier: slice C3's
     * {@code cap R / (R + K(tier))}. The share no longer depends on hit size, and no stack of points
     * reaches {@link #MAX_RESISTANCE}.
     */
    static double turnedAway(double points, int tier) {
        return saturating(points, halfPoint(tier), MAX_RESISTANCE);
    }

    /**
     * {@code riftfall:damage_resistance}, already a fraction (0.10 is 10%), at its own ceiling. The
     * skills source of the three-source stack: it composes after the gear curve rather than adding
     * points to it, so a "+10%" row means 10% at every tier and every kit.
     */
    static double percentFraction(double fraction) {
        return clamp(fraction, MAX_RESISTANCE);
    }

    /** The victim's resistance as one fraction: gear points on the tier curve, then the percent source. */
    public static double resistance(double points, double percent, int tier) {
        return composed(turnedAway(points, tier), percentFraction(percent));
    }

    /**
     * {@code R/(R+K)} rather than a bare fraction because it saturates instead of summing: K is the
     * points that buy half the cap, and no stack of points can reach the cap itself.
     */
    static double saturating(double points, double half, double cap) {
        if (Double.isNaN(points) || points <= 0.0 || half <= 0.0) {
            return 0.0;
        }
        return cap * points / (points + half);
    }

    /**
     * Several mitigations composed so each applies to what the one before it left.
     *
     * <p>
     * The product of the remainders, never the sum of the shares. Two 60% sources sum to 120% and
     * compose to 84%, and that difference is the whole reason this is a function and not a {@code +}.
     */
    static double composed(double... fractions) {
        double left = 1.0;
        for (double fraction : fractions) {
            left *= 1.0 - clamp(fraction, 1.0);
        }
        return 1.0 - left;
    }

    private CombatStats() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CombatStats::onDamage);
    }

    /**
     * The damage left after a composed mitigation fraction is taken off it.
     *
     * <p>
     * Clamped here as well as by each channel's own ceiling so the curve can be tested without a
     * game, and so a value arriving from anywhere else cannot invert the sign and heal the target.
     */
    static float mitigated(float damage, double fraction) {
        if (damage <= 0.0F) {
            return damage;
        }
        return (float) (damage * (1.0 - clamp(fraction, 1.0)));
    }

    /**
     * What a raised shield's {@code guard} is worth as a fraction, at its own ceiling.
     *
     * <p>
     * Its own function because the ceiling is guard's rather than the stack's: {@link #composed} may
     * legitimately exceed {@link #MAX_GUARD} once a second source joins, so capping the composed
     * figure at it would silently cap the whole stack to one channel's limit.
     */
    public static double guardFraction(double guard) {
        return clamp(guard, MAX_GUARD);
    }

    /**
     * The damage after the attacker's true-damage floor is put under it.
     *
     * <p>
     * A floor rather than an addition: the mitigated figure is what the target earned, and this says
     * how little of the original swing that can be reduced to. Measured against the original hit and
     * not against the mitigated one, so mitigation cannot shrink the floor along with everything else.
     */
    static float floored(float mitigated, float original, double fraction) {
        if (original <= 0.0F) {
            return mitigated;
        }
        return Math.max(mitigated, (float) (original * clamp(fraction, MAX_TRUE_DAMAGE)));
    }

    /** NaN reads as none, which is what the attribute's own sanitize does with it too. */
    private static double clamp(double value, double max) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(max, value));
    }

    private static void onDamage(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        float damage = event.getNewDamage();

        double mitigation = resistance(victim.getAttributeValue(RiftfallAttributes.RESISTANCE),
                victim.getAttributeValue(RiftfallAttributes.DAMAGE_RESISTANCE), tierOf(victim));
        if (isGuarding(victim, event.getSource())) {
            mitigation = composed(mitigation,
                    guardFraction(victim.getAttributeValue(RiftfallAttributes.GUARD)));
        }
        // Fireborn composes here, so the true-damage floor still holds under it.
        mitigation = composed(mitigation, RelicSignatureStats.fireTurnedAway(victim, event.getSource()),
                RelicSignatureCosts.frostTurnedAway(victim, event.getSource()));
        damage = mitigated(damage, mitigation);
        damage = floored(damage, event.getOriginalDamage(), trueDamageOf(event.getSource()));
        // Berserker Rage's cost lands on what mitigation left.
        damage = RelicSignatureProcs.rageTaken(victim, damage);

        if (damage != event.getNewDamage()) {
            event.setNewDamage(damage);
        }
    }

    /**
     * The rift tier the victim stands in, or the encounter tier in an Unseated arena; anywhere else reads as
     * the default tier.
     */
    static int tierOf(LivingEntity victim) {
        if (!(victim.level() instanceof ServerLevel level)) {
            return RiftKeyItem.DEFAULT_TIER;
        }
        return tier(UnseatedArena.tier(level.dimension()), RiftLevels.tier(level));
    }

    /** An arena's encounter tier wins; 0 means the level is not one. */
    static int tier(int arena, int rift) {
        return arena > 0 ? arena : rift;
    }

    /**
     * Whether the victim has a shield up and this hit went past it. {@code isBlocking} is not
     * redundant with the second test: a source is "not blocked" both when the shield failed to stop
     * it and when there was no shield.
     */
    private static boolean isGuarding(LivingEntity victim, DamageSource source) {
        return victim.isBlocking()
                && !source.is(DamageTypeTags.BYPASSES_EFFECTS)
                && !victim.isDamageSourceBlocked(source);
    }

    /**
     * {@code getEntity} rather than {@code getDirectEntity}, so an arrow or a spell projectile bills
     * the stat to whoever loosed it rather than to itself.
     */
    private static double trueDamageOf(DamageSource source) {
        Entity attacker = source.getEntity();
        return attacker instanceof LivingEntity living
                ? living.getAttributeValue(RiftfallAttributes.TRUE_DAMAGE)
                : 0.0;
    }
}
