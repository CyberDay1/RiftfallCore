package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Every sentence and label of the skill book's detail page, built on the server as keys plus arguments so the
 * client reads them in its own language. Numbers travel as already-formatted strings; the provider reads
 * {@link #ENGLISH}.
 */
public final class GuideLang {
    public static final String PREFIX = "guide." + RiftfallCore.MODID + ".";
    private static final Map<String, String> TEXT = new LinkedHashMap<>();
    public static final Map<String, String> ENGLISH = Collections.unmodifiableMap(TEXT);
    /** Per-hit figure units; the stage forms read "Release damage / hit". */
    static final List<String> UNITS = List.of("hit", "pulse", "blade", "projectile");

    private GuideLang() { }

    /** A line of the page; an id with no English here is a programming error, not a raw key on screen. */
    public static MutableComponent text(String id, Object... args) {
        if (!TEXT.containsKey(PREFIX + id)) throw new IllegalArgumentException("No guide English for " + id);
        return Component.translatable(PREFIX + id, args);
    }

    /** A label the data may extend: known ones have English here, an unknown one reads as itself. */
    static MutableComponent named(String group, String id, String fallback) {
        return Component.translatableWithFallback(PREFIX + group + "." + id.toLowerCase(Locale.ROOT), fallback);
    }

    /** A status effect's own translated name; an id no loaded mod registers reads as itself. */
    static Object effect(String id) {
        return registered(BuiltInRegistries.MOB_EFFECT, id, MobEffect::getDescriptionId, SkillGuide.readable(id));
    }

    /** Whether a status effect is beneficial; null when no loaded mod registers it. */
    static Boolean beneficial(String id) {
        var location = ResourceLocation.tryParse(id);
        MobEffect effect = location == null ? null : BuiltInRegistries.MOB_EFFECT.getOptional(location).orElse(null);
        return effect == null ? null : effect.isBeneficial();
    }

    static Object attribute(String id, String fallback) {
        return registered(BuiltInRegistries.ATTRIBUTE, id, Attribute::getDescriptionId, fallback);
    }

    static Object entity(String id) {
        return registered(BuiltInRegistries.ENTITY_TYPE, id, EntityType::getDescriptionId, SkillGuide.readable(id));
    }

    /** The game's key with the readable id as fallback, so a client without that mod's lang still reads something. */
    private static <T> Object registered(Registry<T> registry, String id, Function<T, String> key,
                                         String readable) {
        var location = ResourceLocation.tryParse(id);
        T value = location == null ? null : registry.getOptional(location).orElse(null);
        return value == null ? readable : Component.translatableWithFallback(key.apply(value), readable);
    }

    public static MutableComponent figure(String id, Object... args) {
        return text("figure." + id, args);
    }

    /** The same figure for a deferred stage ("Release", "Counter", "Stored pulse"). */
    static MutableComponent stageFigure(Component figure, Component stage) {
        if (figure.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().startsWith(PREFIX + "figure.") && TEXT.containsKey(t.getKey() + ".staged")) {
            Object[] args = new Object[t.getArgs().length + 1];
            args[0] = stage;
            System.arraycopy(t.getArgs(), 0, args, 1, t.getArgs().length);
            return Component.translatable(t.getKey() + ".staged", args);
        }
        return text("labelled", stage, figure);
    }

    /** True for a figure built by {@link #figure} with this id (unit forms included). */
    static boolean isFigure(Component label, String idPrefix) {
        return label.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().startsWith(PREFIX + "figure." + idPrefix);
    }

    /**
     * "a; b; c" or "a and b", folded pairwise so each language orders its own separator. Parts are components
     * or plain strings; one part comes back as it is, none as "".
     */
    static Object join(String joiner, List<?> parts) {
        if (parts.isEmpty()) return "";
        Object out = parts.getFirst();
        for (int i = 1; i < parts.size(); i++) out = text("join." + joiner, out, parts.get(i));
        return out;
    }

    /** {@link #join} as a component, for a line that is not itself an argument. */
    static Component joined(String joiner, List<?> parts) {
        Object out = join(joiner, parts);
        return out instanceof Component c ? c : Component.literal(out.toString());
    }

    private static void put(String id, String english) {
        if (TEXT.put(PREFIX + id, english) != null) throw new IllegalStateException("guide line written twice: " + id);
    }

    /** A figure with a stage form: the stage name leads, the label follows in lower case. */
    private static void figureLine(String id, String english) {
        put("figure." + id, english);
        put("figure." + id + ".staged", "%s " + english.substring(0, 1).toLowerCase(Locale.ROOT) + english.substring(1));
    }

    static {
        put("join.and", "%s and %s");
        put("join.semicolon", "%s; %s");
        put("join.plus", "%s + %s");
        put("join.slash", "%s / %s");
        put("labelled", "%s: %s");
        put("rank", "Rank %s");
        put("compared_rank", "Compared rank");
        put("compared", "%s %s: %s");

        // Figures.
        for (String unit : UNITS) {
            figureLine("damage_" + unit, "Damage / " + unit);
            figureLine("healing_" + unit, "Healing / " + unit);
            figureLine("healing_to_you_" + unit, "Healing to you / " + unit);
        }
        figureLine("all_hits", "All hits (max)");
        figureLine("all_heals", "All heals (max)");
        figureLine("zone_damage", "Zone damage / s");
        figureLine("zone_healing", "Zone healing / s");
        figureLine("rain_centre", "Damage at the center (%s blades)");
        put("figure.release_at_cap", "Release at cap");
        put("figure.dot_per_second", "%s / second");
        put("figure.dot_full", "DoT full duration");
        put("figure.shield_points", "Shield points");
        put("figure.shield_per_enemy", "Shield / enemy");
        put("figure.summons_per_cast", "Summons / cast");
        put("figure.health_per_summon", "Health / summon");
        put("figure.armor_per_summon", "Armor / summon");
        put("figure.summon_healing", "Summon healing / cast");
        put("figure.summon_damage", "Summon damage / hit");
        put("figure.attribute_percent", "%s (%%)");
        put("figure.attribute_seconds", "%s (seconds)");
        put("figure.cooldown_removed", "Cooldown removed (s)");
        put("figure.hit_reduction", "Hit reduction (%%)");
        put("figure.max_mana_percent", "Maximum mana (%%)");
        put("figure.bonus_strike", "Bonus strike (%% power)");

        // Who an impact lands on, and what it does there.
        put("you", "You: %s.");
        put("target", "Target: %s.");
        put("cloud_enemies", "Enemies in the cloud: %s.");
        put("cloud_allies", "You and allies in the cloud: %s.");
        put("cloud_any", "In the cloud: %s.");
        put("chance", "%s%% chance: %s");
        put("except", "%s (except %s)");
        put("only", "%s (only %s)");
        put("removes", "removes %s");
        put("removed.movement_impairing", "movement-impairing effects");
        put("removed.beneficial", "beneficial effects");
        put("removed.harmful", "harmful effects");
        put("adds_levels", "adds %s level(s) of %s, for %ss");
        put("adds_levels_capped", "adds %s level(s) of %s, up to level %s, for %ss");
        put("status", "%s %s for %ss");
        put("launches", "launches upward");
        put("pushes", "pushes or pulls");
        put("no_refresh", "Adding stacks does not refresh the existing duration.");
        put("apply_limit", "%s only affects targets with maximum health at most %s.");
        put("bonus_damage", "Bonus damage: +%s when %s.");
        put("bonus_heal", "Bonus heal: +%s when %s.");
        put("condition.health_below", "target health is below %s%%");
        put("condition.has_effect", "target has %s");
        put("condition.from_behind", "striking from behind");
        put("condition.caster", "the caster");

        put("blink_forward", "Blink: up to %s blocks toward your crosshair; requires a safe destination.");
        put("blink_behind", "Blink: behind the target; requires a safe destination.");
        put("blink_to", "Blink: to the target; requires a safe destination.");
        put("blink_aim", "Aim at a block to land on top when there is room and it is within range; otherwise stop on its near side.");
        put("taunt", "Taunts affected enemies into targeting you.");
        put("summons", "Summons %s for %ss. Owner scaling bonus: %s%%.");
        put("summon_own_attacks", "Summon attacks use its own attacks and cooldowns; they are not a single cast's damage.");
        put("summon_limit", "Nearby summon limit: %s. New summons replace the oldest at the limit.");
        put("creates", "Creates %s for %ss.");
        put("special_action", "Special action: %s.");
        put("special_effect", "Special effect: %s.");

        put("cast_on_self", "Cast on yourself.");
        put("targeting_reach", "Targeting: %s, uses weapon reach.");
        put("targeting_range", "Targeting: %s, %s blocks.");
        put("target_type.aim", "aim");
        put("target_type.area", "area");
        put("target_type.beam", "beam");
        put("target_type.from_trigger", "from trigger");
        put("impact_radius", "Impact radius: %s blocks around the hit.");
        put("area_radius_self", "Area radius: %s blocks. Includes you where applicable.");
        put("area_radius", "Area radius: %s blocks. Does not include you.");
        put("cone", "Cone: %s degrees in front of you.");
        put("projectiles", "%s projectiles per cast. Total assumes every projectile hits the same target.");
        put("zone", "Zone: %s block radius, %ss; one pulse every %ss while a target stays inside.");
        put("assault_line", "Charges through enemies in your path; each enemy is hit once.");
        put("assault_chain", "Travels between targets. Damage is per contact, not a guaranteed single-target total.");
        put("assault_leap", "Leaps toward the target; damage lands on arrival.");
        put("assault_charge", "Charges toward the target; damage lands on contact.");
        put("assault_collision", "Movement follows collision; walls or insufficient headroom can stop it.");
        put("blade_rain", "%s blades land across the area over %ss, after a %ss warning; each hits within %s blocks of where it lands.");
        put("stash", "Stores an effect for %ss. The listed impacts trigger on your melee hits during this window, not immediately on cast.");
        put("movement", "Movement: %s, up to %s blocks at normal size. Obstacles can shorten travel.");
        put("movement_kind.dash", "dash");
        put("movement_kind.grapple", "grapple");
        put("movement_kind.leap", "leap");
        put("grapple", "Requires a solid anchor. Missing the hook spends no mana and starts no cooldown. Sneak to release.");
        put("reset_cooldowns", "Resets your other active skill cooldowns; does not reset itself or ultimates.");
        put("command_summons", "Commands your summons within 48 blocks to attack the target.");
        put("recall_summons", "Recalls your summons within 48 blocks to you and clears their targets.");
        put("shift_aspect", "Changes your active form/aspect.");
        put("counter_window", "Opens a counter window; the counter damage triggers on an incoming hit, not on cast.");

        // Scaling: what the numbers grow with.
        put("scaling.school", "Attack damage + %s + Spell Damage");
        put("scaling.physical_melee", "Attack damage (Strength)");
        for (String school : List.of("arcane", "fire", "frost", "lightning", "soul", "healing")) {
            put("scaling." + school, school.substring(0, 1).toUpperCase(Locale.ROOT) + school.substring(1) + " spell power");
        }
        put("scaling.spell_power", "Spell power (%s)");
        put("scaling.healing_done", "Healing Done / Self Healing where applicable");
        put("scaling.summon", "Summon attributes: %s + Summon Power");
        put("scaling.your", "your %s");
        put("scaling.fixed", "Fixed effects; see rank and duration details");
        put("scaling.dot", "DoT damage and duration bonuses");
        put("scaling.shield_class", "rift tier + class stats + Shield Strength");
        put("scaling.shield_stats", "rift tier + %s / %s + Shield Strength");
        put("scaling.shield", "Shield: %s");
        put("scaling.shield_other", "Shield: %s; other effects: %s");
        put("scaling.reflect", "reflected damage: incoming hit size");
        put("scaling.gun", "held gun damage");
        put("scaling.bow", "held bow / crossbow ranged damage");
        put("scaling.stage", "%s scaling: %s.");
        put("formula", "Spell power starts with your attack damage before your %s bonus, multiplied by your %s bonus (about %s%% for the "
                + "first point, less for each one after), then by your Spell Damage bonus. The skill's coefficient and rank determine "
                + "the final amount; attack damage is the base, not the damage type.");
        put("formula_physical", "Physical power is your attack damage: weapon and gear, multiplied by your %s bonus (about %s%% for the "
                + "first point, less for each one after). The skill's coefficient and rank determine the final amount.");
        put("summon_stat", "Summon %s: %s + %s%% of your %s (%s).");
        put("operation.add_value", "add value");
        put("operation.add_multiplied_base", "add multiplied base");
        put("operation.add_multiplied_total", "add multiplied total");

        // Summon profiles.
        put("summon_heals_cast", "Heals one ally; base action cooldown %ss, plus 0.25s casting time. Travel, target acquisition and Summon Haste affect actual output.");
        put("summon_heals", "Heals one ally; base action cooldown %ss. Travel, target acquisition and Summon Haste affect actual output.");
        put("summon_attacks_cast", "Attacks one enemy; base action cooldown %ss, plus 0.25s casting time. Travel, target acquisition and Summon Haste affect actual output.");
        put("summon_attacks", "Attacks one enemy; base action cooldown %ss. Travel, target acquisition and Summon Haste affect actual output.");
        put("summon_follows", "Follows you and can be attacked. Health includes 4 base points plus your maximum-health contribution.");

        // The server's ranked additions.
        put("dot", "%s: %ss. Reapplication refreshes the effect; damage does not stack.");
        for (DotType type : DotType.values()) {
            put("dot_type." + type.key(), type.key().substring(0, 1).toUpperCase(Locale.ROOT) + type.key().substring(1));
        }
        put("shield_note", "Shield points scale with %s and skill rank. Class stats are weighted two-thirds main stat and one-third secondary stat.");
        put("shield_ally", "Shield absorbs damage. Ally radius: %s blocks.");
        put("shield_enemies", "Shield counts distinct enemies hit, up to %s; ally radius %s blocks.");
        put("reflect", "Reflects %s%% of incoming damage for %ss, at most once every 0.5s, to attackers within 24 blocks.");
        put("reflect_projectiles", "Reflects %s%% of incoming damage for %ss, at most once every 0.5s, to attackers within 24 blocks. Also turns vanilla projectiles back.");
        put("stored_recast_lost", "Recast to release; window %ss, arms after %ss. At expiry: unused charge is lost.");
        put("stored_recast_releases", "Recast to release; window %ss, arms after %ss. At expiry: releases.");
        put("stored_auto_lost", "Releases automatically; window %ss, arms after %ss. At expiry: unused charge is lost.");
        put("stored_auto_releases", "Releases automatically; window %ss, arms after %ss. At expiry: releases.");
        put("stored_gains", "Stores %s units per hit, up to %s. Each unit adds %s%% release power.");
        put("stored_decay", "After %ss without a gain, loses one unit every %ss.");
        put("stored_pulse", "Stored area pulses every %ss in a %s block radius.");
        put("stored_pulse_pull", "Stored area pulses every %ss in a %s block radius. Pulls enemies inward.");
        put("stage.release", "Release");
        put("stage.pulse", "Stored pulse");
        put("stage.counter", "Counter");
        put("gun_note", "With a gun in your main hand, damage per trigger pull contributes to attack damage for this cast.");
        put("bow_note", "With a bow or crossbow in your main hand, Ranged Damage contributes to attack damage for this cast.");
        put("values_note", "Values use your equipment when the book opens. Before critical hits, target defenses and conditional "
                + "combat bonuses. Distances assume normal player size. 2 damage = 1 heart. Reopen to refresh.");
        put("weapon_movement", "Movement skill: no weapon required.");
        put("weapon_required", "Requires a class weapon in your main hand: %s.");
        put("weapon_any", "your class weapon");
        put("bonus_strike_scaling", "Bonus strike scaling: %s.");

        // Passives.
        put("passive.always", "Always active once learned. Uses no mana or loadout slot.");
        put("passive.adds_percent", "Adds %s percentage points to %s.");
        put("passive.adds", "Adds %s to %s.");
        put("passive.haste", "Haste shortens cooldowns: 100 haste halves the time. It is not a flat percentage reduction.");
        put("passive.mana_regen", "Mana regeneration is mana restored per second.");
        put("passive.max_health", "2 health points = 1 heart.");
        put("passive.movement_speed", "Adds to the movement-speed attribute; standard walking speed starts at 0.1.");
        put("passive.draw_speed", "Also speeds up TaCZ gun reloads.");
        put("passive.ranged_damage", "Adds ranged weapon damage. Also raises TaCZ bullet damage, and Ranger bow skills cast with a bow in hand.");
        put("passive.on.monk_night_discipline", "Weapon hits against a target with a harmful effect trigger a bonus strike.");
        put("passive.on.monk_relentless", "Melee hits and damaging active skills trigger a bonus strike against targets at or below 30%% health.");
        put("passive.on.phantom_bonus_backstab_damage", "Melee hits and damaging active skills trigger a bonus strike when attacking from behind (within 60 degrees of directly behind).");
        put("passive.on.ranger_bonus_damage_to_marked", "Arrow hits and damaging active skills trigger a bonus strike against a target with Hunter's Mark, or with the Shattered Armor Predator Mark lands.");
        put("passive.on.reaper_bonus_damage_to_plagued_enemies", "Melee hits and damaging active skills trigger a bonus strike against an envenomed target.");
        put("passive.on.monk_unbroken_stance", "Reduces an incoming hit while your health is above 80%%.");
        put("passive.on.monk_rhythm", "Each weapon hit removes this many seconds from running active-skill cooldowns. Ultimates are excluded.");
        put("passive.on.trickster_cooldown_reduction_after_dodge", "Dodging an attack removes this many seconds from running active-skill cooldowns. Ultimates are excluded.");
        put("passive.on.reaper_mana_regen_on_kill", "A melee or damaging active-skill kill restores this percentage of maximum mana.");
        put("passive.on.monk_cleanse_mastery", "Removing a harmful effect activates the buff; natural expiry does not count.");
        put("passive.on.monk_combo_discipline", "Successful skill casts build the activation counter.");
        put("passive.on.counter", "Melee hits and damaging active skills build the activation counter.");
        put("passive.on.movement", "Completing a movement skill activates the buff; ordinary walking does not.");
        put("passive.on.default", "Activates on the passive's qualifying event.");
        put("passive.buff_lasts", "Buff lasts %ss.");
        put("passive.strike_scaling", "The extra strike scales with the passive's school power, not the damage of the triggering hit.");
        put("passive.reaction_count", "Activates every %s qualifying events. A gap longer than %ss resets the count.");
        put("passive.internal_cooldown", "Internal cooldown: %ss.");
        put("passive.no_chain", "Triggered passive effects do not trigger another reaction.");

        // The client's deferred-strike phrase inside authored descriptions.
        put("stage_damage", "%s damage");
        put("stage_damage_typed", "%s %s damage");
        // The same phrase before the book has an estimate (and in Spell Engine's tooltip): the base coefficient.
        put("stage_coefficient", "%s%% of your attack damage");
        put("stage_coefficient_arcane", "%s%% arcane damage");
        // A shield pool inside authored descriptions, and the same phrase before the book has an estimate.
        put("shield_amount", "%s damage");
        put("shield_unknown", "an amount of damage that grows with the rift's tier and your skill rank");
        for (String type : List.of("arcane", "holy", "soul", "physical", "fire", "frost", "lightning")) {
            put("damage_type." + type, type);
        }
    }
}
