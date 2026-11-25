package com.cyberday1.riftfall.compat;

import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.bettercombat.client.animation.PlayerAttackAnimatable;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/** Client-only bridge, reached only when both animation mods are loaded. */
public final class BetterCombatCasting {
    private static final Map<Player, Integer> RELEASES = new WeakHashMap<>();

    private BetterCombatCasting() {}

    /** Only a confirmed release packet reaches here; rejected casts never interrupt a swing. */
    public static void release(int playerId, String name, float speed) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || name == null || name.isBlank()) return;
        var id = ResourceLocation.tryParse(name);
        if (id == null) return;
        if (!(PlayerAnimationRegistry.getAnimation(id)
                instanceof dev.kosmx.playerAnim.core.data.KeyframeAnimation animation)) return;
        int ticks = CombatReleaseWindow.ticks(animation.endTick, speed, animation.isInfinite);
        if (ticks == 0 || !(client.level.getEntity(playerId) instanceof Player player)) return;
        RELEASES.put(player, player.tickCount + ticks);
        if (player == client.player) ((MinecraftClient_BetterCombat) client).cancelUpswing();
        ((PlayerAttackAnimatable) player).stopAttackAnimation(0);
    }

    public static boolean releasing(Player player) {
        Integer until = RELEASES.get(player);
        if (until == null) return false;
        if (player.tickCount < until) return true;
        RELEASES.remove(player);
        return false;
    }
}
