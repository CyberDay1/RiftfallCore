package com.cyberday1.riftfall.skill;

import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Set;

/** Units and activation rules for the book's passive pages. Amounts always come from the node. */
public final class PassiveGuide {
    private static final Set<String> FRACTIONS = Set.of("apothic_attributes:crit_chance", "apothic_attributes:crit_damage",
            "apothic_attributes:draw_speed", "apothic_attributes:healing_received", "apothic_attributes:life_steal",
            "irons_lib:dodge_chance", "riftfall:shield_strength", "riftfall:spell_damage", "riftfall:healing_done", "riftfall:fire_rate",
            "riftfall:damage_resistance", "riftfall:shielded_reduction", "riftfall:self_healing", "riftfall:threat_generation",
            "riftfall:bleed_damage", "riftfall:burn_damage", "riftfall:venom_damage", "riftfall:summon_power");
    /** Guide tokens for the browser row: the per-rank value's unit, and its stat (an attribute id or a kind). */
    public static final String ROW_UNIT = "row_unit";
    public static final String ROW_STAT = "row_stat";
    public static final String PERCENT = "percent";
    public static final String SECONDS = "seconds";
    public static final String PLAIN = "plain";
    private PassiveGuide() { }

    /** The unit an attribute passive's value is read in. */
    public static String unit(String attribute) {
        return FRACTIONS.contains(attribute) ? PERCENT : attribute.endsWith("_duration") ? SECONDS : PLAIN;
    }

    /** "+2%", "+0.25s", "+0.005": a signed value in its unit. */
    public static String signed(double value, String unit) {
        String number = (value >= 0 ? "+" : "") + precise(PERCENT.equals(unit) ? value * 100 : value);
        return PERCENT.equals(unit) ? number + "%" : SECONDS.equals(unit) ? number + "s" : number;
    }

    private static void row(SkillGuide.Page page, String unit, String stat) {
        page.tokens.putIfAbsent(ROW_UNIT, unit);
        page.tokens.putIfAbsent(ROW_STAT, stat);
    }

    public static SkillGuide.Page describe(PassiveDef def, int rank) {
        var page = new SkillGuide.Page();
        double amount = def.amountAt(rank);
        page.notes.add(GuideLang.text("passive.always"));
        if (!def.grantsSpell()) {
            String attribute = def.attribute().toString();
            boolean percent = FRACTIONS.contains(attribute);
            Object name = name(attribute);
            Component label = percent ? GuideLang.figure("attribute_percent", name)
                    : attribute.endsWith("_duration") ? GuideLang.figure("attribute_seconds", name) : label(name);
            double shown = percent ? amount * 100 : amount;
            page.figures.put(label, precise(shown));
            page.notes.add(GuideLang.text(percent ? "passive.adds_percent" : "passive.adds", precise(shown), name));
            row(page, unit(attribute), attribute);
            if (attribute.contains("haste")) page.notes.add(GuideLang.text("passive.haste"));
            if (attribute.endsWith("mana_regen")) page.notes.add(GuideLang.text("passive.mana_regen"));
            if (attribute.endsWith("max_health")) page.notes.add(GuideLang.text("passive.max_health"));
            if (attribute.endsWith("movement_speed")) page.notes.add(GuideLang.text("passive.movement_speed"));
            if (attribute.endsWith("draw_speed")) page.notes.add(GuideLang.text("passive.draw_speed"));
            if (attribute.equals("ranged_weapon:damage")) page.notes.add(GuideLang.text("passive.ranged_damage"));
            return page;
        }
        String trigger = switch (def.id()) {
            case "monk_night_discipline", "monk_relentless", "phantom_bonus_backstab_damage", "ranger_bonus_damage_to_marked",
                    "reaper_bonus_damage_to_plagued_enemies", "monk_unbroken_stance", "monk_rhythm",
                    "trickster_cooldown_reduction_after_dodge", "reaper_mana_regen_on_kill", "monk_cleanse_mastery",
                    "monk_combo_discipline" -> def.id();
            case "monk_electric_rhythm", "monk_momentum_chains" -> "counter";
            case "monk_slipstream", "sword_immortal_flow_state" -> "movement";
            default -> "default";
        };
        page.notes.add(GuideLang.text("passive.on." + trigger));
        if (def.surge() != null) {
            for (var term : def.surge().terms()) {
                boolean percent = term.operation() != net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
                        || FRACTIONS.contains(term.attribute().toString());
                Object name = name(term.attribute().toString());
                page.figures.put(percent ? GuideLang.figure("attribute_percent", name) : label(name),
                        precise(amount * (percent ? 100 : 1)));
                row(page, percent ? PERCENT : PLAIN, term.attribute().toString());
            }
            page.notes.add(GuideLang.text("passive.buff_lasts", SkillGuide.number(def.surge().seconds())));
        } else if (def.id().equals("monk_rhythm") || def.id().equals("trickster_cooldown_reduction_after_dodge")) {
            page.figures.put(GuideLang.figure("cooldown_removed"), precise(amount));
            row(page, SECONDS, "cooldown_removed");
        } else if (def.id().equals("monk_unbroken_stance")) {
            page.figures.put(GuideLang.figure("hit_reduction"), precise(Math.min(1, amount) * 100));
            row(page, PERCENT, "hit_reduction");
        } else if (def.id().equals("reaper_mana_regen_on_kill")) {
            page.figures.put(GuideLang.figure("max_mana_percent"), precise(amount * 100));
            row(page, PERCENT, "max_mana");
        } else {
            page.figures.put(GuideLang.figure("bonus_strike"), precise(amount * 100));
            row(page, PERCENT, "bonus_strike");
            page.notes.add(GuideLang.text("passive.strike_scaling"));
        }
        if (def.reaction() != null && def.reaction().counts()) {
            page.notes.add(GuideLang.text("passive.reaction_count", Integer.toString(def.reaction().count()),
                    SkillGuide.number(def.reaction().countWindowTicks() / 20.0)));
        }
        if (def.internalCooldownTicks() > 0) page.notes.add(GuideLang.text("passive.internal_cooldown",
                SkillGuide.number(def.internalCooldownTicks() / 20.0)));
        page.notes.add(GuideLang.text("passive.no_chain"));
        return page;
    }

    private static String precise(double value) {
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
    /** The attribute's own translated name; the id made readable when no loaded mod registers it. */
    private static Object name(String id) {
        String result = id.substring(id.indexOf(':') + 1).replace("generic.", "").replace('_', ' ');
        return GuideLang.attribute(id, Character.toUpperCase(result.charAt(0)) + result.substring(1));
    }

    private static Component label(Object name) {
        return name instanceof Component c ? c.copy() : Component.literal(name.toString());
    }
}
