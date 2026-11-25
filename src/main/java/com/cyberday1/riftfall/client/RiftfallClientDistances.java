package com.cyberday1.riftfall.client;

import net.minecraft.client.Minecraft;

/** Distances the client draws Riftfall's block entity renderers out to. */
public final class RiftfallClientDistances {

    private RiftfallClientDistances() {
    }

    /**
     * The player's own render distance, in blocks. The block entity default is a fixed 64, which
     * drops a tear and a World Breaker's floating shards long before the chunks around them go.
     */
    public static int renderBlocks() {
        return Math.max(64, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16);
    }
}
