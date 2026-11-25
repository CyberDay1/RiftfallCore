package com.cyberday1.riftfall.boss;

import com.cyberday1.riftfall.block.marker.MarkerKind;
import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import com.cyberday1.riftfall.network.BossFinderPayload;
import com.cyberday1.riftfall.rift.RiftLevelState;
import com.cyberday1.riftfall.rift.RiftRun;
import com.cyberday1.riftfall.rift.RiftRuns;
import com.cyberday1.riftfall.worldgen.layout.RiftPlan;
import com.cyberday1.riftfall.worldgen.level.RiftChunkGenerator;
import com.cyberday1.riftfall.worldgen.space.TripleMirror;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The HUD arrow's target: the boss room's {@code boss_entry} pad, found from the plan and the template so that
 * entering a rift never generates the boss room. Sent on entry, relog and respawn; cleared on leaving and when
 * the boss dies.
 */
public final class BossFinder {

    /** Which marker the target came from; a room without one falls back down the list. */
    public enum Source { ENTRY, PAD, CENTRE }

    public record Choice(BlockPos pos, Source source) {
    }

    /** The last payload sent to each player, and how many; read by the probe. */
    private static final Map<UUID, BossFinderPayload> LAST = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SENT = new ConcurrentHashMap<>();

    private BossFinder() {
    }

    /** The entry pad, else the boss's pad, else the room's centre. */
    static Choice choose(Optional<BlockPos> entry, Optional<BlockPos> pad, BoundingBox room) {
        if (entry.isPresent()) {
            return new Choice(entry.get(), Source.ENTRY);
        }
        if (pad.isPresent()) {
            return new Choice(pad.get(), Source.PAD);
        }
        return new Choice(room.getCenter(), Source.CENTRE);
    }

    /** A template-local position in the world, as the room was placed (see TemplatePlacementTest). */
    static BlockPos place(Vec3i local, Vec3i size, TripleMirror mirror, Vec3i corner) {
        return new BlockPos(corner.offset(mirror.apply(local, size)));
    }

    /** Where the arrow points in this rift, or empty without a boss room. Reads no blocks. */
    public static Optional<Choice> target(ServerLevel rift) {
        if (!(rift.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator)) {
            return Optional.empty();
        }
        RiftPlan.PlacedRoom room = generator.bossRoom(rift);
        if (room == null) {
            return Optional.empty();
        }
        // The walk consumes the markers; once it has run, what it found is the truth.
        RiftLevelState state = RiftLevelState.of(rift);
        if (state.bossResolved()) {
            return Optional.of(choose(state.bossEntry(), state.bossPad(), room.bounds()));
        }
        StructureTemplate template = rift.getStructureManager().get(room.template()).orElse(null);
        if (template == null) {
            return Optional.of(choose(Optional.empty(), Optional.empty(), room.bounds()));
        }
        return Optional.of(choose(marker(template, MarkerKind.BOSS_ENTRY, room),
                marker(template, MarkerKind.BOSS_ARENA, room), room.bounds()));
    }

    private static Optional<BlockPos> marker(StructureTemplate template, MarkerKind kind, RiftPlan.PlacedRoom room) {
        List<StructureTemplate.StructureBlockInfo> found =
                template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), RiftfallMarkers.block(kind));
        return found.isEmpty() ? Optional.empty()
                : Optional.of(place(found.getFirst().pos(), template.getSize(), room.mirror(), room.corner()));
    }

    /** The boss has died here, by this flag or by an older run's verdict. */
    public static boolean defeated(ServerLevel rift) {
        if (RiftLevelState.of(rift).bossDefeated()) {
            return true;
        }
        RiftRun run = RiftRuns.in(rift.dimension());
        return run != null && run.verdict() == RiftRun.Verdict.CLEARED;
    }

    /** What a player standing in {@code level} should be told. */
    public static BossFinderPayload payload(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof RiftChunkGenerator) || defeated(level)) {
            return BossFinderPayload.NONE;
        }
        BoundingBox box = RiftBossArena.room(level);
        Choice choice = target(level).orElse(null);
        if (box == null || choice == null) {
            return BossFinderPayload.NONE;
        }
        return BossFinderPayload.of(new BossFinderPayload.Target(level.dimension().location(), choice.pos(),
                new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ())));
    }

    /** From {@code RiftfallPayloads#tell}: entry, leaving, relog and respawn. */
    public static void tell(ServerPlayer player) {
        send(player, payload(player.serverLevel()));
    }

    /** From {@code RiftBosses#openTheWayHome}: the boss is dead, so every arrow in this rift goes. */
    static void bossDown(ServerLevel rift) {
        RiftLevelState.of(rift).bossDefeated(true);
        for (ServerPlayer player : List.copyOf(rift.players())) {
            send(player, BossFinderPayload.NONE);
        }
    }

    private static void send(ServerPlayer player, BossFinderPayload payload) {
        LAST.put(player.getUUID(), payload);
        SENT.merge(player.getUUID(), 1, Integer::sum);
        if (!(player instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static @Nullable BossFinderPayload lastSent(UUID player) {
        return LAST.get(player);
    }

    public static int sent(UUID player) {
        return SENT.getOrDefault(player, 0);
    }

    public static void forget(UUID player) {
        LAST.remove(player);
        SENT.remove(player);
    }
}
