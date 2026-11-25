package com.cyberday1.riftfall.progression;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Quiet mod introductions without deleting criteria, recipe unlocks, rewards or advancement tabs. */
public final class AdvancementPresentation {
    private static final Set<String> INTRODUCTIONS = Set.of(
            "atmul:welcome_to_atmul", "crystalcraft_unlimited_java:recipe_unlock");
    private AdvancementPresentation() { }

    public static Map<ResourceLocation, JsonElement> quietIntroductions(Map<ResourceLocation, JsonElement> input) {
        var result = new HashMap<>(input);
        input.forEach((id, value) -> {
            if (!value.isJsonObject() || id.getNamespace().equals("minecraft")
                    || id.getNamespace().equals("riftfall")) return;
            JsonObject advancement = value.getAsJsonObject();
            if (advancement.has("parent") || !advancement.has("display")
                    || !advancement.get("display").isJsonObject()) return;
            boolean root = id.getPath().equals("root") || id.getPath().endsWith("/root");
            if (!root && !INTRODUCTIONS.contains(id.toString()) && !automatic(advancement)) return;
            JsonObject quiet = advancement.deepCopy();
            quiet.getAsJsonObject("display").addProperty("show_toast", false);
            quiet.getAsJsonObject("display").addProperty("announce_to_chat", false);
            result.put(id, quiet);
        });
        return result;
    }

    private static boolean automatic(JsonObject advancement) {
        var criteria = advancement.get("criteria");
        if (criteria == null || !criteria.isJsonObject() || criteria.getAsJsonObject().size() == 0) return false;
        return criteria.getAsJsonObject().entrySet().stream().allMatch(entry -> {
            if (!entry.getValue().isJsonObject()) return false;
            var criterion = entry.getValue().getAsJsonObject();
            var trigger = criterion.get("trigger"); var conditions = criterion.get("conditions");
            return trigger != null && trigger.isJsonPrimitive() && trigger.getAsJsonPrimitive().isString()
                    && trigger.getAsString().equals("minecraft:tick")
                    && (conditions == null || conditions.isJsonObject() && conditions.getAsJsonObject().size() == 0);
        });
    }
}
