package com.cyberday1.riftfall.boss;

import com.cyberday1.riftfall.rift.RiftLevelState;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.cyberday1.riftfall.rift.RiftRun;
import com.cyberday1.riftfall.rift.RiftRuns;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.List;
import java.util.UUID;

/**
 * Remembers party members who die in a rift during its boss fight, so the kill still rolls for them.
 * "In the fight": on the run's roster, and dying in the rift once the gate has started to close and
 * before the boss is dead. Saved with the rift.
 */
public final class BossFallen {

    private BossFallen() {
    }

    static void register() {
        // LOWEST, and not for cancelled events: a death a totem or another mod refused is no death.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BossFallen::died);
    }

    /** Whether a death counts as falling in the fight. */
    static boolean counts(boolean fightOn, boolean bossDefeated, boolean onRoster, boolean spectator) {
        return fightOn && !bossDefeated && onRoster && !spectator;
    }

    /** Everyone recorded as fallen in this rift's fight. */
    public static List<UUID> of(ServerLevel rift) {
        RiftLevelState state = RiftLevelState.peek(rift);
        return state == null ? List.of() : state.fallen();
    }

    private static void died(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel rift)
                || !RiftLevels.isRiftWorld(rift)) {
            return;
        }
        RiftLevelState state = RiftLevelState.of(rift);
        RiftRun run = RiftRuns.in(rift.dimension());
        if (counts(BossGate.fightOn(rift), state.bossDefeated(),
                run == null || run.entrants().contains(player.getUUID()), player.isSpectator())) {
            state.fell(player.getUUID());
        }
    }
}
