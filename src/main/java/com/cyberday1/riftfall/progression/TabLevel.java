package com.cyberday1.riftfall.progression;

import com.cyberday1.riftfall.RiftfallConfig;
import com.cyberday1.riftfall.stat.RiftStats;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Each player's Riftfall level as a vanilla scoreboard score in the tab list's slot.
 *
 * <p>
 * The score is derived, never read: every write is the level {@link RiftStats} buys, so a hand-set
 * score lasts until that player's next login or level change.
 */
public final class TabLevel {

    public static final String OBJECTIVE = "riftfall_level";
    public static final String NAME_KEY = "scoreboard.riftfall.level";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** What server start does with the objective and the list slot. */
    public enum Claim { CLAIM, KEEP, YIELD, REMOVE, NONE }

    private TabLevel() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TabLevel::onStarted);
        NeoForge.EVENT_BUS.addListener(TabLevel::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(TabLevel::onRespawn);
    }

    /** The slot is taken only when it is empty or already ours; another objective there is left alone. */
    public static Claim claim(boolean enabled, @Nullable String shown, boolean exists) {
        if (!enabled) {
            return exists ? Claim.REMOVE : Claim.NONE;
        }
        if (shown == null) {
            return Claim.CLAIM;
        }
        return OBJECTIVE.equals(shown) ? Claim.KEEP : Claim.YIELD;
    }

    /** The score a total shows: the capped level, the same number the character sheet reads. */
    public static int scoreFor(long xp) {
        return RiftXp.levelFor(xp, RiftXp.LEVEL_CAP);
    }

    /** Off until the server config has loaded, which is before any server has a player. */
    public static boolean enabled() {
        return RiftfallConfig.SPEC.isLoaded() && RiftfallConfig.TAB_LIST_LEVEL.get();
    }

    private static void onStarted(ServerStartedEvent event) {
        prepare(event.getServer());
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.getEntity());
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sync(event.getEntity());
    }

    /** Creates or removes the objective per the config, claims the slot, and syncs everyone online. */
    public static Claim prepare(MinecraftServer server) {
        ServerScoreboard board = server.getScoreboard();
        Objective ours = board.getObjective(OBJECTIVE);
        Objective shown = board.getDisplayObjective(DisplaySlot.LIST);
        Claim claim = claim(enabled(), shown == null ? null : shown.getName(), ours != null);
        switch (claim) {
            case CLAIM -> board.setDisplayObjective(DisplaySlot.LIST, ours == null ? create(board) : ours);
            case KEEP -> {
            }
            case YIELD -> {
                if (ours == null) {
                    create(board);
                }
                LOGGER.info("Tab list level: the list slot shows '{}', so {} is kept but not shown",
                        shown.getName(), OBJECTIVE);
            }
            case REMOVE -> board.removeObjective(ours);
            case NONE -> {
                return claim;
            }
        }
        server.getPlayerList().getPlayers().forEach(TabLevel::sync);
        return claim;
    }

    private static Objective create(ServerScoreboard board) {
        // No auto-updated display: a score's display component would replace the number in the list.
        return board.addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY,
                Component.translatableWithFallback(NAME_KEY, "Level"),
                ObjectiveCriteria.RenderType.INTEGER, false, null);
    }

    /** Writes this player's level if it differs. Stand-ins are skipped so they leave no rows behind. */
    public static void sync(Player player) {
        if (!(player instanceof ServerPlayer server) || player instanceof FakePlayer
                || !enabled()) {
            return;
        }
        ServerScoreboard board = server.server.getScoreboard();
        Objective ours = board.getObjective(OBJECTIVE);
        if (ours == null) {
            return;
        }
        int level = scoreFor(RiftStats.of(server.server).xpFor(server.getUUID()));
        ReadOnlyScoreInfo current = board.getPlayerScoreInfo(server, ours);
        if (current == null || current.value() != level) {
            board.getOrCreatePlayerScore(server, ours).set(level);
        }
    }
}
