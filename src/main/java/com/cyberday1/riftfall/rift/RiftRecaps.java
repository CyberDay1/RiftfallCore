package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.gear.GearPool;
import com.cyberday1.riftfall.gear.GunGrade;
import com.cyberday1.riftfall.gear.RiftLootRarity;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.network.RiftRecapPayload;
import com.cyberday1.riftfall.progression.RiftXp;
import com.cyberday1.riftfall.progression.XpAwards;
import com.cyberday1.riftfall.stat.RiftStats;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * The end-of-run recap: counts what each entrant finds while a run is live, writes one
 * {@link RiftRecap} when their XP is banked, and shows it once they are standing outside a rift.
 * Nothing here changes what a run pays; held gear is handed back as the recap goes out.
 */
public final class RiftRecaps {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** How often players owed a recap are checked, in ticks. */
    private static final int DELIVERY_INTERVAL = 20;

    private RiftRecaps() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RiftRecaps::tick);
    }

    /** {@code /riftfall recap}: the caller's run history. */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("recap").executes(context -> {
            history(context.getSource().getPlayerOrException());
            return 1;
        });
    }

    /** Sends a player their run history, for the command and the character sheet's Rift Log link. */
    public static void history(ServerPlayer player) {
        RiftRecapLog log = RiftRecapLog.of(player.server);
        PacketDistributor.sendToPlayer(player, new RiftRecapPayload(log.history(player.getUUID()), false));
        log.seen(player.getUUID());
    }

    /** Counts a barrel the first time a player goes through it, with what their share holds. */
    public static void barrelLooted(ServerPlayer player, Container share) {
        RiftRun run = RiftRuns.in(player.level().dimension());
        if (run == null) {
            return;
        }
        int shards = 0;
        int gear = 0;
        int rarity = RiftHaul.NO_RARITY;
        for (int slot = 0; slot < share.getContainerSize(); slot++) {
            ItemStack stack = share.getItem(slot);
            shards += shards(stack);
            if (isGear(stack)) {
                gear++;
                rarity = Math.max(rarity, rarity(stack));
            }
        }
        run.haul(player.getUUID(), run.haul(player.getUUID()).barrel(shards, gear, rarity));
    }

    /** Counts one stack found outside a barrel, such as this player's boss drop. */
    public static void found(ServerLevel rift, ServerPlayer player, ItemStack stack) {
        RiftRun run = RiftRuns.in(rift.dimension());
        if (run == null) {
            return;
        }
        boolean gear = isGear(stack);
        int shards = shards(stack);
        if (gear || shards > 0) {
            run.haul(player.getUUID(), run.haul(player.getUUID())
                    .found(shards, gear ? 1 : 0, gear ? rarity(stack) : RiftHaul.NO_RARITY));
        }
    }

    private static int shards(ItemStack stack) {
        return stack.is(RiftfallItems.RIFT_SHARD.get()) ? stack.getCount() : 0;
    }

    /** A gear piece is anything the gear pool has a slot for, or a gun. A throwing item is not counted. */
    private static boolean isGear(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        try {
            return GunGrade.isGun(stack) || GearPool.classify(stack.getItem()) != null;
        } catch (RuntimeException unanswerable) {
            return false;
        }
    }

    private static int rarity(ItemStack stack) {
        try {
            return RiftLootRarity.rarityIndex(stack);
        } catch (RuntimeException unreadable) {
            return RiftHaul.NO_RARITY;
        }
    }

    /**
     * Writes an entrant's recap, called where their XP for the run is banked. {@code paidNow} is what
     * that payment was, which is what the level change is read from. Never throws: a recap that
     * cannot be written must not stop a run from ending.
     */
    static void settled(MinecraftServer server, RiftRun run, RiftRun.Verdict verdict, UUID entrant, boolean online,
            boolean inside, long paidNow) {
        try {
            long total = RiftStats.of(server).xpFor(entrant);
            int after = RiftXp.levelFor(Math.max(0L, total), RiftXp.LEVEL_CAP);
            int before = RiftXp.levelFor(Math.max(0L, total - paidNow), RiftXp.LEVEL_CAP);
            RiftRecapLog.of(server).add(entrant,
                    of(run, verdict, entrant, online, inside, System.currentTimeMillis(), before, after));
        } catch (RuntimeException failed) {
            LOGGER.error("Could not write the recap of {} for {}", run.rift().location(), entrant, failed);
        }
    }

    /** The recap itself, from what the run wrote down and the same rules {@link RiftRuns#earnedBy} pays by. */
    static RiftRecap of(RiftRun run, RiftRun.Verdict verdict, UUID entrant, boolean online, boolean inside, long now,
            int levelBefore, int levelAfter) {
        boolean escaped = run.escaped().contains(entrant);
        boolean died = run.died().contains(entrant);
        boolean penalised = RiftRuns.penalised(verdict, run.expired(), escaped, died, online, inside);
        int kills = run.kills(entrant);
        long clear = !penalised && RiftRuns.earnsTheClear(verdict, escaped) ? XpAwards.clearXp(run.tier()) : 0L;
        RiftHaul haul = run.haul(entrant);
        return new RiftRecap(RiftRecap.outcome(verdict, run.expired(), escaped, died, inside), run.tier(),
                run.elapsed(), now, kills, verdict == RiftRun.Verdict.CLEARED,
                XpAwards.forKills(kills, run.tier()), clear, RiftRuns.earnedBy(run, verdict, entrant, online, inside),
                penalised ? (int) XpAwards.DEATH_PERCENT : 100, levelBefore, levelAfter,
                haul.barrels(), haul.shards(), haul.gear(), haul.bestRarity());
    }

    /** Sends a waiting recap to anybody who is alive and outside a rift, so it opens once they are home. */
    private static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % DELIVERY_INTERVAL != 0) {
            return;
        }
        RiftRecapLog log = RiftRecapLog.of(server);
        if (!log.anyUnseen()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (deliverable(log.unseen(player.getUUID()), player.isAlive(),
                    RiftLevels.isRift(player.level().dimension()))) {
                // Held gear comes back here, and the screen says what came back.
                RiftAutoClaims.atRecap(player);
                PacketDistributor.sendToPlayer(player, new RiftRecapPayload(log.history(player.getUUID()), true));
                log.seen(player.getUUID());
            }
        }
    }

    /** A corpse waits for its respawn, and nobody is interrupted inside a rift. */
    static boolean deliverable(boolean unseen, boolean alive, boolean inRift) {
        return unseen && alive && !inRift;
    }
}
