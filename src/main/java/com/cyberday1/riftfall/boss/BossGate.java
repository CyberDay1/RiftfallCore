package com.cyberday1.riftfall.boss;

import com.cyberday1.riftfall.rift.RiftLevelState;
import com.cyberday1.riftfall.rift.RiftLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Shuts the boss room's doorway once the fight is on, one row at a time from the floor up.
 *
 * <p>
 * The room is authored with a rectangle of {@code boss_gate} markers across its entrance. Those are
 * consumed at the same moment the two pads are, and this fills the same positions with bars when
 * either trigger fires. A party that walks in is committed: the gate never reopens, so leaving is
 * killing the boss or dying to it.
 *
 * <p>
 * When the trigger fires with party members still outside, the bars wait {@link BossGatePull#delayTicks}
 * with a countdown, then the party is pulled onto the arrival pad and the gate rises behind them.
 *
 * <h2>Why the triggers are polled and not pushed</h2>
 * Two things start a fight and neither is an event the mod has. {@link RiftBosses#take} is boss
 * creation and says nothing about engagement; walking up to the doorway is not an event at all. A
 * poll every {@link #POLL_INTERVAL} ticks catches both, and cannot disagree with the threat tables the way
 * a "first threat" listener bolted onto {@code ThreatEvents} would.
 *
 * <h2>The cheap check has to come first</h2>
 * ⚠ Asking for the gate positions <em>generates the boss room</em> — {@code bossGate} consumes
 * markers, which reads blocks. So the outer gate is {@link RiftBossArena#occupied}, which reads the
 * plan and no blocks, and the marker walk only happens once somebody is standing in the room. That
 * rule and the reasoning behind it live there rather than here, because {@link BossSpawn} has to
 * obey exactly the same one.
 */
public final class BossGate {

    /**
     * How often the triggers are polled, in ticks. Wants to be short: this is the lag between walking
     * up to the doorway and the gate reacting.
     */
    static final int POLL_INTERVAL = 6;

    /**
     * How long one row takes to go in, in ticks. Deliberately not {@link #POLL_INTERVAL}, which it
     * used to share: a doorway is only a few rows tall, so at the poll rate the whole gate slammed
     * shut in about a second and read as a snap rather than a close. Raising the poll rate to slow
     * the gate down would have bought that at the cost of the trigger noticing you late, which is the
     * one thing the poll rate must not do.
     *
     * <p>
     * The close takes {@code rows x ROW_INTERVAL} ticks, so it scales with whatever the author drew:
     * the 4-row gate in {@code boss_arena.nbt} takes 40 ticks, two seconds.
     */
    static final int ROW_INTERVAL = 10;

    /**
     * How close to the boss's own pad a player has to get to shut the doorway behind them, in
     * blocks.
     *
     * <p>
     * Anchored on the pad and not on the doorway, because the doorway is the thing you walk through:
     * any range wider than about a block fired at the threshold. Half the pad-to-gate gap measured in
     * {@code boss_arena.nbt}, so the gate shuts once a player is nearer the boss than the door they
     * came in by.
     */
    static final double PAD_TRIGGER_RANGE = 11.5;

    /**
     * The fallback, measured against the doorway itself, for a room whose author drew a gate but
     * marked no pad. Shutting early beats a doorway that never shuts at all.
     */
    static final double GATE_TRIGGER_RANGE = 8.0;

    /**
     * What the gate is made of, with a stand-in for a pack that does not have it.
     *
     * <p>
     * Quark ships in this pack and gold bars are the intended look, but Riftfall is glue and a
     * missing block would place air — an open gate that reports itself shut, which is worse than the
     * wrong metal. Iron bars are vanilla and cannot be absent.
     */
    private static final ResourceLocation BARS = ResourceLocation.parse("quark:gold_bars");

    /** Gates that have finished rising, so a fight already sealed is never re-examined. */
    private static final Set<ServerLevel> SHUT = new HashSet<>();

    /**
     * The levels that reached {@link #SHUT} with no doorway to shut. Held apart so {@link #sealed}
     * can tell a door that closed behind a party from a room that never had one.
     */
    private static final Set<ServerLevel> GATELESS = new HashSet<>();

    /** Gates part way up, keyed by the level they are in. */
    private static final Map<ServerLevel, Rising> RISING = new HashMap<>();

    /** Gates tripped and waiting for the party, keyed by level. Not saved: a restart re-trips. */
    private static final Map<ServerLevel, Waiting> WAITING = new HashMap<>();

    /** A tripped gate's countdown: when it ends, and the last whole second shown. */
    private static final class Waiting {
        private final List<BlockPos> positions;
        private final int endsAt;
        private int shown;

        private Waiting(List<BlockPos> positions, int endsAt, int shown) {
            this.positions = positions;
            this.endsAt = endsAt;
            this.shown = shown;
        }
    }

    /**
     * A doorway mid-close: every block of it lowest row first, and how many have been placed.
     * Positions arrive in that order from the marker walk, which scans y outermost.
     */
    private static final class Rising {
        private final List<BlockPos> positions;
        private int placed;

        private Rising(List<BlockPos> positions) {
            this.positions = positions;
        }
    }

    private BossGate() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BossGate::tick);
        NeoForge.EVENT_BUS.addListener(BossGate::forgetLevel);
        NeoForge.EVENT_BUS.addListener(BossGate::remember);
        BossFallen.register();
    }

    /**
     * Reads back a gate that shut before a restart (G9). The bars are blocks and survive on their
     * own; this is the record that they are a shut door, which {@link #sealed} and the rejoin gather
     * read.
     */
    private static void remember(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && RiftLevels.isRiftWorld(level)) {
            RiftLevelState state = RiftLevelState.peek(level);
            if (state != null && state.gateShut()) {
                SHUT.add(level);
                if (state.gateless()) {
                    GATELESS.add(level);
                }
            }
        }
    }

    /**
     * Whether a doorway has risen and is standing in this rift.
     *
     * <p>
     * The state the ruling on design question Q-6 selects — a run is in the boss room once the
     * boss is engaged and the door shuts — rather than {@link RiftBosses#engagedIn}, which goes false
     * the instant the boss dies. Read-only: nothing outside this class opens or shuts a gate.
     */
    public static boolean sealed(ServerLevel level) {
        return sealed(SHUT, GATELESS, level);
    }

    /** Whether the boss fight is on: the gate is rising or shut, or the room had none to shut. */
    public static boolean fightOn(ServerLevel level) {
        return RISING.containsKey(level) || SHUT.contains(level);
    }

    /** Whether this rift's gate was tripped and is counting down for the party. For the probe. */
    public static boolean waiting(ServerLevel level) {
        return WAITING.containsKey(level);
    }

    /** The same question over plain sets, so the asymmetry can be asserted without a server. */
    static <T> boolean sealed(Set<T> shut, Set<T> gateless, T level) {
        return shut.contains(level) && !gateless.contains(level);
    }

    /**
     * Records that a level's doorway has shut, and whether it ever had one.
     *
     * <p>
     * ⚠ For tests, and nothing in play calls it: {@link #advance} is still the only thing that shuts
     * a real door. The state {@link #sealed} answers for is reached by a gate rising a row at a time
     * in a generated arena with somebody walking up to the boss's pad, which no headless run can
     * arrange &mdash; so without this every feature that reads {@code sealed} is untestable.
     */
    public static void recordShut(ServerLevel level, boolean gateless) {
        SHUT.add(level);
        if (gateless) {
            GATELESS.add(level);
        }
    }

    /** Forgets a level, as unloading one does. Exposed with {@link #recordShut} and for its reason. */
    public static void forget(ServerLevel level) {
        RISING.keySet().remove(level);
        WAITING.keySet().remove(level);
        SHUT.remove(level);
        GATELESS.remove(level);
    }

    /**
     * The block a gate is built from: gold bars where they exist, iron bars where they do not.
     *
     * <p>
     * Resolved per call rather than cached in a static, because a static initialiser here would run
     * before the registries are populated and freeze the fallback in.
     */
    static BlockState bars() {
        Block block = BuiltInRegistries.BLOCK.getOptional(BARS).orElse(Blocks.IRON_BARS);
        return block.defaultBlockState();
    }

    /**
     * Which blocks of {@code positions} go in on this step: the whole of the lowest row not yet
     * placed. A row at a time rather than a block at a time so the gate rises evenly across the
     * doorway instead of filling one column and starting the next.
     */
    static int rowEnd(List<BlockPos> positions, int from) {
        int y = positions.get(from).getY();
        int end = from;
        while (end < positions.size() && positions.get(end).getY() == y) {
            end++;
        }
        return end;
    }

    private static void tick(ServerTickEvent.Post event) {
        int now = event.getServer().getTickCount();
        boolean polling = now % POLL_INTERVAL == 0;
        boolean advancing = now % ROW_INTERVAL == 0;
        // Both rates are slow, so most ticks are neither and never look at a level at all.
        if (!polling && !advancing && WAITING.isEmpty()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Rising rising = RISING.get(level);
            Waiting waiting = WAITING.get(level);
            if (rising != null) {
                if (advancing) {
                    advance(level, rising);
                }
            } else if (waiting != null) {
                count(level, waiting, now);
            } else if (polling && !SHUT.contains(level) && !level.players().isEmpty()) {
                consider(level, now);
            }
        }
    }

    /** Shows the countdown each new second; at zero pulls the party in and starts the bars. */
    private static void count(ServerLevel level, Waiting waiting, int now) {
        int left = waiting.endsAt - now;
        if (left > 0) {
            int seconds = BossGatePull.secondsLeft(left);
            if (seconds != waiting.shown) {
                waiting.shown = seconds;
                BossGatePull.tell(level, seconds);
            }
            return;
        }
        WAITING.remove(level);
        close(level, waiting.positions);
    }

    /** Pulls the party in, then starts the bars. */
    private static void close(ServerLevel level, List<BlockPos> gate) {
        BossGatePull.pull(level, gate);
        RISING.put(level, new Rising(new ArrayList<>(gate)));
    }

    /**
     * Starts the gate if either trigger has fired. Returns without touching a block in the common
     * case, which is every rift nobody has reached the boss room of yet.
     */
    private static void consider(ServerLevel level, int now) {
        if (!RiftBossArena.occupied(level)) {
            return;
        }
        // Safe now: a player is standing in the room, so its chunks are loaded and the marker walk
        // reads rather than generates. This also consumes the markers, which is what stops them
        // shipping as scenery.
        List<BlockPos> gate = RiftBossArena.gate(level);
        if (gate.isEmpty()) {
            // No gate authored: nothing to shut, and nothing to reconsider on later ticks.
            SHUT.add(level);
            GATELESS.add(level);
            RiftLevelState.of(level).gateShut(true);
            return;
        }
        // The pad is only asked for once proximity is the question, and by then the walk above has
        // cached it. It stays the second half of the condition so that a boss engaged from range
        // still seals the room around a party that never walked in.
        if (RiftBosses.engagedIn(level) || anyPlayerNear(level, RiftBossArena.pad(level), gate)) {
            int delay = BossGatePull.delayTicks();
            // Nobody left outside (a solo player, a party that walked in together): no wait.
            if (delay > 0 && !BossGatePull.outside(level, gate).isEmpty()) {
                int seconds = BossGatePull.secondsLeft(delay);
                WAITING.put(level, new Waiting(List.copyOf(gate), now + delay, seconds));
                BossGatePull.tell(level, seconds);
            } else {
                close(level, gate);
            }
        }
    }

    /** Puts in the next row, and retires the gate once the last one is up. */
    private static void advance(ServerLevel level, Rising rising) {
        BlockState bars = bars();
        int end = rowEnd(rising.positions, rising.placed);
        for (int index = rising.placed; index < end; index++) {
            level.setBlockAndUpdate(rising.positions.get(index), bars);
        }
        rising.placed = end;
        if (rising.placed >= rising.positions.size()) {
            RISING.remove(level);
            SHUT.add(level);
            RiftLevelState.of(level).gateShut(false);
            // The doorway is shut, which is exactly when the things a doorway does not stop have to
            // go: a portal entity crosses on an overlap and never touches the bars.
            ArenaSeal.sweep(level);
        }
    }

    /**
     * Whether somebody standing at these coordinates has come far enough in to start the gate.
     *
     * <p>
     * Pure and handed both anchors, so the choice between them can be asserted against the arena's
     * own measured markers without a level or a player.
     */
    static boolean inTriggerRange(double x, double y, double z, Optional<BlockPos> pad,
            List<BlockPos> gate) {
        if (pad.isEmpty()) return within(x, y, z, gate, GATE_TRIGGER_RANGE);
        if (within(x, y, z, List.of(pad.get()), PAD_TRIGGER_RANGE)) return true;
        if (gate.isEmpty()) return false;
        int minX = gate.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int maxX = gate.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minZ = gate.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxZ = gate.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        int minY = gate.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        int maxY = gate.stream().mapToInt(BlockPos::getY).max().orElseThrow();
        if (y < minY - 1 || y > maxY + 2) return false;
        // The boss pad identifies the inside of the doorway after any room rotation/mirror.
        // Leave enough space for the player's whole body to clear the rising bars.
        boolean lateral = maxX - minX >= maxZ - minZ
                ? x >= minX - 1 && x <= maxX + 2
                : z >= minZ - 1 && z <= maxZ + 2;
        return lateral && BossGatePull.depth(x, z, pad.get(), gate) >= BossGatePull.CLEAR_DEPTH;
    }

    private static boolean within(double x, double y, double z, List<BlockPos> anchors, double range) {
        double squared = range * range;
        for (BlockPos pos : anchors) {
            double dx = x - (pos.getX() + 0.5);
            double dy = y - (pos.getY() + 0.5);
            double dz = z - (pos.getZ() + 0.5);
            if (dx * dx + dy * dy + dz * dz <= squared) {
                return true;
            }
        }
        return false;
    }

    private static boolean anyPlayerNear(ServerLevel level, Optional<BlockPos> pad, List<BlockPos> gate) {
        var generator = level.getChunkSource().getGenerator();
        var bounds = generator instanceof com.cyberday1.riftfall.worldgen.level.RiftChunkGenerator rift
                ? rift.bossRoomBounds(level) : null;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()
                    && bounds != null && bounds.isInside(player.blockPosition())
                    && inTriggerRange(player.getX(), player.getY(), player.getZ(), pad, gate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Forgets a rift that is coming down, the same way {@link RiftBosses} does. Both maps are keyed by
     * the level object, so without this a closed rift would be held alive by a gate that never rose.
     */
    private static void forgetLevel(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            forget(level);
        }
    }
}
