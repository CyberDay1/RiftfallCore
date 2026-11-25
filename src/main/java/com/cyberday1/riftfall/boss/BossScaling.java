package com.cyberday1.riftfall.boss;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.rift.RiftKeyItem;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What makes a tier 10 boss different from the same boss at tier 1.
 *
 * <p>
 * Called once from {@link RiftBosses#take}, after the mob is built and before it joins the level, so
 * that nothing the boss's own spawn code does can run after us.
 *
 * <p>
 * Note that <strong>no curve here touches {@code MAX_HEALTH}</strong>. Effective health is applied to
 * incoming damage by {@link #damageTakenMultiplier} instead. That is the least obvious decision in
 * the subsystem, and there are two independent reasons for it.
 */
public final class BossScaling {

    /** Extra attack damage per tier, as a fraction of the boss's own. The lever players feel most. */
    static final double DAMAGE_PER_TIER = 0.25;

    /**
     * Extra armour per tier, in points, added <strong>flat</strong> where every other curve here is
     * proportional. Armour defaults to zero on most mobs, so a proportional modifier would scale
     * nothing. Clamped at 30.
     */
    static final double ARMOUR_PER_TIER = 1.5;

    /**
     * Extra movement speed per tier, as a fraction of the boss's own. Small on purpose: past roughly
     * 0.05 a high-tier boss outruns a sprinting player and positioning stops being a move.
     */
    static final double SPEED_PER_TIER = 0.015;

    /**
     * Extra follow range per tier, as a fraction of the boss's own. Also the range {@link ThreatGoal}
     * reads to decide who is in the fight, so this widens the arena as well as the leash.
     */
    static final double FOLLOW_RANGE_PER_TIER = 0.08;

    /**
     * Extra knockback resistance per tier, as a fraction of the boss's own, so deliberately nothing
     * for a boss whose designer left it at zero. Clamped at 1.0, total immunity.
     */
    static final double KNOCKBACK_RESISTANCE_PER_TIER = 0.05;

    /**
     * How much of an extra boss's worth of effective health each tier adds. The real difficulty lever
     * and the one to reach for first: at {@value} a tier 10 boss takes damage as though it had about
     * 1.7 times the health, while still showing and behaving as its designed self.
     */
    static final double EFFECTIVE_HEALTH_PER_TIER = 0.08;

    /**
     * What the second, third and fourth player each add to a boss's effective health, as fractions of
     * its solo health: 60/50/40, so a party of four fights {@code H(4) = 2.50}. Every player past the
     * fourth adds the last step again. Fixed when the boss is taken, so a death never lowers it.
     */
    static final double[] HEADCOUNT_STEPS = {0.60, 0.50, 0.40};

    private static final ResourceLocation DAMAGE_ID = RiftfallCore.id("tier_attack_damage");
    private static final ResourceLocation ARMOUR_ID = RiftfallCore.id("tier_armour");
    private static final ResourceLocation SPEED_ID = RiftfallCore.id("tier_movement_speed");
    private static final ResourceLocation FOLLOW_RANGE_ID = RiftfallCore.id("tier_follow_range");
    private static final ResourceLocation KNOCKBACK_ID = RiftfallCore.id("tier_knockback_resistance");

    private BossScaling() {
    }

    /**
     * Scales a boss to a tier. Tier 1 is a no-op by arithmetic rather than by a special case, since
     * every curve is per tier <em>above</em> the first.
     */
    public static void scale(Mob mob, int tier) {
        int steps = steps(tier);
        AttributeModifier.Operation ofItsOwn = AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
        apply(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, DAMAGE_PER_TIER * steps, ofItsOwn);
        apply(mob, Attributes.ARMOR, ARMOUR_ID, ARMOUR_PER_TIER * steps,
                AttributeModifier.Operation.ADD_VALUE);
        apply(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED_PER_TIER * steps, ofItsOwn);
        apply(mob, Attributes.FOLLOW_RANGE, FOLLOW_RANGE_ID, FOLLOW_RANGE_PER_TIER * steps, ofItsOwn);
        apply(mob, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID,
                KNOCKBACK_RESISTANCE_PER_TIER * steps, ofItsOwn);

        // Must stay last. A no-op today since nothing above touches MAX_HEALTH, but raising a maximum
        // never raises the current value (LivingEntity#onAttributeUpdated only clamps down), so
        // without this a health line added above would ship a boss at a fraction of its own bar.
        mob.setHealth(mob.getMaxHealth());
    }

    /**
     * The fraction of its normal damage a solo boss at this tier actually takes; this is where
     * effective health lives instead of {@code MAX_HEALTH}. Hyperbolic rather than exponential so that
     * it can never reach zero however high a tier somebody types.
     */
    public static float damageTakenMultiplier(int tier) {
        return damageTakenMultiplier(tier, 1);
    }

    /** {@link #damageTakenMultiplier(int)} for a boss taken against a party of {@code headcount}. */
    public static float damageTakenMultiplier(int tier, int headcount) {
        return (float) (1.0 / ((1.0 + EFFECTIVE_HEALTH_PER_TIER * steps(tier)) * partyHealth(headcount)));
    }

    /** {@code H(n)}: a party of this size's effective health as a multiple of solo. 1.0 at or below one. */
    public static double partyHealth(int headcount) {
        double health = 1.0;
        for (int player = 2; player <= headcount; player++) {
            health += HEADCOUNT_STEPS[Math.min(player - 2, HEADCOUNT_STEPS.length - 1)];
        }
        return health;
    }

    /** Tiers above the first, floored at zero so a nonsense tier cannot make a boss weaker. */
    private static int steps(int tier) {
        return Math.max(0, tier - RiftKeyItem.DEFAULT_TIER);
    }

    /**
     * Adds one permanent modifier, if the boss has that attribute and there is anything to add.
     *
     * <p>
     * The null check is the whole reason this is a method: these are other people's mobs, and
     * {@code ATTACK_DAMAGE} in particular comes from {@code Monster#createMonsterAttributes}, so a
     * boss extending {@code PathfinderMob} may never declare it. Asking with {@code getAttribute}
     * rather than {@code getAttributeValue} matters for the same reason — the latter throws.
     */
    private static void apply(Mob mob, Holder<Attribute> attribute, ResourceLocation id, double amount,
                              AttributeModifier.Operation operation) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null || amount == 0.0) {
            return;
        }
        instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
    }
}
