package com.cyberday1.riftfall.lore;

import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What the story calls a rift's defender (story review S10): an epithet per theme, built on the world's name, so
 * the two story strings do not print another mod's entity name. Boss bars and loot messages keep the real name.
 */
public final class DefenderNames {
    private static final String FILE = "/data/riftfall/lore/defenders.json";
    private static final String PREFIX = "lore.riftfall.defender.";
    /** Suffix of the key that opens a sentence: "The Warden of ...". */
    private static final String START = ".start";

    private static final Map<String, String> ENGLISH = load();

    private DefenderNames() {}

    public static String key(String theme) { return PREFIX + theme; }

    /** The English for every key, for the lang provider. */
    public static Map<String, String> english() { return ENGLISH; }

    /** The defender's epithet for this origin's theme, or empty: the caller falls back to the real name. */
    public static Optional<Component> of(ShardOrigin origin, boolean opensSentence) {
        String key = key(origin.theme().getPath());
        if (!ENGLISH.containsKey(key)) return Optional.empty();
        return Optional.of(Component.translatable(opensSentence ? key + START : key, origin.world()));
    }

    private static Map<String, String> load() {
        try (var stream = DefenderNames.class.getResourceAsStream(FILE)) {
            if (stream == null) throw new IllegalStateException("Missing " + FILE);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IllegalStateException("Unsupported defender names");
            var english = new LinkedHashMap<String, String>();
            for (var theme : root.getAsJsonObject("themes").entrySet()) {
                String name = theme.getValue().getAsString();
                if (name.isEmpty() || !name.contains("%s")) throw new IllegalStateException("Defender name needs %s: " + theme.getKey());
                english.put(key(theme.getKey()), name);
                english.put(key(theme.getKey()) + START, Character.toUpperCase(name.charAt(0)) + name.substring(1));
            }
            return Collections.unmodifiableMap(english);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + FILE, e);
        }
    }
}
