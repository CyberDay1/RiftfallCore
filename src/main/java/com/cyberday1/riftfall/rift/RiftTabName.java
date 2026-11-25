package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * A suffix on a player's tab list name while they stand alive in a rift instance: {@code Steve ◆ Rift T4}.
 * Server side through {@code PlayerEvent.TabListNameFormat}, so a vanilla client shows it too.
 */
public final class RiftTabName {

    public static final String KEY = "tab.riftfall.in_rift";
    public static final String FALLBACK = " ◆ Rift T%s";
    public static final ChatFormatting COLOUR = ChatFormatting.DARK_PURPLE;

    private RiftTabName() {
    }

    public static void register() {
        register(NeoForge.EVENT_BUS);
    }

    static void register(IEventBus bus) {
        // LOW, so a name another mod sets first is kept and the suffix goes after it.
        bus.addListener(EventPriority.LOW, RiftTabName::name);
        bus.addListener(RiftTabName::onLoggedIn);
        bus.addListener(RiftTabName::onChangedDimension);
        bus.addListener(RiftTabName::onRespawn);
        bus.addListener(EventPriority.LOWEST, RiftTabName::onDeath);
    }

    /** On until the server config says otherwise; read live, so a change shows at the next refresh. */
    public static boolean enabled() {
        return RiftfallConfig.SPEC.isLoaded() ? RiftfallConfig.TAB_LIST_RIFT.get()
                : RiftfallConfig.TAB_LIST_RIFT.getDefault();
    }

    /** The tier to show, or nothing: only a living player in a rift instance shows one. */
    public static OptionalInt shown(boolean enabled, boolean alive, boolean instance, int tier) {
        return enabled && alive && instance ? OptionalInt.of(tier) : OptionalInt.empty();
    }

    public static MutableComponent suffix(int tier) {
        return Component.translatableWithFallback(KEY, FALLBACK, String.valueOf(tier)).withStyle(COLOUR);
    }

    /** What a vanilla client draws for a player with no tab name: the profile name in team colours. */
    public static MutableComponent base(@Nullable PlayerTeam team, String profileName) {
        return PlayerTeam.formatNameForTeam(team, Component.literal(profileName));
    }

    /** The name with the suffix after it, on top of whatever name an earlier listener set. */
    public static Component decorate(@Nullable Component current, Component base, int tier) {
        return (current != null ? current : base).copy().append(suffix(tier));
    }

    static void name(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        OptionalInt tier = shown(enabled(), player.isAlive(), RiftLevels.isRift(player.level().dimension()),
                RiftLevels.tier(player.serverLevel()));
        if (tier.isPresent()) {
            event.setDisplayName(decorate(event.getDisplayName(),
                    base(player.getTeam(), player.getGameProfile().getName()), tier.getAsInt()));
        }
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        refresh(event.getEntity(), false);
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        refresh(event.getEntity(), false);
    }

    private static void onDeath(LivingDeathEvent event) {
        refresh(event.getEntity(), false);
    }

    /** Forced: the respawned player is a new object, so the client's copy of the old name goes unseen otherwise. */
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        refresh(event.getEntity(), true);
    }

    /**
     * Recomputes the name; vanilla broadcasts it only if it changed. {@code force} sends it anyway when it did not,
     * because a fresh player's cache starts at the new name and cannot see what clients were last told.
     */
    static void refresh(Object entity, boolean force) {
        if (!(entity instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        Component before = player.getTabListDisplayName();
        player.refreshTabListName();
        if (force && Objects.equals(before, player.getTabListDisplayName())) {
            player.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player));
        }
    }
}
