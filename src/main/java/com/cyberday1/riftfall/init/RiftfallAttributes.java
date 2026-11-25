package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.skill.ChargeRules;
import com.cyberday1.riftfall.skill.RiftMana;
import com.cyberday1.riftfall.skill.SkillNode;
import com.cyberday1.riftfall.stat.StatBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The gear stats no mod in the pack provides, the seven allocatable primaries, and the one
 * thing that stops any of them being dead.
 *
 * <h2>Registering an attribute puts it on nothing</h2>
 * {@code AttributeSupplier#createInstance} answers null for a holder the entity type's supplier does
 * not carry, and {@code AttributeMap#addTransientAttributeModifiers} drops the modifier inside an
 * {@code if (attributeinstance != null)}. No log line, no exception. So a rolled item would show the
 * stat in its own tooltip and change nothing at all. {@link #attach} is the half of this class that
 * fixes that, and it is why every one of these is added to every living type rather than to players
 * only &mdash; mobs raise shields and mobs swing weapons.
 *
 * <p>
 * Other mods do the same job with a mixin on {@code LivingEntity#createLivingAttributes}; NeoForge
 * offers {@link EntityAttributeModificationEvent} instead, which needs no mixin and cannot fight one.
 */
public final class RiftfallAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, RiftfallCore.MODID);

    /**
     * The fraction a raised shield turns away from damage it could not have blocked.
     *
     * <p>
     * Not partial blocking: a successful vanilla block already negates the whole hit, and Shield
     * Expansion cancels a melee hit before the shield is even consulted. This is for the damage that
     * gets through anyway &mdash; {@code #bypasses_shield} magic, which is most of what the pack
     * throws, and hits from outside the frontal arc.
     */
    public static final DeferredHolder<Attribute, Attribute> GUARD =
            ranged("guard", 0.0, 0.0, 0.9);

    /**
     * The fraction of a hit that arrives whatever the target did about it. Attacker-side.
     *
     * <p>
     * Applied as a <em>floor</em> under the mitigated damage rather than as a separate addition. An
     * additive form can hand out more than the attacker actually swung once several mitigations have
     * already run; a floor provably cannot exceed the raw hit.
     *
     * <p>
     * The ceiling is low on purpose. This is the one stat in the set with no counterplay at all, so
     * the interesting number is small.
     */
    public static final DeferredHolder<Attribute, Attribute> TRUE_DAMAGE =
            ranged("true_damage", 0.0, 0.0, 0.25);

    /**
     * The chance of one more item out of a roll. Read by the loot code and by nothing else.
     *
     * <p>
     * No combat hook and no listener: it is registered and attached here purely so that the one
     * attachment pass covers it, because doing this twice would mean editing {@link #attach} again
     * for a stat whose only consumer does not exist yet.
     *
     * <p>
     * ⭐ Deliberately not capped here. The ceiling that matters is the barrel's, and it is a real one:
     * {@link com.cyberday1.riftfall.block.barrel.BarrelDraws} never returns more draws than there are
     * empty slots, so stacking this past what a container holds buys nothing rather than breaking
     * something. A second cap on the attribute would only be a smaller, invisible one in front of it.
     * 1024 is the number vanilla uses for the same intent on {@code minecraft:luck}.
     */
    public static final DeferredHolder<Attribute, Attribute> SCAVENGING =
            ranged("scavenging", 0.0, 0.0, 1024.0);

    /**
     * Riftfall's own mitigation channel, in points, read at damage time by {@code CombatStats}.
     *
     * <p>
     * Ours because {@code puffish_attributes:resistance} is unreadable by construction &mdash; its
     * {@code EntityAttributeInstanceMixin} forces {@code getBaseValue()} and {@code calculateValue()}
     * to return NaN &mdash; so nothing can compose against it or cap it.
     *
     * <p>
     * ⭐ The value is <em>points</em>, not a fraction; what a point turns away is
     * {@code CombatStats#turnedAway} (slice C3: {@code cap R/(R+K(tier))}) and nowhere else. Uncapped here for {@link #SCAVENGING}'s
     * reason &mdash; the real cap is on the fraction, not the points.
     *
     * <p>
     * ⛔ Writers must use {@code add_value}, and the reason is not that the base is zero.
     * {@code AttributeInstance#calculateValue} multiplies {@code base + }&sum;{@code add_value},
     * not the base, so {@code add_multiplied_base} is inert only while nothing writes
     * {@code add_value} here &mdash; which this channel does by construction. A proportional writer
     * would therefore take a share of whatever else happened to land on the channel, making one
     * affix's worth depend on which others rolled. So this is a standing rule, not a tautology.
     */
    public static final DeferredHolder<Attribute, Attribute> RESISTANCE =
            ranged("resistance", 0.0, 0.0, 1024.0);

    /**
     * Damage resistance as a fraction (0.10 is 10%): the percent rows' channel (Monk Damage Resist,
     * Paladin, Reaper, Tactician). {@code CombatStats} composes it after the {@link #RESISTANCE} tier
     * curve, capped at that channel's ceiling. Writers use {@code add_value}.
     */
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_RESISTANCE =
            ranged("damage_resistance", 0.0, 0.0, 1.0);

    /**
     * The fraction of a target's resistance an attack ignores. ⛔ Registered and attached only.
     *
     * <p>
     * Nothing reads it and nothing may be written against it yet. It now has something it
     * <em>could</em> pierce &mdash; {@link #RESISTANCE} is ours and readable, unlike
     * {@code puffish_attributes:resistance}, whose {@code EntityAttributeInstanceMixin} forces both
     * {@code getBaseValue()} and {@code calculateValue()} to return NaN. What it should do to that
     * channel is still an open design question, so it stays registered and unread.
     */
    public static final DeferredHolder<Attribute, Attribute> RESIST_PIERCE =
            ranged("resist_pierce", 0.0, 0.0, 0.9);

    /** Riftfall's mana bar. The defaults are ruled; see {@code RiftMana}. */
    public static final DeferredHolder<Attribute, Attribute> MAX_MANA =
            ranged("max_mana", RiftMana.BASE_POOL, 0.0, 1_000_000.0);

    /** Mana regenerated per second. */
    public static final DeferredHolder<Attribute, Attribute> MANA_REGEN =
            ranged("mana_regen", RiftMana.BASE_REGEN, 0.0, 1_000_000.0);

    /** The Charge bank bows and guns spend, in melee-seconds; Dexterity raises it. The floor keeps fire from going free. */
    public static final DeferredHolder<Attribute, Attribute> MAX_CHARGE =
            ranged("max_charge", ChargeRules.BASE_BANK, 1.0, 1_000.0);

    /** Charge refilled a second in combat, in melee-seconds; Agility raises it. */
    public static final DeferredHolder<Attribute, Attribute> CHARGE_REGEN =
            ranged("charge_regen", ChargeRules.BASE_REFILL, 0.0, 1_000.0);

    /**
     * Ability haste, in points: a Riftfall cooldown lasts {@code cd / (1 + haste / 100)}, so 100 haste is
     * twice as many casts, never a zero cooldown. Read by {@code SkillCasts} on every cast route; a passive's
     * own proc timer is not an ability and ignores it.
     */
    public static final DeferredHolder<Attribute, Attribute> ABILITY_HASTE =
            ranged("ability_haste", 0.0, 0.0, 1000.0);

    /** The fraction added to a Spell Engine heal whose caster is also its target (Tranquility). */
    public static final DeferredHolder<Attribute, Attribute> SELF_HEALING =
            ranged("self_healing", 0.0, 0.0, 10.0);

    /** The fraction cut from a hit taken while Riftfall's shield pool holds points (Gentle Guard). */
    public static final DeferredHolder<Attribute, Attribute> SHIELDED_REDUCTION =
            ranged("shielded_reduction", 0.0, 0.0, 0.9);

    /** The fraction added to every shield pool this entity's Shield casts grant (Paladin +10% shield strength). */
    public static final DeferredHolder<Attribute, Attribute> SHIELD_STRENGTH =
            ranged("shield_strength", 0.0, 0.0, 10.0);

    /** The fraction added to the threat this entity earns on a boss's table (Paladin threat generation). */
    public static final DeferredHolder<Attribute, Attribute> THREAT_GENERATION =
            ranged("threat_generation", 0.0, 0.0, 10.0);

    /**
     * The fraction added to a held TaCZ gun's rounds a minute, and to how fast a bolt-action or pump gun cycles
     * (Gunner Fast Hands). Capped at +100%; TaCZ's own 1200 rpm ceiling still holds.
     */
    public static final DeferredHolder<Attribute, Attribute> FIRE_RATE =
            ranged("fire_rate", 0.0, 0.0, 1.0);

    /** The fraction added to every Spell Engine heal this entity casts, on anyone (Cleric +10% healing done). */
    public static final DeferredHolder<Attribute, Attribute> HEALING_DONE =
            ranged("healing_done", 0.0, 0.0, 10.0);

    /** The fraction added to the owner terms of every summon a Summon node casts (Summoner summons deal more damage). */
    public static final DeferredHolder<Attribute, Attribute> SUMMON_POWER =
            ranged("summon_power", 0.0, 0.0, 10.0);

    /** Seconds added to the lifespan of every summon a Summon node casts (Summoner summon duration). */
    public static final DeferredHolder<Attribute, Attribute> SUMMON_DURATION =
            ranged("summon_duration", 0.0, 0.0, 600.0);

    /** The fraction added to each DoT of its type this entity lands (H6): bleed, burn, venom. */
    public static final DeferredHolder<Attribute, Attribute> BLEED_DAMAGE =
            ranged("bleed_damage", 0.0, 0.0, 10.0);
    public static final DeferredHolder<Attribute, Attribute> BURN_DAMAGE =
            ranged("burn_damage", 0.0, 0.0, 10.0);
    public static final DeferredHolder<Attribute, Attribute> VENOM_DAMAGE =
            ranged("venom_damage", 0.0, 0.0, 10.0);

    /** Seconds added to every DoT a DoT node lands, its zone's cloud included (Reaper DoT duration increased). */
    public static final DeferredHolder<Attribute, Attribute> DOT_DURATION =
            ranged("dot_duration", 0.0, 0.0, 600.0);

    /**
     * The fraction added to the power of every Riftfall magic class school (holy, command, divine, summoning, shadow,
     * arcana); physical is untouched (Arcanist +10% spell damage).
     */
    public static final DeferredHolder<Attribute, Attribute> SPELL_DAMAGE =
            ranged("spell_damage", 0.0, 0.0, 10.0);

    /**
     * Haste in ability haste's points, one per authored node family ({@code riftfall:heal_haste}, ...), added to
     * ability haste on that family's nodes only. Walked from the enum, as {@link #PRIMARIES} is.
     */
    public static final Map<SkillNode.Category, DeferredHolder<Attribute, Attribute>> CATEGORY_HASTE =
            categoryHaste();

    private static Map<SkillNode.Category, DeferredHolder<Attribute, Attribute>> categoryHaste() {
        Map<SkillNode.Category, DeferredHolder<Attribute, Attribute>> map = new EnumMap<>(SkillNode.Category.class);
        for (SkillNode.Category category : SkillNode.Category.values()) {
            map.put(category, ranged(category.key() + "_haste", 0.0, 0.0, 1000.0));
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * The ceiling on a primary, in points. {@link #SCAVENGING}'s number for {@link #SCAVENGING}'s
     * reason: the cap that matters is on what a point buys, and that lives in the derivation table.
     */
    private static final double PRIMARY_MAX = 1024.0;

    /**
     * One attribute per allocatable primary, so a spent point is visible before it does anything.
     *
     * <p>
     * Built by walking {@link StatBlock.Primary#values()} rather than written out, so a primary
     * added to the enum cannot arrive without a channel. The alternative is a hand-kept list and a
     * test that it is total; this makes the question unaskable.
     *
     * <p>
     * ⛔ These are <em>point counts</em>, not effects. What a point does is the derivation table,
     * which is a later slice; nothing here converts one into anything.
     */
    public static final Map<StatBlock.Primary, DeferredHolder<Attribute, Attribute>> PRIMARIES =
            primaries();

    private static Map<StatBlock.Primary, DeferredHolder<Attribute, Attribute>> primaries() {
        Map<StatBlock.Primary, DeferredHolder<Attribute, Attribute>> map =
                new EnumMap<>(StatBlock.Primary.class);
        for (StatBlock.Primary primary : StatBlock.Primary.values()) {
            map.put(primary, ranged(primary.key(), 0.0, 0.0, PRIMARY_MAX));
        }
        return Collections.unmodifiableMap(map);
    }

    /** The channel a primary's points are written on. Never null: {@link #PRIMARIES} is total. */
    public static DeferredHolder<Attribute, Attribute> primary(StatBlock.Primary primary) {
        return PRIMARIES.get(primary);
    }

    private static final List<DeferredHolder<Attribute, Attribute>> ALL = all();

    private static List<DeferredHolder<Attribute, Attribute>> all() {
        List<DeferredHolder<Attribute, Attribute>> every =
                new ArrayList<>(List.of(GUARD, TRUE_DAMAGE, SCAVENGING, RESIST_PIERCE, RESISTANCE, DAMAGE_RESISTANCE, MAX_MANA, MANA_REGEN,
                        MAX_CHARGE, CHARGE_REGEN,
                        ABILITY_HASTE, SELF_HEALING, SHIELDED_REDUCTION, SHIELD_STRENGTH,
                        THREAT_GENERATION, HEALING_DONE, FIRE_RATE, SUMMON_POWER, SUMMON_DURATION, BLEED_DAMAGE,
                        BURN_DAMAGE, VENOM_DAMAGE, DOT_DURATION));
        every.add(SPELL_DAMAGE);
        every.addAll(CATEGORY_HASTE.values());
        every.addAll(PRIMARIES.values());
        return List.copyOf(every);
    }

    /**
     * {@code mrpg_lib}'s crowd-control resist, registered on every living entity. Named as an id and
     * never imported, because the mod that owns it is one the pack happens to have rather than one
     * Riftfall depends on. It was {@code more_rpg_classes:tenacity} before mrpg-lib 2.8.0 renamed
     * the namespace; nothing registers the old id any more.
     */
    public static final ResourceLocation PACK_TENACITY =
            ResourceLocation.fromNamespaceAndPath("mrpg_lib", "tenacity");

    private RiftfallAttributes() {
    }

    public static void register(IEventBus modEventBus) {
        ATTRIBUTES.register(modEventBus);
        modEventBus.addListener(RiftfallAttributes::attach);
    }

    /** The lang key an attribute of this id is looked up under, in one place so nothing drifts. */
    public static String descriptionId(ResourceLocation id) {
        return "attribute.name." + id.getNamespace() + "." + id.getPath();
    }

    /**
     * Puts every one of these on every entity type that has attributes at all.
     *
     * <p>
     * Guarded by {@code has} so that a type another mod already gave one of these keeps that mod's
     * default rather than being reset to ours.
     *
     * <p>
     * The primaries ride the same loop rather than being attached to players only. A mob has no
     * allocation and never will &mdash; but an attribute the entity does not carry makes
     * {@code getInstance} answer null, and this class exists because that is silent. One loop means
     * {@link com.cyberday1.riftfall.stat.StatModifiers} can never write into nothing.
     */
    private static void attach(EntityAttributeModificationEvent event) {
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            for (DeferredHolder<Attribute, Attribute> attribute : ALL) {
                if (!event.has(type, attribute)) {
                    event.add(type, attribute);
                }
            }
        }
    }

    /**
     * @param min the floor, which is also what a NaN sanitizes to. Zero for every one: each of
     *            these is a bonus over nothing, and a negative one would read as a stat that hurts.
     */
    private static DeferredHolder<Attribute, Attribute> ranged(
            String path, double defaultValue, double min, double max) {
        return ATTRIBUTES.register(path, () -> new RangedAttribute(
                descriptionId(RiftfallCore.id(path)), defaultValue, min, max).setSyncable(true));
    }
}
