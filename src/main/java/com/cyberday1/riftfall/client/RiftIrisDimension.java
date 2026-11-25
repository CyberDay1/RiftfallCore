package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallClientConfig;
import com.cyberday1.riftfall.RiftfallClientConfig.RiftShaderLighting;
import com.cyberday1.riftfall.home.ShatteredHomeDimensions;
import com.cyberday1.riftfall.worldgen.level.RiftDimensions;
import com.mojang.logging.LogUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Iris dimension a rift or home is shaded as. A rift has no sky light, so a pack's Overworld profile
 * (Iris's pick for an unknown dimension) gives it no ambient light; the End profile is built for that.
 * The home has sky light, so it keeps the Overworld profile unless the player picks another.
 */
public final class RiftIrisDimension {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Iris's own constants: it compares dimensions by identity, so a copy would not do.
    private static final Object END = irisId("END");
    private static final Object NETHER = irisId("NETHER");

    private RiftIrisDimension() {
    }

    /** Iris's id for the profile a rift or home should use, or null to leave Iris's answer alone. */
    @Nullable
    public static Object forLevel(@Nullable ClientLevel level) {
        if (level == null) return null;
        RiftShaderLighting choice;
        if (level.dimensionTypeRegistration().is(RiftDimensions.RIFT_TYPE)) {
            choice = choice(RiftfallClientConfig.RIFT_SHADER_LIGHTING, RiftShaderLighting.END);
        } else if (level.dimensionTypeRegistration().is(ShatteredHomeDimensions.HOME_TYPE)) {
            choice = choice(RiftfallClientConfig.HOME_SHADER_LIGHTING, RiftShaderLighting.OVERWORLD);
        } else {
            return null;
        }
        return switch (choice) {
            case END -> END;
            case NETHER -> NETHER;
            case OVERWORLD -> null;
        };
    }

    private static RiftShaderLighting choice(ModConfigSpec.EnumValue<RiftShaderLighting> value, RiftShaderLighting fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }

    @Nullable
    private static Object irisId(String field) {
        try {
            return Class.forName("net.irisshaders.iris.shaderpack.DimensionId").getField(field).get(null);
        } catch (ReflectiveOperationException | LinkageError absent) {
            LOGGER.warn("Riftfall: Iris DimensionId.{} not found, rifts keep Iris's own lighting profile", field);
            return null;
        }
    }
}
