package com.cyberday1.riftfall.lore;

import com.cyberday1.riftfall.home.ShatteredHomeProgress;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The god's line when the Core takes a shard (story review S1): one line on the action bar, picked like the respawn
 * lines, with the count sent to chat. A hand-in that lands a tile says a {@code tile} line instead.
 */
public final class CoreHandInLines {
    private static final String FILE = "/data/riftfall/lore/core_handin.json";
    private static final String PREFIX = "lore.riftfall.core.handin.";
    public static final String PROGRESS = "message.riftfall.core.progress";

    private static final Map<String, String> ENGLISH = new LinkedHashMap<>();
    private static final Map<String, Integer> POOLS = load();
    private static final Map<UUID, String> LAST = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private CoreHandInLines() {}

    public static String key(String pool, int n) { return PREFIX + pool + "." + n; }

    /** The English for every key, for the lang provider. */
    public static Map<String, String> english() { return Collections.unmodifiableMap(ENGLISH); }

    /** The pool for this hand-in: a landed tile first, then the known pool, then the home's act. */
    public static String poolName(int homeRank, boolean archiveRead, boolean tilePlaced) {
        return tilePlaced ? "tile" : archiveRead ? "known" : RespawnLines.act(homeRank);
    }

    public static List<String> pool(String name) {
        var keys = new ArrayList<String>();
        for (int i = 0; i < POOLS.getOrDefault(name, 0); i++) keys.add(key(name, i));
        return keys;
    }

    /** One key at random from the pool, avoiding {@code last}. */
    public static String pick(int homeRank, boolean archiveRead, boolean tilePlaced, String last, RandomSource random) {
        var keys = pool(poolName(homeRank, archiveRead, tilePlaced));
        if (keys.size() > 1) keys.remove(last);
        return keys.get(random.nextInt(keys.size()));
    }

    /** Says the line on the action bar and the count in chat. Returns the key said. */
    public static String say(ServerLevel home, Player player, int phase, int shards, boolean tilePlaced) {
        int rank = ShatteredHomeProgress.of(home).unlockedTiles().size();
        var server = home.getServer();
        boolean known = server != null && NoteFinds.found(server, player.getUUID(), RespawnLines.ARCHIVE_NOTE);
        String key = pick(rank, known, tilePlaced, LAST.get(player.getUUID()), RANDOM);
        LAST.put(player.getUUID(), key);
        player.displayClientMessage(Component.translatable(key), true);
        player.displayClientMessage(Component.translatable(PROGRESS, phase, shards, rank).withStyle(ChatFormatting.GRAY), false);
        return key;
    }

    private static Map<String, Integer> load() {
        try (var stream = CoreHandInLines.class.getResourceAsStream(FILE)) {
            if (stream == null) throw new IllegalStateException("Missing " + FILE);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IllegalStateException("Unsupported hand-in lines");
            var pools = new LinkedHashMap<String, Integer>();
            for (var pool : root.getAsJsonObject("pools").entrySet()) {
                var lines = pool.getValue().getAsJsonArray();
                for (int i = 0; i < lines.size(); i++) ENGLISH.put(key(pool.getKey(), i), lines.get(i).getAsString());
                pools.put(pool.getKey(), lines.size());
            }
            for (var need : List.of("act1", "act2", "act3", "act4", "known", "tile"))
                if (pools.getOrDefault(need, 0) == 0) throw new IllegalStateException("Hand-in pool " + need + " is empty");
            return Collections.unmodifiableMap(pools);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + FILE, e);
        }
    }
}
