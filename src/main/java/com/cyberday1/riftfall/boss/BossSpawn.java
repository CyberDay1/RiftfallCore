package com.cyberday1.riftfall.boss;

import com.cyberday1.riftfall.rift.RiftLevelState;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Puts one boss on the arena pad, drawn from {@link RiftBosses#rosterForTier}, the first time anybody walks
 * into a rift's boss room.
 *
 * <p>
 * This is the feature {@link RiftBossArena} was written for and left empty: the pad has been findable
 * since the arena markers went in, and until now nothing stood on it but {@code /riftfall boss}, a
 * command that admits in its own javadoc to being scaffolding.
 *
 * <h2>Why not literally at generation</h2>
 * The room is built on a worldgen worker, into a chunk that is loaded but not ticking and may not be
 * ticking for the rest of the session. An entity added there is an entity added off the server
 * thread to a chunk with no entity section in play, and {@link RiftBosses#take} rewrites goal lists,
 * which is not a thing to do from a worker. The same problem is why the mob spawners in every other
 * room are <em>blocks</em> that fire later rather than mobs written at generation.
 *
 * <p>
 * So the boss is decided at generation and made on arrival, which is the same split. The decision is
 * seeded on the arena pad — a block position no other rift has, because no other rift has this
 * layout — so a given rift's boss is a fact about that rift and not about which tick somebody
 * happened to walk in on. What arrival buys over a spawner block is that a boss must not be a full
 * cube of scenery sitting on the pad the party is about to fight over.
 *
 * <h2>One attempt, not one success</h2>
 * The level is marked done before the mob is built, so a roster that names something unspawnable
 * gives an empty arena rather than a spawn attempt every {@value #POLL_INTERVAL} ticks for the rest
 * of the run. A missing boss is a bug to read in the log; a poll retrying it forever is a bug that
 * hides itself.
 */
public final class BossSpawn {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * How often the arena is checked for arrivals, in ticks. Shares {@link BossGate}'s rate on
     * purpose: the two answer the same question about the same room, and a boss that appeared a
     * visible moment after the gate started shutting would read as the gate having summoned it.
     */
    static final int POLL_INTERVAL = BossGate.POLL_INTERVAL;

    /** Keeps the pick clear of the layout, the palettes and the spawner lattice, which share a seed. */
    private static final long SALT = 0x1F0B0554AC9L;

    /** Rifts whose boss has been dealt with, so an arena is never asked about twice. */
    private static final Set<ServerLevel> DONE = new HashSet<>();

    private BossSpawn() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BossSpawn::tick);
        NeoForge.EVENT_BUS.addListener(BossSpawn::forgetLevel);
        NeoForge.EVENT_BUS.addListener(BossSpawn::forgetAll);
        NeoForge.EVENT_BUS.addListener(BossSpawn::remember);
    }

    /** Reads back that a rift restored after a restart already had its boss, so it gets no second (G9). */
    private static void remember(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && RiftLevels.isRiftWorld(level)) {
            RiftLevelState state = RiftLevelState.peek(level);
            if (state != null && state.bossSpawned()) {
                DONE.add(level);
            }
        }
    }

    private static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % POLL_INTERVAL != 0) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!DONE.contains(level) && !level.players().isEmpty()) {
                consider(level);
            }
        }
    }

    /**
     * Spawns the boss if somebody has arrived. Reads no block in the common case, which is every rift
     * nobody has walked into the boss room of yet — see {@link RiftBossArena#occupied} for why that
     * matters more here than it looks.
     */
    private static void consider(ServerLevel level) {
        if (!RiftBossArena.occupied(level)) {
            return;
        }
        DONE.add(level);
        RiftLevelState.of(level).bossSpawned(true);
        // Safe now, and only now: a player is standing in the room, so the marker walk this starts
        // reads chunks that are already there instead of building them on the server thread.
        Optional<BlockPos> pad = RiftBossArena.pad(level);
        if (pad.isEmpty()) {
            LOGGER.warn("Boss room in {} carries no arena pad, so it gets no boss", level.dimension().location());
            return;
        }
        spawn(level, pad.get());
    }

    /**
     * Builds one boss and puts it on {@code pad}.
     *
     * <p>
     * ⚠ The order of the last four calls is the one {@link RiftBosses#take} and
     * {@code RiftMobSpawnerBlockEntity} both spell out, and all three of us have to agree:
     * {@code finalizeMobSpawn} is where a mob is handed its weapon and settles its own attributes, so
     * it runs first; {@code take} replaces the target selector and applies the tier curve on top of
     * that result; {@code addFreshEntity} last, because rewriting the goals of a mob the world is
     * already ticking is a race for nothing.
     */
    private static void spawn(ServerLevel level, BlockPos pad) {
        Optional<Holder<EntityType<?>>> picked = pick(level, pad);
        if (picked.isEmpty()) {
            LOGGER.warn("Nothing on #{} to be the boss of {}", RiftBosses.rosterForTier(RiftLevels.tier(level)).location(),
                    level.dimension().location());
            return;
        }
        EntityType<?> type = picked.get().value();
        if (!(type.create(level) instanceof Mob boss)) {
            LOGGER.warn("#{} names {}, which is not a mob, so it has nothing to aggro with",
                    RiftBosses.ROSTER.location(), EntityType.getKey(type));
            return;
        }

        int tier = RiftLevels.tier(level);
        boss.moveTo(pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5, facing(level, pad), 0.0F);
        com.cyberday1.riftfall.mob.VanillaWorldSpawns.authorize(boss);
        EventHooks.finalizeMobSpawn(boss, level, level.getCurrentDifficultyAt(pad),
                MobSpawnType.STRUCTURE, null);
        // A boss that wandered off while the party walked to it is a boss fight in a corridor.
        boss.setPersistenceRequired();
        RiftBosses.take(boss, tier);
        level.addFreshEntity(boss);
        LOGGER.info("Spawned {} on the arena pad of {} as its tier {} boss", EntityType.getKey(type),
                level.dimension().location(), tier);
    }

    /**
     * Which boss this rift has. Read off the level's own registries rather than
     * {@code BuiltInRegistries}, because tag membership is a datapack fact and the built-in registry
     * knows the types but not which of them the pack put on the roster.
     *
     * <p>
     * Empty when the tag is unbound or has nothing in it, which is a pack with no bosses configured
     * rather than an error to fall back from: quietly promoting some other mob would put a creeper on
     * the pad with a boss's health bar.
     */
    /** The boss this rift's pad would get; for the rig probe. */
    public static Optional<Holder<EntityType<?>>> pickFor(ServerLevel level, BlockPos pad) { return pick(level, pad); }

    private static Optional<Holder<EntityType<?>>> pick(ServerLevel level, BlockPos pad) {
        var roster = level.registryAccess().registryOrThrow(Registries.ENTITY_TYPE)
                .getTag(RiftBosses.rosterForTier(RiftLevels.tier(level)));
        // An attuned key's boss, if this tier's roster still names it; otherwise the usual draw.
        var attuned = RiftLevels.attunement(level.dimension()).boss();
        if (attuned.isPresent() && roster.isPresent()) {
            for (Holder<EntityType<?>> holder : roster.get())
                if (holder.unwrapKey().map(key -> key.location().equals(attuned.get())).orElse(false)) return Optional.of(holder);
            LOGGER.warn("Attuned boss {} is not on this tier's roster; drawing one instead", attuned.get());
        }
        return roster.flatMap(r -> r.getRandomElement(RandomSource.create(pad.asLong() ^ SALT)));
    }

    /**
     * The yaw that has the boss looking at the doorway the party will come through, or at nothing in
     * particular when the room's author marked no entry. Small, and the first thing anybody sees.
     */
    private static float facing(ServerLevel level, BlockPos pad) {
        BlockPos entry = RiftBossArena.entry(level).orElse(null);
        if (entry == null) {
            return level.getRandom().nextFloat() * 360.0F;
        }
        double toX = entry.getX() - pad.getX();
        double toZ = entry.getZ() - pad.getZ();
        return (float) (Mth.atan2(toZ, toX) * (180.0F / Math.PI)) - 90.0F;
    }

    /**
     * Forgets a rift that is coming down, the same way {@link RiftBosses} and {@link BossGate} do.
     * The set is keyed by the level object, so without this a closed rift is held alive by a record
     * that it once had a boss.
     */
    private static void forgetLevel(LevelEvent.Unload event) {
        DONE.remove(event.getLevel());
    }

    /**
     * Drops every record when the server stops, because a singleplayer client starts a fresh server
     * per world and these levels do not outlive one.
     */
    private static void forgetAll(ServerStoppingEvent event) {
        DONE.clear();
    }
}
