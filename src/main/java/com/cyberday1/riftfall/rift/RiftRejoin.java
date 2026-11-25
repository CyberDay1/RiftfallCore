package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.boss.BossGate;
import com.cyberday1.riftfall.boss.RiftBossArena;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Logging back into a rift: the party gathers you if the boss door has shut behind them, and nothing
 * can hurt you until you have had your bearings.
 *
 * <p>
 * Both halves are one method because the order between them is load-bearing: the window is anchored
 * on where the player is standing, so it has to be opened after the teleport and never before.
 *
 * <p>
 * The window is a cancelled damage event and never {@code setInvulnerable}, which is saved to the
 * player's file: losing this state means a player can be hurt, which is the safe direction.
 */
public final class RiftRejoin {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * How long the window may last, in ticks. Five seconds, and a ceiling rather than a duration:
     * any of the other three conditions ends it sooner.
     */
    static final int WINDOW_TICKS = 100;

    /**
     * How far from the login position a player may go before the window ends, in blocks. The ruling's
     * "more than half a block or so", measured from where they logged in rather than per tick, which
     * is the reading chosen knowing a knockback carries you out of it.
     */
    static final double MOVE_LIMIT = 0.5D;

    /**
     * Spell casts worth ending the window for, named by class so that recognising one costs no
     * dependency on the mod firing it. Iron's is the only cast in this pack that reaches the
     * NeoForge bus at all; Build A Spell publishes no event and Spell Engine publishes its own.
     */
    static final List<String> CAST_EVENTS =
            List.of("io.redspace.ironsspellbooks.api.events.SpellPreCastEvent");

    private static final Map<UUID, Window> OPEN = new HashMap<>();

    /** What each name in {@link #CAST_EVENTS} resolved to at registration; see {@link #castWiring}. */
    private static final List<String> WIRING = new ArrayList<>();

    private RiftRejoin() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RiftRejoin::onLogin);
        NeoForge.EVENT_BUS.addListener(RiftRejoin::onLogout);
        // HIGHEST so a hit that is about to be withdrawn is withdrawn before anything acts on it.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RiftRejoin::damage);
        NeoForge.EVENT_BUS.addListener(RiftRejoin::attacked);
        NeoForge.EVENT_BUS.addListener(RiftRejoin::loosed);
        NeoForge.EVENT_BUS.addListener(RiftRejoin::sweep);
        NeoForge.EVENT_BUS.addListener(RiftRejoin::forgetAll);
        listenForCasts();
    }

    /** One open window: the rift it was opened in, where the player stood, and when. */
    record Window(ResourceKey<Level> rift, Vec3 origin, long opened) {
    }

    // ---------------------------------------------------------------------------
    // Logging in
    // ---------------------------------------------------------------------------

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            rejoin(player, RiftRuns.in(player.level().dimension()));
        }
    }

    /**
     * Gathers a returning entrant to their party and opens their window, in that order.
     *
     * <p>
     * ⛔ The order is the slice. Opening first would leave the gather's own teleport to break the
     * window on the next tick, dropping a player into a live boss fight with the protection R-18
     * exists to give them already spent.
     *
     * <p>
     * The roster is the gate, as it is everywhere else here: somebody an operator teleported in
     * never went through a door and nothing here is theirs.
     */
    static void rejoin(ServerPlayer player, @Nullable RiftRun run) {
        if (run == null || run.entry(player.getUUID()) == null) {
            return;
        }
        gather(player, run);
        open(player);
    }

    /**
     * Moves a returning player to the arena's entry marker if the boss door has shut behind their
     * party, and answers whether it happened.
     *
     * <p>
     * ⛔ {@link BossGate#sealed} and never {@code RiftBosses#engagedIn}, which goes false the instant
     * the boss dies &mdash; a party that cleared the arena and has not walked out yet would stop
     * counting, and the member relogging into that window is exactly who this exists for.
     *
     * <p>
     * Not a dimension change, so it takes out no permit and the boundary never sees it:
     * {@code ServerPlayer#teleportTo} only calls {@code changeDimension} when the level differs.
     */
    static boolean gather(ServerPlayer player, RiftRun run) {
        ServerLevel rift = player.serverLevel();
        if (!BossGate.sealed(rift)) {
            return false;
        }
        Optional<BlockPos> entry = RiftBossArena.entry(rift);
        if (entry.isEmpty()) {
            // A room with a door and no entry marker gathers nobody: the pad is the one block a
            // party must not be put on, so there is deliberately no fallback to fall back to.
            return false;
        }
        BlockPos at = entry.get();
        player.teleportTo(rift, at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        warnIfCleared(player, run, rift);
        return true;
    }

    /**
     * The clear invariant's tripwire: a run reading CLEARED with no tear standing at its arena's
     * exit marker.
     *
     * <p>
     * ⛔ Kept rather than deleted when {@code RiftRuns#cleared} was made to hold that invariant,
     * because it is still reachable for a cause the invariant does not cover: a tear that <em>did</em>
     * open can afterwards be broken, by a player in creative or by anything in the pack that breaks
     * blocks. So a line from here means that, or a regression in {@code RiftRuns#cleared}.
     *
     * <p>
     * Design question Q-7 &mdash; whether the gather should refuse this state &mdash; is closed as
     * moot: the ordering it was about is gone.
     */
    private static void warnIfCleared(ServerPlayer player, RiftRun run, ServerLevel rift) {
        if (run.verdict() != RiftRun.Verdict.CLEARED) {
            return;
        }
        BlockPos exit = RiftBossArena.exit(rift).orElse(null);
        if (exit == null || !RiftTearBlock.standsAt(rift, exit)) {
            LOGGER.warn("Gathered {} into a cleared arena in {} with no way home standing at {}; "
                            + "either the tear was broken after it opened, or the clear invariant "
                            + "in RiftRuns#cleared has regressed",
                    player.getName().getString(), rift.dimension().location(), exit);
        }
    }

    /**
     * Opens the window over a player, anchored where they are standing <em>now</em>. Whether they
     * should have one is {@link #rejoin}'s question; this only does it.
     */
    static void open(ServerPlayer player) {
        OPEN.put(player.getUUID(), new Window(player.level().dimension(), player.position(),
                player.server.getTickCount()));
    }

    // ---------------------------------------------------------------------------
    // Ending it
    // ---------------------------------------------------------------------------

    /** Whether five seconds have gone by. */
    static boolean elapsed(long opened, long now) {
        return now - opened >= WINDOW_TICKS || now < opened;
    }

    /** Whether the player has left the ball they logged in at the centre of. */
    static boolean leftTheBall(Vec3 origin, Vec3 now) {
        return now.distanceToSqr(origin) > MOVE_LIMIT * MOVE_LIMIT;
    }

    /**
     * Whether this window is over. Three of the four conditions; the fourth is acting, which removes
     * the window outright rather than being read back here.
     */
    static boolean ends(Window window, ResourceKey<Level> where, Vec3 at, long now) {
        return !window.rift().equals(where)
                || elapsed(window.opened(), now)
                || leftTheBall(window.origin(), at);
    }

    /**
     * Whether the window still stands over this player, ending it if this is the moment it ends.
     *
     * <p>
     * Asked from both the sweep and the damage hook, so that "the window is over" is decided in one
     * place however the question arrives.
     */
    static boolean holds(UUID player, ResourceKey<Level> where, Vec3 at, long now) {
        Window window = OPEN.get(player);
        if (window == null) {
            return false;
        }
        if (ends(window, where, at, now)) {
            OPEN.remove(player);
            return false;
        }
        return true;
    }

    /**
     * Ends the window because the player did something offensive. Attacking, loosing an arrow,
     * casting a spell and landing damage all arrive here.
     */
    static void acted(@Nullable Player player) {
        if (player != null) {
            OPEN.remove(player.getUUID());
        }
    }

    /** How many windows are open, which nothing but a test has any business asking. */
    static int openCount() {
        return OPEN.size();
    }

    static void forget() {
        OPEN.clear();
    }

    // ---------------------------------------------------------------------------
    // The listeners
    // ---------------------------------------------------------------------------

    /**
     * Refuses a hit at a protected player, and ends the window of whoever landed one.
     *
     * <p>
     * Both halves here because they are the same event seen from the two ends. Dealing damage is the
     * broadest of the four break conditions and the only one that reaches a spell mod with no event
     * of its own: the source's entity is the cause rather than the arrow, so a bow, a spell and a
     * summon that credits its owner all read as the player.
     */
    private static void damage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer dealer) {
            acted(dealer);
        }
        if (event.getEntity() instanceof ServerPlayer player
                && holds(player.getUUID(), player.level().dimension(), player.position(),
                        player.server.getTickCount())) {
            event.setCanceled(true);
        }
    }

    private static void attacked(AttackEntityEvent event) {
        acted(event.getEntity());
    }

    private static void loosed(ArrowLooseEvent event) {
        acted(event.getEntity());
    }

    /**
     * Ends the windows that are over, so the map empties on its own rather than waiting for a hit.
     * Nothing else reads it, so this changes no behaviour; it is what makes the state honest.
     */
    private static void sweep(ServerTickEvent.Post event) {
        if (OPEN.isEmpty()) {
            return;
        }
        long now = event.getServer().getTickCount();
        for (UUID id : new ArrayList<>(OPEN.keySet())) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null) {
                OPEN.remove(id);
            } else {
                holds(id, player.level().dimension(), player.position(), now);
            }
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        acted(event.getEntity());
    }

    private static void forgetAll(ServerStoppingEvent event) {
        forget();
    }

    // ---------------------------------------------------------------------------
    // Spell casts, without a dependency on the mod casting them
    // ---------------------------------------------------------------------------

    /**
     * Whether a named event class is one a caster can be read off. Iron's {@code SpellPreCastEvent}
     * extends {@code PlayerEvent}, so the listener needs no reflection on the mod's own API &mdash;
     * and a rename upstream degrades to "this condition does not fire" rather than to a class cast
     * on the bus.
     */
    static boolean usable(@Nullable Class<?> type) {
        return type != null && PlayerEvent.class.isAssignableFrom(type);
    }

    /** The named class if this pack has it, or null. */
    @Nullable
    static Class<?> resolve(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void listenForCasts() {
        WIRING.clear();
        for (String name : CAST_EVENTS) {
            Class<?> type = resolve(name);
            if (!usable(type)) {
                WIRING.add(name + " -> " + (type == null ? "absent" : "not a PlayerEvent"));
                continue;
            }
            Consumer<PlayerEvent> ended = cast -> acted(cast.getEntity());
            NeoForge.EVENT_BUS.addListener((Class<Event>) type, (Consumer) ended);
            WIRING.add(name + " -> listening");
        }
    }

    /**
     * What {@link #listenForCasts} actually did on this pack, one line per name.
     *
     * <p>
     * For the probe, and it is the only way to ask: the class is absent from the dev classpath, so
     * a typo in {@link #CAST_EVENTS} cannot fail a unit test and would otherwise fail nothing at
     * all &mdash; the condition would simply never fire.
     */
    public static List<String> castWiring() {
        return List.copyOf(WIRING);
    }
}
