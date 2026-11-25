package com.cyberday1.riftfall.client;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Constructor defaults only: vanilla loads saved personal bindings afterward. */
public final class PackKeyDefaults {
    private static final Map<String, String> DEFAULTS = load();

    private PackKeyDefaults() { }

    public static String binding(String name) { return DEFAULTS.get(name); }

    private static Map<String, String> load() {
        try (var stream = PackKeyDefaults.class.getResourceAsStream("/assets/riftfall/key_defaults.json")) {
            if (stream == null) throw new IllegalStateException("Missing Riftfall key defaults");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> result = new HashMap<>();
            json.getAsJsonObject("defaults").entrySet().forEach(e -> result.put(e.getKey(), e.getValue().getAsString()));
            return Map.copyOf(result);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read Riftfall key defaults", error);
        }
    }
}
