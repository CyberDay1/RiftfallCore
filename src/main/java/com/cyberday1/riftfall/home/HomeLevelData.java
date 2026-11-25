package com.cyberday1.riftfall.home;

import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;

/**
 * The overworld's clock and spawn without its weather. A level with sky light runs the rain cycle from this, and
 * {@code DerivedLevelData} would hand it the overworld's storm; a home never rains.
 */
final class HomeLevelData extends DerivedLevelData {
    HomeLevelData(WorldData worldData, ServerLevelData overworld) {
        super(worldData, overworld);
    }

    @Override
    public boolean isRaining() {
        return false;
    }

    @Override
    public boolean isThundering() {
        return false;
    }
}
