package com.cyberday1.riftfall.home;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;

/**
 * Homes gained sky light on 2026-11-18. A chunk saved before then is marked lit but carries no sky light, and
 * the light engine would fill its missing sections with full sky light, inside the ground and under roofs too.
 * Such a chunk is lit again on load, as a new chunk would be.
 */
public final class HomeSkyLight {
    private HomeSkyLight() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(HomeSkyLight::onLoad);
    }

    private static void onLoad(ChunkDataEvent.Load event) {
        if (event.getLevel() instanceof Level level && ShatteredHomeLevels.isShatteredHome(level.dimension())
                && level.dimensionType().hasSkyLight() && savedWithoutSkyLight(event.getData())) {
            event.getChunk().setLightCorrect(false);
        }
    }

    /** True for a home level, and for worldgen or placement views of one. */
    public static boolean inHome(LevelReader reader) {
        Level level = reader instanceof WorldGenLevel view ? view.getLevel() : reader instanceof Level l ? l : null;
        return level != null && ShatteredHomeLevels.isShatteredHome(level.dimension());
    }

    /** No section of the saved chunk holds sky light: written before homes had any. */
    static boolean savedWithoutSkyLight(CompoundTag chunk) {
        var sections = chunk.getList("sections", Tag.TAG_COMPOUND);
        for (int i = 0; i < sections.size(); i++) {
            if (sections.getCompound(i).contains("SkyLight", Tag.TAG_BYTE_ARRAY)) return false;
        }
        return true;
    }
}
