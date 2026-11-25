package com.cyberday1.riftfall.lore;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.home.ShatteredHomeLayout;
import com.cyberday1.riftfall.home.ShatteredHomeLevels;
import com.cyberday1.riftfall.home.ShatteredHomeProgress;
import com.cyberday1.riftfall.home.npc.depth.ResidentProgress;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

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
 * The god's line when it brings a player back home after a death (lore draft v3 section 8): one line on the action
 * bar, to that player only, picked by the home's act and never the same line twice in a row. The first death ever
 * always says {@code act1.0}. Narrative only: nothing is taken (ruling 9).
 */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class RespawnLines {
    private static final String FILE = "/data/riftfall/lore/respawn.json";
    private static final String PREFIX = "lore.riftfall.respawn.";
    /** Read this and the god stops pretending: the known pool replaces the act pools. */
    public static final String ARCHIVE_NOTE = "rootbound_archive_1";
    /** Ticks after the respawn, so the line is not spent behind the loading screen. */
    static final int DELAY = 20;

    private static final Map<String, String> ENGLISH = new LinkedHashMap<>();
    private static final Map<String, Integer> POOLS = load();
    private static final Map<UUID, Long> DUE = new HashMap<>();

    private RespawnLines() {}

    public static String key(String pool, int n) { return PREFIX + pool + "." + n; }

    /** The English for every key, for the lang provider. */
    public static Map<String, String> english() { return Collections.unmodifiableMap(ENGLISH); }

    /** The first tile past the planned home, where the {@code wild} lines join the known pool. */
    public static int wildernessRank() { return ShatteredHomeLayout.tiles().size() + 1; }

    /** The act pool for a home rank, as the recognition gates count it. */
    public static String act(int homeRank) {
        return homeRank <= 3 ? "act1" : homeRank <= 9 ? "act2" : homeRank <= 17 ? "act3" : "act4";
    }

    /** The keys this player may hear: by act, or the known pool (plus wild past the home) once the archive is read. */
    public static List<String> pool(int homeRank, boolean archiveRead, int wildernessRank) {
        var names = archiveRead ? (homeRank >= wildernessRank ? List.of("known", "wild") : List.of("known"))
                : List.of(act(homeRank));
        var keys = new ArrayList<String>();
        for (var name : names) for (int i = 0; i < POOLS.getOrDefault(name, 0); i++) keys.add(key(name, i));
        return keys;
    }

    /** One key: {@code act1.0} on the first death ever, else at random from the pool, avoiding {@code last}. */
    public static String pick(int deaths, int homeRank, boolean archiveRead, String last, RandomSource random) {
        if (deaths <= 1) return key("act1", 0);
        var keys = new ArrayList<>(pool(homeRank, archiveRead, wildernessRank()));
        if (keys.size() > 1) keys.remove(last);
        return keys.get(random.nextInt(keys.size()));
    }

    @SubscribeEvent
    static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ShatteredHomeLevels.isShatteredHome(player.level().dimension())) return;
        DUE.put(player.getUUID(), player.server.getTickCount() + (long) DELAY);
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (!DUE.isEmpty()) deliver(server.getTickCount(), server.getPlayerList()::getPlayer);
    }

    /** Says every line due by {@code now}. Public for the rig probe: its fake players are found by its own lookup. */
    public static void deliver(long now, java.util.function.Function<UUID, ServerPlayer> find) {
        var it = DUE.entrySet().iterator();
        while (it.hasNext()) {
            var due = it.next();
            if (due.getValue() > now) continue;
            it.remove();
            var player = find.apply(due.getKey());
            if (player != null) say(player);
        }
    }

    static String say(ServerPlayer player) {
        if (!ShatteredHomeLevels.isShatteredHome(player.level().dimension())) return "";
        var record = ResidentProgress.of(player.server);
        var me = record.player(player.getUUID());
        int rank = ShatteredHomeProgress.of(player.serverLevel()).unlockedTiles().size();
        int deaths = player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
        String key = pick(deaths, rank, me.notes().contains(ARCHIVE_NOTE), me.respawnLine(), player.getRandom());
        me.respawnLine(key);
        record.setDirty();
        player.displayClientMessage(Component.translatable(key), true);
        return key;
    }

    /** Whether a line is waiting for this player; the probe checks a respawn queued one. */
    public static boolean waiting(UUID player) { return DUE.containsKey(player); }

    private static Map<String, Integer> load() {
        try (var stream = RespawnLines.class.getResourceAsStream(FILE)) {
            if (stream == null) throw new IllegalStateException("Missing " + FILE);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IllegalStateException("Unsupported respawn lines");
            var pools = new LinkedHashMap<String, Integer>();
            for (var pool : root.getAsJsonObject("pools").entrySet()) {
                var lines = pool.getValue().getAsJsonArray();
                for (int i = 0; i < lines.size(); i++) ENGLISH.put(key(pool.getKey(), i), lines.get(i).getAsString());
                pools.put(pool.getKey(), lines.size());
            }
            for (var need : List.of("act1", "act2", "act3", "act4", "known", "wild"))
                if (pools.getOrDefault(need, 0) == 0) throw new IllegalStateException("Respawn pool " + need + " is empty");
            return Collections.unmodifiableMap(pools);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + FILE, e);
        }
    }
}
