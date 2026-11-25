package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallConfig;
import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.progression.XpAwards;
import com.cyberday1.riftfall.skill.ManaRules;
import com.cyberday1.riftfall.skill.RangedCharge;
import com.cyberday1.riftfall.skill.RiftMana;
import com.cyberday1.riftfall.worldgen.layout.RiftPlan;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every run in progress, and the one rule that ends one: a run is over when its rift has no living
 * player left in it.
 *
 * <p>
 * A wipe and everybody walking out therefore end a run the same way, and no way out needs an ending of
 * its own. They are not the same outcome: the ways out record who got clear, and {@link RiftRun#verdict}
 * reads that back at the end. Ending a run douses the frame and closes the level together, so a dungeon
 * can never be left behind a dark frame nor a lit frame left pointing at nothing.
 *
 * <p>
 * ⭐ The exception is a rift whose last holders logged out: that run pauses instead (G2), and a run
 * still going when the server stops is written down and brought back at the next start (G9). See
 * {@link RiftRun#advance} and {@link RiftRunStore}.
 *
 * <p>
 * A rift from {@code /riftfall rift open} has no run and is not watched here at all.
 */
public final class RiftRuns {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * How often the return debts of online players are checked, in ticks. Nothing depends on it being
     * prompt: it is the backstop for a session that ends without a logout, which is to say a crash.
     */
    private static final int SWEEP_INTERVAL = 100;

    /** What the run limit is stated in, against what it is counted in. */
    private static final int TICKS_PER_MINUTE = 20 * 60;

    /** What a party is told when their arena had no way home to open; see {@link #cleared}. */
    private static final String NO_WAY_HOME = "message." + RiftfallCore.MODID + ".run_no_way_home";

    private static final Map<ResourceKey<Level>, RiftRun> BY_RIFT = new HashMap<>();
    private static final Map<RiftRun.Frame, RiftRun> BY_FRAME = new HashMap<>();

    private RiftRuns() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RiftRuns::tick);
        // LOW for RiftDeaths' reason and it is the same reason: a mod that cancels this event is
        // keeping its subject alive, and a cancelled event never reaches a later listener. Counting
        // at NORMAL would pay for kills that were then undone.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, RiftRuns::recordDeath);
        NeoForge.EVENT_BUS.addListener(RiftRuns::returnStranded);
        NeoForge.EVENT_BUS.addListener(RiftRuns::respawned);
        NeoForge.EVENT_BUS.addListener(RiftRuns::forgiveOnLogout);
        NeoForge.EVENT_BUS.addListener(RiftRuns::pauseOnLogout);
        NeoForge.EVENT_BUS.addListener(RiftRuns::resumeOnLogin);
        NeoForge.EVENT_BUS.addListener(RiftRuns::restore);
        NeoForge.EVENT_BUS.addListener(RiftRuns::forgetAll);
    }

    /**
     * The run a frame is currently standing open onto, or null if it leads nowhere.
     *
     * <p>
     * A run whose level has gone, or is going at the end of this tick, answers null too: the caller is
     * asking whether walking in reaches a dungeon. The run is left in the map for {@link #tick} to end
     * properly rather than being torn down from inside a block's shape scan.
     */
    @Nullable
    public static RiftRun at(ServerLevel level, BlockPos corner) {
        RiftRun run = BY_FRAME.get(new RiftRun.Frame(level.dimension(), corner));
        if (run == null) {
            return null;
        }
        return level.getServer().getLevel(run.rift()) != null && !RiftLevels.isClosing(run.rift()) ? run : null;
    }

    /** The run a rift belongs to, or null for a level nobody entered through a frame. */
    @Nullable
    public static RiftRun in(ResourceKey<Level> rift) {
        return BY_RIFT.get(rift);
    }

    /**
     * Starts a run: the frame has just made itself a dungeon and is now the way into it.
     *
     * <p>
     * The way back out is opened here, before the first player is near it, so that walking in and
     * being able to walk out again are the same event and cannot come apart.
     *
     * <p>
     * A tear, standing in the middle of the root room rather than a copper frame raised beside it.
     * Players land in a ring around it -- {@link RiftLevels#arrival} -- so the first thing anybody
     * sees on the way in is the way out.
     */
    public static RiftRun begin(ServerLevel level, BlockPos corner, ServerLevel rift) {
        RiftRun.Frame frame = new RiftRun.Frame(level.dimension(), corner);
        RiftRun run = new RiftRun(rift.dimension(), frame, RiftLevels.tier(rift));
        run.theme(themeOf(rift));
        BY_RIFT.put(rift.dimension(), run);
        BY_FRAME.put(frame, run);
        if (!RiftTearBlock.open(rift, RiftLevels.wayOut(rift), Direction.Axis.X, RiftPortalKind.BAIL,
                RiftPortalBlock.intensityFor(RiftLevels.tier(rift)), true)) {
            LOGGER.error("Could not open a way out of {}. The only ways home are an anchor and dying.",
                    rift.dimension().location());
        }
        return run;
    }

    /**
     * Puts a player on a run's roster on their way through, and records where they came from and what
     * they brought. Four records, one moment: the roster (the lockout, dies with the run), the debt
     * (the rescue, outlives run and server), the counted kit, and the mark on the items themselves.
     *
     * <p>
     * Marking runs first, so the kit is a picture of the inventory as it will be past the door.
     */
    public static void entering(RiftRun run, ServerPlayer player) {
        RiftEntry entry = RiftEntry.of(player);
        RiftLoot.stamp(player, run.rift().location());
        run.entering(player.getUUID(), entry, carrying(player.getInventory()));
        RiftReturns.of(player.server).owe(player.getUUID(), entry);
        // M-4: a fresh entry fills the bar. A rejoin never comes through here.
        RiftMana.refill(player.getUUID(), ManaRules.Moment.RIFT_ENTRY);
        RangedCharge.refill(player.getUUID(), ManaRules.Moment.RIFT_ENTRY);
        com.cyberday1.riftfall.story.StoryBridge.entered(player, run);
    }

    /**
     * Everything in an inventory, across all three of its compartments: {@code Inventory} numbers the
     * 36 carried slots, then the four pieces of armour, then the off hand, and what somebody walked in
     * wearing is most of what they walked in with.
     *
     * <p>
     * The stacks are live. {@link RiftRun#entering} copies them, and that is the one place a copy is
     * made.
     */
    private static List<ItemStack> carrying(Inventory inventory) {
        List<ItemStack> carried = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                carried.add(stack);
            }
        }
        return carried;
    }

    /**
     * The way home for a player standing in a rift, or null if there is not one for them.
     *
     * <p>
     * Both ways out ask for this, so they cannot send anybody to different places. It is the entrant's
     * own doorstep rather than the run's: a party that met at one frame arrived from four directions.
     * Null for anybody the roster does not know, so a mob that followed the party in stays put.
     */
    @Nullable
    public static DimensionTransition leaving(ServerPlayer player) {
        RiftRun run = in(player.level().dimension());
        RiftEntry entry = run == null ? null : run.entry(player.getUUID());
        if (entry == null) {
            return null;
        }
        MinecraftServer server = player.server;
        RiftEntry saved = RiftReturns.of(server).owed(player.getUUID());
        if (saved != null) entry = saved;

        // The debt is forgiven on arrival rather than here: changeDimension can still be refused by
        // another mod's travel event, and a debt dropped for a trip that did not happen leaves a
        // player who then crashes with nothing to be rescued by. The escape is recorded there for the
        // same reason, and it is recorded here at all because both ways out come through this method.
        UUID owner = player.getUUID();
        // The way out is ours, so it says so: the boundary refuses everything it has no permit for,
        // and both ways home and the anchor are all this one method.
        try {
            return RiftEvacuation.transition(server, player, entry,
                DimensionTransition.PLAY_PORTAL_SOUND
                        .then(DimensionTransition.PLACE_PORTAL_TICKET)
                        .then(arrived -> {
                            RiftEvacuation.completed(server, (ServerPlayer) arrived);
                            run.escaped(owner);
                            settleOnTheWayOut(server, run, owner);
                        }));
        } catch (IllegalStateException unavailable) {
            LOGGER.warn("No return destination available for {}; leaving the exit available", owner);
            return null;
        }
    }

    /**
     * Pays somebody walking out of a beaten run there and then, rather than at the run's ending (C-3).
     *
     * <p>
     * Their payout is already final: the verdict cannot leave CLEARED and they are out. Paying it at
     * the door means it never waits on a partner who logged out inside, a pause that has no limit,
     * and a stop cannot fall between the clear and the money. The ending pays only what is left.
     * Only a beaten run: a bail before the boss stays open, because the party may still clear it.
     */
    static void settleOnTheWayOut(MinecraftServer server, RiftRun run, UUID owner) {
        if (run.verdict() != RiftRun.Verdict.CLEARED) {
            return;
        }
        long owed = earnedBy(run, RiftRun.Verdict.CLEARED, owner, true, false) - run.settled(owner);
        if (owed > 0L) {
            XpAwards.award(server, owner, owed);
            run.settle(owner, owed);
            LOGGER.info("{} XP paid on the way out of {}", owed, run.rift().location());
            // Their numbers are final here too, so the recap is written with the payment.
            RiftRecaps.settled(server, run, RiftRun.Verdict.CLEARED, owner, true, false, owed);
        }
    }

    /**
     * Records that a rift was beaten, and holds the invariant that goes with it: a run is CLEARED
     * only while a way home stands in its arena, or it is CLEARED and already winding up.
     *
     * <p>
     * ⭐ {@code wayHome} is where the caller says it opened one, and it is <em>read back</em> rather
     * than believed. A position can be checked and a boolean cannot, so a caller that grew a bug
     * cannot assert its way past this; the worst it can do is name a block with no tear on it, which
     * reads exactly like having opened none.
     *
     * <p>
     * ⛔ The flag is written either way, and refusing it would be the trap. A party that killed the
     * boss and got no tear is sealed in &mdash; {@code BossGate} never reopens &mdash; so withholding
     * the clear would leave them there uncredited until the clock killed them for it, which is worse
     * than the state this replaced. Instead they are paid and the run is wound up.
     *
     * <p>
     * A rift with no run behind it (anything from {@code /riftfall rift open}, and the probes) is a
     * no-op.
     */
    /** The rendered theme, or null for a rift not on Riftfall's generator. */
    @Nullable
    public static net.minecraft.resources.ResourceLocation theme(ServerLevel rift) {
        return themeOf(rift);
    }

    /** The rendered theme, or null for a rift not on Riftfall's generator. */
    @Nullable
    static net.minecraft.resources.ResourceLocation themeOf(ServerLevel rift) {
        try {
            return rift.getChunkSource().getGenerator()
                    instanceof com.cyberday1.riftfall.worldgen.level.RiftChunkGenerator generator
                    ? generator.theme(rift).location() : null;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** Credits a tracked boss's death to the players the boss loot rolls for. No run: no-op. */
    public static void bossKilled(ServerLevel rift, net.minecraft.world.entity.LivingEntity boss) {
        RiftRun run = BY_RIFT.get(rift.dimension());
        if (run != null) {
            run.bossKilled(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()),
                    com.cyberday1.riftfall.loot.RiftBossLoot.participants(rift).stream().map(ServerPlayer::getUUID).toList());
        }
    }

    public static void cleared(ServerLevel rift, @Nullable BlockPos wayHome) {
        RiftRun run = BY_RIFT.get(rift.dimension());
        if (run == null) {
            return;
        }
        run.cleared();
        if (wayHome != null && RiftTearBlock.standsAt(rift, wayHome)) {
            return;
        }
        LOGGER.error("The boss died in {} and no way home stands {}; paying the clear and winding "
                        + "the run up rather than sealing the party in", rift.dimension().location(),
                wayHome == null ? "(the arena carries no usable exit_portal)" : "at " + wayHome);
        // The same loop and guard as expel, deliberately: "the entrants standing in here" has one
        // spelling. The escape is what earnsTheClear reads, so without it they would bank their
        // kills and lose the thousand with nothing anywhere saying so.
        for (ServerPlayer player : List.copyOf(rift.players())) {
            if (run.entry(player.getUUID()) != null && player.isAlive()) {
                run.escaped(player.getUUID());
                player.sendSystemMessage(Component.translatable(NO_WAY_HOME));
            }
        }
        run.finish();
    }

    /**
     * Told when a portal block goes, so a run does not keep hold of a frame that is no longer there.
     * Takes the block's own position rather than a corner, exactly as {@link RiftPortals#forget} does:
     * the map is keyed on the bottom-left corner, which always pops when a frame collapses, and every
     * other block of the frame is simply a lookup miss.
     */
    public static void frameBroken(ServerLevel level, BlockPos pos) {
        RiftRun run = BY_FRAME.remove(new RiftRun.Frame(level.dimension(), pos));
        if (run != null) {
            run.frameBroken();
        }
    }

    private static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!BY_RIFT.isEmpty()) {
            // Read once for the whole sweep: every run on this server is under the same clock.
            int limit = limitTicks(RiftfallConfig.RIFT_TIME_LIMIT_MINUTES.get());
            // Collected first: ending a run edits both maps, and one of them is being walked.
            List<RiftRun> over = new ArrayList<>();
            for (RiftRun run : BY_RIFT.values()) {
                ServerLevel rift = server.getLevel(run.rift());
                if (rift == null || run.over(holders(rift), limit)) {
                    over.add(run);
                    continue;
                }
                considerTheLock(rift, run);
                RiftClock.resync(rift, run, limit);
            }
            for (RiftRun run : over) {
                end(server, run);
            }
        }
        if (server.getTickCount() % SWEEP_INTERVAL == 0) {
            sweepReturns(server);
        }
    }

    /**
     * The run limit in ticks, converted once from the minutes it is stated in. Zero, and anything
     * below it, is no limit rather than an instant one: a clock nobody can ever beat would delete
     * every rift on the tick it was built.
     */
    static int limitTicks(int minutes) {
        return minutes <= 0 ? 0 : minutes * TICKS_PER_MINUTE;
    }

    /**
     * Closes a run's roster if this is the tick somebody on it went deeper than the entrance.
     *
     * <p>
     * Ruled 2026-09-21: <em>"left the portal room should be what locks the roster."</em> Asked
     * only while the run is open, so the poll stops for good the moment it fires and a locked run
     * &mdash; which is the steady state &mdash; costs nothing.
     */
    static void considerTheLock(ServerLevel rift, RiftRun run) {
        if (!run.locked() && leftThePortalRoom(rift, run)) {
            run.lock();
        }
    }

    /**
     * Whether anybody on this run's roster has gone deeper than the room they arrived in, which is
     * what closes the roster behind them.
     *
     * <p>
     * Only asked while a run is unlocked, so the cost is bounded to the opening of a run. No block
     * is read: {@link RoomCaption#roomAt} answers off the plan, and reading a block in a chunk
     * nobody has built would build it on this thread.
     *
     * <p>
     * The roster is the gate on who may trigger it, as it is everywhere else here: an operator who
     * teleported into somebody else's dungeon does not close the door behind them.
     */
    private static boolean leftThePortalRoom(ServerLevel rift, RiftRun run) {
        for (ServerPlayer player : rift.players()) {
            if (run.entry(player.getUUID()) != null
                    && pastThePortalRoom(RoomCaption.roomAt(rift, player.blockPosition()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The test itself: standing inside an authored room the layout placed deeper than the entrance.
     *
     * <p>
     * ⛔ Positive evidence, not the absence of it. {@code roomAt} answers null in the seam between
     * two rooms, and a lock is irreversible and takes away a player's ability to join their party,
     * so a doorway straddler is "not yet left" rather than "left". {@code RiftPlan#entrance} is
     * defined as the depth-zero room, so "deeper than zero" and "not the portal room" are the same
     * set by construction.
     */
    static boolean pastThePortalRoom(@Nullable RiftPlan.PlacedRoom room) {
        return room != null && room.depth() > 0;
    }

    /**
     * How many players in this rift are holding it open. Alive is the only test, so spectators and
     * creative players count. Dead counts as gone at once: a corpse stays in {@code level.players()}
     * until respawn is clicked, and waiting for that would let a wiped party hold their own run open
     * by not clicking.
     *
     * <p>
     * ⚠ The downed state breaks this. Once a death leaves a spectating ghost in the level, this count
     * says a wiped party is still holding their dungeon open, for ever. The ghost-is-not-a-holder
     * distinction has to arrive in the same change that makes ghosts.
     */
    static Set<UUID> holders(ServerLevel rift) {
        Set<UUID> holders = new HashSet<>();
        for (ServerPlayer player : rift.players()) {
            if (player.isAlive()) {
                holders.add(player.getUUID());
            }
        }
        return holders;
    }

    /**
     * Ends a run: the frame goes out, the dungeon comes down, and the roster goes with the record.
     *
     * <p>
     * The frame is doused first and the order is load-bearing. It is the half that can fail &mdash; its
     * chunk may not be loaded, and reading a block there is a synchronous chunk load &mdash; and a run
     * that deleted its dungeon while leaving its frame lit would hand out a free rift.
     */
    // Package-private rather than private so {@code RiftExpelTest} can drive it: {@link #tick}
    // reads the config to get here, and a config spec cannot be loaded in a unit test.
    static void end(MinecraftServer server, RiftRun run) {
        // ⛔ Before the run leaves the map, and that order is the whole of why an expiry confiscates
        // anything: RiftDeaths#confiscate looks the run up by dimension to read the kit it splits
        // against, and a run already removed reads as "nobody we know" and hands every rift drop back.
        if (run.expired()) {
            ServerLevel expiring = server.getLevel(run.rift());
            if (expiring != null) {
                expel(run, expiring);
            }
        }
        BY_RIFT.remove(run.rift());
        RiftRunStore.of(server).setDirty();
        RiftRun.Verdict verdict = run.verdict();
        bookTolls(server, run, verdict);
        long awarded = awardXp(server, run, verdict);
        // Built before the level closes; posted last, so a subscriber's failure cannot leave a frame lit.
        List<RiftRunReport> reports = RiftRunReport.of(run, verdict,
                player -> com.cyberday1.riftfall.home.HomeAccess.homeOwner(server, player));
        RiftRun.Frame frame = run.frame();
        if (frame != null) {
            BY_FRAME.remove(frame);
            ServerLevel level = server.getLevel(frame.level());
            if (level != null) {
                // Two kinds of door, one corner, and each call ignores the kind it is not: the run
                // has only ever known this position by name and cannot tell a frame from a plate.
                RiftPortalShape.douse(level, frame.corner());
                RiftTearBlock.close(level, frame.corner());
            }
        }
        ServerLevel rift = server.getLevel(run.rift());
        if (rift != null) {
            RiftLevels.close(rift);
        }
        LOGGER.info("Run in {} is over: {}{}", run.rift().location(), verdict,
                run.reached() ? "" : "; nobody ever reached it");
        if (awarded > 0) {
            LOGGER.info("{} XP awarded for {}", awarded, run.rift().location());
        }
        settled(server, run, reports);
    }

    static void settled(MinecraftServer server, RiftRun run, List<RiftRunReport> reports) {
        try {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new RiftSettledEvent(server, run.rift().location(), reports));
        } catch (RuntimeException failed) {
            LOGGER.error("A settle subscriber failed for {}", run.rift().location(), failed);
        }
    }

    /**
     * Kills everybody the roster left inside an expired run, because the ruling is that an expiry is a
     * death. The quartered XP is the other half of the same penalty and is paid by {@link #earned} a
     * moment later.
     *
     * <p>
     * A real death rather than a bill that looks like one, so the price and the destination are
     * vanilla's and there is no second spelling of either: {@code RiftDeaths#confiscate} empties the
     * inventory before anything can drop, wears what the player carried in, holds it in
     * {@link RiftClaims} outside the level, and loses the rest exactly as a defeat does.
     *
     * <p>
     * Anybody the roster does not know is left alone, for the reason {@link #leaving} answers null for
     * them: they never walked through a door and nothing here is theirs to charge.
     */
    private static void expel(RiftRun run, ServerLevel rift) {
        // Copied first: dying takes the player out of this list at the end of the tick.
        for (ServerPlayer player : List.copyOf(rift.players())) {
            if (run.entry(player.getUUID()) != null && player.isAlive()) {
                player.kill();
            }
        }
        LOGGER.info("Time is up in {}", run.rift().location());
    }

    /**
     * Pays the run's entrants, and answers how much XP went out in total.
     *
     * <p>
     * Nobody is looked up in the player list, because nobody has to be: an award raises a total in
     * {@link com.cyberday1.riftfall.stat.RiftStats}, which is keyed by UUID and outlives both the
     * session and the process. A player who walked out and quit before the last of their party did
     * is credited here and finds it waiting. That is the whole of the offline case, and it is why
     * there is no note to leave &mdash; {@link RiftTolls} exists because a defeat has to reach into
     * an inventory, and an award never does.
     */
    private static long awardXp(MinecraftServer server, RiftRun run, RiftRun.Verdict verdict) {
        long paid = 0L;
        RiftReturns returns = RiftReturns.of(server);
        for (UUID entrant : run.entrants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entrant);
            // Less whatever was already paid on the way out of a beaten run; see settleOnTheWayOut.
            boolean inside = inside(run, player, returns.owed(entrant));
            long xp = earnedBy(run, verdict, entrant, player != null, inside) - run.settled(entrant);
            if (xp > 0L) {
                XpAwards.award(server, entrant, xp);
                paid += xp;
            }
            // Anybody paid at the door already has their recap; see settleOnTheWayOut.
            if (run.settled(entrant) <= 0L) {
                RiftRecaps.settled(server, run, verdict, entrant, player != null, inside, Math.max(0L, xp));
            }
        }
        return paid;
    }

    /**
     * What this run's ending banks for one entrant, from everything the run wrote down about them.
     * Stated as a function of the run so the whole of the payout can be read in one place and tested
     * against a run that was really driven rather than against four loose booleans.
     */
    static long earnedBy(RiftRun run, RiftRun.Verdict verdict, UUID entrant, boolean online,
            boolean inside) {
        boolean escaped = run.escaped().contains(entrant);
        return earned(verdict, escaped,
                penalised(verdict, run.expired(), escaped, run.died().contains(entrant), online, inside),
                run.kills(entrant), run.tier());
    }

    /**
     * Whether the ending found this entrant in the rift, which is who an expiry reaches.
     *
     * <p>
     * Asked two ways because there are two kinds of answer. An online player is asked where they are
     * standing, which is the only reading that cannot be a hundred ticks stale. An offline one is
     * still believed to be inside for as long as {@link RiftReturns} owes them a way home, which is
     * the same test {@link #owesAToll} already selects on.
     */
    static boolean inside(RiftRun run, @Nullable ServerPlayer player, @Nullable RiftEntry owed) {
        return player == null ? owed != null : player.level().dimension().equals(run.rift());
    }

    /**
     * Whether the ending charges this entrant the death penalty &mdash; the quartered XP here, and
     * for anybody who was not present to pay it the durability bill {@link #owesAToll} leaves.
     *
     * <p>
     * Three ways to earn it and no more. You died inside. You were not there when the party wiped,
     * which is the branch that inherits somebody else's failure. Or the clock ran out while you were
     * still in, which is a loss however far you had got &mdash; a party that had killed the boss and
     * not yet walked out is kicked and charged like everybody else, and that harsh reading was raised
     * explicitly and ruled without a carve-out.
     *
     * <p>
     * ⛔ Walking out clears all three, and the {@code online} clause is why the wipe branch cannot be
     * written as "absent" alone: a player standing in a rift an operator closed is neither dead nor
     * escaped, and charging them would be billing somebody for a run somebody else ended.
     *
     * <p>
     * ⛔ {@code inside} is on the expiry clause because the clock does not reach somebody who was not
     * in the dungeon it ran out in. That is the ruling on Q-11 and it is narrower than it looks:
     * an offline entrant who logged out inside is still inside, and only a player standing somewhere
     * else entirely walks away from it.
     *
     * <p>
     * ⛔ And {@link #bailed} is the fourth way, which closes the one remaining way to be paid in full
     * for a run nobody beat. See Q-12.
     */
    static boolean penalised(RiftRun.Verdict verdict, boolean expired, boolean escaped, boolean died,
            boolean online, boolean inside) {
        return died
                || bailed(verdict, escaped, inside)
                || (!escaped && ((expired && inside) || (verdict == RiftRun.Verdict.WIPED && !online)));
    }

    /**
     * Whether this entrant was out of a rift nobody beat. Ruled 2026-09-21: <em>"bailing before
     * killing the boss still gives you 25% exp"</em>.
     *
     * <p>
     * ⭐ It reads "out" two ways and they are two different people. {@code escaped} is what the run
     * wrote down at the door, which is the player who walked home early; {@code !inside} is where
     * they actually were when it ended, which additionally catches somebody an operator pulled out.
     * Both were answered in one breath, so they are charged in one clause.
     *
     * <p>
     * ⛔ The verdict term is what keeps this off a clear. Beating the dungeon and walking out is the
     * one way to be paid in full, and it still pays the thousand on top.
     */
    static boolean bailed(RiftRun.Verdict verdict, boolean escaped, boolean inside) {
        return verdict != RiftRun.Verdict.CLEARED && (escaped || !inside);
    }

    /**
     * What one entrant banks for this run. Stated here and tested here, for the reason
     * {@link #owesAToll} is: the loop around it needs a server and a live rift, and what is worth
     * checking is which clause does what.
     *
     * <p>
     * ⛔ The death clause is first and the order is the rule, not a shortcut. Dying pays a quarter
     * of what the kills were worth <em>and nothing else</em>, so the clear has to be unreachable
     * from it rather than merely unlikely. In play the two cannot both hold &mdash; dying sends a
     * player to a respawn rather than through a tear, so a corpse is never on the escaped list
     * &mdash; which is exactly why the guard would rot unnoticed if it were written the other way
     * round.
     *
     * <p>
     * ⚠ {@code penalised} is wider than dying and {@link #penalised} is where it is decided: a
     * player who was away when their party wiped, and anybody still inside when the clock ran out,
     * are charged the same quarter for the same reason. A player who neither died, nor was
     * penalised, nor got out keeps their kills in full and forfeits the clear.
     */
    static long earned(RiftRun.Verdict verdict, boolean escaped, boolean penalised, int kills, int tier) {
        long fought = XpAwards.forKills(kills, tier);
        if (penalised) {
            return XpAwards.afterDeath(fought);
        }
        return earnsTheClear(verdict, escaped) ? XpAwards.earned(fought, XpAwards.clearXp(tier)) : fought;
    }

    /**
     * Whether one entrant brought this run home, which is what the clear bonus is paid for.
     *
     * <p>
     * Beating the dungeon is what pays, and walking out of it is what collects &mdash; so the
     * bonus extracts exactly as the loot does. Both clauses earn their place. Without the first,
     * stepping through a frame and straight back out again would be a clear, which is a rift's
     * worth of progression for a key and no fight. Without the second, the reward for a fight
     * would not be a reward for surviving it, and dying inside already costs everything else.
     */
    static boolean earnsTheClear(RiftRun.Verdict verdict, boolean escaped) {
        return verdict == RiftRun.Verdict.CLEARED && escaped;
    }

    /**
     * Writes down what one death inside a rift meant for the run it happened in.
     *
     * <p>
     * ⛔ A player's corpse is nobody's kill, and the early return is the whole of that rule.
     * {@link LivingEntity#getKillCredit} names whoever last hit them, so without it killing a
     * party member in a rift would be worth XP.
     *
     * <p>
     * Credit is {@code getKillCredit}'s and not the damage source's, deliberately: it is the rule
     * vanilla already uses to decide who a mob's experience orbs belong to, so a rift pays for a
     * kill exactly where the world does. That takes in an arrow and a tamed pet's owner, and
     * leaves out a mob that killed another mob.
     *
     * <p>
     * A roster member's kill is shared in full with every living, non-spectating party member
     * still in this rift, regardless of distance or who landed the hit. The run guards membership,
     * deaths and exits; the level's live player list excludes offline and out-of-dimension players.
     */
    private static void recordDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        RiftRun run = BY_RIFT.get(dead.level().dimension());
        if (run != null) {
            applyDeath(run, dead);
        }
    }

    /** Applies a death to its resolved run; also exercised without registering a global live run. */
    static void applyDeath(RiftRun run, LivingEntity dead) {
        if (dead instanceof ServerPlayer player) {
            run.died(player.getUUID());
        } else if (dead.level() instanceof ServerLevel level
                && dead.getKillCredit() instanceof ServerPlayer killer) {
            run.partyKilled(killer.getUUID(), level.players().stream()
                    .filter(player -> player.isAlive() && !player.isSpectator())
                    .map(ServerPlayer::getUUID).toList(),
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()));
        }
    }

    /**
     * Leaves a bill for every entrant who was not there when their run wiped. An offline player's
     * inventory is a file rather than a container, so nothing can be taken here; {@link RiftTolls} is
     * the note, and their next login is when it is charged.
     */
    private static void bookTolls(MinecraftServer server, RiftRun run, RiftRun.Verdict verdict) {
        RiftReturns returns = RiftReturns.of(server);
        RiftTolls tolls = null;
        for (UUID entrant : run.entrants()) {
            boolean online = server.getPlayerList().getPlayer(entrant) != null;
            if (!owesAToll(verdict, run.expired(), run.escaped().contains(entrant), online,
                    returns.owed(entrant))) {
                continue;
            }
            if (tolls == null) {
                tolls = RiftTolls.of(server);
            }
            tolls.owe(entrant, run.rift().location(), run.carriedIn(entrant));
        }
    }

    /**
     * Whether one entrant inherits the verdict as a bill. Every clause fails silently on its own and
     * the real selection needs a server, so the rule is stated here and tested here.
     *
     * <p>
     * Still being owed a return is what "believed to be inside" means, and it is why nothing new has to
     * be written down at entry: the debt goes on at the door and comes off the moment the player is
     * seen outside a rift. So anybody who escaped had it forgiven on arrival, and anybody online and
     * dead paid at their death.
     *
     * <p>
     * ⚠ The escape clause reads as redundant against a wipe and is not: a wipe is a run nobody got out
     * of, so it can never fire there, but an expired run can be one somebody walked out of an hour ago
     * and they are not billed for the clock running out behind them. Expiry is billed at all because
     * the clock ending a run is a loss, and the half of the penalty an absent player cannot be charged
     * on the spot is exactly what a toll is for.
     */
    static boolean owesAToll(RiftRun.Verdict verdict, boolean expired, boolean escaped, boolean online,
            @Nullable RiftEntry owed) {
        return !online && owed != null && !escaped
                && (expired || verdict == RiftRun.Verdict.WIPED);
    }

    /**
     * Clears the return debt of every online player who is not in a rift. Cheap enough to be
     * unconditional: one hash lookup per online player, and the map is empty on any server where
     * nobody is in a dungeon.
     */
    static void sweepReturns(MinecraftServer server) {
        RiftReturns returns = RiftReturns.of(server);
        if (returns.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (returns.recovering(player.getUUID())) {
                if (player.isAlive()) recoverPlayer(server, player);
                continue;
            }
            if (!RiftLevels.isRift(player.level().dimension())) {
                returns.forgive(player.getUUID());
            }
        }
    }

    /**
     * Puts a player back on their own doorstep when the rift they logged out of is not there any more.
     *
     * <p>
     * A teleport rather than a redirect, because vanilla has already dealt with the level: an unknown
     * respawn dimension is logged and swapped for the overworld with the coordinates left as they
     * were, so the position being corrected is one inside a deleted dungeon read as an overworld one.
     * A player whose rift still stands is left where they are; a disconnect does not spend their place
     * in the run.
     */
    static void returnStranded(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        RiftReturns returns = RiftReturns.of(server);
        RiftEntry entry = returns.owed(player.getUUID());
        if (entry == null || (RiftLevels.isRift(player.level().dimension())
                && !RiftLevels.isClosing(player.level().dimension()))) {
            return;
        }

        returns.recover(player.getUUID());
        if (!player.isAlive()) return; // Let death respawn choose the home/bed, never rescue a corpse.
        if (recoverPlayer(server, player)) {
            LOGGER.info("Recovered {} after the rift they were in was closed", player.getName().getString());
        } else {
            LOGGER.warn("{} could not yet return from a closed rift to {}; recovery will retry",
                    player.getName().getString(), entry.origin().location());
        }
    }

    private static boolean recoverPlayer(MinecraftServer server, ServerPlayer player) {
        try { return RiftEvacuation.returnPlayer(server, player); }
        catch (IllegalStateException unavailable) {
            LOGGER.warn("No return destination available for {}; retaining the return record", player.getUUID());
            return false;
        }
    }

    /** Respawn has already selected a home/bed. Do not pull that player to an old entry on login. */
    static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !RiftLevels.isRift(player.level().dimension())) {
            RiftReturns.of(player.server).forgive(player.getUUID());
            player.fallDistance = 0;
        }
    }

    /**
     * Clears a leaving player's debt if they are not in a rift. The state at logout is the state their
     * next login starts from: without this, a player who walked out and quit within
     * {@link #SWEEP_INTERVAL} would be teleported back to a doorstep they had already left through.
     */
    static void forgiveOnLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !RiftLevels.isRift(player.level().dimension())) {
            var returns = RiftReturns.of(player.server);
            if (!returns.recovering(player.getUUID())) returns.forgive(player.getUUID());
        }
    }

    /**
     * Marks an entrant away when they log out standing in their run's rift, which is what lets
     * {@link RiftRun#advance} pause the run instead of ending it. A server stop disconnects everybody
     * through here too, so every run still going at a stop is paused before the final save writes it.
     */
    private static void pauseOnLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }
        RiftRun run = BY_RIFT.get(player.level().dimension());
        if (run == null) {
            return;
        }
        run.loggedOut(player.getUUID());
        if (run.away().contains(player.getUUID()) && alone(player)) {
            LOGGER.info("Run in {} paused: {} logged out with nobody else inside",
                    run.rift().location(), player.getName().getString());
        }
    }

    private static boolean alone(ServerPlayer leaving) {
        for (ServerPlayer other : leaving.serverLevel().players()) {
            if (other != leaving && other.isAlive()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Clears an entrant's away mark on login, wherever they land. Standing in the rift, they hold it
     * open and the run resumes on the next tick; anywhere else, they are no longer away inside it.
     */
    private static void resumeOnLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            back(player.getUUID());
        }
    }

    /** Clears one entrant's away mark on every run. Shared by the login listener and the probe. */
    public static void back(UUID player) {
        for (RiftRun run : BY_RIFT.values()) {
            run.loggedIn(player);
        }
    }

    /**
     * Brings back every run still going at the last stop, and its level, before anybody can log in.
     *
     * <p>
     * ⛔ Order: read the records, sweep every rift folder they do not name, then rebuild. The sweep
     * used to delete every folder on {@code ServerAboutToStartEvent}; it now runs here because only
     * the records can say which folders are still somebody's run. A record whose level was already
     * gone at the stop is restored without one, and ends on the first tick like any run whose level
     * went, which pays it out rather than dropping it.
     */
    private static void restore(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        List<RiftRunStore.Record> records = RiftRunStore.of(server).records();
        Set<String> keep = new HashSet<>();
        for (RiftRunStore.Record record : records) {
            if (record.level()) {
                keep.add(RiftLevels.folderName(record.run().rift()));
            }
        }
        RiftLevels.sweep(server, keep);
        int paused = 0;
        for (RiftRunStore.Record record : records) {
            RiftRun run = record.run();
            if (record.level()) {
                try {
                    RiftLevels.restore(server, run.rift(), record.seed(), record.tier(), record.barrels(), record.attunement());
                } catch (RuntimeException e) {
                    LOGGER.error("Could not restore rift {}; its run ends on the first tick", run.rift().location(), e);
                }
            }
            BY_RIFT.put(run.rift(), run);
            if (run.frame() != null) {
                BY_FRAME.put(run.frame(), run);
            }
            if (run.paused()) {
                paused++;
            }
        }
        if (!records.isEmpty()) {
            LOGGER.info("Restored {} rift run(s) from the last stop, {} paused", records.size(), paused);
        }
    }

    /**
     * What the store writes: every live run, and what its level is rebuilt from. A run whose level is
     * already gone or going is written without one, so it ends rather than vanishes after a restart.
     */
    static List<RiftRunStore.Record> records() {
        List<RiftRunStore.Record> records = new ArrayList<>(BY_RIFT.size());
        for (RiftRun run : BY_RIFT.values()) {
            boolean level = RiftLevels.knows(run.rift()) && !RiftLevels.isClosing(run.rift());
            records.add(new RiftRunStore.Record(run, level, RiftLevels.seed(run.rift()),
                    RiftLevels.tier(run.rift()), RiftLevels.barrelRules(run.rift()), RiftLevels.attunement(run.rift())));
        }
        return records;
    }

    /** Whether any run is live, which is what keeps the store dirty. */
    static boolean anyLive() {
        return !BY_RIFT.isEmpty();
    }

    /**
     * Drops every run once the server has stopped. After the stop rather than at it, because the
     * final save happens in between and {@link RiftRunStore} writes what is here at that moment.
     * The maps are static and a singleplayer client starts a new server per world, so without this
     * the second world of a session opens holding the first world's runs.
     */
    private static void forgetAll(ServerStoppedEvent event) {
        forget();
    }

    /** Drops every run. Shared by the shutdown listener and by tests, which want the same reset. */
    static void forget() {
        BY_RIFT.clear();
        BY_FRAME.clear();
    }
}
