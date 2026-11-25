package com.cyberday1.riftfall.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The backstop behind the gallery's barrier wall: a rift boss in its room takes no damage from a
 * player outside that room, or from one standing in the entrance gallery.
 *
 * <p>
 * The wall stops anything that collides with blocks. This catches what does not: an area spell
 * centred on the caster reaches a boss standing under the ledge through its floor.
 *
 * <p>
 * The gallery is found, not listed: the open cells reachable from the boss_entry marker without
 * crossing a solid block, the doorway or the one-way glass. Panes count as open because a bullet
 * can remove them. A flood that reaches the pad or outgrows {@link #MAX_CELLS} is a room with no
 * wall, which answers "no gallery" rather than refusing the whole arena.
 */
public final class BossGallery {

    /** Nearly three times the sealed gallery's 1,489 cells, and a tenth of the arena's air. */
    static final int MAX_CELLS = 4096;

    private static final Map<ServerLevel, Set<Long>> GALLERIES = new HashMap<>();

    private BossGallery() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BossGallery::refuse);
        NeoForge.EVENT_BUS.addListener(BossGallery::forgetLevel);
    }

    private static void refuse(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || player.isCreative()
                || RiftBosses.tracked(event.getEntity()) == null
                || !(event.getEntity().level() instanceof ServerLevel rift)) {
            return;
        }
        BoundingBox room = RiftBossArena.room(rift);
        if (room == null) {
            return;
        }
        boolean elsewhere = player.level() != rift;
        if (refused(room, event.getEntity().blockPosition(), elsewhere ? null : player.blockPosition(),
                elsewhere ? Set.of() : gallery(rift))) {
            event.setCanceled(true);
        }
    }

    /**
     * Whether a hit from a player at {@code attacker} (null: another level) is refused. A boss that
     * has left its room is fought where it stands.
     */
    static boolean refused(BoundingBox room, BlockPos boss, @Nullable BlockPos attacker, Set<Long> gallery) {
        if (!room.isInside(boss)) {
            return false;
        }
        return attacker == null || !room.isInside(attacker) || gallery.contains(attacker.asLong());
    }

    /** The rift's gallery cells as packed positions, empty for a room without a sealed one. */
    public static Set<Long> gallery(ServerLevel rift) {
        Set<Long> known = GALLERIES.get(rift);
        if (known != null) {
            return known;
        }
        BoundingBox room = RiftBossArena.room(rift);
        Optional<BlockPos> entry = RiftBossArena.entry(rift);
        Optional<BlockPos> pad = RiftBossArena.pad(rift);
        if (room == null || entry.isEmpty() || pad.isEmpty()) {
            GALLERIES.put(rift, Set.of());
            return Set.of();
        }
        List<BlockPos> gate = RiftBossArena.gate(rift);
        Set<Long> cells = flood(entry.get(), pad.get(), room, gate,
                pos -> rift.isLoaded(pos) ? open(rift, pos) : null);
        if (cells == null) {
            // An unloaded cell: ask again once the room is in.
            return Set.of();
        }
        GALLERIES.put(rift, cells);
        return cells;
    }

    /** A cell a projectile can sit in, or a pane one can shatter. */
    private static boolean open(ServerLevel rift, BlockPos pos) {
        BlockState state = rift.getBlockState(pos);
        return state.getBlock() instanceof IronBarsBlock
                || state.getCollisionShape(rift, pos, CollisionContext.empty()).isEmpty();
    }

    /**
     * The open cells joined to {@code entry} inside {@code room}, with the doorway shut. Null when
     * {@code open} answers null (an unloaded cell); empty when the flood reaches the pad or passes
     * {@link #MAX_CELLS}.
     */
    static @Nullable Set<Long> flood(BlockPos entry, BlockPos pad, BoundingBox room, List<BlockPos> gate,
            Function<BlockPos, Boolean> open) {
        Set<Long> shut = new HashSet<>();
        for (BlockPos pos : gate) {
            shut.add(pos.asLong());
        }
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        seen.add(entry.asLong());
        todo.add(entry);
        long padCell = pad.asLong();
        long abovePad = pad.above().asLong();
        while (!todo.isEmpty()) {
            BlockPos pos = todo.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                long key = next.asLong();
                if (seen.contains(key) || shut.contains(key) || !room.isInside(next)) {
                    continue;
                }
                Boolean passable = open.apply(next);
                if (passable == null) {
                    return null;
                }
                if (!passable) {
                    continue;
                }
                if (key == padCell || key == abovePad || seen.size() >= MAX_CELLS) {
                    return Set.of();
                }
                seen.add(key);
                todo.add(next);
            }
        }
        return Set.copyOf(seen);
    }

    /** Forgets a cached gallery, as unloading the rift does. For the probe. */
    public static void forget(ServerLevel rift) {
        GALLERIES.remove(rift);
    }

    private static void forgetLevel(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            GALLERIES.remove(level);
        }
    }
}
