package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.home.HomeBoundary;
import com.cyberday1.riftfall.network.HomeBoundaryPayload;
import net.minecraft.client.Minecraft;

public final class HomeBoundaryClient {
    private HomeBoundaryClient() {}
    public static void accept(HomeBoundaryPayload payload) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.dimension().location().equals(payload.dimension()))
            HomeBoundary.update(level, payload.tiles());
    }
}
