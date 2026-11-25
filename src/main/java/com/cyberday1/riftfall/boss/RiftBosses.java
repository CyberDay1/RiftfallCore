package com.cyberday1.riftfall.boss;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.loot.RiftBossLoot;
import com.cyberday1.riftfall.mixin.AccessorEntity;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.cyberday1.riftfall.rift.RiftPortalBlock;
import com.cyberday1.riftfall.rift.RiftPortalKind;
import com.cyberday1.riftfall.rift.RiftRun;
import com.cyberday1.riftfall.rift.RiftRuns;
import com.cyberday1.riftfall.rift.RiftTearBlock;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every boss a rift is currently running, and the one call that turns a mob into one.
 *
 * <p>
 * The bosses come from other mods and this package names none of them: it knows {@code Mob} and
 * nothing else. That is durability, not licensing -- a subsystem written against the shape every mob
 * shares survives those mods updating and works on the one nobody has picked yet.
 *
 * <p>
 * Nothing here is persisted. A boss lives in a rift, a rift cannot survive a restart, so a record
 * that outlived one would describe a mob in a level that no longer exists.
 */
public final class RiftBosses {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The entity types a rift is willing to call a boss.
     *
     * <p>
     * A tag rather than a list in code, for the reason this class already gives: naming other mods'
     * mobs in Java would tie the subsystem to whichever pack it shipped in. A tag is data, so a pack
     * can add its own and removing a mod costs nothing &mdash; every entry is written
     * {@code required: false}, exactly as the {@code rift_mobs} rosters are.
     *
     * <p>
     * ⚠ This is a <em>whitelist</em>, not a hint. {@link #isBoss} gates {@code /riftfall boss} on
     * it, so an id missing here is a mob nobody can summon as one.
     *
     * @see #isBoss
     */
    public static final TagKey<EntityType<?>> ROSTER =
            TagKey.create(Registries.ENTITY_TYPE, RiftfallCore.id("rift_bosses"));

    /** Starter characters (levels 1–19) use the audited, smaller encounters, including level 10's tier 2. */
    public static final TagKey<EntityType<?>> BEGINNER_ROSTER =
            TagKey.create(Registries.ENTITY_TYPE, RiftfallCore.id("rift_bosses_beginner"));

    public static TagKey<EntityType<?>> rosterForTier(int tier) {
        return tier <= 2 ? BEGINNER_ROSTER : ROSTER;
    }

    /**
     * How often every table is aged by one {@link ThreatTable#decay} step, in ticks. The other half of
     * the rate; raising it makes the table lurch, which is visible because the rings move together.
     */
    static final int DECAY_INTERVAL = 20;

    /**
     * Keyed on the mob rather than its id, because every reader wants the entity. {@code Entity} does
     * not override equality, so this is identity-keyed, which is the right notion here.
     * Insertion-ordered so several open rifts tick in a stable order.
     */
    private static final Map<Mob, Tracked> BOSSES = new LinkedHashMap<>();

    /** What {@link #take} writes on a boss so it can be taken back after a reload; see {@link #readopt}. */
    private static final String MARK = RiftfallCore.MODID + ":boss";
    private static final String TIER = "tier";
    private static final String HEADCOUNT = "headcount";

    /**
     * What we keep about a boss. Tier and headcount are stored rather than read back: they are what
     * the boss was <em>scaled</em> at, so its incoming-damage multiplier must agree with them, and a
     * headcount read later would drop the moment somebody died.
     */
    record Tracked(int tier, int headcount, ThreatTable threat, TargetRotation rotation, ThreatGoal goal) {
    }

    private RiftBosses() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RiftBosses::tick);
        NeoForge.EVENT_BUS.addListener(RiftBosses::openTheWayHome);
        NeoForge.EVENT_BUS.addListener(RiftBosses::forgetLevel);
        NeoForge.EVENT_BUS.addListener(RiftBosses::forgetAll);
        NeoForge.EVENT_BUS.addListener(RiftBosses::readopt);
        ThreatEvents.register();
    }

    /**
     * Whether {@link #ROSTER} names this type.
     *
     * <p>
     * Asked of the {@link Holder} rather than the {@link EntityType}, because the holder is what
     * carries tag membership; going through the type would mean a registry lookup to get back the
     * holder the caller already had.
     */
    public static boolean isBoss(Holder<EntityType<?>> type) {
        return type.is(ROSTER);
    }

    /**
     * Turns a freshly constructed mob into a rift boss. The entry point, and the only one.
     *
     * <p>
     * ⚠ Call after constructing and positioning the mob and before {@code level.addFreshEntity}. This
     * rewrites goal lists and sets health, and doing either to a mob the world is already ticking is a
     * race for no reason. After {@code EventHooks.finalizeMobSpawn} too, which is where a vanilla mob
     * is handed its weapon: a boss that has not been finalized is a boss with empty hands.
     *
     * <p>
     * The wipe is {@code removeAllGoals(g -> true)} rather than a filter on goal types: a filter would
     * have to know what this boss's targeting goals are called, which is the one thing we promised not
     * to know. Everything in {@code targetSelector} is by definition what we are replacing.
     */
    public static void take(Mob mob, int tier) {
        take(mob, tier, headcount(mob.level()));
    }

    /** {@link #take(Mob, int)} against a stated party size, for a boss with no run behind it. */
    public static void take(Mob mob, int tier, int headcount) {
        int party = Math.max(1, headcount);
        adopt(mob, tier, party);
        BeginnerBosses.prepare(mob, tier);
        BossScaling.scale(mob, tier);
        CompoundTag mark = new CompoundTag();
        mark.putInt(TIER, tier);
        mark.putInt(HEADCOUNT, party);
        mob.getPersistentData().put(MARK, mark);
        LOGGER.debug("Took {} as a tier {} rift boss for a party of {}", EntityType.getKey(mob.getType()), tier,
                party);
    }

    /** Puts a mob on the register with a fresh threat table. Scaling is {@link #take}'s, and is saved with the mob. */
    private static void adopt(Mob mob, int tier, int party) {
        mob.targetSelector.removeAllGoals(goal -> true);
        ThreatTable threat = new ThreatTable();
        TargetRotation rotation = new TargetRotation();
        ThreatGoal goal = new ThreatGoal(mob, threat, rotation);
        mob.targetSelector.addGoal(0, goal);
        BOSSES.put(mob, new Tracked(tier, party, threat, rotation, goal));
    }

    /**
     * Takes a boss back when it is loaded from disk: after a chunk unload, or after a restart that
     * kept its rift (G9). Without it the mob comes back with a boss's health and none of the
     * register, so killing it would never clear the run. The mark {@link #take} wrote is the proof.
     */
    private static void readopt(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || BOSSES.containsKey(mob)) {
            return;
        }
        // Not getPersistentData(): that would leave an empty compound on every mob that joins.
        CompoundTag data = ((AccessorEntity) mob).riftfall$persistentDataOrNull();
        CompoundTag mark = data == null ? null : data.getCompound(MARK);
        if (mark != null && !mark.isEmpty()) {
            adopt(mob, mark.getInt(TIER), Math.max(1, mark.getInt(HEADCOUNT)));
            // Attributes persist in NBT, AI goals do not. Restore the entry encounter without healing it.
            BeginnerBosses.prepare(mob, mark.getInt(TIER));
        }
    }

    /**
     * The party a boss in this level is scaled for: the run's roster, which is locked once anybody
     * leaves the portal room and so before the boss room is reached. One for a rift with no run.
     */
    private static int headcount(Level level) {
        RiftRun run = RiftRuns.in(level.dimension());
        return run == null ? 1 : Math.max(1, run.entrants().size());
    }

    /**
     * Whether this mob is still on the register.
     *
     * <p>
     * Here for the probe, and for one question it is the only way to ask: after a boss is discarded
     * rather than killed, this going false is the evidence that the reaping branch in {@link #tick}
     * actually ran. Without it, "no tear opened" is equally consistent with the reaping branch being
     * correct and with the server never having ticked &mdash; and those are the two readings the
     * despawn control exists to tell apart.
     */
    public static boolean tracks(Mob mob) {
        return BOSSES.containsKey(mob);
    }

    /** What we know about this entity, or null if it is not a boss of ours. */
    @Nullable
    static Tracked tracked(LivingEntity entity) {
        return entity instanceof Mob mob ? BOSSES.get(mob) : null;
    }

    /**
     * What one boss's table holds for one player, raw, or zero if it holds nothing.
     *
     * <p>
     * ⚠ The raw number, which nothing in play ever shows: {@link ThreatTable#shareOfTop} is the
     * reading a player gets, and a share cannot tell "both legs paid" from "neither did" because it
     * is a ratio to itself. This exists for the probe, on {@link #tracks}'s terms -- a measurement
     * that would otherwise have to be inferred from a number designed to hide it.
     */
    public static double threatOn(Mob boss, UUID player) {
        Tracked tracked = BOSSES.get(boss);
        return tracked == null ? 0.0 : tracked.threat().of(player);
    }

    /** Who tops one boss's threat table, or null. For the probe, on {@link #threatOn}'s terms. */
    @Nullable
    public static UUID tableTop(Mob boss) {
        Tracked tracked = BOSSES.get(boss);
        return tracked == null ? null : tracked.threat().top();
    }

    /** The party size a boss was taken for, or zero if it is not ours. For the probe. */
    public static int headcountOf(Mob boss) {
        Tracked tracked = BOSSES.get(boss);
        return tracked == null ? 0 : tracked.headcount();
    }

    /** How many random target draws a boss has made, or zero if it is not ours. For the probe. */
    public static int drawsOf(Mob boss) {
        Tracked tracked = BOSSES.get(boss);
        return tracked == null ? 0 : tracked.rotation().draws();
    }

    /**
     * Whether a boss in this level has anybody on its threat table.
     *
     * <p>
     * This is the closest thing the subsystem has to "the fight has started", and it is a poll rather
     * than an event on purpose: {@link #take} is boss <em>creation</em> and fires nothing, and threat
     * arrives from three separate listeners that would each have to remember to announce a first
     * entry. Asking the tables cannot fall out of step with them.
     */
    public static boolean engagedIn(Level level) {
        for (Map.Entry<Mob, Tracked> entry : BOSSES.entrySet()) {
            if (entry.getKey().level() == level && !entry.getValue().threat().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds threat to every boss in a level, which is what a contribution nobody aimed at anything is.
     * Damage names its boss and goes onto that one table; healing names nobody, so it lands on
     * everything engaged -- a healer keeping the party up is a problem for every boss in the room.
     */
    static void creditLevel(Level level, UUID player, double amount) {
        for (Map.Entry<Mob, Tracked> entry : BOSSES.entrySet()) {
            if (entry.getKey().level() == level) {
                entry.getValue().threat().add(player, amount);
            }
        }
    }

    /**
     * Taunts one boss: pins its rotation to this player for {@link TargetRotation#TAUNT_TICKS} and
     * puts them at the top of its table so the ring shows who pulled.
     * {@link #taunt(ServerLevel, ServerPlayer, double)} is what an item calls.
     */
    public static void taunt(Mob boss, ServerPlayer player) {
        Tracked tracked = BOSSES.get(boss);
        if (tracked != null) {
            taunt(boss, tracked, player);
        }
    }

    private static void taunt(Mob boss, Tracked tracked, ServerPlayer player) {
        tracked.threat().taunt(player.getUUID());
        tracked.rotation().taunt(player.getUUID(), boss.tickCount);
    }

    /**
     * Taunts every boss near a player, and answers how many heard it.
     *
     * <p>
     * A taunt reaches a room rather than a target. A party fighting two bosses wants both looking at
     * whoever pulled, and picking one of them would need the item to decide which -- a decision it has
     * no way to make well and the register already has the information for. So the walk lives here,
     * where the register does, and the item stays ignorant of it.
     *
     * <p>
     * Distance is measured to the boss because that is the thing being taunted; a boss beyond the
     * radius is in another fight.
     */
    public static int taunt(ServerLevel level, ServerPlayer player, double radius) {
        double reach = radius * radius;
        int heard = 0;
        for (Map.Entry<Mob, Tracked> entry : BOSSES.entrySet()) {
            Mob boss = entry.getKey();
            if (boss.level() == level && boss.distanceToSqr(player) <= reach) {
                taunt(boss, entry.getValue(), player);
                heard++;
            }
        }
        return heard;
    }

    /**
     * Opens the way home at the arena's authored marker when a rift boss dies.
     *
     * <p>
     * ⛔ On {@code LivingDeathEvent} and deliberately <em>not</em> on the reaping branch in
     * {@link #tick}. That branch treats death, {@code /kill}, despawn and chunk unload as one thing,
     * which is right for forgetting a threat table and wrong for this: a rift whose arena chunk
     * unloaded for a moment would come back with a tear standing in it and the boss still alive. Of
     * those four, only death is a reward.
     *
     * <p>
     * The tear stands at {@link RiftBossArena#exit}, which is the block the arena's author marked,
     * not where the boss fell.
     *
     * <p>
     * ⛔ The tear is opened <em>before</em> the run is told, and the order is the invariant:
     * {@code RiftRuns#cleared} reads the block back before it leaves a run flagged and running, so
     * handing it the position is what stops a run reading CLEARED inside a sealed room with no way
     * out of it. A rift with no run behind it -- an opened one, a probe's -- is a no-op there.
     */
    private static void openTheWayHome(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob boss) || BOSSES.remove(boss) == null) {
            return;
        }
        if (!(boss.level() instanceof ServerLevel rift)) {
            return;
        }
        // The guns-and-attachments roll; an empty table drops nothing.
        RiftBossLoot.drop(rift, boss, event.getSource().getEntity());
        com.cyberday1.riftfall.lore.BossDeathLine.fell(rift, boss);
        com.cyberday1.riftfall.story.StoryBridge.defenderFell(rift);
        RiftRuns.bossKilled(rift, boss);
        BossFinder.bossDown(rift);
        BlockPos at = RiftBossArena.exit(rift).orElse(null);
        // clearAndOpen rather than open: the author marked this column as the doorway, so a fight
        // that left something standing in it must not cost the party their way home.
        boolean opened = at != null && RiftTearBlock.clearAndOpen(rift, at, Direction.Axis.X,
                RiftPortalKind.BOSS, RiftPortalBlock.intensityFor(RiftLevels.tier(rift)), true);
        if (at != null && !opened) {
            // Not simply "blocked" any more: clearAndOpen takes rubble down first, so reaching here
            // means the column could not be freed at all.
            LOGGER.error("Could not open a way home at {} in {}; the arena's exit column would not "
                    + "clear.", at, rift.dimension().location());
        }
        RiftRuns.cleared(rift, opened ? at : null);
    }

    /**
     * The whole per-tick job: forget bosses that have gone, age the tables, prune the departed, draw.
     * On {@code ServerTickEvent.Post} rather than a level tick, so decay is one rate for the server
     * rather than one per rift, and so this cannot run against a level halfway through teardown.
     */
    private static void tick(ServerTickEvent.Post event) {
        if (BOSSES.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        boolean decaying = server.getTickCount() % DECAY_INTERVAL == 0;
        boolean drawing = server.getTickCount() % ThreatRing.INTERVAL == 0;

        Iterator<Map.Entry<Mob, Tracked>> bosses = BOSSES.entrySet().iterator();
        while (bosses.hasNext()) {
            Map.Entry<Mob, Tracked> entry = bosses.next();
            Mob boss = entry.getKey();
            // Death, /kill, despawn and chunk unload all arrive as a removed entity and all mean the
            // same thing here; an unloaded boss is taken back by readopt when it loads again.
            // Testing the entity covers all four; LivingDeathEvent would cover one.
            if (boss.isRemoved() || !boss.isAlive() || !(boss.level() instanceof ServerLevel level)) {
                bosses.remove();
                continue;
            }
            ThreatTable threat = entry.getValue().threat();
            if (decaying) {
                threat.decay();
            }
            prune(level, threat);
            if (drawing) {
                ThreatRing.draw(level, boss, threat);
            }
        }
    }

    /**
     * Drops anybody who is no longer in the fight. Died, walked out, sent home, logged out and
     * spectating are all "this id does not name a live player standing in this level", so all five are
     * one lookup rather than five listeners that would have to agree with each other.
     *
     * <p>
     * Asymmetric with {@link ThreatGoal} on purpose: this decides who is on the table, the goal decides
     * who can be targeted. A creative player stays here -- their threat records real damage and should
     * decay like anybody's -- and is refused there, since chasing them is a boss walking at nothing.
     */
    private static void prune(ServerLevel level, ThreatTable threat) {
        if (threat.isEmpty()) {
            return;
        }
        for (UUID id : threat.ranked()) {
            if (!(level.getEntity(id) instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) {
                threat.forget(id);
            }
        }
    }

    /**
     * Forgets every boss in a level that is coming down. {@code RiftLevels#tearDown} posts
     * {@code LevelEvent.Unload} for every rift it closes however it was closed, so this covers all of
     * them without this class knowing runs exist. The per-tick sweep would get there a tick later,
     * holding strong references to every mob in a deleted world in the meantime.
     */
    private static void forgetLevel(LevelEvent.Unload event) {
        BOSSES.keySet().removeIf(boss -> boss.level() == event.getLevel());
    }

    /**
     * Drops every boss when the server stops. A singleplayer client starts a fresh server per world,
     * so without this the second world of a session opens holding the first world's bosses.
     */
    private static void forgetAll(ServerStoppingEvent event) {
        BOSSES.clear();
    }
}
