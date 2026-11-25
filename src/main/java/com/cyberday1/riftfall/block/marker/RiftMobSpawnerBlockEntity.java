package com.cyberday1.riftfall.block.marker;

import com.cyberday1.riftfall.gear.RelicSignatureCosts;

import com.cyberday1.riftfall.init.RiftfallBlockEntities;
import com.cyberday1.riftfall.mob.MobScaling;
import com.cyberday1.riftfall.mob.RiftMobs;
import com.cyberday1.riftfall.mob.RiftMobBalance;
import com.cyberday1.riftfall.mob.RiftMobPlacement;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.cyberday1.riftfall.theme.RiftTheme;
import com.cyberday1.riftfall.worldgen.level.RiftChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

/**
 * A spawner that fires once and then is not there any more.
 *
 * <p>
 * This is the behaviour the marker block has been waiting for since it was imported. The block
 * carries vanilla's entire trial-spawner state vocabulary &mdash; inactive, waiting for players,
 * active, ejecting reward, cooldown &mdash; and nothing has ever driven any of it, because nothing
 * placed the block either. Generation places them now, and what they do is the simplest of the
 * things that vocabulary describes: wait until somebody is close, make a handful of mobs, and go.
 *
 * <h2>Why one shot, and not vanilla's cooldown</h2>
 * Three reasons, and the first is the one that matters.
 *
 * <p>
 * A cleared room has to stay cleared. A rift is a run: a party fights its way inward and then has to
 * get back out, and a spawner that recharges means the way out is the way in again with the same
 * fights in it. That is a treadmill rather than a dungeon, and it also quietly makes retreating the
 * wrong move in a game where retreating should be a move.
 *
 * <p>
 * The second is arithmetic. A tier 1 rift is 288 rooms and carries something like nine hundred
 * spawners, and a rift twelve tiers up carries more; a block entity that keeps ticking after it has
 * done its job is nine hundred pieces of upkeep for the rest of the run. Removing itself is not an
 * optimisation bolted on afterwards, it is the reason the count can be that high at all.
 *
 * <p>
 * The third is that it is honest about what these are. They are not encounters to be farmed, they
 * are how the dungeon is populated &mdash; the same job the baked-in mobs of a hand-built dungeon do,
 * done at generation time because the rooms are procedural and their authors could not know where
 * the floors would end up.
 *
 * <h2>What it makes</h2>
 * {@link RiftMobs} answers that from two entity-type tags, the rift's theme choosing between them,
 * and {@link MobScaling} scales what comes out to the rift's tier. Both happen at the moment the
 * spawner fires rather than at generation, so one rift's roster is one decision in one place.
 *
 * <h2>Why {@code finalizeSpawn} runs, and runs first</h2>
 * It used to be skipped, on the grounds that a mob's own spawn code writes attributes and whatever
 * runs last wins. The premise is right and the conclusion was wrong: that argues for <em>ordering</em>
 * the two, not for dropping one of them. What {@code finalizeSpawn} mostly does is not attributes at
 * all &mdash; it is the only place a vanilla mob is given its equipment. Skipping it made every
 * pillager in the rift a crossbowman with no crossbow, and a pillager has no melee goal to fall back
 * on, so it was harmless: it walked up and stood there. Skeletons were less obvious but not much
 * better, armed by {@code reassessWeaponGoal} into a punch. A slime never got a size.
 *
 * <p>
 * So it runs, and it runs before {@link MobScaling}, which is the order that makes both work: the mob
 * equips itself and settles its own attributes, then the tier curve is applied on top of the result.
 * Slimes force the same order independently &mdash; {@code setSize} resets current health, so a slime
 * finalized after scaling would arrive at its unscaled health.
 *
 * <p>
 * Called through NeoForge's {@code EventHooks} rather than directly, which is what the platform asks
 * of callers: it fires {@code FinalizeSpawnEvent} first, so a mod that wants no rift mob of some kind
 * can say so, and a cancelled spawn is blocked by the platform without this needing to check.
 */
public class RiftMobSpawnerBlockEntity extends BlockEntity {

    /**
     * How close a player has to come, in blocks.
     *
     * <p>
     * Short enough that the room ahead is not emptying itself while the party is still in the last
     * one, and long enough that the mobs are not appearing inside the player. Roughly the far side of
     * a small room.
     */
    private static final double TRIGGER_RANGE = 10.0;

    /** Ticks between proximity checks. A twentieth of a second's accuracy is not worth anything here. */
    private static final int INTERVAL = 20;

    private static final int MIN_BATCH = 2;
    private static final int MAX_BATCH = 4;

    public RiftMobSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(RiftfallBlockEntities.RIFT_MOB_SPAWNER.get(), pos, state);
    }

    /**
     * Nothing is saved and nothing is loaded, deliberately.
     *
     * <p>
     * Everything this needs it can reach at the moment it fires: the roster comes from the level's
     * theme, the tier from the level, and the batch size off the level's own random. So a spawner
     * that has not fired is fully described by the block being there, and one that has fired is
     * described by the block not being there. There is no third state to persist and so no saved data
     * to get out of step with the world.
     *
     * <p>
     * The batch roll is a running random rather than one seeded on the position, because this fires
     * at runtime and is never asked to reproduce. Placement is the opposite case and is seeded: chunks
     * generate out of order across threads, and a rift must lay out identically every time it is built.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, RiftMobSpawnerBlockEntity spawner) {
        // Staggered by position so that a room's worth of spawners do not all check on the same tick.
        if ((level.getGameTime() + Mth.abs(pos.hashCode())) % INTERVAL != 0) {
            return;
        }
        if (!(level instanceof ServerLevel server)
                || !level.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, TRIGGER_RANGE)) {
            return;
        }
        trigger(server, pos);
    }

    /**
     * Fires one spawner now, wherever it is and whoever is watching.
     *
     * <p>
     * Split out from the tick so that {@code /riftfall rift spawners} can reach it: a spawner's
     * trigger is a player standing next to it, which is exactly the one thing a headless server has
     * none of, so without this the whole of what a spawner does could only be seen by hand.
     */
    public static void trigger(ServerLevel level, BlockPos pos) {
        // Before the mobs rather than after: the spawner stands in the block a mob is about to be put
        // in, and it is a full cube.
        level.removeBlock(pos, false);
        fire(level, pos);
    }

    private static void fire(ServerLevel level, BlockPos pos) {
        Optional<ResourceKey<RiftTheme>> theme = themeOf(level);
        if (theme.isEmpty()) {
            return;
        }
        spawnBatch(level, pos, theme.get(), RiftLevels.tier(level));
    }

    /** Shared by marker activation and the isolated-server encounter regression probe. */
    public static List<Mob> spawnBatch(ServerLevel level, BlockPos pos, ResourceKey<RiftTheme> theme, int tier) {
        List<Mob> spawned = new ArrayList<>();
        RandomSource random = level.getRandom();
        int batch = RelicSignatureCosts.batch(MIN_BATCH + random.nextInt(MAX_BATCH - MIN_BATCH + 1),
                RelicSignatureCosts.nearest(level, pos.getCenter(), TRIGGER_RANGE));
        boolean eliteSpawned = false;
        for (int index = 0; index < batch; index++) {
            Optional<EntityType<?>> type = RiftMobs.pick(theme, random, !eliteSpawned);
            if (type.isEmpty()) {
                break;
            }
            Entity entity = type.get().create(level);
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    random.nextFloat() * 360.0F, 0.0F);
            if (!RiftMobPlacement.place(level, mob, pos)) { mob.discard(); continue; }
            // Placed before this call, because it reads the mob's position to decide local difficulty
            // and to pick where a jockey or a swarm goes.
            com.cyberday1.riftfall.mob.SporeEncounters.authorize(mob);
            com.cyberday1.riftfall.mob.VanillaWorldSpawns.authorize(mob);
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()),
                    MobSpawnType.SPAWNER, null);
            // Finalization can enlarge a slime or other modded mob. Recheck its actual body.
            if (!RiftMobPlacement.place(level, mob, pos)) { mob.discard(); continue; }
            // A rift mob is part of the dungeon, so it stays put until something kills it. Without
            // this the far half of a rift would quietly empty itself while the party fought the near
            // half.
            mob.setPersistenceRequired();
            MobScaling.scale(mob, tier);
            if (level.addFreshEntity(mob)) {
                spawned.add(mob);
                eliteSpawned |= RiftMobBalance.elite(type.get());
            }
        }
        return spawned;
    }

    /**
     * The theme of the rift this spawner is standing in, or empty if it is not standing in one.
     *
     * <p>
     * Asked of the generator rather than of the dimension id, for the reason {@code RiftSpawns} gives:
     * the shipped {@code riftfall:rift} dimension is a rift and its id does not say so, so an id test
     * would leave every spawner in it inert.
     */
    private static Optional<ResourceKey<RiftTheme>> themeOf(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator
                ? Optional.of(generator.theme(level))
                : Optional.empty();
    }
}
