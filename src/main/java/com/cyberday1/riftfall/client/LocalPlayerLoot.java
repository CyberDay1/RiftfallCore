package com.cyberday1.riftfall.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import java.util.Set;
import java.util.UUID;

/**
 * Who "you" are, for the one question a rift barrel's art asks.
 *
 * <p>
 * A barrel is looted per player, so the block cannot know how to draw itself without knowing which
 * client is looking. On a client there is exactly one answer and it is a static lookup; there is no
 * answer at all on a server, which is why this is a class of its own rather than three lines inside
 * the block entity.
 */
public final class LocalPlayerLoot {

    private LocalPlayerLoot() {
    }

    public static boolean hasLooted(Set<UUID> opened) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && opened.contains(player.getUUID());
    }

    /**
     * Re-bakes the section a barrel is in, so its art changes now rather than eventually.
     *
     * <p>
     * Deliberately not {@code Level#setBlocksDirty}: that one asks
     * {@code ModelManager#requiresRender} whether the two states it was handed differ, and a barrel
     * whose looted art just changed is a barrel whose <em>state</em> did not &mdash; so it is handed
     * the same state twice and answers no. The re-bake would then wait for the next real state
     * change on the block, which is the lid closing behind the player who caused it.
     */
    public static void rebake(BlockPos pos) {
        Minecraft.getInstance().levelRenderer.setBlocksDirty(
                pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }
}
