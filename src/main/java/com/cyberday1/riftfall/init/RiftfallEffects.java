package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.skill.DotType;
import com.cyberday1.riftfall.skill.Dots;
import com.cyberday1.riftfall.skill.ShifterForms;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Riftfall's own status effects: plain attribute buffs a skill applies through Spell Engine's
 * STATUS_EFFECT impact. Each names its attribute by id and resolves it when applied, so an effect on
 * another mod's attribute (Iron's dodge) needs no hard dependency and does nothing without that mod.
 */
public final class RiftfallEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, RiftfallCore.MODID);

    /** Ability haste a level: amplifier 1 is the catalogue's "+12% haste". */
    public static final double HASTE_PER_LEVEL = 6.0;
    /** Iron's dodge a level (its base is 1.0, so 0.04 is 4%): amplifier 1 is "+8% dodge". */
    public static final double DODGE_PER_LEVEL = 0.04;
    public static final ResourceLocation IRONS_DODGE = ResourceLocation.fromNamespaceAndPath("irons_lib", "dodge_chance");
    /** H7, the Tactician batch: Apothic crit chance a level (a fraction), amplifier 1 is "+10% crit chance". */
    public static final double CRIT_CHANCE_PER_LEVEL = 0.05;
    /** Apothic crit damage a level (its base is x1.5), amplifier 2 is "+30% crit damage". */
    public static final double CRIT_DAMAGE_PER_LEVEL = 0.10;
    public static final ResourceLocation APOTHIC_CRIT_CHANCE =
            ResourceLocation.fromNamespaceAndPath("apothic_attributes", "crit_chance");
    public static final ResourceLocation APOTHIC_CRIT_DAMAGE =
            ResourceLocation.fromNamespaceAndPath("apothic_attributes", "crit_damage");
    /** Riftfall mana regen a level: 20% of RiftMana.BASE_REGEN, so amplifier 1 is "+40% mana regen". */
    public static final double MANA_REGEN_PER_LEVEL = 2.0;
    /** H7's mob blind: follow range -50% a level (multiplied in), so amplifier 1 leaves a mob no sight at all. */
    public static final double BLIND_PER_LEVEL = -0.5;
    public static final ResourceLocation FOLLOW_RANGE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "generic.follow_range");

    public static final DeferredHolder<MobEffect, MobEffect> HASTENED = EFFECTS.register("hastened",
            () -> new AttributeEffect(0xE8C547, RiftfallCore.id("ability_haste"), RiftfallCore.id("effect/hastened"),
                    HASTE_PER_LEVEL));
    public static final DeferredHolder<MobEffect, MobEffect> EVASIVE = EFFECTS.register("evasive",
            () -> new AttributeEffect(0x9FC7D8, IRONS_DODGE, RiftfallCore.id("effect/evasive"), DODGE_PER_LEVEL));
    public static final DeferredHolder<MobEffect, MobEffect> KEEN = EFFECTS.register("keen",
            () -> new AttributeEffect(0xD9A23A, APOTHIC_CRIT_CHANCE, RiftfallCore.id("effect/keen"),
                    CRIT_CHANCE_PER_LEVEL));
    public static final DeferredHolder<MobEffect, MobEffect> LETHAL = EFFECTS.register("lethal",
            () -> new AttributeEffect(0xA8322D, APOTHIC_CRIT_DAMAGE, RiftfallCore.id("effect/lethal"),
                    CRIT_DAMAGE_PER_LEVEL));
    public static final DeferredHolder<MobEffect, MobEffect> INVIGORATED = EFFECTS.register("invigorated",
            () -> new AttributeEffect(0x4F7FD9, RiftfallCore.id("mana_regen"), RiftfallCore.id("effect/invigorated"),
                    MANA_REGEN_PER_LEVEL));
    public static final DeferredHolder<MobEffect, MobEffect> BLINDED = EFFECTS.register("blinded",
            () -> new AttributeEffect(0x3A3A3A, FOLLOW_RANGE, RiftfallCore.id("effect/blinded"), BLIND_PER_LEVEL,
                    MobEffectCategory.HARMFUL, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** How long an aspect lasts, in ticks: the Shifter's form stance (20 s). */
    public static final int ASPECT_TICKS = 400;
    public static final ResourceLocation MAX_HEALTH = ResourceLocation.fromNamespaceAndPath("minecraft", "generic.max_health");
    public static final ResourceLocation ATTACK_DAMAGE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "generic.attack_damage");
    /**
     * The form's fallback look, when no Woodwalkers model can be worn (ShifterForms): the body's
     * {@code minecraft:generic.scale}, which every client sees, and the effect's own particle in the form's colour.
     * Bear grows a tenth, under two blocks tall, so it still fits a one-by-two passage; Tiger shrinks a tenth.
     */
    public static final ResourceLocation SCALE = ResourceLocation.fromNamespaceAndPath("minecraft", "generic.scale");
    public static final double BEAR_SCALE = 0.1;
    public static final double TIGER_SCALE = -0.1;
    public static final int BEAR_COLOR = 0x7A5230;
    public static final int TIGER_COLOR = 0xE08A1E;
    /** Bear: max health +20% and threat +20%, a tenth larger, brown dust. */
    public static final DeferredHolder<MobEffect, MobEffect> BEAR_ASPECT = EFFECTS.register("bear_aspect",
            () -> new AspectEffect(BEAR_COLOR, List.of(
                    new Term(MAX_HEALTH, RiftfallCore.id("effect/bear_aspect/health"), 0.2,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                    new Term(RiftfallCore.id("threat_generation"), RiftfallCore.id("effect/bear_aspect/threat"), 0.2,
                            AttributeModifier.Operation.ADD_VALUE),
                    new Term(SCALE, RiftfallCore.id("effect/bear_aspect/scale"), BEAR_SCALE,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE))));
    /** Tiger: attack damage +10% and crit chance +5%, a tenth smaller, orange dust. */
    public static final DeferredHolder<MobEffect, MobEffect> TIGER_ASPECT = EFFECTS.register("tiger_aspect",
            () -> new AspectEffect(TIGER_COLOR, List.of(
                    new Term(ATTACK_DAMAGE, RiftfallCore.id("effect/tiger_aspect/attack"), 0.1,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                    new Term(APOTHIC_CRIT_CHANCE, RiftfallCore.id("effect/tiger_aspect/crit"), 0.05,
                            AttributeModifier.Operation.ADD_VALUE),
                    new Term(SCALE, RiftfallCore.id("effect/tiger_aspect/scale"), TIGER_SCALE,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE))));

    /** H6's DoTs: each ticks its type's damage once a second (skill.Dots). */
    public static final DeferredHolder<MobEffect, MobEffect> BLEEDING = EFFECTS.register("bleeding",
            () -> new DotEffect(0x8A0303, DotType.BLEED));
    public static final DeferredHolder<MobEffect, MobEffect> BURNING = EFFECTS.register("burning",
            () -> new DotEffect(0xE25822, DotType.BURN));
    public static final DeferredHolder<MobEffect, MobEffect> ENVENOMED = EFFECTS.register("envenomed",
            () -> new DotEffect(0x4E9A06, DotType.VENOM));

    /** Apothic life steal a level (a fraction of a physical hit healed): amplifier 1 is Vampiric Aura's "+20%". */
    public static final double LIFE_STEAL_PER_LEVEL = 0.10;
    public static final ResourceLocation APOTHIC_LIFE_STEAL =
            ResourceLocation.fromNamespaceAndPath("apothic_attributes", "life_steal");
    /** Mardroeme Mushroom's Berserk: +30% attack speed, and no skill casts while it lasts (SkillCasts#eligible). */
    public static final DeferredHolder<MobEffect, MobEffect> BERSERK = EFFECTS.register("berserk",
            () -> new AttributeEffect(0xB22222, ResourceLocation.withDefaultNamespace("generic.attack_speed"),
                    RiftfallCore.id("effect/berserk"), 0.30, MobEffectCategory.BENEFICIAL,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    public static final DeferredHolder<MobEffect, MobEffect> VAMPIRIC = EFFECTS.register("vampiric",
            () -> new AttributeEffect(0x7A0F1E, APOTHIC_LIFE_STEAL, RiftfallCore.id("effect/vampiric"),
                    LIFE_STEAL_PER_LEVEL));

    /** A counter window is open (RiftCounters): no attribute, its length is the window's. */
    public static final DeferredHolder<MobEffect, MobEffect> PARRYING = EFFECTS.register("parrying",
            () -> new MarkerEffect(0xC9D6E3));

    /** A reflect window is open (RiftReflect): no attribute, its length is the window's. */
    public static final DeferredHolder<MobEffect, MobEffect> REFLECTING = EFFECTS.register("reflecting",
            () -> new MarkerEffect(0x7FD4E8));

    private RiftfallEffects() {
    }

    /** A beneficial effect with no attribute and no tick: a state other code reads (the parry window). */
    public static final class MarkerEffect extends MobEffect {
        MarkerEffect(int color) {
            super(MobEffectCategory.BENEFICIAL, color);
        }
    }

    /** A Riftfall DoT: harmful, no attribute, one tick of its type a second while it lasts. */
    public static final class DotEffect extends MobEffect {

        private final DotType type;

        DotEffect(int color, DotType type) {
            super(MobEffectCategory.HARMFUL, color);
            this.type = type;
        }

        public DotType type() {
            return type;
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int remaining, int amplifier) {
            return Dots.ticksAt(remaining);
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            Dots.tick(entity, type);
            return true;
        }
    }

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }

    /** One modifier, {@code perLevel x (amplifier + 1)}, on an attribute named by id; ADD_VALUE unless told. */
    public static final class AttributeEffect extends MobEffect {

        private final ResourceLocation attribute;
        private final ResourceLocation modifierId;
        private final double perLevel;
        private final AttributeModifier.Operation operation;

        AttributeEffect(int color, ResourceLocation attribute, ResourceLocation modifierId, double perLevel) {
            this(color, attribute, modifierId, perLevel, MobEffectCategory.BENEFICIAL, AttributeModifier.Operation.ADD_VALUE);
        }

        AttributeEffect(int color, ResourceLocation attribute, ResourceLocation modifierId, double perLevel,
                        MobEffectCategory category, AttributeModifier.Operation operation) {
            super(category, color);
            this.attribute = attribute;
            this.modifierId = modifierId;
            this.perLevel = perLevel;
            this.operation = operation;
        }

        public ResourceLocation attribute() {
            return attribute;
        }

        public double amountAt(int amplifier) {
            return perLevel * (amplifier + 1);
        }

        private Optional<Holder<Attribute>> holder() {
            return BuiltInRegistries.ATTRIBUTE.getHolder(attribute).map(h -> h);
        }

        @Override
        public void createModifiers(int amplifier, BiConsumer<Holder<Attribute>, AttributeModifier> out) {
            holder().ifPresent(h -> out.accept(h, modifier(amplifier)));
        }

        @Override
        public void addAttributeModifiers(AttributeMap attributes, int amplifier) {
            holder().map(attributes::getInstance).ifPresent(instance -> {
                instance.removeModifier(modifierId);
                instance.addTransientModifier(modifier(amplifier));
            });
        }

        @Override
        public void removeAttributeModifiers(AttributeMap attributes) {
            holder().map(attributes::getInstance).ifPresent(instance -> instance.removeModifier(modifierId));
        }

        private AttributeModifier modifier(int amplifier) {
            return new AttributeModifier(modifierId, amountAt(amplifier), operation);
        }
    }

    /** One attribute an aspect moves: {@code perLevel x (amplifier + 1)}, on an attribute named by id. */
    public record Term(ResourceLocation attribute, ResourceLocation modifierId, double perLevel,
                       AttributeModifier.Operation operation) {

        AttributeModifier modifier(int amplifier) {
            return new AttributeModifier(modifierId, perLevel * (amplifier + 1), operation);
        }

        Optional<Holder<Attribute>> holder() {
            return BuiltInRegistries.ATTRIBUTE.getHolder(attribute).map(h -> h);
        }
    }

    /**
     * A Shifter form stance. One at a time: taking an aspect ends every other one the entity carries, so a swap
     * cancels the old form. The form is its stats; its look is a Woodwalkers model when one can be worn
     * (ShifterForms), else its scale and its dust.
     */
    public static final class AspectEffect extends MobEffect {

        private final List<Term> terms;

        AspectEffect(int color, List<Term> terms) {
            super(MobEffectCategory.BENEFICIAL, color, dust(color));
            this.terms = List.copyOf(terms);
        }

        /** The form's particle: dust in its colour, a little larger than redstone's. */
        static DustParticleOptions dust(int color) {
            return new DustParticleOptions(new Vector3f(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F), 1.5F);
        }

        public List<Term> terms() {
            return terms;
        }

        @Override
        public void onEffectStarted(LivingEntity entity, int amplifier) {
            if (entity.level().isClientSide) {
                return;
            }
            for (DeferredHolder<MobEffect, MobEffect> other : aspects()) {
                if (other.get() != this && entity.hasEffect(other)) {
                    entity.removeEffect(other);
                }
            }
            if (entity instanceof ServerPlayer player) {
                ShifterForms.reconcile(player);
            }
        }

        /** The form's body-scale term (the stand-in for a model), or null. */
        public Term scaleTerm() {
            return terms.stream().filter(t -> t.attribute().equals(SCALE)).findFirst().orElse(null);
        }

        /** Puts the scale term on or takes it off (off while a model is worn, ShifterForms). Answers whether it moved. */
        public boolean bodyScale(LivingEntity entity, int amplifier, boolean shown) {
            Term term = scaleTerm();
            var instance = term == null ? null : term.holder().map(entity.getAttributes()::getInstance).orElse(null);
            if (instance == null || instance.hasModifier(term.modifierId()) == shown) {
                return false;
            }
            if (shown) {
                instance.addTransientModifier(term.modifier(amplifier));
            } else {
                instance.removeModifier(term.modifierId());
            }
            return true;
        }

        @Override
        public void createModifiers(int amplifier, BiConsumer<Holder<Attribute>, AttributeModifier> out) {
            for (Term term : terms) {
                term.holder().ifPresent(h -> out.accept(h, term.modifier(amplifier)));
            }
        }

        @Override
        public void addAttributeModifiers(AttributeMap attributes, int amplifier) {
            for (Term term : terms) {
                term.holder().map(attributes::getInstance).ifPresent(instance -> {
                    instance.removeModifier(term.modifierId());
                    instance.addTransientModifier(term.modifier(amplifier));
                });
            }
        }

        @Override
        public void removeAttributeModifiers(AttributeMap attributes) {
            for (Term term : terms) {
                term.holder().map(attributes::getInstance).ifPresent(instance -> instance.removeModifier(term.modifierId()));
            }
        }
    }

    /** The Shifter's aspects, in swap order. */
    public static List<DeferredHolder<MobEffect, MobEffect>> aspects() {
        return List.of(BEAR_ASPECT, TIGER_ASPECT);
    }

    /** The aspect {@code entity} carries, or null. */
    public static DeferredHolder<MobEffect, MobEffect> aspectOf(LivingEntity entity) {
        for (DeferredHolder<MobEffect, MobEffect> aspect : aspects()) {
            if (entity.hasEffect(aspect)) {
                return aspect;
            }
        }
        return null;
    }

    /**
     * Takes {@code aspect} for {@link #ASPECT_TICKS}; any other aspect ends. The dust is off when the form will
     * wear a model (ShifterForms), unless the server keeps it.
     */
    public static void takeAspect(LivingEntity entity, DeferredHolder<MobEffect, MobEffect> aspect) {
        boolean dust = !ShifterForms.wouldWear(entity) || ShifterForms.dustWithModel();
        entity.addEffect(new MobEffectInstance(aspect, ASPECT_TICKS, 0, false, dust, true));
    }

    /** Every effect here, for tests and the lang file. */
    public static List<DeferredHolder<MobEffect, MobEffect>> all() {
        return List.of(HASTENED, EVASIVE, KEEN, LETHAL, INVIGORATED, BLINDED, BEAR_ASPECT, TIGER_ASPECT, BLEEDING, BURNING,
                ENVENOMED, VAMPIRIC, BERSERK);
    }
}
