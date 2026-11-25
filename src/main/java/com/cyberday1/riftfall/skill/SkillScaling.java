package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.stat.Derivation;
import com.cyberday1.riftfall.stat.StatBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import static com.cyberday1.riftfall.skill.SkillGuide.*;

/** Book labels follow the spell's school, not the attribute temporarily modified for ranks. */
public final class SkillScaling {
    private SkillScaling() { }

    public static @Nullable StatBlock.Primary primary(String school) {
        return switch (school) {
            case "riftfall:arcana", "riftfall:command", "riftfall:divine",
                    "riftfall:summoning", "riftfall:shadow" -> StatBlock.Primary.INTELLECT;
            case "riftfall:holy" -> StatBlock.Primary.VITALITY;
            default -> null;
        };
    }

    /** Identify only stats actually read by impacts, including a summon's separate owner attributes. */
    public static void describe(JsonObject spell, SkillGuide.Power power, SkillGuide.Page page) {
        var sources = new LinkedHashSet<Component>();
        boolean attack = false;
        for (var element : array(spell, "impacts")) {
            JsonObject impact = element.getAsJsonObject();
            JsonObject action = object(impact, "action");
            String type = string(action, "type", "");
            JsonObject status = object(action, "status_effect");
            boolean powered = type.equals("DAMAGE") || type.equals("HEAL")
                    || value(status, "amplifier_power_multiplier", 0) != 0
                    || value(status, "amplifier_cap_power_multiplier", 0) != 0
                    || value(object(status, "apply_limit"), "spell_power_multiplier", 0) != 0;
            if (powered) {
                String school = string(impact, "school", string(spell, "school", ""));
                Component label = label(school);
                sources.add(label == null ? GuideLang.text("scaling.spell_power", school) : label);
                attack |= attackScaled(school);
                Component formula = formula(school);
                if (formula != null && !page.notes.contains(formula)) page.notes.add(formula);
            }
            if (type.equals("HEAL")) sources.add(GuideLang.text("scaling.healing_done"));
            if (type.equals("SUMMON")) {
                var ownerStats = new LinkedHashSet<String>();
                for (var entry : array(object(object(action, "summon"), "attribute_scaling"), "entries")) {
                    JsonObject stat = entry.getAsJsonObject();
                    for (var term : array(stat, "modifiers")) {
                        JsonObject modifier = term.getAsJsonObject();
                        double coefficient = value(modifier, "coefficient", 0);
                        if (coefficient == 0) continue;
                        String ownerId = string(modifier, "attribute_id", "");
                        Object owner = attribute(ownerId);
                        ownerStats.add(ownerId);
                        String operation = string(modifier, "operation", "ADD_VALUE");
                        page.notes.add(GuideLang.text("summon_stat", attribute(string(stat, "attribute_id", "")),
                                number(value(modifier, "base", 0)), number(coefficient * (1 + power.ownerScaling()) * 100), owner,
                                GuideLang.named("operation", operation, operation.toLowerCase(Locale.ROOT).replace('_', ' '))));
                    }
                }
                if (!ownerStats.isEmpty()) {
                    List<Object> yours = new ArrayList<>();
                    ownerStats.forEach(owner -> yours.add(GuideLang.text("scaling.your", attribute(owner))));
                    sources.add(GuideLang.text("scaling.summon", GuideLang.join("plus", yours)));
                    attack |= ownerStats.contains("minecraft:generic.attack_damage");
                }
            }
        }
        if (!sources.isEmpty()) page.scaling(GuideLang.joined("semicolon", List.copyOf(sources)), attack);
        page.tokens.put("damage_type", damageType(string(spell, "school", "")));
    }

    public static String damageType(String school) {
        return switch (school) {
            case "riftfall:arcana", "riftfall:command", "riftfall:summoning", "spell_power:arcane" -> "arcane";
            case "riftfall:holy", "riftfall:divine", "spell_power:healing" -> "holy";
            case "riftfall:shadow", "spell_power:soul" -> "soul";
            case "spell_power:physical_melee", "spell_power:physical_ranged" -> "physical";
            case "spell_power:fire" -> "fire";
            case "spell_power:frost" -> "frost";
            case "spell_power:lightning" -> "lightning";
            default -> "";
        };
    }

    private static Object attribute(String id) {
        return GuideLang.attribute(id, id.substring(id.indexOf(':') + 1).replace("generic.", "").replace('_', ' '));
    }

    /** Whether the school's label starts from attack damage. */
    public static boolean attackScaled(String school) {
        return primary(school) != null || school.equals("spell_power:physical_melee");
    }

    /** Null means the caller should use its translated attribute fallback. */
    public static @Nullable Component label(String school) {
        StatBlock.Primary primary = primary(school);
        if (primary != null) return GuideLang.text("scaling.school", primaryName(primary));
        return switch (school) {
            case "spell_power:physical_melee" -> GuideLang.text("scaling.physical_melee");
            case "spell_power:arcane", "spell_power:fire", "spell_power:frost", "spell_power:lightning",
                    "spell_power:soul", "spell_power:healing" -> GuideLang.text("scaling." + school.substring(school.indexOf(':') + 1));
            default -> null;
        };
    }

    /**
     * How the school's power is built. Physical skills read attack damage after Strength's multiplier, so
     * Strength is their input; a class school divides it out and takes its own primary instead (2026-11-08).
     */
    public static @Nullable Component formula(String school) {
        if (school.equals("spell_power:physical_melee")) {
            String percent = String.format(Locale.ROOT, "%.2f", Derivation.strengthDamage().coefficient() * 100);
            return GuideLang.text("formula_physical", primaryName(StatBlock.Primary.STRENGTH), percent);
        }
        StatBlock.Primary primary = primary(school);
        if (primary == null) return null;
        String percent = String.format(Locale.ROOT, "%.2f", Derivation.PER_BAND * 100);
        return GuideLang.text("formula", primaryName(StatBlock.Primary.STRENGTH), primaryName(primary), percent);
    }

    /** The primary stat's own attribute name ({@code RiftfallAttributes.descriptionId}), as the tooltips show it. */
    static Component primaryName(StatBlock.Primary primary) {
        return Component.translatable("attribute.name." + RiftfallCore.MODID + "." + primary.key());
    }
}
