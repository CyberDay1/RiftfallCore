package com.cyberday1.riftfall.shield;

import com.cyberday1.riftfall.init.RiftfallAttributes;
import com.cyberday1.riftfall.stat.ClassAlignment;
import com.cyberday1.riftfall.stat.Derivation;
import com.cyberday1.riftfall.stat.StatBlock;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.jetbrains.annotations.Nullable;

/**
 * How many pool points a Shield node grants: a number of median boss hits at the rift's tier, times
 * the node's rank multiplier, times the caster's class main and secondary stat (ruled 2026-10-03).
 * By ruling, the size is based on the damage mobs and bosses deal, so
 * the hit is measured, not picked: rig check {@code combat-decisions} fails if this table drifts
 * from what the decisions probe lands on the worn kit. Tiers between and past the measured ones are
 * read off the straight line through their neighbours.
 */
public final class ShieldSizing {

    /** The tiers the decisions probe measures ({@code COMBAT_TIERS} in the rig). */
    static final int[] TIERS = {1, 8, 20};

    /**
     * Median over boss rows of each row's median hit on the worn kit, at each of {@link #TIERS}. Re-measured
     * 2026-10-10 with slice C3's tier curve live (rig-20261010-190518): the kit now turns away 10/30/45%.
     */
    static final double[] BOSS_HIT = {3.600, 9.411, 20.299};

    /** How many median boss hits a rank-0 shield holds. The one number to move. */
    public static final double BOSS_HITS = 1.0;

    private ShieldSizing() {
    }

    /** The median boss hit at {@code tier}, measured at {@link #TIERS} and straight-line between and past them. */
    public static double bossHit(int tier) {
        if (tier <= TIERS[0]) {
            return BOSS_HIT[0];
        }
        int upper = 1;
        while (upper < TIERS.length - 1 && tier > TIERS[upper]) {
            upper++;
        }
        int lower = upper - 1;
        double slope = (BOSS_HIT[upper] - BOSS_HIT[lower]) / (TIERS[upper] - TIERS[lower]);
        return BOSS_HIT[lower] + slope * (tier - TIERS[lower]);
    }

    /** The points a Shield cast grants at {@code tier}, given the node's rank multiplier. */
    public static double points(int tier, double magnitudeMultiplier) {
        return BOSS_HITS * bossHit(tier) * magnitudeMultiplier;
    }

    /** The main stat's share of the two (class-stat-alignment's {@code w}). Not yet ruled. */
    public static final double MAIN_WEIGHT = 2.0 / 3.0;

    /** What the class's main and secondary stat multiply a grant by: 1 + PER_BAND x the weighted pair, each through the curve. */
    public static double statMultiplier(double main, double secondary) {
        return 1.0 + Derivation.PER_BAND * (MAIN_WEIGHT * Derivation.effective(main)
                + (1.0 - MAIN_WEIGHT) * Derivation.effective(secondary));
    }

    /** The term for this caster: its class's pair read off its own attributes, or 1 with no class pair. */
    public static double statMultiplier(LivingEntity caster, @Nullable String skillClass) {
        ClassAlignment.Pair pair = ClassAlignment.pair(skillClass);
        if (pair == null) {
            return 1.0;
        }
        return statMultiplier(value(caster, pair.main()), value(caster, pair.secondary()));
    }

    /** 1 + the caster's {@code riftfall:shield_strength}: every pool its Shield casts grant is this much larger. */
    public static double strengthMultiplier(LivingEntity caster) {
        AttributeInstance instance = caster.getAttribute(RiftfallAttributes.SHIELD_STRENGTH);
        return 1.0 + (instance == null ? 0.0 : instance.getValue());
    }

    private static double value(LivingEntity caster, StatBlock.Primary primary) {
        AttributeInstance instance = caster.getAttribute(RiftfallAttributes.primary(primary));
        return instance == null ? 0.0 : instance.getValue();
    }
}
