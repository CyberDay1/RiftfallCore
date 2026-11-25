package com.cyberday1.riftfall.skill;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The skill table: every {@code data/<ns>/skill_node/*.json}, one node per file, by id. Pure, so the
 * loader and the tests run the same {@link #parse}. A file that cannot be read is refused by its file
 * name; every file ends up either loaded or refused, never neither. A {@code "kind": "passive"} file
 * is a {@link PassiveDef}; castables and passives share one id space, as they do in the book.
 */
public record SkillTable(Map<String, SkillDef> byId, Map<String, String> ascendancyClass,
                         Map<String, PassiveDef> passives) {

    public static final String DIRECTORY = "skill_node";
    public static final SkillTable EMPTY = new SkillTable(Map.of(), Map.of(), Map.of());

    /** Holds the V1 dev nodes until N1b authors a real class. */
    public static final String DEV_CLASS = "dev";
    /** The 13 classes of the catalogue, plus the dev class. */
    public static final Set<String> CLASSES = Set.of("arcanist", "cleric", "gunner", "monk", "paladin",
            "phantom", "ranger", "reaper", "shifter", "summoner", "sword_immortal", "tactician", "trickster",
            DEV_CLASS);

    /** What a reload produced: the table, one line per refused file, and how many files it was handed. */
    public record Loaded(SkillTable table, List<String> refusals, int files) {
        public Loaded {
            refusals = List.copyOf(refusals);
        }
    }

    public SkillTable(Map<String, SkillDef> byId, Map<String, String> ascendancyClass) {
        this(byId, ascendancyClass, Map.of());
    }

    public SkillTable {
        byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
        ascendancyClass = Collections.unmodifiableMap(new LinkedHashMap<>(ascendancyClass));
        passives = Collections.unmodifiableMap(new LinkedHashMap<>(passives));
    }

    /** The castable with this id, or null. Never a passive, so nothing can equip or cast one. */
    public @Nullable SkillDef get(String id) {
        return byId.get(id);
    }

    public @Nullable PassiveDef passive(String id) {
        return passives.get(id);
    }

    /** Whatever a point can buy under this id: a castable or a passive, or null. */
    public SkillBook.@Nullable Purchase purchase(String id) {
        SkillDef def = byId.get(id);
        return def != null ? def : passives.get(id);
    }

    /** Castable ids only. */
    public Set<String> ids() {
        return byId.keySet();
    }

    /** Castables and passives: one per loaded file. */
    public int size() {
        return byId.size() + passives.size();
    }

    /** The class an ascendancy belongs to, or null when no loaded node names it. */
    public @Nullable String classOf(String ascendancy) {
        return ascendancyClass.get(ascendancy);
    }

    public Set<String> ascendancies() {
        return ascendancyClass.keySet();
    }

    // -- the file format -----------------------------------------------------------------------

    /** A lever's file key, also what the skill screen is told; {@link #key} is the JSON {@code type}. */
    public enum LeverType {
        DAMAGE, MOVEMENT, HEAL, SHIELD, CONTROL, DOT, SUMMON, BUFF;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The lever's first rate (damage, magnitude, cost or owner scaling) and its seconds a rank, or 0. */
        public static double[] rates(SkillNode.Lever lever) {
            return switch (lever) {
                case SkillNode.Damage d -> new double[] {d.damagePerRank(), 0.0};
                case SkillNode.Movement m -> new double[] {0.0, 0.0};
                case SkillNode.Heal h -> new double[] {h.magnitudePerRank(), 0.0};
                case SkillNode.Shield s -> new double[] {s.magnitudePerRank(), 0.0};
                case SkillNode.Control c -> new double[] {c.costPerRank(), 0.0};
                case SkillNode.DoT d -> new double[] {d.damagePerRank(), d.durationSecondsPerRank()};
                case SkillNode.Summon s -> new double[] {s.ownerScalingPerRank(), s.durationSecondsPerRank()};
                case SkillNode.Buff b -> new double[] {b.magnitudePerRank(), b.durationSecondsPerRank()};
            };
        }

        public static LeverType of(SkillNode.Lever lever) {
            return switch (lever) {
                case SkillNode.Damage d -> DAMAGE;
                case SkillNode.Movement m -> MOVEMENT;
                case SkillNode.Heal h -> HEAL;
                case SkillNode.Shield s -> SHIELD;
                case SkillNode.Control c -> CONTROL;
                case SkillNode.DoT d -> DOT;
                case SkillNode.Summon s -> SUMMON;
                case SkillNode.Buff b -> BUFF;
            };
        }

        MapCodec<? extends SkillNode.Lever> codec() {
            return switch (this) {
                case DAMAGE -> Codec.DOUBLE.fieldOf("damage_per_rank")
                        .xmap(SkillNode.Damage::new, SkillNode.Damage::damagePerRank);
                case MOVEMENT -> MapCodec.unit(new SkillNode.Movement());
                case HEAL -> Codec.DOUBLE.fieldOf("magnitude_per_rank")
                        .xmap(SkillNode.Heal::new, SkillNode.Heal::magnitudePerRank);
                case SHIELD -> SHIELD_CODEC;
                case CONTROL -> Codec.DOUBLE.fieldOf("cost_per_rank")
                        .xmap(SkillNode.Control::new, SkillNode.Control::costPerRank);
                case DOT -> DOT_CODEC;
                case SUMMON -> SUMMON_CODEC;
                case BUFF -> BUFF_CODEC;
            };
        }
    }

    private static final MapCodec<SkillNode.Shield> SHIELD_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("magnitude_per_rank").forGetter(SkillNode.Shield::magnitudePerRank),
            Codec.DOUBLE.optionalFieldOf("ally_radius", 0.0).forGetter(SkillNode.Shield::allyRadius)
    ).apply(i, SkillNode.Shield::new));

    private static final MapCodec<SkillNode.DoT> DOT_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("damage_per_rank").forGetter(SkillNode.DoT::damagePerRank),
            Codec.DOUBLE.fieldOf("duration_seconds_per_rank").forGetter(SkillNode.DoT::durationSecondsPerRank)
    ).apply(i, SkillNode.DoT::new));

    private static final MapCodec<SkillNode.Summon> SUMMON_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("owner_scaling_per_rank").forGetter(SkillNode.Summon::ownerScalingPerRank),
            Codec.DOUBLE.fieldOf("duration_seconds_per_rank").forGetter(SkillNode.Summon::durationSecondsPerRank)
    ).apply(i, SkillNode.Summon::new));

    private static final MapCodec<SkillNode.Buff> BUFF_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("magnitude_per_rank").forGetter(SkillNode.Buff::magnitudePerRank),
            Codec.DOUBLE.fieldOf("duration_seconds_per_rank").forGetter(SkillNode.Buff::durationSecondsPerRank)
    ).apply(i, SkillNode.Buff::new));

    private static <E extends Enum<E>> Codec<E> byKey(E[] values, String what) {
        return Codec.STRING.comapFlatMap(key -> {
            for (E value : values) {
                if (value.name().toLowerCase(Locale.ROOT).equals(key)) {
                    return DataResult.success(value);
                }
            }
            List<String> known = new ArrayList<>();
            for (E value : values) {
                known.add(value.name().toLowerCase(Locale.ROOT));
            }
            return DataResult.error(() -> "unknown " + what + " '" + key + "', known: " + known);
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }

    /** A node's DoT (H6): its type, coefficient and seconds. */
    private static final Codec<SkillNode.DotSpec> DOT_CODEC_FIELD = RecordCodecBuilder.create(i -> i.group(
            byKey(DotType.values(), "dot type").fieldOf("type").forGetter(SkillNode.DotSpec::type),
            Codec.DOUBLE.fieldOf("coefficient").forGetter(SkillNode.DotSpec::coefficient),
            Codec.DOUBLE.fieldOf("seconds").forGetter(SkillNode.DotSpec::seconds)
    ).apply(i, SkillNode.DotSpec::new));

    /** A spell passive's reaction terms: an event Riftfall fires, and a hit or cast counter. */
    private static final Codec<Reactions.Reaction> REACTION_CODEC = RecordCodecBuilder.create(i -> i.group(
            byKey(Reactions.On.values(), "reaction event").optionalFieldOf("on")
                    .forGetter(r -> Optional.ofNullable(r.on())),
            Codec.INT.optionalFieldOf("count", Reactions.Reaction.NO_COUNT).forGetter(Reactions.Reaction::count),
            Codec.INT.optionalFieldOf("count_window_ticks", 0).forGetter(Reactions.Reaction::countWindowTicks)
    ).apply(i, (on, count, window) -> new Reactions.Reaction(on.orElse(null), count, window)));

    private static final Codec<Reactions.Term> TERM_CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("attribute").forGetter(Reactions.Term::attribute),
            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.CODEC.fieldOf("operation")
                    .forGetter(Reactions.Term::operation)
    ).apply(i, Reactions.Term::new));

    /** The {@code riftfall:surge} buff: seconds, and the attributes the node's power raises. */
    private static final Codec<Reactions.Surge> SURGE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("seconds").forGetter(Reactions.Surge::seconds),
            TERM_CODEC.listOf().fieldOf("terms").forGetter(Reactions.Surge::terms)
    ).apply(i, Reactions.Surge::new));

    /** A node's pool sized by the enemies its cast struck: boss hits each, the cap, the party radius. */
    private static final Codec<SkillNode.PerEnemy> PER_ENEMY_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("shield").forGetter(SkillNode.PerEnemy::shield),
            Codec.INT.fieldOf("cap").forGetter(SkillNode.PerEnemy::cap),
            Codec.DOUBLE.optionalFieldOf("ally_radius", 0.0).forGetter(SkillNode.PerEnemy::allyRadius)
    ).apply(i, SkillNode.PerEnemy::new));

    /** A castable's reflect window: the share of each hit sent back, projectiles turned. */
    private static final Codec<Reflects.Spec> REFLECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("share").forGetter(Reflects.Spec::share),
            Codec.DOUBLE.fieldOf("seconds").forGetter(Reflects.Spec::seconds),
            Codec.BOOL.optionalFieldOf("projectiles", false).forGetter(Reflects.Spec::projectiles)
    ).apply(i, Reflects.Spec::new));

    /** A castable's value stored between casts. */
    private static final Codec<Stored.Spec> STORED_CODEC = RecordCodecBuilder.create(i -> i.group(
            byKey(Stored.Release.values(), "store release").fieldOf("release").forGetter(Stored.Spec::release),
            Codec.INT.fieldOf("window_ticks").forGetter(Stored.Spec::windowTicks),
            Codec.INT.optionalFieldOf("arm_ticks", 0).forGetter(Stored.Spec::armTicks),
            byKey(Stored.Expiry.values(), "store expiry").optionalFieldOf("on_expire", Stored.Expiry.RELEASE)
                    .forGetter(Stored.Spec::onExpire),
            Codec.DOUBLE.optionalFieldOf("per_hit", 0.0).forGetter(Stored.Spec::perHit),
            Codec.INT.optionalFieldOf("cap", 0).forGetter(Stored.Spec::cap),
            Codec.INT.optionalFieldOf("decay_after_ticks", 0).forGetter(Stored.Spec::decayAfterTicks),
            Codec.INT.optionalFieldOf("decay_every_ticks", 0).forGetter(Stored.Spec::decayEveryTicks),
            Codec.DOUBLE.optionalFieldOf("per_unit", 0.0).forGetter(Stored.Spec::perUnit),
            byKey(Stored.Anchor.values(), "store anchor").optionalFieldOf("anchor", Stored.Anchor.CASTER)
                    .forGetter(Stored.Spec::anchor),
            Codec.DOUBLE.optionalFieldOf("aim_range", 0.0).forGetter(Stored.Spec::aimRange),
            Codec.DOUBLE.optionalFieldOf("radius", 0.0).forGetter(Stored.Spec::radius),
            Codec.DOUBLE.optionalFieldOf("angle", 0.0).forGetter(Stored.Spec::angle),
            Codec.INT.optionalFieldOf("pulse_ticks", 0).forGetter(Stored.Spec::pulseTicks),
            Codec.DOUBLE.optionalFieldOf("pull", 0.0).forGetter(Stored.Spec::pull)
    ).apply(i, Stored.Spec::new));

    /**
     * The node keys read beside {@link #CODEC} (its group is full): a reflect window and a stored value. Kept out of
     * {@link #FIELDS} so the node codec's own key set is unchanged.
     */
    public static final Set<String> TERM_FIELDS = Set.of("reflect", "stored");

    private static final Codec<SkillNode.Lever> LEVER = byKey(LeverType.values(), "lever")
            .dispatch("type", LeverType::of, LeverType::codec);

    /** One node file. ⛔ No tier, prerequisite or parent field: ruled out 2026-09-22. */
    public static final Codec<SkillDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(SkillDef::id),
            Codec.STRING.fieldOf("class").forGetter(SkillDef::skillClass),
            Codec.STRING.optionalFieldOf("ascendancy").forGetter(d -> Optional.ofNullable(d.ascendancy())),
            byKey(SkillBook.Kind.values(), "kind").fieldOf("kind").forGetter(SkillDef::kind),
            byKey(SkillNode.Engine.values(), "engine").fieldOf("engine").forGetter(d -> d.cast().engine()),
            ResourceLocation.CODEC.fieldOf("ability").forGetter(d -> d.cast().ability()),
            ResourceLocation.CODEC.fieldOf("power_attribute").forGetter(d -> d.cast().powerAttribute()),
            Codec.DOUBLE.fieldOf("mana_cost").forGetter(SkillDef::manaCost),
            Codec.INT.fieldOf("base_cooldown_ticks").forGetter(d -> d.cast().baseCooldownTicks()),
            Codec.DOUBLE.fieldOf("cooldown_per_rank").forGetter(d -> d.cast().cooldownPerRank()),
            Codec.INT.fieldOf("max_rank").forGetter(d -> d.cast().maxRank()),
            LEVER.fieldOf("lever").forGetter(d -> d.cast().lever()),
            Codec.DOUBLE.optionalFieldOf("power_multiplier", SkillNode.NATIVE_POWER)
                    .forGetter(d -> d.cast().powerMultiplier()),
            byKey(SkillNode.Category.values(), "category").optionalFieldOf("category")
                    .forGetter(d -> Optional.ofNullable(d.cast().category())),
            DOT_CODEC_FIELD.optionalFieldOf("dot").forGetter(d -> Optional.ofNullable(d.cast().dot())),
            PER_ENEMY_CODEC.optionalFieldOf("per_enemy").forGetter(d -> Optional.ofNullable(d.cast().perEnemy()))
    ).apply(i, (id, skillClass, ascendancy, kind, engine, ability, power, mana, cooldown, perRank, maxRank, lever,
                multiplier, category, dot, perEnemy) ->
            new SkillDef(new SkillNode(id, engine, ability, power, cooldown, maxRank, perRank, lever, multiplier,
                    category.orElse(null), dot.orElse(null), perEnemy.orElse(null)), kind, skillClass,
                    ascendancy.orElse(null), mana)));

    /** Every key a node file may carry. Anything else, a tier or a prerequisite included, is refused. */
    public static final Set<String> FIELDS = Set.of("id", "class", "ascendancy", "kind", "engine", "ability",
            "power_attribute", "mana_cost", "base_cooldown_ticks", "cooldown_per_rank", "max_rank", "lever",
            "power_multiplier", "category", "dot", "per_enemy");

    /**
     * One passive file. The kind is read only to refuse anything but {@code passive}. An {@code attribute}
     * passive is flat; a {@code spell} passive names its shape and may set its own internal cooldown.
     */
    public static final Codec<PassiveDef> PASSIVE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(PassiveDef::id),
            Codec.STRING.fieldOf("class").forGetter(PassiveDef::skillClass),
            Codec.STRING.optionalFieldOf("ascendancy").forGetter(d -> Optional.ofNullable(d.ascendancy())),
            byKey(SkillBook.Kind.values(), "kind").fieldOf("kind").forGetter(PassiveDef::kind),
            Codec.INT.fieldOf("max_rank").forGetter(d -> d.node().maxRank()),
            ResourceLocation.CODEC.optionalFieldOf("attribute").forGetter(d -> Optional.ofNullable(d.attribute())),
            ResourceLocation.CODEC.optionalFieldOf("spell").forGetter(d -> Optional.ofNullable(d.spell())),
            byKey(PassiveNode.Shape.values(), "shape").optionalFieldOf("shape")
                    .forGetter(d -> d.grantsSpell() ? Optional.of(d.node().shape()) : Optional.empty()),
            Codec.INT.optionalFieldOf("internal_cooldown_ticks", PassiveDef.SPELL_COOLDOWN)
                    .forGetter(PassiveDef::internalCooldownTicks),
            Codec.DOUBLE.fieldOf("bonus_per_rank").forGetter(d -> d.node().bonusPerRank()),
            Codec.DOUBLE.optionalFieldOf("bonus_base", 0.0).forGetter(d -> d.node().bonusBase()),
            Codec.DOUBLE.optionalFieldOf("target_health_below", PassiveDef.ANY_HEALTH)
                    .forGetter(PassiveDef::targetHealthBelow),
            REACTION_CODEC.optionalFieldOf("reaction").forGetter(d -> Optional.ofNullable(d.reaction())),
            SURGE_CODEC.optionalFieldOf("surge").forGetter(d -> Optional.ofNullable(d.surge()))
    ).apply(i, (id, skillClass, ascendancy, kind, maxRank, attribute, spell, shape, cooldown, perRank, base,
                window, reaction, surge) -> {
        if (kind != SkillBook.Kind.PASSIVE) {
            throw new IllegalArgumentException(id + ": a passive file must say kind passive, was " + kind);
        }
        if (spell.isPresent() == shape.isEmpty()) {
            throw new IllegalArgumentException(id + ": a spell passive names its shape, and only a spell passive has one");
        }
        return new PassiveDef(new PassiveNode(id, shape.orElse(PassiveNode.Shape.FLAT), maxRank, perRank, base),
                skillClass, ascendancy.orElse(null), attribute.orElse(null), spell.orElse(null), cooldown, window,
                reaction.orElse(null), surge.orElse(null));
    }));

    /** Every key a passive file may carry. */
    public static final Set<String> PASSIVE_FIELDS = Set.of("id", "class", "ascendancy", "kind", "max_rank",
            "attribute", "spell", "shape", "internal_cooldown_ticks", "bonus_per_rank", "bonus_base",
            "target_health_below", "reaction", "surge");

    /** Whether a file is a passive, read off its {@code kind} alone. */
    public static boolean isPassive(JsonElement json) {
        return json instanceof JsonObject object && object.get("kind") instanceof JsonElement kind
                && kind.isJsonPrimitive() && SkillBook.Kind.PASSIVE.key().equals(kind.getAsString());
    }

    public static DataResult<PassiveDef> decodePassive(JsonElement json) {
        if (json instanceof JsonObject object) {
            Set<String> unknown = new TreeSet<>(object.keySet());
            unknown.removeAll(PASSIVE_FIELDS);
            if (!unknown.isEmpty()) {
                return DataResult.error(() -> "unknown field(s) " + unknown + " on a passive; known: "
                        + new TreeSet<>(PASSIVE_FIELDS));
            }
        }
        try {
            return PASSIVE_CODEC.parse(JsonOps.INSTANCE, json);
        } catch (IllegalArgumentException refused) {
            return DataResult.error(refused::getMessage);
        }
    }

    public static JsonElement encode(PassiveDef def) {
        return PASSIVE_CODEC.encodeStart(JsonOps.INSTANCE, def).getOrThrow();
    }

    /** One file to a node, or the reason it is not one. Constructor refusals come back as errors too. */
    public static DataResult<SkillDef> decode(JsonElement json) {
        JsonElement reflect = null;
        JsonElement stored = null;
        if (json instanceof JsonObject object) {
            Set<String> unknown = new TreeSet<>(object.keySet());
            unknown.removeAll(FIELDS);
            unknown.removeAll(TERM_FIELDS);
            if (!unknown.isEmpty()) {
                return DataResult.error(() -> "unknown field(s) " + unknown
                        + "; a node carries no tier, prerequisite or parent");
            }
            reflect = object.get("reflect");
            stored = object.get("stored");
            if (reflect != null || stored != null) {
                JsonObject node = object.deepCopy();
                node.remove("reflect");
                node.remove("stored");
                json = node;
            }
        }
        try {
            DataResult<SkillDef> def = CODEC.parse(JsonOps.INSTANCE, json);
            if (def.error().isPresent() || (reflect == null && stored == null)) {
                return def;
            }
            Reflects.Spec r = null;
            Stored.Spec st = null;
            if (reflect != null) {
                DataResult<Reflects.Spec> read = REFLECT_CODEC.parse(JsonOps.INSTANCE, reflect);
                if (read.error().isPresent()) {
                    return DataResult.error(() -> "reflect: " + read.error().get().message());
                }
                r = read.getOrThrow();
            }
            if (stored != null) {
                DataResult<Stored.Spec> read = STORED_CODEC.parse(JsonOps.INSTANCE, stored);
                if (read.error().isPresent()) {
                    return DataResult.error(() -> "stored: " + read.error().get().message());
                }
                st = read.getOrThrow();
            }
            return DataResult.success(def.getOrThrow().withTerms(r, st));
        } catch (IllegalArgumentException refused) {
            return DataResult.error(refused::getMessage);
        }
    }

    public static JsonElement encode(SkillDef def) {
        JsonElement json = CODEC.encodeStart(JsonOps.INSTANCE, def).getOrThrow();
        if (json instanceof JsonObject object) {
            if (def.reflect() != null) {
                object.add("reflect", REFLECT_CODEC.encodeStart(JsonOps.INSTANCE, def.reflect()).getOrThrow());
            }
            if (def.stored() != null) {
                object.add("stored", STORED_CODEC.encodeStart(JsonOps.INSTANCE, def.stored()).getOrThrow());
            }
        }
        return json;
    }

    // -- the table -----------------------------------------------------------------------------

    /**
     * Builds the table from every file a reload found. Files are read in id order, so which of two
     * duplicates survives does not depend on pack hash order.
     */
    public static Loaded parse(Map<ResourceLocation, JsonElement> files) {
        Map<String, SkillDef> byId = new LinkedHashMap<>();
        Map<String, PassiveDef> passives = new LinkedHashMap<>();
        Map<String, ResourceLocation> source = new LinkedHashMap<>();
        Map<String, String> ascendancyClass = new LinkedHashMap<>();
        List<String> refusals = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> file : new TreeMap<>(files).entrySet()) {
            ResourceLocation name = file.getKey();
            DataResult<? extends SkillBook.Purchase> read = isPassive(file.getValue())
                    ? decodePassive(file.getValue()) : decode(file.getValue());
            if (read.error().isPresent()) {
                refusals.add(name + ": " + read.error().get().message());
                continue;
            }
            SkillBook.Purchase def = read.getOrThrow();
            String refusal = refusal(def, source, ascendancyClass);
            if (refusal != null) {
                refusals.add(name + ": " + refusal);
                continue;
            }
            if (def instanceof PassiveDef passive) {
                passives.put(def.id(), passive);
            } else {
                byId.put(def.id(), (SkillDef) def);
            }
            source.put(def.id(), name);
            if (def.isAscendancy()) {
                ascendancyClass.put(def.ascendancy(), def.skillClass());
            }
        }
        return new Loaded(new SkillTable(byId, ascendancyClass, passives), refusals, files.size());
    }

    private static @Nullable String refusal(SkillBook.Purchase def, Map<String, ResourceLocation> source,
                                            Map<String, String> ascendancyClass) {
        boolean universal = def instanceof PassiveDef && def.universal();
        if (!CLASSES.contains(def.skillClass()) && !universal) {
            return "unknown class '" + def.skillClass() + "', known: " + new TreeSet<>(CLASSES)
                    + (def instanceof PassiveDef ? " or " + SkillBook.Purchase.UNIVERSAL : "");
        }
        if (source.containsKey(def.id())) {
            return "duplicate id '" + def.id() + "', already defined by " + source.get(def.id());
        }
        if (def.isAscendancy()) {
            String owner = ascendancyClass.get(def.ascendancy());
            if (owner != null && !owner.equals(def.skillClass())) {
                return "ascendancy '" + def.ascendancy() + "' belongs to class '" + owner
                        + "', this file says '" + def.skillClass() + "'";
            }
        }
        return null;
    }
}
