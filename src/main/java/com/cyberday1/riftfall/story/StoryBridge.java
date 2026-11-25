package com.cyberday1.riftfall.story;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.HomeAccess;
import com.cyberday1.riftfall.home.ShatteredHomeLevels;
import com.cyberday1.riftfall.home.ShatteredHomeProgress;
import com.cyberday1.riftfall.lore.CoreTileBeats;
import com.cyberday1.riftfall.rift.RiftKeyItem;
import com.cyberday1.riftfall.rift.RiftRun;
import com.cyberday1.riftfall.rift.RiftRunReport;
import com.cyberday1.riftfall.rift.RiftSettledEvent;
import com.cyberday1.riftfall.stat.RiftStats;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Gameplay to quest book. Server events write {@link StoryState}; the state is then mirrored into hidden
 * {@code riftfall:story/*} advancements, which FTB Quests advancement tasks read on the server. No FTB class is
 * linked, and nothing here reads a client.
 *
 * <p>
 * The mirror is re-derived on login, on arrival in a home and after every event, so a grant lost to a crash heals
 * at the next of those. A failure here is logged and never reaches the gameplay call that reported it.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class StoryBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> MISSING = new HashSet<>();

    private StoryBridge() {}

    /** From {@code RiftRuns.entering}: the player is on the run's roster. */
    public static void entered(ServerPlayer player, RiftRun run) {
        guard("entry", player, server -> {
            StoryRules.entered(StoryState.of(server), player.getUUID(), run.rift().location().toString());
            reconcile(player);
        });
    }

    /** A key forged or seated in a breaker ({@code KeyForgeMenu}, {@code WorldBreakerBlock}). */
    public static void keyReady(Player player, String how) {
        if (player instanceof ServerPlayer server) guard("key", server, s -> {
            StoryRules.keyReady(StoryState.of(s), server.getUUID(), how);
            reconcile(server);
        });
    }

    /**
     * From {@code HolyCoreBlockEntity#offer}, after the shard is consumed and the Core updated. A player's first
     * offering hears beat 2; {@code shards} is the home's count after this one, which decides its last sentence.
     */
    public static void offered(ServerLevel core, Player player, int tiles, int shards) {
        UUID home = HomeAccess.owner(core.dimension().location());
        if (home == null || !(player instanceof ServerPlayer feeder)) return;
        guard("offer", feeder, server -> {
            var fresh = StoryRules.offered(StoryState.of(server), feeder.getUUID(), home, tiles);
            if (fresh.contains(StoryMilestone.OFFERED)) {
                var warm = CoreTileBeats.voice(StoryLines.WARM);
                if (StoryRules.fourMoreIsTrue(shards, ShatteredHomeProgress.SHARDS_PER_TILE)) {
                    warm.append(" ").append(CoreTileBeats.voice(StoryLines.FOUR_MORE));
                }
                feeder.sendSystemMessage(warm);
            }
            reconcile(feeder);
            reconcileHome(server, home);
        });
    }

    /**
     * A1.01, from {@code HolyCoreBlockEntity#inspectOrRepair}: touching the Core of the player's own home (their
     * team's, when they share one) delivers the greeting once and records the introduction. Another home's Core
     * says nothing extra.
     */
    public static void coreTouched(ServerLevel core, Player player) {
        UUID home = HomeAccess.owner(core.dimension().location());
        if (home == null || !(player instanceof ServerPlayer toucher)) return;
        guard("greeting", toucher, server -> {
            if (!home.equals(HomeAccess.homeOwner(server, toucher.getUUID()))) return;
            if (StoryRules.introduced(StoryState.of(server), toucher.getUUID()).isEmpty()) return;
            for (String line : StoryLines.GREETING) toucher.sendSystemMessage(CoreTileBeats.voice(line));
            reconcile(toucher);
        });
    }

    /**
     * Decision 11, from {@code RiftBossLoot#dropFor} when this player's own roll held no World Shard: whether the
     * boss owes them one. See {@link StoryRules#firstShard}; a failure here owes nothing.
     */
    public static boolean firstShardOwed(ServerPlayer player, ServerLevel rift) {
        if (player instanceof FakePlayer || player.server == null) return false;
        try {
            return StoryRules.firstShard(StoryState.of(player.server), player.getUUID(), rift.dimension().location().toString());
        } catch (RuntimeException failed) {
            LOGGER.error("Story bridge failed on the first shard for {}", player.getGameProfile().getName(), failed);
            return false;
        }
    }

    /** Beat 1, from {@code RiftBosses} as a defender falls: once per player, to each one still in the fight. */
    public static void defenderFell(ServerLevel rift) {
        for (ServerPlayer player : com.cyberday1.riftfall.loot.RiftBossLoot.participants(rift)) {
            guard("defender", player, server -> {
                if (StoryRules.firstAnchor(StoryState.of(server), player.getUUID())) {
                    player.sendSystemMessage(CoreTileBeats.voice(StoryLines.FIRST_ANCHOR));
                }
            });
        }
    }

    /** A class chosen, a skill equipped or an ascension taken ({@code SkillService}). */
    public static void buildChanged(ServerPlayer player) {
        guard("build", player, server -> reconcile(player));
    }

    /** From {@code ResidentDepth.panel}: a resident's landing opened for this player in a home. */
    public static void residentMet(ServerPlayer player, NpcRole role) {
        guard("resident", player, server -> {
            StoryRules.residentMet(StoryState.of(server), player.getUUID(), role.name().toLowerCase(java.util.Locale.ROOT));
            reconcile(player);
        });
    }

    @SubscribeEvent
    public static void settled(RiftSettledEvent event) {
        settled(event.server(), event.rift(), event.reports(), event.server().getPlayerList()::getPlayer);
    }

    /** One settled run: each entrant's facts, and the Core's line on a first safe return to whoever is online. */
    public static void settled(MinecraftServer server, ResourceLocation rift, List<RiftRunReport> reports,
                               java.util.function.Function<UUID, ServerPlayer> online) {
        try {
            StoryState state = StoryState.of(server);
            for (RiftRunReport report : reports) {
                var fresh = StoryRules.settled(state, report, rift.toString());
                ServerPlayer player = online.apply(report.player());
                if (player == null || player instanceof FakePlayer) continue;
                if (fresh.contains(StoryMilestone.FIRST_CLEAR)) player.sendSystemMessage(CoreTileBeats.voice(StoryLines.RETURN));
                reconcile(player);
            }
        } catch (RuntimeException failed) {
            LOGGER.error("Story bridge failed on the settle of {}", rift, failed);
        }
    }

    @SubscribeEvent
    public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getCrafting().getItem() instanceof RiftKeyItem) keyReady(event.getEntity(), "crafted");
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) guard("login", player, server -> reconcile(player));
    }

    /** Arriving in a home is when a new team member first stands in it, so catch-up runs here too. */
    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ShatteredHomeLevels.isShatteredHome(event.getTo())) {
            guard("arrival", player, server -> reconcile(player));
        }
    }

    /** Grants every milestone the record owes this player and they do not yet hold. */
    public static void reconcile(ServerPlayer player) {
        MinecraftServer server = player.server;
        UUID id = player.getUUID();
        var owed = StoryRules.owed(StoryState.of(server), id, HomeAccess.homeOwner(server, id),
                RiftStats.of(server).bookFor(id));
        for (StoryMilestone milestone : owed) grant(server, player, milestone);
    }

    private static void reconcileHome(MinecraftServer server, UUID home) {
        for (ServerPlayer member : server.getPlayerList().getPlayers()) {
            if (!(member instanceof FakePlayer) && home.equals(HomeAccess.homeOwner(server, member.getUUID()))) {
                reconcile(member);
            }
        }
    }

    private static void grant(MinecraftServer server, ServerPlayer player, StoryMilestone milestone) {
        AdvancementHolder holder = server.getAdvancements().get(milestone.advancement());
        if (holder == null) {
            if (MISSING.add(milestone.advancement())) LOGGER.error("Story advancement {} is missing", milestone.advancement());
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) return;
        List<String> remaining = new ArrayList<>();
        progress.getRemainingCriteria().forEach(remaining::add);
        for (String criterion : remaining) player.getAdvancements().award(holder, criterion);
    }

    private static void guard(String what, ServerPlayer player, java.util.function.Consumer<MinecraftServer> body) {
        if (player instanceof FakePlayer || player.server == null) return;
        try {
            body.accept(player.server);
        } catch (RuntimeException failed) {
            LOGGER.error("Story bridge failed on {} for {}", what, player.getGameProfile().getName(), failed);
        }
    }
}
