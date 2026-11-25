package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.gear.RelicSignatureCosts;

import com.cyberday1.riftfall.init.RiftfallAttributes;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.cyberday1.riftfall.shield.RiftShields;
import com.cyberday1.riftfall.shield.ShieldSizing;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Casts one node: gate on Riftfall's own timer, put the rank on the caster for the length of the
 * call, invoke, put the caster back.
 *
 * <p>A rank reaches the cast by two channels. Damage and magnitude go on the caster's power
 * attribute as a transient modifier, removed in a {@code finally} because a melee node's attribute
 * is the one {@code WeaponFloor} owns. Everything an attribute cannot carry travels as
 * {@link Riders}, handed to the invoker. A Shield node also grants Riftfall's own pool once the cast
 * lands, sized by {@link ShieldSizing} at the rift's tier and on the caster's class pair.
 *
 * <p>Names no mod type. {@link RiftSpellEngine}, {@link RiftSpellGate} and {@link RiftIronsCast} do.
 */
public final class SkillCasts {

    /** What a cast did, or the reason it did not. Eight different facts, not one boolean. */
    public enum Result {
        CAST, ON_COOLDOWN, NO_MANA, NO_ENGINE, UNKNOWN_ABILITY, UNKNOWN_ATTRIBUTE, BAD_RANK, NOT_CASTABLE, NO_TARGET, WRONG_WEAPON
    }

    /** What the invoker did. Only {@link #CAST} keeps the mana, starts the timer and grants a shield. */
    enum Invocation {
        CAST, NO_SUCH_ABILITY, NOT_CASTABLE, NO_TARGET;

        /** {@code castable} is the ability's {@code active} block being present, not its type label. */
        static Invocation gate(boolean exists, boolean castable) {
            if (!exists) {
                return NO_SUCH_ABILITY;
            }
            return castable ? CAST : NOT_CASTABLE;
        }
    }

    /**
     * An ability's top-level school and that school's damage type, which is what power is read from,
     * and the school each DAMAGE impact is built with, which is what the hit lands as.
     */
    public record AbilitySchool(String school, String damageType, List<String> damageSchools) {

        /** An impact's own school when it names one, else the ability's: {@code SpellImpacts#performImpact}. */
        static <S> S impactSchool(S ability, @Nullable S impact) {
            return impact != null ? impact : ability;
        }

        /** Whether some DAMAGE impact lands in a school other than the top-level one. */
        public boolean overridden() {
            return damageSchools.stream().anyMatch(s -> !s.equals(school));
        }
    }

    /** What a cast costs, taken just before the engine runs and handed back if it does not cast. */
    public interface Price {
        Price FREE = new Price() {
            @Override
            public boolean take() {
                return true;
            }

            @Override
            public void refund() {
            }
        };

        /** All or nothing; false refuses the cast with {@link Result#NO_MANA}. */
        boolean take();

        void refund();
    }

    /**
     * The levers no caster attribute can carry, as the invoker receives them. Each field is one
     * archetype's lever and stays separate: an engine that can carry one may not carry another.
     */
    public record Riders(double dotSeconds, double summonSeconds, double ownerScaling, double costMultiplier,
                         double buffSeconds) {

        public static final Riders NONE = new Riders(0.0, 0.0, 0.0, 1.0, 0.0);

        public static Riders of(SkillNode node, int rank) {
            return new Riders(SkillRanks.dotSeconds(node, rank), SkillRanks.summonSeconds(node, rank),
                    SkillRanks.ownerScaling(node, rank), SkillRanks.costMultiplier(node, rank),
                    SkillRanks.buffSeconds(node, rank));
        }

        /** As {@link #of(SkillNode, int)}, plus the caster's summon passives on a Summon node (the Summoner batch). */
        public static Riders of(SkillNode node, int rank, double summonSeconds, double summonPower) {
            return of(node, rank, summonSeconds, summonPower, 0.0);
        }

        /** As above, plus the caster's DoT duration on a DoT node or one that lands a DoT (the Reaper batch). */
        public static Riders of(SkillNode node, int rank, double summonSeconds, double summonPower, double dotSeconds) {
            Riders base = of(node, rank);
            boolean summon = node.lever() instanceof SkillNode.Summon;
            boolean dot = dotSeconds > 0.0 && (node.lever() instanceof SkillNode.DoT || node.dot() != null);
            if (!summon && !dot) {
                return base;
            }
            return new Riders(base.dotSeconds() + (dot ? dotSeconds : 0.0),
                    base.summonSeconds() + (summon ? summonSeconds : 0.0),
                    base.ownerScaling() + (summon ? summonPower : 0.0), base.costMultiplier(), base.buffSeconds());
        }
    }

    /** The seam a test stands in for the engine at, so the rank can be read where the engine reads it. */
    @FunctionalInterface
    interface Invoker {
        Invocation invoke(ServerLevel level, LivingEntity caster, SkillNode node, Riders riders);
    }

    /**
     * What runs inside Riftfall's price: {@link #refuse} after the timer is checked and before the bar
     * is touched, {@link #run} once it is paid, {@link #landed} once a cast is kept. Riftfall's own cast
     * is one; a use that started in the engine (the item path, a melee hit) is another.
     */
    interface Paid {
        @Nullable
        Result refuse();

        Invocation run();

        default void landed() {
        }
    }

    /** Where a Shield cast's points go. A seam so a test can read the grant off a mocked caster. */
    @FunctionalInterface
    interface Shields {
        void grant(LivingEntity caster, double points);
    }

    private static final ResourceLocation POWER_ID =
            ResourceLocation.fromNamespaceAndPath("riftfall", "skill_rank_power");

    private static final Logger LOGGER = LogUtils.getLogger();

    private static Boolean spellEngine;
    /** Calls to {@link #charged}, any outcome. Probe read: a relic air jump must not be one. */
    private static final java.util.concurrent.atomic.AtomicLong CHARGED_CALLS = new java.util.concurrent.atomic.AtomicLong();
    private static Boolean irons;

    private SkillCasts() {
    }

    public static long chargedCalls() {
        return CHARGED_CALLS.get();
    }

    /** Checked before payment, recasts, or engine delivery on every cast route. */
    static boolean eligible(LivingEntity caster) {
        return caster.isAlive() && !caster.isSpectator() && !RelicSignatureCosts.refusesCast(caster);
    }

    /** Empty self casts may grant a Riftfall shield or open a store after engine delivery. */
    static boolean hasSelfEffect(LivingEntity caster, SkillNode node, boolean emptySelfCast) {
        SkillDef def = SkillDefs.get(node.id());
        return emptySelfCast && (node.lever() instanceof SkillNode.Shield
                || caster instanceof ServerPlayer && def != null && def.stored() != null);
    }

    /**
     * Spell Engine's AREA delivery can return false even after a successful synchronous completion.
     * A queued delivery returns true; a missing required target does neither.
     */
    public static boolean delivered(java.util.function.Function<java.util.function.Consumer<Boolean>, Boolean> delivery) {
        boolean[] completed = {false};
        boolean accepted = Boolean.TRUE.equals(delivery.apply(success -> completed[0] |= success));
        return accepted || completed[0];
    }

    public static Result cast(ServerLevel level, LivingEntity caster, SkillNode node, int rank) {
        return cast(level, caster, node, rank, Price.FREE);
    }

    public static Result cast(ServerLevel level, LivingEntity caster, SkillNode node, int rank, Price price) {
        return cast(level, caster, node, rank, price, (String) null);
    }

    /** {@code skillClass} is the caster's: a Shield grant scales on its main and secondary stat. */
    public static Result cast(ServerLevel level, LivingEntity caster, SkillNode node, int rank, Price price,
                              @Nullable String skillClass) {
        if (!engineAvailable(node.engine())) {
            return Result.NO_ENGINE;
        }
        Invoker invoker = node.engine() == SkillNode.Engine.SPELL_ENGINE ? RiftSpellEngine::cast : RiftIronsCast::cast;
        return cast(level, caster, node, rank, price, skillClass, invoker, RiftShields::grant);
    }

    static Result cast(ServerLevel level, LivingEntity caster, SkillNode node, int rank, Invoker invoker) {
        return cast(level, caster, node, rank, Price.FREE, invoker);
    }

    static Result cast(ServerLevel level, LivingEntity caster, SkillNode node, int rank, Price price,
                       Invoker invoker) {
        return cast(level, caster, node, rank, price, null, invoker, RiftShields::grant);
    }

    static Result cast(ServerLevel level, LivingEntity caster, SkillNode node, int rank, Price price,
                       @Nullable String skillClass, Invoker invoker) {
        return cast(level, caster, node, rank, price, skillClass, invoker, RiftShields::grant);
    }

    static Result cast(ServerLevel level, LivingEntity caster, SkillNode node, int rank, Price price,
                       Invoker invoker, Shields shields) {
        return cast(level, caster, node, rank, price, null, invoker, shields);
    }

    static Result cast(ServerLevel level, LivingEntity caster, SkillNode node, int rank, Price price,
                       @Nullable String skillClass, Invoker invoker, Shields shields) {
        return charged(level, caster, node, rank, price, new Paid() {
            private AttributeInstance power;
            private int struck;
            private Vec3 origin;

            @Override
            public Result refuse() {
                power = powerOf(caster, node.powerAttribute());
                return power == null ? Result.UNKNOWN_ATTRIBUTE : null;
            }

            @Override
            public Invocation run() {
                origin = caster.position();
                if (node.perEnemy() != null) {
                    EnemyHits.open(caster.getUUID());
                }
                try {
                    return atRankedPower(caster, node, rank,
                            () -> invoker.invoke(level, caster, node, Riders.of(node, rank,
                                    attribute(caster, RiftfallAttributes.SUMMON_DURATION),
                                    attribute(caster, RiftfallAttributes.SUMMON_POWER),
                                    attribute(caster, RiftfallAttributes.DOT_DURATION))));
                } finally {
                    if (node.perEnemy() != null) {
                        struck = EnemyHits.close(caster.getUUID());
                    }
                }
            }

            @Override
            public void landed() {
                if (TravelCast.defer(caster.getUUID(), node.id(), hits -> { struck = hits; rewards(); })) return;
                rewards();
            }

            private void rewards() {
                if (node.lever() instanceof SkillNode.Shield shield) {
                    // Read once the rank is off: the power attribute can be the class's own stat.
                    double points = shieldPoints(level, caster, node, rank, skillClass);
                    shields.grant(caster, points);
                    for (ServerPlayer ally : allies(level, caster, shield.allyRadius())) {
                        shields.grant(ally, points);
                    }
                }
                SkillNode.PerEnemy perEnemy = node.perEnemy();
                if (perEnemy != null) {
                    double points = perEnemyShieldPoints(level, caster, node, rank, skillClass, struck);
                    if (points > 0.0) {
                        shields.grant(caster, points);
                        // The party in reach where the cast began, not where a charge ended (ruled 2026-11-16).
                        for (ServerPlayer ally : allies(level, caster, origin, perEnemy.allyRadius())) {
                            shields.grant(ally, points);
                        }
                    }
                }
            }
        });
    }

    /**
     * Runs {@code body} with the rank on the caster's power attribute, as a cast carries it: a transient modifier,
     * always taken off again. At most one of the two multipliers is off 1.0: no archetype buys damage and magnitude.
     */
    static <T> T atRank(AttributeInstance power, SkillNode node, int rank, java.util.function.Supplier<T> body) {
        return atRank(power, node, rank, 1.0, body);
    }

    /** The same with {@code extra} multiplied in: the crafted-ammo bonus of a cast that used ammo up. */
    static <T> T atRank(AttributeInstance power, SkillNode node, int rank, double extra,
                        java.util.function.Supplier<T> body) {
        double bonus = SkillRanks.damageMultiplier(node, rank) * SkillRanks.magnitudeMultiplier(node, rank) * extra
                - 1.0;
        power.addTransientModifier(new AttributeModifier(POWER_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        try {
            return body.get();
        } finally {
            power.removeModifier(POWER_ID);
        }
    }

    /** The shared cast scope, also used by deferred strikes and measurement probes. */
    public static <T> T atRankedPower(LivingEntity caster, SkillNode node, int rank, java.util.function.Supplier<T> body) {
        AttributeInstance power = powerOf(caster, node.powerAttribute());
        if (power == null) throw new IllegalArgumentException("Missing power attribute for " + node.id());
        double ammo = SkillAmmo.multiplier(caster.getUUID(), node.id());
        return GunnerGuns.withGun(caster, node, () -> RangerWeapons.withBow(caster, node,
                () -> atRank(power, node, rank, ammo, body)));
    }

    /**
     * A counter's strike: {@code body} runs at {@code node}'s rank power, outside any price or
     * timer, the window's cast having paid both. False when the caster has no instance of the node's power attribute.
     */
    static boolean atRank(LivingEntity caster, SkillNode node, int rank, Runnable body) {
        AttributeInstance power = powerOf(caster, node.powerAttribute());
        if (power == null || !node.holds(rank)) {
            return false;
        }
        atRankedPower(caster, node, rank, () -> {
            body.run();
            return null;
        });
        return true;
    }

    /** The pool a Shield node's cast grants its caster and each ally in reach. The cast and the book both read it. */
    static double shieldPoints(ServerLevel level, LivingEntity caster, SkillNode node, int rank,
                               @Nullable String skillClass) {
        return ShieldSizing.points(RiftLevels.tier(level),
                SkillRanks.magnitudeMultiplier(node, rank) * ShieldSizing.statMultiplier(caster, skillClass))
                * ShieldSizing.strengthMultiplier(caster);
    }

    /** The same for a {@link SkillNode.PerEnemy} node's pool at {@code struck} enemies. */
    static double perEnemyShieldPoints(ServerLevel level, LivingEntity caster, SkillNode node, int rank,
                                       @Nullable String skillClass, int struck) {
        return perEnemyPoints(node.perEnemy(), struck, RiftLevels.tier(level),
                SkillRanks.magnitudeMultiplier(node, rank) * ShieldSizing.statMultiplier(caster, skillClass))
                * ShieldSizing.strengthMultiplier(caster);
    }

    /**
     * The pool a {@link SkillNode.PerEnemy} node grants for {@code struck} enemies at {@code tier}: its boss hits per
     * enemy, counted to the cap, at the tier's median boss hit, times {@code multiplier} (rank and class pair).
     */
    static double perEnemyPoints(SkillNode.PerEnemy perEnemy, int struck, int tier, double multiplier) {
        return ShieldSizing.bossHit(tier) * multiplier * perEnemy.bossHits(struck);
    }

    /**
     * Riftfall's price around {@code paid}: the node's timer, then the bar, kept and the timer started
     * only if it casts. The one place mana and cooldown are charged (ruled 2026-10-06: every route).
     */
    static Result charged(ServerLevel level, LivingEntity caster, SkillNode node, int rank, Price price, Paid paid) {
        CHARGED_CALLS.incrementAndGet();
        if (!eligible(caster)) {
            return Result.NOT_CASTABLE;
        }
        if (!node.holds(rank)) {
            return Result.BAD_RANK;
        }
        if (!SkillWeapons.canCast(caster, node)) {
            return Result.WRONG_WEAPON;
        }
        UUID who = caster.getUUID();
        long now = level.getGameTime();
        // A live store's recast releases it in place of the cast: free, on the running timer.
        if (Stored.recast(level, caster, node, rank, now)) {
            return Result.CAST;
        }
        if (SkillCooldowns.readyAt(caster, node.id()) > now) {
            return Result.ON_COOLDOWN;
        }
        Result refused = paid.refuse();
        if (refused != null) {
            return refused;
        }
        if (!price.take()) {
            return Result.NO_MANA;
        }
        Invocation invoked = Invocation.NO_SUCH_ABILITY;
        Runnable ammoBack = SkillAmmo.begin(caster, node);
        try {
            invoked = paid.run();
        } finally {
            if (invoked != Invocation.CAST) {
                price.refund();
                ammoBack.run();
            }
        }
        if (invoked != Invocation.CAST) {
            if (invoked == Invocation.NO_TARGET) return Result.NO_TARGET;
            return invoked == Invocation.NOT_CASTABLE ? Result.NOT_CASTABLE : Result.UNKNOWN_ABILITY;
        }
        paid.landed();
        GunnerGuns.landed(level, caster, node);
        if (!SkillCooldowns.bypasses(caster)) {
            SkillCooldowns.start(who, node.id(), now, SkillRanks.cooldownTicks(node, rank, abilityHaste(caster, node)));
        }
        // The reaction triggers' cast and move events, once the cast is kept.
        Reactions.cast(caster, node);
        if (!TravelCast.defer(who, node.id(), hits -> Stored.landed(level, caster, node, rank, level.getGameTime())))
            Stored.landed(level, caster, node, rank, now);
        return Result.CAST;
    }

    /**
     * The other living players within {@code radius} of a player caster, from the level's own list. A mob's
     * Shield cast has no party, so it shields nobody else.
     */
    static List<ServerPlayer> allies(ServerLevel level, LivingEntity caster, double radius) {
        return allies(level, caster, caster.position(), radius);
    }

    /** The same measured from {@code from}, the caster's position when that is null. */
    static List<ServerPlayer> allies(ServerLevel level, LivingEntity caster, @Nullable Vec3 from, double radius) {
        if (!(radius > 0.0) || !(caster instanceof ServerPlayer) || level == null) {
            return List.of();
        }
        Vec3 centre = from == null ? caster.position() : from;
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p != caster && p.isAlive() && !p.isSpectator() && p.distanceToSqr(centre) <= radius * radius) {
                out.add(p);
            }
        }
        return out;
    }

    /** The caster's value of one of Riftfall's attributes; 0 for an entity without it. */
    static double attribute(LivingEntity caster, Holder<Attribute> attribute) {
        AttributeInstance instance = caster.getAttribute(attribute);
        return instance == null ? 0.0 : instance.getValue();
    }

    /** The caster's {@code riftfall:ability_haste}, read as the timer starts; 0 for an entity without it. */
    static double abilityHaste(LivingEntity caster) {
        AttributeInstance haste = caster.getAttribute(RiftfallAttributes.ABILITY_HASTE);
        return haste == null ? 0.0 : haste.getValue();
    }

    /** Ability haste plus the node family's own haste, if the node names a family (Cleric heal cooldown). */
    public static double abilityHaste(LivingEntity caster, SkillNode node) {
        double haste = abilityHaste(caster);
        if (node.category() == null) {
            return haste;
        }
        AttributeInstance family = caster.getAttribute(RiftfallAttributes.CATEGORY_HASTE.get(node.category()));
        return family == null ? haste : haste + family.getValue();
    }

    /** The caster's instance of a named attribute, or null if the id or the instance is absent. */
    private static AttributeInstance powerOf(LivingEntity caster, ResourceLocation id) {
        Optional<Holder.Reference<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.getHolder(id);
        return holder.map(caster::getAttribute).orElse(null);
    }

    /** How many abilities the invoker can see, or 0 with no engine. A probe's positive control. */
    public static int abilityCount(ServerLevel level) {
        return spellEngineLoaded() ? RiftSpellEngine.count(level) : 0;
    }

    /** A few ability ids, so a zero above is diagnosable from a log rather than only reported. */
    public static List<String> abilitySample(ServerLevel level, int limit) {
        return spellEngineLoaded() ? RiftSpellEngine.sample(level, limit) : List.of();
    }

    /** An ability's schools as the engine parsed them, or empty with no engine, no such ability or no school. */
    public static Optional<AbilitySchool> abilitySchool(ServerLevel level, ResourceLocation ability) {
        return spellEngineLoaded() ? RiftSpellEngine.school(level, ability) : Optional.empty();
    }

    /** An ability's target and delivery types as the engine parsed them, or empty. A probe's read. */
    public static Optional<String> abilityShape(ServerLevel level, ResourceLocation ability) {
        return spellEngineLoaded() ? RiftSpellEngine.shape(level, ability) : Optional.empty();
    }

    /** How many casts have had their riders put on the engine's cast, or 0 with no engine. */
    public static int ridersDelivered() {
        return spellEngineLoaded() ? RiftSpellEngine.delivered() : 0;
    }

    /** Whether the invoking engine is present at all, so a probe can say why it declined. */
    public static boolean engineAvailable(SkillNode.Engine engine) {
        return switch (engine) {
            case SPELL_ENGINE -> spellEngineLoaded();
            case IRONS_SPELLBOOKS -> ironsLoaded();
        };
    }

    /** Presence only: the Iron's hooks are required mixins, so a moved target fails the load, not a cast. */
    private static boolean ironsLoaded() {
        Boolean known = irons;
        if (known == null) {
            ModList mods = ModList.get();
            known = mods != null && mods.isLoaded("irons_spellbooks");
            irons = known;
        }
        return known;
    }

    /** Present is not enough: the engine must be one {@link SpellEngineCompat} supports. */
    static boolean usable(Optional<String> spellEngineVersion) {
        return spellEngineVersion.map(SpellEngineCompat::supports).orElse(false);
    }

    private static boolean spellEngineLoaded() {
        Boolean known = spellEngine;
        if (known == null) {
            // Resolved on first use rather than in a static initialiser: ModList is not answerable
            // during mod construction, and this class can be touched from a probe at any time.
            Optional<String> version = ModList.get().getModContainerById("spell_engine")
                    .map(c -> c.getModInfo().getVersion().toString());
            known = usable(version);
            if (version.isPresent() && !known) {
                LOGGER.warn("Spell Engine {} is loaded but unsupported; spell_engine skills are off", version.get());
            }
            spellEngine = known;
        }
        return known;
    }
}
