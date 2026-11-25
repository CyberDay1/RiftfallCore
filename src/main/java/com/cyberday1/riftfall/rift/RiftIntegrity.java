package com.cyberday1.riftfall.rift;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * A rift is exactly the dungeon that was authored, for as long as it exists.
 *
 * <p>
 * Every block in a rift was put there by somebody building a template, and the room is the encounter.
 * A party that can mine is a party that can dig around the fight, tunnel out of a sealed arena, drop
 * through a floor to skip half the dungeon, or strip the dressing for the blocks. None of those are
 * exploits anybody has to go looking for &mdash; they are the shortest path, and a dungeon that
 * permits them stops being a dungeon.
 *
 * <p>
 * So the rule is one-way: nothing in a rift comes apart. Not the dungeon, and not the ladder somebody
 * put down against it either. That is a stronger rule than sparing only the dungeon's own blocks, and
 * it is also a much smaller one. Telling a player's block from the dungeon's needs a set of every
 * position anybody ever built at, kept per level for the life of the run; a level where nothing at all
 * may be broken needs no set, no bookkeeping and no memory. The strict rule is the cheap one.
 *
 * <h2>Building is allowed, and that is a decision rather than an oversight</h2>
 * It was banned to begin with, on the same argument as mining: a party that can build can pillar over
 * a gap the room meant them to cross, wall a doorway against whatever is chasing them, or bridge
 * across an arena the fight was laid out inside. That was overruled, and the reason is the one that
 * beats a tidy rule &mdash; the rooms are an imported library that was never authored against this
 * restriction, and several of them do not hand you a way across on their own. A party with nothing to
 * put down cannot finish them. A room crossed the short way is a worse dungeon; a room that cannot be
 * crossed at all is not one.
 *
 * <p>
 * The cost is accepted rather than argued away. If routing around rooms turns out to be how rifts get
 * played, the answer is rooms that close their own gaps, not a rule that makes the ones we have
 * unfinishable. Note the asymmetry this leaves: what you place, you cannot take back, because nothing
 * in a rift breaks. Scaffolding is spent for the run and goes with the level.
 *
 * <h2>What each way in is worth</h2>
 * Mining is refused at {@link BlockEvent.BreakEvent}, which is the authority.
 *
 * <p>
 * Buckets stay refused, in both directions, and they are the one thing building being allowed did not
 * settle. A block put down is a block; a bucket poured is terrain that flows, spreads through the room
 * it was poured in and cannot be taken back out of it, and the same item empties the pool a room was
 * built around. The scooping half is the strong argument, and it is why this did not simply follow
 * placement. The item is denied rather than the interaction, so holding a bucket does not stop you
 * opening a chest.
 *
 * <p>
 * Explosions are emptied rather than cancelled. A stack of TNT is portable and a creeper is free, so a
 * blast must not be able to open a wall &mdash; but it still happens, and it still throws whoever is
 * near it. Cancelling the event outright would delete the knockback, which is half of what a boss's
 * explosion is for.
 *
 * <p>
 * Mobs are refused separately, and have to be, because every rule above this one is a player's. The
 * break event is only ever fired for a player, so an enderman lifting a block, a ravager going through
 * a wall or a wither clearing its own path would each have walked straight past all of it. Two hooks
 * cover it: the griefing check, which is most of them, and the demolition event for the few that never
 * consult it. This matters most exactly where the party is least able to afford it &mdash; a boss room
 * is a sealed arena, and the boss is the thing most likely to be able to open it.
 *
 * <h2>Why the cracking is suppressed on both sides</h2>
 * {@link PlayerEvent.BreakSpeed} is cancelled so a block never cracks at all, which is how a block is
 * made to feel unbreakable rather than merely left unbroken. Refusing the break alone is already
 * correct without this: the client predicts its own break against a sequence number, the server
 * acknowledges that sequence having changed nothing, and {@code BlockStatePredictionHandler} puts the
 * block back. Nothing is lost and no hole survives. What is lost is the second the player spent
 * swinging, every time, at every wall they will ever test.
 *
 * <p>
 * That listener runs on whichever side asked, so suppressing the animation means both sides have to be
 * able to answer the same question. They can, and it survived building being allowed for the reason
 * the break rule was written one-way in the first place: the answer does not depend on who put the
 * block there, because in a rift nothing is breakable, the party's own scaffolding included. All
 * either side needs to know is whether it is standing in one. That is what {@link RiftLevels#isRift}
 * answers &mdash; it reads the dimension's name and nothing else, so the client can ask it too, which
 * is why it is used here in preference to the more thorough test below.
 *
 * <p>
 * It is worth seeing that as a constraint rather than a happy accident. The day a player is allowed to
 * mine back something they placed, this suppression has to go or be told apart per block, and there is
 * no cheap way to tell a client which blocks those are.
 *
 * <p>
 * A rift that is not a run instance &mdash; the shipped {@code riftfall:rift}, or a datapack's own
 * &mdash; is named differently, so neither side recognises it and the block cracks, vanishes for a
 * tick and comes back. It is still unbreakable; it just costs a swing to find out. Nobody plays those,
 * and getting it wrong there is worth not having a rule that is true on one side and truer on the
 * other.
 *
 * <h2>Rift ore breaks</h2>
 * {@code #riftfall:rift_mineable} is the exception. The ore passes only fill cells the room authored as
 * air, or a prop's own pocket, so mining opens nothing; a tag, so the client agrees. {@code RiftOresTest}
 * keeps tagged blocks out of the authored library.
 *
 * <h2>Creative is exempt</h2>
 * Somebody has to be able to open a wall to look behind it, and every alternative &mdash; a command, a
 * permission, a config &mdash; is a second mechanism for a thing creative mode already means. A
 * creative player is not a party member.
 */
public final class RiftIntegrity {

    private RiftIntegrity() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RiftIntegrity::refuseBreak);
        NeoForge.EVENT_BUS.addListener(RiftIntegrity::refuseBucketOnBlock);
        NeoForge.EVENT_BUS.addListener(RiftIntegrity::refuseBucketInAir);
        NeoForge.EVENT_BUS.addListener(RiftIntegrity::refuseCracking);
        NeoForge.EVENT_BUS.addListener(RiftIntegrity::spareFromExplosion);
        NeoForge.EVENT_BUS.addListener(RiftIntegrity::refuseGriefing);
        NeoForge.EVENT_BUS.addListener(RiftIntegrity::refuseMobDemolition);
    }

    /** Nothing in a rift comes out, and this is the promise the rest of them only decorate. */
    private static void refuseBreak(BlockEvent.BreakEvent event) {
        if (isRift(event.getLevel()) && !event.getPlayer().isCreative()
                && !com.cyberday1.riftfall.worldgen.ore.RiftOres.mineable(event.getState())
                && !com.cyberday1.riftfall.tool.LootingToolEffects.personalBreak(event.getPlayer(), event.getState())) {
            event.setCanceled(true);
        }
    }

    /** A bucket used against a block: pouring into the room, or scooping the room's own pool out. */
    private static void refuseBucketOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isBucketInRift(event.getLevel(), event.getEntity(), event.getItemStack())) {
            event.setUseItem(TriState.FALSE);
        }
    }

    /** And the same bucket aimed at nothing, which is how a fluid is picked up from a distance. */
    private static void refuseBucketInAir(PlayerInteractEvent.RightClickItem event) {
        if (isBucketInRift(event.getLevel(), event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    /**
     * Stops the block cracking in the first place, on both sides, for the reason the class comment
     * spends most of its length on: one side alone leaves a hole that is not there.
     */
    private static void refuseCracking(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!player.isCreative() && RiftLevels.isRift(player.level().dimension())
                && !com.cyberday1.riftfall.worldgen.ore.RiftOres.mineable(event.getState())
                && !com.cyberday1.riftfall.tool.LootingToolEffects.personalBreak(player, event.getState())) {
            event.setCanceled(true);
        }
    }

    /**
     * Takes every block out of a blast's list.
     *
     * <p>
     * Filtered rather than cancelled: the explosion still happens and still throws whoever is near it.
     * Only the terrain is spared, which in a rift is all of it.
     */
    private static void spareFromExplosion(ExplosionEvent.Detonate event) {
        if (isRift(event.getLevel())) {
            event.getAffectedBlocks().clear();
        }
    }

    /**
     * The same rule applied to whatever is chasing the party.
     *
     * <p>
     * {@link BlockEvent.BreakEvent} is a player's event, so every rule above it stops at the party.
     * An enderman lifts a block out of the floor without firing one, and the room it does it to is the
     * one a boss fight was laid out inside.
     */
    private static void refuseGriefing(EntityMobGriefingEvent event) {
        if (isRift(event.getEntity().level())) {
            event.setCanGrief(false);
        }
    }

    /** The demolition that never consults that rule at all: a wither, the dragon, a zombie on a door. */
    private static void refuseMobDemolition(LivingDestroyBlockEvent event) {
        if (isRift(event.getEntity().level())) {
            event.setCanceled(true);
        }
    }

    private static boolean isBucketInRift(Level level, Player player, ItemStack stack) {
        return stack.getItem() instanceof BucketItem && !player.isCreative() && isRift(level);
    }

    /**
     * Whether a level is a rift, asked of the generator for the reason {@link RiftSpawns} gives: the
     * shipped {@code riftfall:rift}, a run's throwaway instance and a datapack's own rift are all
     * rifts, and only the generator is true of all three.
     *
     * <p>
     * Server-side only, and every listener that uses it is server-side only. The one that is not
     * &mdash; {@link #refuseCracking} &mdash; asks {@link RiftLevels#isRift} instead, on purpose.
     */
    private static boolean isRift(LevelAccessor level) {
        return level instanceof ServerLevel server && RiftLevels.isRiftWorld(server);
    }
}
