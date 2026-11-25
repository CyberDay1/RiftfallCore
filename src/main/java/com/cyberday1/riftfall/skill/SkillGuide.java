package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.network.SkillScreenPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Readable, target-independent facts from the loaded spell data, as {@link GuideLang} keys. No client or optional engine types. */
public final class SkillGuide {
    private SkillGuide() { }

    public record Power(double base, double multiplier, double effectSeconds, double spawnSeconds,
                        double summonSeconds, double ownerScaling, double healingDone, double selfHealing) {
        public Power(double base, double multiplier, double effectSeconds, double spawnSeconds,
                     double summonSeconds, double ownerScaling) {
            this(base, multiplier, effectSeconds, spawnSeconds, summonSeconds, ownerScaling, 0, 0);
        }
        public double hit(double coefficient) { return base * multiplier * coefficient; }
        public double heal(double coefficient, boolean self) {
            return hit(coefficient) * (1 + Math.max(0, healingDone) + (self ? Math.max(0, selfHealing) : 0));
        }
    }

    public static final class Page {
        public final Map<Component, String> figures = new LinkedHashMap<>();
        public final List<Component> notes = new ArrayList<>();
        /** Numbers the authored descriptions name in braces, plus damage-type ids. */
        public final Map<String, String> tokens = new LinkedHashMap<>();
        /** What the numbers grow with; {@code fixed} while nothing does. */
        public Component scaling = GuideLang.text("scaling.fixed");
        public boolean fixed = true;
        /** Whether {@link #scaling} names attack damage (the gun and bow riders build on it). */
        public boolean attackScaled;
        public void figure(Component label, double value) { figures.put(label, number(value)); }
        public void scaling(Component label, boolean attack) { scaling = label; fixed = false; attackScaled = attack; }
    }

    public static SkillScreenPayload.Guide compare(Page from, Page to) {
        return compare(from, to, GuideLang.text("compared_rank"));
    }

    public static SkillScreenPayload.Guide compare(Page from, Page to, Component comparedRank) {
        List<SkillScreenPayload.Figure> figures = new ArrayList<>();
        from.figures.forEach((label, value) -> figures.add(new SkillScreenPayload.Figure(label, value,
                to.figures.getOrDefault(label, value))));
        List<Component> notes = new ArrayList<>(from.notes);
        // Duration and summon figures can change even when there is no damage row.
        for (int i = 0; i < Math.min(notes.size(), to.notes.size()); i++) {
            if (!notes.get(i).equals(to.notes.get(i))) notes.set(i, GuideLang.text("compared", notes.get(i), comparedRank, to.notes.get(i)));
        }
        return new SkillScreenPayload.Guide(figures, notes, from.tokens, from.scaling);
    }

    public static Page describe(JsonObject spell, Power power) {
        Page page = new Page();
        JsonObject deliver = object(spell, "deliver");
        JsonArray clouds = array(deliver, "clouds");
        boolean cloud = !clouds.isEmpty();
        JsonObject projectile = object(deliver, "projectile");
        JsonObject launch = object(projectile, "launch_properties");
        int shots = 1 + (int) value(launch, "extra_launch_count", 0);
        int directions = Math.max(1, array(projectile, "direction_offsets").size());
        shots *= directions;
        String delivery = string(object(deliver, "custom"), "handler", "");
        boolean rain = delivery.equals("riftfall:" + BladeRainPlan.HANDLER);
        String unit = cloud ? "pulse" : rain ? "blade" : shots > 1 ? "projectile" : "hit";
        double damage = 0, heal = 0, extraSelfHeal = 0;
        boolean selfTarget = string(object(spell, "target"), "type", "CASTER").equals("CASTER");
        int statusIndex = 0;
        for (JsonElement element : array(spell, "impacts")) {
            JsonObject impact = element.getAsJsonObject();
            JsonObject action = object(impact, "action");
            String type = string(action, "type", "");
            boolean self = selfTarget || bool(action, "apply_to_caster");
            // A cloud around the caster lands on whoever stands in it.
            String recipient = cloud && selfTarget && !bool(action, "apply_to_caster") ? cloudRecipient(type, action)
                    : self ? "you" : "target";
            double chance = value(impact, "chance", 1);
            int rolled = page.notes.size();
            switch (type) {
                case "DAMAGE", "HEAL" -> {
                    JsonObject amount = object(action, type.equals("DAMAGE") ? "damage" : "heal");
                    double hit = power.hit(value(amount, "spell_power_coefficient", 1));
                    if (type.equals("DAMAGE")) damage += hit;
                    else {
                        double coefficient = value(amount, "spell_power_coefficient", 1);
                        heal += power.heal(coefficient, self);
                        if (!self && bool(object(object(spell, "target"), "area"), "include_caster")) {
                            extraSelfHeal += power.heal(coefficient, true) - power.heal(coefficient, false);
                        }
                    }
                    for (JsonElement modifier : array(impact, "target_modifiers")) {
                        JsonObject rule = modifier.getAsJsonObject();
                        double bonus = value(object(rule, "modifier"), "power_multiplier", 0);
                        if (bonus != 0) page.notes.add(GuideLang.text(type.equals("DAMAGE") ? "bonus_damage" : "bonus_heal",
                                number(power.base * value(amount, "spell_power_coefficient", 1) * bonus), conditions(rule)));
                    }
                }
                case "STATUS_EFFECT" -> {
                    JsonObject status = object(action, "status_effect");
                    String mode = string(status, "apply_mode", "SET");
                    String id = string(status, "effect_id", "");
                    double seconds = value(status, "duration", 10) + power.effectSeconds;
                    if (mode.equals("REMOVE")) {
                        JsonObject remove = object(status, "remove");
                        Object removed = !id.isEmpty() ? GuideLang.effect(id) : GuideLang.text("removed." + (bool(remove, "movement_impairing")
                                ? "movement_impairing" : bool(remove, "select_beneficial") ? "beneficial" : "harmful"));
                        page.notes.add(GuideLang.text(recipient, restricted(GuideLang.text("removes", removed), impact)));
                    } else {
                        statusIndex++;
                        page.tokens.put("effect_duration_" + statusIndex, number(seconds));
                        page.tokens.putIfAbsent("effect_duration", number(seconds));
                        int amplifier = (int) value(status, "amplifier", 0)
                                + (int) (value(status, "amplifier_power_multiplier", 0) * power.hit(1));
                        int cap = (int) value(status, "amplifier_cap", 0)
                                + (int) (value(status, "amplifier_cap_power_multiplier", 0) * power.hit(1));
                        if (mode.equals("ADD")) {
                            page.notes.add(GuideLang.text(recipient, cap > 0
                                    ? GuideLang.text("adds_levels_capped", Integer.toString(amplifier), GuideLang.effect(id), Integer.toString(cap + 1), number(seconds))
                                    : GuideLang.text("adds_levels", Integer.toString(amplifier), GuideLang.effect(id), number(seconds))));
                            if (status.has("refresh_duration") && !bool(status, "refresh_duration")) page.notes.add(GuideLang.text("no_refresh"));
                        } else {
                            if (cap > 0) amplifier = Math.min(amplifier, cap);
                            page.notes.add(GuideLang.text(recipient, restricted(GuideLang.text("status", GuideLang.effect(id),
                                    Integer.toString(1 + amplifier), number(seconds)), impact)));
                        }
                        page.tokens.putIfAbsent("effect_amplifier", Integer.toString(amplifier + 1));
                        page.tokens.putIfAbsent("effect_amplifier_cap", Integer.toString(cap + 1));
                        if (status.has("apply_limit")) {
                            JsonObject limit = object(status, "apply_limit");
                            page.notes.add(GuideLang.text("apply_limit", GuideLang.effect(id),
                                    number(value(limit, "health_base", 0) + value(limit, "spell_power_multiplier", 0) * power.hit(1))));
                        }
                    }
                }
                case "TELEPORT" -> {
                    JsonObject teleport = object(action, "teleport");
                    double distance = value(object(teleport, "forward"), "distance", 0);
                    page.tokens.put("teleport_distance", number(distance));
                    page.notes.add(distance > 0 ? GuideLang.text("blink_forward", number(distance))
                            : GuideLang.text(string(teleport, "mode", "").equals("BEHIND_TARGET") ? "blink_behind" : "blink_to"));
                    if (distance > 0) page.notes.add(GuideLang.text("blink_aim"));
                }
                case "VELOCITY" -> {
                    JsonObject velocity = object(action, "velocity");
                    double lift = value(object(velocity, "push"), "y", 0);
                    page.notes.add(GuideLang.text(recipient, restricted(GuideLang.text(lift > 0 ? "launches" : "pushes"), impact)));
                }
                case "AGGRO" -> page.notes.add(GuideLang.text("taunt"));
                case "SUMMON" -> {
                    JsonObject summon = object(action, "summon");
                    double seconds = value(object(object(summon, "behaviour"), "lifespan"), "active_seconds", 0)
                            + Math.round(power.summonSeconds);
                    page.tokens.putIfAbsent("summon_duration", number(seconds));
                    page.notes.add(GuideLang.text("summons", GuideLang.entity(string(summon, "entity_type_id", "companion")),
                            number(seconds), number(power.ownerScaling * 100)));
                    page.notes.add(GuideLang.text("summon_own_attacks"));
                    page.notes.add(GuideLang.text("summon_limit", "8"));
                }
                case "SPAWN" -> {
                    for (JsonElement spawned : array(action, "spawns")) {
                        JsonObject spawn = spawned.getAsJsonObject();
                        page.notes.add(GuideLang.text("creates", GuideLang.entity(string(spawn, "entity_type_id", "entity")),
                                number(value(spawn, "time_to_live_seconds", 0) + power.spawnSeconds)));
                    }
                }
                case "CUSTOM" -> custom(page, string(object(action, "custom"), "handler", ""));
                default -> page.notes.add(GuideLang.text("special_action", readable(type)));
            }
            if (chance < 1) {
                for (int i = rolled; i < page.notes.size(); i++) {
                    page.notes.set(i, GuideLang.text("chance", number(chance * 100), page.notes.get(i)));
                }
            }
        }
        if (damage > 0) page.figure(GuideLang.figure("damage_" + unit), damage);
        if (heal > 0) page.figure(GuideLang.figure("healing_" + unit), heal);
        if (heal > 0 && extraSelfHeal > 0) page.figure(GuideLang.figure("healing_to_you_" + unit), heal + extraSelfHeal);
        page.tokens.put("damage", number(damage));
        page.tokens.put("heal", number(heal));
        double range = value(spell, "range", 0);
        String target = string(object(spell, "target"), "type", "CASTER");
        Component targetName = GuideLang.named("target_type", target, readable(target));
        page.notes.add(target.equals("CASTER") ? GuideLang.text("cast_on_self")
                : string(spell, "range_mechanic", "").equals("MELEE") ? GuideLang.text("targeting_reach", targetName)
                : GuideLang.text("targeting_range", targetName, number(range)));
        double radius = value(object(spell, "area_impact"), "radius", 0);
        page.tokens.put("impact_range", number(radius));
        if (radius > 0) page.notes.add(GuideLang.text("impact_radius", number(radius)));
        JsonObject area = object(object(spell, "target"), "area");
        if (target.equals("AREA")) {
            page.notes.add(GuideLang.text(bool(area, "include_caster") ? "area_radius_self" : "area_radius", number(range)));
            double angle = value(area, "angle_degrees", 360);
            if (angle < 360) page.notes.add(GuideLang.text("cone", number(angle)));
        }
        if (shots > 1) {
            page.notes.add(GuideLang.text("projectiles", Integer.toString(shots)));
            if (damage > 0) page.figure(GuideLang.figure("all_hits"), damage * shots);
            if (heal > 0) page.figure(GuideLang.figure("all_heals"), heal * shots);
        }
        for (JsonElement element : clouds) {
            JsonObject zone = element.getAsJsonObject();
            double seconds = value(zone, "time_to_live_seconds", 0) + power.spawnSeconds;
            double interval = value(zone, "impact_tick_interval", 20) / 20;
            double zoneRadius = value(object(zone, "volume"), "radius", 0);
            page.notes.add(GuideLang.text("zone", number(zoneRadius), number(seconds), number(interval)));
            page.tokens.put("cloud_duration", number(seconds));
            page.tokens.put("cloud_radius", number(zoneRadius));
            // Report rate, not a fabricated guaranteed total: entry time and cloud tick phase matter.
            if (damage > 0 && interval > 0) page.figure(GuideLang.figure("zone_damage"), damage / interval);
            if (heal > 0 && interval > 0) page.figure(GuideLang.figure("zone_healing"), heal / interval);
        }
        if (delivery.startsWith("riftfall:assault/")) {
            String mode = delivery.substring(delivery.lastIndexOf('/') + 1);
            page.notes.add(GuideLang.text(switch (mode) {
                case "line" -> "assault_line";
                case "chain", "chain_leap", "rampage" -> "assault_chain";
                case "leap" -> "assault_leap";
                default -> "assault_charge";
            }));
            page.notes.add(GuideLang.text("assault_collision"));
        }
        if (rain) {
            int hits = BladeRainPlan.centreHits(radius);
            page.notes.add(GuideLang.text("blade_rain", Integer.toString(BladeRainPlan.COUNT),
                    number(BladeRainPlan.WINDOW_TICKS / 20.0), number(BladeRainPlan.TELEGRAPH_TICKS / 20.0),
                    number(BladeRainPlan.IMPACT_RADIUS)));
            if (damage > 0) page.figure(GuideLang.figure("rain_centre", Integer.toString(hits)), damage * hits);
        }
        if (string(deliver, "type", "").equals("STASH_EFFECT")) {
            JsonObject stash = object(deliver, "stash_effect");
            page.notes.add(0, GuideLang.text("stash", number(value(stash, "duration", 10))));
        }
        SkillScaling.describe(spell, power, page);
        return page;
    }

    /** Spell Engine sends a beneficial effect to allies and any other to enemies. */
    private static String cloudRecipient(String type, JsonObject action) {
        JsonObject status = object(action, "status_effect");
        if (!type.equals("STATUS_EFFECT") || string(status, "apply_mode", "SET").equals("REMOVE")) return "cloud_any";
        Boolean beneficial = GuideLang.beneficial(string(status, "effect_id", ""));
        return beneficial == null ? "cloud_any" : beneficial ? "cloud_allies" : "cloud_enemies";
    }

    private static void custom(Page page, String handler) {
        if (handler.startsWith("riftfall:travel/")) {
            String[] parts = handler.split("/");
            double distance = Double.parseDouble(parts[2]) * (parts[1].equals("grapple") ? 2 : 1);
            page.notes.add(GuideLang.text("movement", GuideLang.named("movement_kind", parts[1], readable(parts[1])), number(distance)));
            if (parts[1].equals("grapple")) page.notes.add(GuideLang.text("grapple"));
            return;
        }
        String note = switch (handler) {
            case "riftfall:reset_cooldowns" -> "reset_cooldowns";
            case "riftfall:command_summons" -> "command_summons";
            case "riftfall:recall_summons" -> "recall_summons";
            case "riftfall:shift_aspect" -> "shift_aspect";
            case "riftfall:counter_window" -> "counter_window";
            // These are explained by their node terms or the delivery, not separate damage events.
            case "riftfall:burn", "riftfall:bleed", "riftfall:venom", "riftfall:reflect_window",
                    "riftfall:store_hit", "riftfall:enemy_struck", "riftfall:assault_contact" -> "";
            default -> null;
        };
        if (note == null) page.notes.add(GuideLang.text("special_effect", readable(handler)));
        else if (!note.isEmpty()) page.notes.add(GuideLang.text(note));
    }

    private static Component restricted(Component clause, JsonObject impact) {
        for (JsonElement element : array(impact, "target_modifiers")) {
            JsonObject rule = element.getAsJsonObject();
            if (string(rule, "execute", "").equals("DENY")) return GuideLang.text("except", clause, conditions(rule));
            if (string(rule, "execute", "").equals("ALLOW")) return GuideLang.text("only", clause, conditions(rule));
        }
        return clause;
    }

    /** An unregistered id travels as a plain readable string argument. */
    private static Object conditions(JsonObject rule) {
        List<Object> conditions = new ArrayList<>();
        for (JsonElement element : array(rule, "conditions")) {
            JsonObject condition = element.getAsJsonObject();
            if (condition.has("health_percent_below")) conditions.add(GuideLang.text("condition.health_below",
                    number(value(condition, "health_percent_below", 0) * 100)));
            if (condition.has("entity_type")) conditions.add(GuideLang.entity(string(condition, "entity_type", "")));
            String predicate = string(condition, "entity_predicate_id", "");
            if (predicate.endsWith(":has_effect")) conditions.add(GuideLang.text("condition.has_effect", GuideLang.effect(string(condition, "entity_predicate_param", ""))));
            else if (predicate.endsWith(":from_behind")) conditions.add(GuideLang.text("condition.from_behind"));
            else if (predicate.endsWith(":is_caster")) conditions.add(GuideLang.text("condition.caster"));
        }
        return GuideLang.join("and", conditions);
    }

    public static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }
    public static JsonArray array(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonArray() ? parent.getAsJsonArray(key) : new JsonArray();
    }
    public static double value(JsonObject parent, String key, double fallback) {
        return parent.has(key) && parent.get(key).isJsonPrimitive() ? parent.get(key).getAsDouble() : fallback;
    }
    public static String string(JsonObject parent, String key, String fallback) {
        return parent.has(key) && parent.get(key).isJsonPrimitive() ? parent.get(key).getAsString() : fallback;
    }
    private static boolean bool(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonPrimitive() && parent.get(key).getAsBoolean();
    }
    public static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value).replaceAll("\\.0$", "");
    }
    static String readable(String id) {
        return id.substring(id.indexOf(':') + 1).replace('_', ' ').toLowerCase(Locale.ROOT);
    }
}
