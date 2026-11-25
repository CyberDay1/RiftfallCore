package com.cyberday1.riftfall.boss;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.RiftfallConfig;
import com.cyberday1.riftfall.rift.RiftRun;
import com.cyberday1.riftfall.rift.RiftRuns;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The party half of the boss gate: who is waited for, the countdown they see, and where they land
 * when the wait ends. "Party" is the run's roster, as everywhere else in a rift.
 */
public final class BossGatePull {

    /** How far past the doorway's plane a body is clear of the rising bars; the trigger's own margin. */
    static final double CLEAR_DEPTH = 1.5;

    /** How far from the arrival pad, in blocks, arrivals are spread before any are stacked. */
    static final int SPREAD = 2;

    static final int DEFAULT_DELAY_SECONDS = 5;

    static final String COUNTDOWN = "message." + RiftfallCore.MODID + ".boss_gate_countdown";

    private BossGatePull() {
    }

    /** The configured wait in ticks; the default when the server config is not loaded. */
    public static int delayTicks() {
        int seconds = RiftfallConfig.SPEC.isLoaded() ? RiftfallConfig.BOSS_GATE_DELAY_SECONDS.get()
                : DEFAULT_DELAY_SECONDS;
        return Math.max(0, seconds) * 20;
    }

    /** Whole seconds left on a countdown, rounded up so the last shown number is 1. */
    static int secondsLeft(int ticksLeft) {
        return Math.max(0, (ticksLeft + 19) / 20);
    }

    /** Who the gate waits for and pulls in: alive, playing, and on the run's roster when there is a run. */
    static boolean eligible(boolean alive, boolean spectator, boolean onRoster) {
        return alive && !spectator && onRoster;
    }

    /** Every party member standing in this rift who is alive and playing. */
    public static List<ServerPlayer> party(ServerLevel rift) {
        RiftRun run = RiftRuns.in(rift.dimension());
        List<ServerPlayer> party = new ArrayList<>();
        for (ServerPlayer player : rift.players()) {
            if (eligible(player.isAlive(), player.isSpectator(),
                    run == null || run.entrants().contains(player.getUUID()))) {
                party.add(player);
            }
        }
        return party;
    }

    /**
     * Signed distance from the doorway's middle plane toward the boss pad: positive is inside. The
     * plane is the long axis of the gate rectangle, the same choice {@link BossGate#inTriggerRange}
     * makes.
     */
    public static double depth(double x, double z, BlockPos pad, List<BlockPos> gate) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : gate) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (maxX - minX >= maxZ - minZ) {
            double plane = (minZ + maxZ + 1) * 0.5;
            return (z - plane) * Math.signum(pad.getZ() + 0.5 - plane);
        }
        double plane = (minX + maxX + 1) * 0.5;
        return (x - plane) * Math.signum(pad.getX() + 0.5 - plane);
    }

    /** Whether someone here is in the room and at least {@code margin} past the doorway's plane. */
    public static boolean inside(double x, double y, double z, @Nullable BoundingBox room, Optional<BlockPos> pad,
            List<BlockPos> gate, double margin) {
        if (room == null || !room.isInside(Mth.floor(x), Mth.floor(y), Mth.floor(z))) {
            return false;
        }
        return pad.isEmpty() || gate.isEmpty() || depth(x, z, pad.get(), gate) >= margin;
    }

    /** Whether this player is already in the room and clear of the bars, so need not be moved. */
    static boolean clear(ServerPlayer player, @Nullable BoundingBox room, Optional<BlockPos> pad,
            List<BlockPos> gate) {
        return inside(player.getX(), player.getY(), player.getZ(), room, pad, gate, CLEAR_DEPTH);
    }

    /** The party members the gate would have to pull in: everyone not already clear inside. */
    static List<ServerPlayer> outside(ServerLevel rift, List<BlockPos> gate) {
        BoundingBox room = RiftBossArena.room(rift);
        Optional<BlockPos> pad = RiftBossArena.pad(rift);
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : party(rift)) {
            if (!clear(player, room, pad, gate)) {
                out.add(player);
            }
        }
        return out;
    }

    /** The countdown, on the action bar of every party member in the rift. */
    static void tell(ServerLevel rift, int seconds) {
        Component line = Component.translatable(COUNTDOWN, seconds);
        for (ServerPlayer player : party(rift)) {
            player.displayClientMessage(line, true);
        }
    }

    /**
     * Arrival spots around {@code entry}, nearest first, one per arrival. When fewer are free than
     * asked for, the free ones are reused in turn; with none free, everyone lands on the entry.
     */
    static List<BlockPos> spots(BlockPos entry, int count, Predicate<BlockPos> free) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -SPREAD; dx <= SPREAD; dx++) {
            for (int dz = -SPREAD; dz <= SPREAD; dz++) {
                candidates.add(entry.offset(dx, 0, dz));
            }
        }
        candidates.sort(Comparator.comparingInt((BlockPos p) -> (int) p.distSqr(entry))
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos candidate : candidates) {
            if (found.size() == count) {
                break;
            }
            if (free.test(candidate)) {
                found.add(candidate);
            }
        }
        if (found.isEmpty()) {
            found.add(entry);
        }
        List<BlockPos> out = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            out.add(found.get(index % found.size()));
        }
        return out;
    }

    /**
     * Moves every party member who is not already clear inside onto the arrival pad, spread out, and
     * answers how many moved. Nobody moves when the room has no {@code boss_entry}: the pad is the one
     * block a party must not be put on.
     */
    static int pull(ServerLevel rift, List<BlockPos> gate) {
        Optional<BlockPos> entry = RiftBossArena.entry(rift);
        if (entry.isEmpty()) {
            return 0;
        }
        List<ServerPlayer> moving = outside(rift, gate);
        if (moving.isEmpty()) {
            return 0;
        }
        BoundingBox room = RiftBossArena.room(rift);
        Optional<BlockPos> pad = RiftBossArena.pad(rift);
        Set<BlockPos> taken = new HashSet<>();
        for (ServerPlayer player : party(rift)) {
            if (!moving.contains(player)) {
                taken.add(player.blockPosition());
            }
        }
        List<BlockPos> spots = spots(entry.get(), moving.size(),
                at -> !taken.contains(at) && standable(rift, at, room, pad, gate));
        for (int index = 0; index < moving.size(); index++) {
            ServerPlayer player = moving.get(index);
            BlockPos at = spots.get(index);
            float yaw = pad.map(p -> (float) (Mth.atan2(p.getZ() - at.getZ(), p.getX() - at.getX())
                    * (180.0F / Math.PI)) - 90.0F).orElse(player.getYRot());
            // Same level, so teleportTo never reaches changeDimension and the boundary never sees it.
            player.teleportTo(rift, at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, yaw, 0.0F);
            player.resetFallDistance();
        }
        return moving.size();
    }

    /** A cell a player can stand in: inside and clear of the bars, on a floor, with room for the body. */
    private static boolean standable(ServerLevel rift, BlockPos at, @Nullable BoundingBox room,
            Optional<BlockPos> pad, List<BlockPos> gate) {
        if (!inside(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, room, pad, gate, CLEAR_DEPTH)) {
            return false;
        }
        BlockPos below = at.below();
        if (rift.getBlockState(below).getCollisionShape(rift, below).isEmpty()) {
            return false;
        }
        return rift.noCollision(new AABB(at.getX() + 0.2, at.getY(), at.getZ() + 0.2,
                at.getX() + 0.8, at.getY() + 1.8, at.getZ() + 0.8));
    }
}
