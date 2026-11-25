package com.cyberday1.riftfall.skill;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One castable node: what it casts, what carries its power, its cooldown, and the lever a point
 * buys for its archetype. Every archetype buys cooldown; the {@link Lever} is what it buys on top.
 *
 * <p>DoT, Summon and Buff each carry two rates and every other lever one, which is the ruling rather
 * than an accident. Passives are {@link PassiveNode}: they have no cast, so no ability and no cooldown.
 *
 * <p>{@code powerMultiplier} scales the bound ability's whole power on every cast, so a pack ability
 * lands at the catalogue's number without a copy: catalogue coefficient over the pack's.
 */
public record SkillNode(String id,
                        Engine engine,
                        ResourceLocation ability,
                        ResourceLocation powerAttribute,
                        int baseCooldownTicks,
                        int maxRank,
                        double cooldownPerRank,
                        Lever lever,
                        double powerMultiplier,
                        @Nullable Category category,
                        @Nullable DotSpec dot,
                        @Nullable PerEnemy perEnemy) {

    /** A node that casts its ability at the ability's own power. */
    public static final double NATIVE_POWER = 1.0;
    /** {@code tools/gen_str_physical.py}'s ANCHOR and EXCHANGE: the 320% rung is a 3.2 coefficient. */
    public static final double CATALOGUE_ANCHOR = 320.0;
    public static final double EXCHANGE = 3.2;

    public SkillNode(String id, Engine engine, ResourceLocation ability, ResourceLocation powerAttribute,
                     int baseCooldownTicks, int maxRank, double cooldownPerRank, Lever lever) {
        this(id, engine, ability, powerAttribute, baseCooldownTicks, maxRank, cooldownPerRank, lever, NATIVE_POWER);
    }

    public SkillNode(String id, Engine engine, ResourceLocation ability, ResourceLocation powerAttribute,
                     int baseCooldownTicks, int maxRank, double cooldownPerRank, Lever lever, double powerMultiplier) {
        this(id, engine, ability, powerAttribute, baseCooldownTicks, maxRank, cooldownPerRank, lever, powerMultiplier,
                null);
    }

    public SkillNode(String id, Engine engine, ResourceLocation ability, ResourceLocation powerAttribute,
                     int baseCooldownTicks, int maxRank, double cooldownPerRank, Lever lever, double powerMultiplier,
                     @Nullable Category category) {
        this(id, engine, ability, powerAttribute, baseCooldownTicks, maxRank, cooldownPerRank, lever, powerMultiplier,
                category, null);
    }

    public SkillNode(String id, Engine engine, ResourceLocation ability, ResourceLocation powerAttribute,
                     int baseCooldownTicks, int maxRank, double cooldownPerRank, Lever lever, double powerMultiplier,
                     @Nullable Category category, @Nullable DotSpec dot) {
        this(id, engine, ability, powerAttribute, baseCooldownTicks, maxRank, cooldownPerRank, lever, powerMultiplier,
                category, dot, null);
    }

    /**
     * A pool sized by the enemies the cast struck (the Paladin's "Unbreakable Vanguard"): {@code shield} median boss
     * hits for each enemy struck, counted up to {@code cap}, to the caster and every other player within
     * {@code allyRadius}. Rides any lever: the rank's magnitude, the class pair and shield strength scale it as they
     * scale a Shield node's pool.
     */
    public record PerEnemy(double shield, int cap, double allyRadius) {
        public PerEnemy {
            nonNegative("shield", shield);
            nonNegative("allyRadius", allyRadius);
            if (cap < 1) {
                throw new IllegalArgumentException("a per-enemy pool counts at least one enemy, cap was " + cap);
            }
        }

        /** The boss hits {@code struck} enemies are worth: each counts until the cap. */
        public double bossHits(int struck) {
            return shield * Math.max(0, Math.min(struck, cap));
        }
    }

    /**
     * The DoT the node's ability lands through its {@code riftfall:<type>} impact (H6): {@code coefficient} of the
     * cast's power over {@code seconds}, before a rank's extra seconds.
     */
    public record DotSpec(DotType type, double coefficient, double seconds) {
        public DotSpec {
            if (type == null) {
                throw new IllegalArgumentException("a DoT needs a type");
            }
            nonNegative("coefficient", coefficient);
            if (!(seconds > 0.0) || Double.isInfinite(seconds)) {
                throw new IllegalArgumentException("a DoT lasts a positive time, was " + seconds);
            }
        }
    }

    /**
     * An authored node family (PA-2: authored, never read off the archetype). Each has its own haste attribute,
     * {@code riftfall:<key>_haste}, added to ability haste on that family's nodes only. Seven, one per scoped
     * cooldown passive in the catalogue.
     */
    public enum Category {
        HEAL, MOVEMENT, SUMMON, TRAP, EXPLOSIVE, FORM, STEALTH;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Which engine runs this node's ability. {@code castSlot} invokes either ({@link RiftIronsCast} for
     * Iron's); an Iron's node is also granted and priced on Iron's own item routes ({@link RiftIronsGate}).
     */
    public enum Engine {
        SPELL_ENGINE,
        IRONS_SPELLBOOKS
    }

    /** What a point buys besides cooldown, one record per ruled archetype. */
    public sealed interface Lever permits Damage, Movement, Heal, Shield, Control, DoT, Summon, Buff {
    }

    public record Damage(double damagePerRank) implements Lever {
        public Damage {
            nonNegative("damagePerRank", damagePerRank);
        }
    }

    /** Cooldown only, by ruling. */
    public record Movement() implements Lever {
    }

    public record Heal(double magnitudePerRank) implements Lever {
        public Heal {
            nonNegative("magnitudePerRank", magnitudePerRank);
        }
    }

    /** {@code allyRadius} above 0 grants the same pool to every other player that close (a party shield). */
    public record Shield(double magnitudePerRank, double allyRadius) implements Lever {
        public Shield {
            nonNegative("magnitudePerRank", magnitudePerRank);
            nonNegative("allyRadius", allyRadius);
        }

        public Shield(double magnitudePerRank) {
            this(magnitudePerRank, 0.0);
        }
    }

    /** Lower cost, geometric like cooldown. Scales Riftfall's own mana charge, so it reaches every engine. */
    public record Control(double costPerRank) implements Lever {
        public Control {
            belowOne("costPerRank", costPerRank);
        }
    }

    public record DoT(double damagePerRank, double durationSecondsPerRank) implements Lever {
        public DoT {
            nonNegative("damagePerRank", damagePerRank);
            nonNegative("durationSecondsPerRank", durationSecondsPerRank);
        }
    }

    /** Owner scaling is a fraction of the ability's own authored owner coefficients, per rank. */
    public record Summon(double ownerScalingPerRank, double durationSecondsPerRank) implements Lever {
        public Summon {
            nonNegative("ownerScalingPerRank", ownerScalingPerRank);
            nonNegative("durationSecondsPerRank", durationSecondsPerRank);
        }
    }

    /** Magnitude rides the power attribute like Heal; duration is the buff effect's own seconds. */
    public record Buff(double magnitudePerRank, double durationSecondsPerRank) implements Lever {
        public Buff {
            nonNegative("magnitudePerRank", magnitudePerRank);
            nonNegative("durationSecondsPerRank", durationSecondsPerRank);
        }
    }

    public SkillNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("a node needs an id");
        }
        if (engine == null || ability == null || powerAttribute == null || lever == null) {
            throw new IllegalArgumentException(id + ": engine, ability, powerAttribute and lever are required");
        }
        if (baseCooldownTicks < 1) {
            throw new IllegalArgumentException(id + ": base cooldown must be at least one tick, was " + baseCooldownTicks);
        }
        if (maxRank < 0) {
            throw new IllegalArgumentException(id + ": maxRank cannot be negative, was " + maxRank);
        }
        // A rate of 1.0 would take the cooldown to zero in one rank, which is the thing the
        // geometric shape exists to prevent.
        belowOne("cooldownPerRank", cooldownPerRank);
        if (!(powerMultiplier > 0.0) || Double.isInfinite(powerMultiplier)) {
            throw new IllegalArgumentException(id + ": powerMultiplier must be finite and above zero, was " + powerMultiplier);
        }
    }

    /** Whether a cast hands the engine a power term at all. */
    public boolean rescaled() {
        return powerMultiplier != NATIVE_POWER;
    }

    /**
     * The multiplier that lands a catalogue percentage on an ability whose own coefficient is
     * {@code packCoefficient}: the generator's coefficient for that percentage, over the pack's.
     */
    public static double powerFor(double cataloguePercent, double packCoefficient) {
        if (!(cataloguePercent > 0.0) || !(packCoefficient > 0.0)) {
            throw new IllegalArgumentException("need a positive percentage and coefficient, was "
                    + cataloguePercent + " over " + packCoefficient);
        }
        return cataloguePercent / CATALOGUE_ANCHOR * EXCHANGE / packCoefficient;
    }

    /** True when {@code rank} is one this node can be held at. Out of range is refused, not clamped. */
    public boolean holds(int rank) {
        return rank >= 0 && rank <= maxRank;
    }

    static void nonNegative(String name, double rate) {
        if (!(rate >= 0.0) || Double.isInfinite(rate)) {
            throw new IllegalArgumentException(name + " must be a finite non-negative rate, was " + rate);
        }
    }

    static void belowOne(String name, double rate) {
        if (!(rate >= 0.0 && rate < 1.0)) {
            throw new IllegalArgumentException(name + " must be in [0, 1), was " + rate);
        }
    }
}
