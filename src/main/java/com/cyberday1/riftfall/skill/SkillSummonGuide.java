package com.cyberday1.riftfall.skill;

import com.google.gson.JsonObject;
import java.util.function.ToDoubleFunction;
import static com.cyberday1.riftfall.skill.SkillGuide.*;

/** Numbers for the native summon profiles, evaluated against the owner's current attributes. */
public final class SkillSummonGuide {
    private SkillSummonGuide() { }
    public static void describe(JsonObject spell, Power power, Page page, ToDoubleFunction<String> owner) {
        for (var element : array(spell, "impacts")) {
            var summon = object(object(element.getAsJsonObject(), "action"), "summon");
            if (!string(summon, "entity_type_id", "").startsWith("riftfall:summon_")) continue;
            page.figure(GuideLang.figure("summons_per_cast"), value(summon, "spawn_count", 1));
            page.figure(GuideLang.figure("health_per_summon"), 4 + inherited(summon, "minecraft:generic.max_health", power, owner));
            page.figure(GuideLang.figure("armor_per_summon"), inherited(summon, "minecraft:generic.armor", power, owner));
            for (var action : array(object(summon, "behaviour"), "actions")) {
                var entry = action.getAsJsonObject();
                var cast = object(entry, "spell_cast");
                boolean heal = string(cast, "spell_id", "").endsWith("sprite")
                        || string(cast, "spell_id", "").endsWith("spirit_healer");
                page.figure(GuideLang.figure(heal ? "summon_healing" : "summon_damage"),
                        inherited(summon, "minecraft:generic.attack_damage", power, owner));
                double ticks = value(cast, "cooldown", value(object(entry, "melee_attack"), "duration", 20));
                page.notes.add(GuideLang.text((heal ? "summon_heals" : "summon_attacks") + (cast.size() > 0 ? "_cast" : ""),
                        number(ticks / 20)));
            }
            page.notes.add(GuideLang.text("summon_follows"));
        }
    }
    static double inherited(JsonObject summon, String attribute, Power power, ToDoubleFunction<String> owner) {
        double total = 0;
        for (var entry : array(object(summon, "attribute_scaling"), "entries")) {
            var stat = entry.getAsJsonObject();
            if (!string(stat, "attribute_id", "").equals(attribute)) continue;
            for (var term : array(stat, "modifiers")) {
                var modifier = term.getAsJsonObject();
                total += value(modifier, "base", 0) + value(modifier, "coefficient", 0)
                        * (1 + power.ownerScaling()) * owner.applyAsDouble(string(modifier, "attribute_id", ""));
            }
        }
        return total;
    }
}
