package com.cyberday1.riftfall.mob;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.rift.RiftKeyItem;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What makes a tier 10 rift's ordinary mobs different from a tier 1 rift's.
 *
 * <p>
 * The sibling of {@link com.cyberday1.riftfall.boss.BossScaling}, applied at the same moment in the
 * same order &mdash; after the mob is constructed, before it is added to the level, so that nothing
 * of the mob's own runs after us and writes over the top. Everything that class says about touching
 * a foreign mob's attributes holds here word for word: {@code getAttribute} is nullable and a modded
 * mob may not declare a given one, {@code addOrReplacePermanentModifier} is used because the plain
 * add throws on a duplicate id, and every id is namespaced so that an equipment change cannot delete
 * ours by collision.
 *
 * <h2>Where this parts company with the boss curve, and why</h2>
 * Bosses are not scaled through {@code MAX_HEALTH}, for two reasons: the attribute is clamped at
 * 1024, and a boss keys its phase changes to <em>fractions</em> of its own health, so inflating the
 * maximum re-paces a fight its author designed rather than making a harder one. Neither reason
 * survives the trip to an ordinary mob. A zombie has twenty health and no phases, so the ceiling is
 * three orders of magnitude away and there is no pacing to break.
 *
 * <p>
 * So this scales health directly, which is also the more honest thing for the player: a rift mob
 * that takes four hits instead of two <em>reads</em> as tougher, where one that quietly halves your
 * damage reads as your weapon being broken. Health is the lever the player can see, and it is the one
 * this scaling leads with; the resistance below is there because health alone cannot be pushed far enough
 * without running into that same 1024.
 *
 * <p>
 * The numbers are smaller than the boss ones across the board. A rift is walked through many mobs
 * and fought against few, so a curve tuned to make one encounter climb steeply makes a corridor
 * impassable.
 *
 * <h2>Why armour was replaced rather than raised</h2>
 * There used to be an armour line here. It was dead on arrival, and not because of the number: the
 * pack's damage reaches the player through a magic type that carries {@code bypasses_armor}, and what
 * survives that meets pierce and shred. A census of the 217 mobs in the rift rosters found 111 of them
 * sitting at zero armour, which is the same fact from the other side &mdash; nobody tunes a lever the
 * pack has already routed around.
 *
 * <p>
 * {@code puffish_attributes:resistance} is the one that cannot be routed around, so it takes over the
 * job. It is applied after armour and before the attacker's shred, and the only damage that escapes it
 * is tagged {@code bypasses_effects}. Nothing in Riftfall depends on the mod being installed: the
 * attribute is looked up by id at runtime and the line simply does not happen without it.
 *
 * <h2>Why health is shaped as well as scaled</h2>
 * Ordinary encounters use RiftMobBalance's health band before applying tier gains. Health above
 * 60 contributes a quarter of its original excess, capped at 100 before tier scaling. This keeps
 * imported miniboss-sized health bars from dominating corridors even at tier one. The per-tier gain
 * then combines a flat part for fragile mobs with a proportional part capped at {@link #HEALTH_KNEE}.
 */
public final class MobScaling {

    /**
     * Extra health per tier, as a fraction of the mob's own, counting only the health below
     * {@link #HEALTH_KNEE}.
     *
     * <p>
     * Raising it lengthens every fight in the dungeon at once, which is felt as the rift being slow
     * rather than as it being hard; reach for {@link #DAMAGE_PER_TIER} first if the intent is danger.
     */
    static final double HEALTH_PER_TIER = 0.07;

    /**
     * Extra health per tier in points, added to every mob whatever it started with.
     *
     * <p>
     * Small, and aimed at one end of the roster: a mob with two health gains nothing worth having from
     * a fraction of two, so without this the bottom of the roster stays a rounding error at every tier.
     */
    static final double HEALTH_FLAT_PER_TIER = 0.5;

    /**
     * The health above which {@link #HEALTH_PER_TIER} stops counting.
     *
     * <p>
     * Set above the roster's ninth decile, so it is the elites it bites and not the ordinary room. A
     * proportional curve pays out most to whatever already had the most, and the two-hundred-health
     * pieces in the library do not need to be the thing a tier 20 rift is about.
     */
    static final double HEALTH_KNEE = 60.0;

    /**
     * Extra attack damage per tier, as a fraction of the mob's own.
     *
     * <p>
     * Lower than the boss curve's 0.25 because of how the two are met. A boss is one thing hitting a
     * prepared party; the mobs from a spawner arrive several at a time and their damage stacks, so
     * the same per-tier fraction is several times the pressure.
     */
    static final double DAMAGE_PER_TIER = 0.12;

    /**
     * How much of an extra mob's worth of effective health each tier buys, spent as resistance.
     *
     * <p>
     * The same hyperbolic shape the boss curve uses and for the same reason: a tier is an unbounded
     * integer somebody can type, and {@code 1 - 1/(1 + k*steps)} approaches total immunity without ever
     * arriving. Smaller than the boss's {@code 0.08} because a corridor holds several mobs and a boss
     * room holds one.
     */
    static final double RESISTANCE_PER_TIER = 0.05;

    /**
     * Extra movement speed per tier, as a fraction of the mob's own.
     *
     * <p>
     * Deliberately tiny. A mob faster than a sprinting player cannot be disengaged from, and unlike a
     * boss fight &mdash; which a player chose to be in &mdash; a corridor is somewhere they are trying
     * to get through.
     */
    static final double SPEED_PER_TIER = 0.008;

    /**
     * Extra follow range per tier, as a fraction of the mob's own.
     *
     * <p>
     * This is the one that decides how much of a room comes at you at once, because it is the range
     * at which a mob notices a player at all. Raising it turns a rift from a series of encounters
     * into one long one.
     */
    static final double FOLLOW_RANGE_PER_TIER = 0.05;

    private static final ResourceLocation HEALTH_ID = RiftfallCore.id("tier_mob_max_health");
    private static final ResourceLocation DAMAGE_ID = RiftfallCore.id("tier_mob_attack_damage");
    private static final ResourceLocation RESISTANCE_ID = RiftfallCore.id("tier_mob_resistance");
    private static final ResourceLocation SPEED_ID = RiftfallCore.id("tier_mob_movement_speed");
    private static final ResourceLocation FOLLOW_RANGE_ID = RiftfallCore.id("tier_mob_follow_range");

    /** Resolved by id rather than imported, because Riftfall does not depend on the mod that owns it. */
    private static final ResourceLocation RESISTANCE_ATTRIBUTE =
            ResourceLocation.fromNamespaceAndPath("puffish_attributes", "resistance");

    private MobScaling() {
    }

    /**
     * Scales one rift mob to a tier.
     *
     * <p>
     * Tier gains begin above tier one. Ordinary mobs above 60 base health are first brought into
     * the corridor encounter band by RiftMobBalance; bosses use their separate scaling path.
     */
    public static void scale(Mob mob, int tier) {
        SporeCombat.scale(mob, tier);
        int steps = Math.max(0, tier - RiftKeyItem.DEFAULT_TIER);
        AttributeModifier.Operation ofItsOwn = AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
        apply(mob, Attributes.MAX_HEALTH, HEALTH_ID, healthGain(mob, steps),
                AttributeModifier.Operation.ADD_VALUE);
        apply(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, DAMAGE_PER_TIER * steps, ofItsOwn);
        apply(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED_PER_TIER * steps, ofItsOwn);
        apply(mob, Attributes.FOLLOW_RANGE, FOLLOW_RANGE_ID, FOLLOW_RANGE_PER_TIER * steps, ofItsOwn);
        // Absent whenever the mod that owns the attribute is not installed, which is a supported case.
        BuiltInRegistries.ATTRIBUTE.getHolder(RESISTANCE_ATTRIBUTE).ifPresent(attribute ->
                apply(mob, attribute, RESISTANCE_ID, resistanceFraction(steps), ofItsOwn));

        // Last, and unlike the boss curve's copy of this line it is doing real work: raising a maximum
        // never raises the current value (LivingEntity#onAttributeUpdated only clamps down), so without
        // this every rift mob would arrive at its tier 1 health inside a taller bar.
        mob.setHealth(mob.getMaxHealth());
    }

    /**
     * The adjustment from the original base: corridor health tuning plus the tier's health gain.
     *
     * <p>
     * Read off the base rather than the current value so that equipment cannot compound into it, and
     * added as {@code ADD_VALUE} rather than as a multiplier so that the shape survives: a multiplier
     * applied to the raw base would undo the flat part on exactly the small mobs it exists for.
     */
    private static double healthGain(Mob mob, int steps) {
        double base = mob.getAttributeBaseValue(Attributes.MAX_HEALTH);
        double tuned = RiftMobBalance.health(base);
        return tuned - base + steps * (HEALTH_FLAT_PER_TIER + HEALTH_PER_TIER * Math.min(tuned, HEALTH_KNEE));
    }

    /**
     * The fraction of incoming damage a rift mob at this tier turns away, between zero and one.
     *
     * <p>
     * The attribute is read by the mod that owns it as a proportional modifier on the damage rather
     * than as a value of its own, so this is the number the player's hit is multiplied down by; the
     * effective health it buys is {@code 1 + RESISTANCE_PER_TIER * steps}.
     */
    public static double resistanceFraction(int steps) {
        return 1.0 - 1.0 / (1.0 + RESISTANCE_PER_TIER * steps);
    }

    private static void apply(Mob mob, Holder<Attribute> attribute, ResourceLocation id, double amount,
                              AttributeModifier.Operation operation) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (amount == 0.0) { instance.removeModifier(id); return; }
        instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
    }
}
