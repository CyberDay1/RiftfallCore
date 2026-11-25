package com.cyberday1.riftfall.gear;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Removes the donor's world-tier advancement tree before criteria and client displays are built. */
public final class ApotheosisProgression {
    public static final String TIER_KEY = "key.apotheosis.open_world_tier_select";

    private ApotheosisProgression() { }

    public static boolean blockedScreen(String name) {
        return name.equals("dev.shadowsoffire.apotheosis.client.WorldTierSelectScreen")
                || name.equals("dev.shadowsoffire.apotheosis.client.WorldTierTutorialScreen")
                || name.equals("dev.shadowsoffire.apotheosis.client.WorldTierDetailScreen");
    }

    public static Map<ResourceLocation, JsonElement> filterAdvancements(Map<ResourceLocation, JsonElement> input) {
        var removed = new HashSet<ResourceLocation>();
        input.keySet().stream().filter(id -> id.getNamespace().equals("apotheosis")
                && (id.getPath().startsWith("progression/") || id.getPath().startsWith("gateways/")))
                .forEach(removed::add);
        // Addon descendants must not leave orphaned tabs, criteria or rewards behind.
        boolean changed;
        do {
            changed = false;
            for (var entry : input.entrySet()) {
                if (removed.contains(entry.getKey()) || !entry.getValue().isJsonObject()) continue;
                var parent = entry.getValue().getAsJsonObject().get("parent");
                if (parent != null && parent.isJsonPrimitive() && parent.getAsJsonPrimitive().isString()
                        && removed.contains(ResourceLocation.tryParse(parent.getAsString()))) {
                    changed |= removed.add(entry.getKey());
                }
            }
        } while (changed);
        var result = new HashMap<>(input);
        removed.forEach(result::remove);
        return result;
    }
}
