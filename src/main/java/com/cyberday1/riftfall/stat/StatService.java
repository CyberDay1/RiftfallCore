package com.cyberday1.riftfall.stat;

import com.cyberday1.riftfall.progression.RiftXp;
import com.cyberday1.riftfall.rift.RiftLevels;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

/**
 * The server verb for attribute points: the sheet's Confirm and {@code /riftfall stats add} both end here.
 * The client sends only the seven deltas; the level, class, rift and pool are read here, and a batch lands
 * whole or not at all.
 */
public final class StatService {

    private StatService() {
    }

    /** The player's level from stored XP, as {@code SkillService.levelOf} reads it. */
    static int level(RiftStats stats, UUID player) {
        return RiftXp.levelFor(stats.xpFor(player), RiftXp.LEVEL_CAP);
    }

    public static int earned(RiftStats stats, UUID player) {
        return AttributePoints.earned(level(stats, player), stats.hasUnseatedBonus(player));
    }

    public static int available(RiftStats stats, UUID player) {
        return AttributePoints.available(level(stats, player), stats.hasUnseatedBonus(player), stats.allocFor(player));
    }

    /** Checks and stores a batch, without touching an entity. Nothing is written unless the answer is OK. */
    static AttributePoints.Allocate allocate(RiftStats stats, UUID player, boolean inRift,
                                             Map<StatBlock.Primary, Integer> deltas) {
        int level = level(stats, player);
        boolean bonus = stats.hasUnseatedBonus(player);
        StatBlock alloc = stats.allocFor(player);
        AttributePoints.Allocate result = AttributePoints.check(level, bonus, alloc, stats.bookFor(player).skillClass(),
                inRift, deltas);
        if (result == AttributePoints.Allocate.OK) {
            stats.setAlloc(player, AttributePoints.allocated(level, bonus, alloc, deltas));
        }
        return result;
    }

    /** Places a batch and puts it on the player in the same call. */
    public static AttributePoints.Allocate allocate(ServerPlayer player, Map<StatBlock.Primary, Integer> deltas) {
        RiftStats stats = RiftStats.of(player.server);
        AttributePoints.Allocate result = allocate(stats, player.getUUID(),
                RiftLevels.isRift(player.level().dimension()), deltas);
        if (result == AttributePoints.Allocate.OK) {
            StatModifiers.storeAlloc(player, stats.allocFor(player.getUUID()));
        }
        return result;
    }

    /**
     * The Unseated's ten points, once (D4: every player in the arena when the binding contains it). Returns
     * whether this call granted them. The containment calls it through {@code StatCommand.awardUnseated}.
     */
    public static boolean grantUnseatedBonus(ServerPlayer player) {
        return RiftStats.of(player.server).grantUnseatedBonus(player.getUUID());
    }
}
