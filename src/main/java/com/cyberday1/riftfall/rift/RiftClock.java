package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallConfig;
import com.cyberday1.riftfall.network.RiftClockPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Tells a client how long its run has left (R-1). The run's clock and the level's game time both advance once a
 * server tick while anybody holds the rift, so one end time stays true; the resync covers a config reload.
 */
public final class RiftClock {

    /** How often a running rift repeats its clock to everybody inside, in run ticks. */
    static final int RESYNC = 600;

    private RiftClock() {
    }

    /** What a client in this run should count down; {@link RiftClockPayload#NONE} with no run or no limit. */
    static RiftClockPayload of(@Nullable RiftRun run, long gameTime, int limitTicks) {
        if (run == null || limitTicks <= 0) {
            return RiftClockPayload.NONE;
        }
        return new RiftClockPayload(gameTime + Math.max(0, limitTicks - run.elapsed()), limitTicks);
    }

    /** A client that is new or has changed level: its clock, or none. From {@code RiftfallPayloads#tell}. */
    public static void tell(ServerPlayer player) {
        send(player, of(RiftRuns.in(player.level().dimension()), player.level().getGameTime(),
                RiftRuns.limitTicks(RiftfallConfig.RIFT_TIME_LIMIT_MINUTES.get())));
    }

    /** From the run sweep, every {@link #RESYNC} ticks of a run. */
    static void resync(ServerLevel rift, RiftRun run, int limitTicks) {
        if (run.elapsed() % RESYNC != 0) {
            return;
        }
        RiftClockPayload clock = of(run, rift.getGameTime(), limitTicks);
        for (ServerPlayer player : rift.players()) {
            send(player, clock);
        }
    }

    private static void send(ServerPlayer player, RiftClockPayload clock) {
        // A fake player has no client to tell.
        if (!(player instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(player, clock);
        }
    }
}
