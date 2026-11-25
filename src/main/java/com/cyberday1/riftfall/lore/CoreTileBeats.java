package com.cyberday1.riftfall.lore;

import com.cyberday1.riftfall.home.ShatteredHomeLayout;
import com.cyberday1.riftfall.home.ShatteredHomeTiles;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

/**
 * The Core's line in chat when a home tile lands (story review S2, suggestion 3): the quest-line beat drafted for
 * that tile, to everyone in the home. Tiles with no beat say nothing. No quest wiring.
 */
public final class CoreTileBeats {
    private static final String FILE = "/data/riftfall/lore/core_beats.json";
    private static final String PREFIX = "lore.riftfall.core.beat.";
    public static final String WILDERNESS = PREFIX + "wilderness";

    private static final Map<String, String> ENGLISH = load();

    private CoreTileBeats() {}

    public static String key(String tileId) { return PREFIX + tileId; }

    /** The English for every key, for the lang provider. */
    public static Map<String, String> english() { return ENGLISH; }

    /** The Core's voice in chat. */
    public static MutableComponent voice(String key) { return Component.translatable(key).withStyle(ChatFormatting.ITALIC); }

    /** The beat for the tile just placed: its own, or the wilderness beat for the first tile past the planned home. */
    public static Optional<String> beat(int layoutVersion, ShatteredHomeTiles.GridPos position, int homeRank) {
        if (!ShatteredHomeLayout.supports(layoutVersion)) return Optional.empty();
        var tile = ShatteredHomeLayout.at(layoutVersion, position);
        if (tile.isPresent()) return Optional.of(key(tile.get().id())).filter(ENGLISH::containsKey);
        return homeRank == ShatteredHomeLayout.tiles(layoutVersion).size() + 1 ? Optional.of(WILDERNESS) : Optional.empty();
    }

    /** Says the beat, if the tile has one, to everyone in the home and to whoever fed the Core. */
    public static Optional<String> placed(ServerLevel home, Player feeder, int layoutVersion,
                                          ShatteredHomeTiles.GridPos position, int homeRank) {
        var key = beat(layoutVersion, position, homeRank);
        if (key.isEmpty()) return key;
        var hearers = new LinkedHashSet<Player>(home.players());
        hearers.add(feeder);
        for (var player : hearers) player.displayClientMessage(voice(key.get()), false);
        return key;
    }

    private static Map<String, String> load() {
        try (var stream = CoreTileBeats.class.getResourceAsStream(FILE)) {
            if (stream == null) throw new IllegalStateException("Missing " + FILE);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IllegalStateException("Unsupported tile beats");
            var english = new LinkedHashMap<String, String>();
            for (var tile : root.getAsJsonObject("tiles").entrySet()) english.put(key(tile.getKey()), tile.getValue().getAsString());
            english.put(WILDERNESS, root.get("wilderness").getAsString());
            return Collections.unmodifiableMap(english);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + FILE, e);
        }
    }
}
