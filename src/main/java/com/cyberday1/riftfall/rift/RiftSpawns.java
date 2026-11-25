package com.cyberday1.riftfall.rift;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Nothing wanders into a rift.
 *
 * <p>
 * A rift is a dungeon, and a dungeon's population is meant to be a thing it was built with. The
 * primary guarantee is not here: {@code riftfall:rift} is a biome with an empty spawn list, and a
 * rift level is created with no custom spawners, so vanilla's spawning machinery has nothing to draw
 * from before this class is consulted. This is the backstop for the case that machinery is handed
 * something anyway.
 *
 * <h2>Why a backstop at all</h2>
 * An empty spawn list is a fact about our data, and data is what a modpack edits. A biome modifier
 * from another mod, or a datapack the player installed, can add spawns to any biome it can name --
 * ours is in no tags, which is most of the protection, but "most" is not the answer the feature
 * wants. The list can be added to; this cannot, because it does not read the list.
 *
 * <h2>What counts as natural</h2>
 * The spawns cancelled here are the ones that happen on their own:
 *
 * <ul>
 * <li>{@link MobSpawnType#NATURAL} -- the per-tick spawner that fills a loaded world.
 * <li>{@link MobSpawnType#CHUNK_GENERATION} -- the herd of animals a fresh chunk is born with.
 * <li>{@link MobSpawnType#PATROL} -- pillager patrols, which arrive from a {@code CustomSpawner}.
 * </ul>
 *
 * <p>
 * Everything else is left alone, because everything else is somebody's doing: a spawner block or a
 * spawn egg the dungeon was built with, a summon from a command or a boss fight, a conversion, a
 * mob a player bred or bucketed. Turning those off would not be turning spawning off, it would be
 * taking the dungeon's own tools away from it.
 */
public final class RiftSpawns {

    private static final Set<MobSpawnType> NATURAL =
            EnumSet.of(MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION, MobSpawnType.PATROL);

    private RiftSpawns() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RiftSpawns::cancelNaturalSpawns);
    }

    /**
     * The last point at which a spawn can be refused: NeoForge drops a mob cancelled here rather
     * than adding it to the level, so it never exists as far as the world is concerned.
     */
    private static void cancelNaturalSpawns(FinalizeSpawnEvent event) {
        if (NATURAL.contains(event.getSpawnType()) && isRift(event.getLevel().getLevel())) {
            event.setSpawnCancelled(true);
        }
    }

    /**
     * Whether a level is a rift.
     *
     * <p>
     * Asked of the generator rather than the dimension id, because there are three ways to be one
     * and only this is true of all of them: the shipped {@code riftfall:rift}, the throwaway level a
     * portal opens at {@code riftfall:rifts/<uuid>}, and any dimension a datapack builds on our
     * generator. {@link RiftLevels#isRift} answers a different question -- whether a level is a
     * temporary instance that gets deleted -- and would say no to the first and third.
     */
    private static boolean isRift(ServerLevel level) {
        return RiftLevels.isRiftWorld(level);
    }
}
