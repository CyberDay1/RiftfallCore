package com.cyberday1.riftfall.home;

import com.cyberday1.riftfall.RiftfallCore;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.*;

/** Op debug: {@code /riftfall home tiles all|<id>} places authored tiles the way a shard unlock does. */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class HomeTileCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "message." + RiftfallCore.MODID + ".home.tiles.";
    public static final String NOT_HOME = KEY + "not_home";
    public static final String LAYOUT = KEY + "layout";
    public static final String UNKNOWN = KEY + "unknown";
    public static final String BUSY = KEY + "busy";
    public static final String START = KEY + "start";
    public static final String PRESENT = KEY + "present";
    public static final String PRESENT_LIST = KEY + "present_list";
    public static final String PLACED = KEY + "placed";
    public static final String FAILED = KEY + "failed";
    public static final String DONE = KEY + "done";
    /** The rig waits on this log line. */
    public static final String DONE_MARKER = "Riftfall home tiles: done";

    public static final Map<String, String> ENGLISH = Map.ofEntries(
            Map.entry(NOT_HOME, "Stand in a Shattered Home to place its tiles."),
            Map.entry(LAYOUT, "This home uses layout version %s, which has no authored tiles to place."),
            Map.entry(UNKNOWN, "Layout version %2$s has no tile called %1$s."),
            Map.entry(BUSY, "Tiles are already being placed in this home."),
            Map.entry(START, "Placing %s missing tiles, one per tick."),
            Map.entry(PRESENT, "%s is already present at (%s, %s)."),
            Map.entry(PRESENT_LIST, "Already present: %s"),
            Map.entry(PLACED, "Placed %s at (%s, %s) (%s of %s)."),
            Map.entry(FAILED, "Could not place %s at (%s, %s); skipped. See the server log."),
            Map.entry(DONE, "Done: %s placed, %s already present, %s failed. The home has %s shards, phase %s."));

    enum Result { PLACED, PRESENT, FAILED }

    private static final class Job {
        final ServerLevel level; final CommandSourceStack source; final Deque<ShatteredHomeLayout.Tile> queue;
        final int total; int placed, present, failed;
        Job(ServerLevel level, CommandSourceStack source, List<ShatteredHomeLayout.Tile> missing, int present) {
            this.level = level; this.source = source; this.queue = new ArrayDeque<>(missing);
            this.total = missing.size(); this.present = present;
        }
    }
    private static final Map<ResourceKey<Level>, Job> JOBS = new LinkedHashMap<>();

    private HomeTileCommand() {}

    /** Shards a home holds once it has this many tiles: rank 25 = 120. */
    static int shardsFor(int tiles) { return ShatteredHomeProgress.SHARDS_PER_TILE * Math.max(0, tiles - 1); }

    /** Authored tiles not yet placed, in unlock order. */
    static List<ShatteredHomeLayout.Tile> missing(int version, List<ShatteredHomeTiles.GridPos> placed) {
        return ShatteredHomeLayout.tiles(version).stream().filter(t -> !placed.contains(t.position())).toList();
    }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var tiles = Commands.literal("tiles").requires(s -> s.hasPermission(2))
                .then(Commands.literal("all").executes(ctx -> all(ctx.getSource())))
                .then(Commands.argument("tile", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ids(ctx.getSource()), builder))
                        .executes(ctx -> one(ctx.getSource(), StringArgumentType.getString(ctx, "tile"))));
        event.getDispatcher().register(Commands.literal("riftfall").then(Commands.literal("home").then(tiles)));
    }

    private static List<String> ids(CommandSourceStack source) {
        var level = source.getLevel();
        int version = ShatteredHomeLevels.isShatteredHome(level.dimension())
                ? ShatteredHomeProgress.of(level).layoutVersion() : ShatteredHomeLayout.VERSION;
        if (!ShatteredHomeLayout.supports(version)) return List.of();
        return ShatteredHomeLayout.tiles(version).stream().map(ShatteredHomeLayout.Tile::id).toList();
    }

    private static ShatteredHomeProgress home(CommandSourceStack source) throws CommandSyntaxException {
        var level = source.getLevel();
        if (!ShatteredHomeLevels.isShatteredHome(level.dimension())) throw HomeCommands.error(NOT_HOME);
        var progress = ShatteredHomeProgress.of(level);
        if (!ShatteredHomeLayout.supports(progress.layoutVersion())) throw HomeCommands.error(LAYOUT, progress.layoutVersion());
        if (JOBS.containsKey(level.dimension())) throw HomeCommands.error(BUSY);
        return progress;
    }

    static int all(CommandSourceStack source) throws CommandSyntaxException {
        var progress = home(source);
        var level = source.getLevel();
        var tiles = ShatteredHomeLayout.tiles(progress.layoutVersion());
        var missing = missing(progress.layoutVersion(), progress.unlockedTiles());
        var present = tiles.stream().filter(t -> !missing.contains(t)).map(t -> Component.literal(t.id())).toList();
        if (!present.isEmpty()) source.sendSuccess(() -> Component.translatable(PRESENT_LIST,
                ComponentUtils.formatList(present, Component.translatable(HomeCommands.ALLIES_SEPARATOR))), false);
        var job = new Job(level, source, missing, present.size());
        if (missing.isEmpty()) { finish(job); return 1; }
        source.sendSuccess(() -> Component.translatable(START, missing.size()), false);
        JOBS.put(level.dimension(), job);
        return missing.size();
    }

    static int one(CommandSourceStack source, String id) throws CommandSyntaxException {
        var progress = home(source);
        var tile = ShatteredHomeLayout.tiles(progress.layoutVersion()).stream().filter(t -> t.id().equals(id)).findFirst()
                .orElseThrow(() -> HomeCommands.error(UNKNOWN, id, progress.layoutVersion()));
        var level = source.getLevel();
        var job = new Job(level, source, List.of(tile), 0);
        step(job);
        return job.placed;
    }

    /** One tile through the Core's path: place, record, raise the shards, repairs, boundary. */
    static Result place(ServerLevel level, ShatteredHomeLayout.Tile tile) {
        var progress = ShatteredHomeProgress.of(level);
        if (progress.unlockedTiles().contains(tile.position())) return Result.PRESENT;
        try {
            if (!ShatteredHomeTiles.placeTile(level, tile.position(), tile.template())) return Result.FAILED;
        } catch (RuntimeException e) {
            LOGGER.error("Home tile {} threw while placing", tile.id(), e);
            return Result.FAILED;
        }
        progress.recordPlacedTile(tile.position());
        // Never lowers a count; the Core reads it back on its next refresh.
        progress.raiseShards(shardsFor(progress.unlockedTiles().size()));
        HomeSurfaceRepairs.repairBrook(level, progress);
        HomeSurfaceRepairs.repairTile(level, tile.position());
        progress.applyBorder(level);
        return Result.PLACED;
    }

    private static void step(Job job) {
        var tile = job.queue.poll();
        if (tile == null) return;
        var g = tile.position();
        switch (place(job.level, tile)) {
            case PLACED -> {
                job.placed++;
                int n = job.placed + job.failed;
                job.source.sendSuccess(() -> Component.translatable(PLACED, tile.id(), g.x(), g.z(), n, job.total), false);
            }
            case PRESENT -> {
                job.present++;
                job.source.sendSuccess(() -> Component.translatable(PRESENT, tile.id(), g.x(), g.z()), false);
            }
            case FAILED -> {
                job.failed++;
                job.source.sendFailure(Component.translatable(FAILED, tile.id(), g.x(), g.z()));
            }
        }
        if (job.queue.isEmpty()) finish(job);
    }

    private static void finish(Job job) {
        var progress = ShatteredHomeProgress.of(job.level);
        job.source.sendSuccess(() -> Component.translatable(DONE, job.placed, job.present, job.failed,
                progress.shards(), progress.phase() + 1), false);
        LOGGER.info("{} in {}: {} placed, {} present, {} failed", DONE_MARKER, job.level.dimension().location(),
                job.placed, job.present, job.failed);
    }

    /** Runs a home's queued placements now; the rig uses it for a rerun. */
    public static void drain(ServerLevel level) {
        var job = JOBS.remove(level.dimension());
        if (job != null) while (!job.queue.isEmpty()) step(job);
    }

    public static boolean busy(ServerLevel level) { return JOBS.containsKey(level.dimension()); }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (JOBS.isEmpty()) return;
        // Chunk-heavy: one tile per home per tick.
        for (var it = JOBS.values().iterator(); it.hasNext(); ) {
            var job = it.next();
            step(job);
            if (job.queue.isEmpty()) it.remove();
        }
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { JOBS.clear(); }
}
