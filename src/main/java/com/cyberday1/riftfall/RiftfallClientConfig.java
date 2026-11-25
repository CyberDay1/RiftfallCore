package com.cyberday1.riftfall;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Display settings each player picks for themselves; nothing here reaches the server. */
public final class RiftfallClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Which of a shader pack's three lighting profiles a Riftfall level is drawn with. */
    public enum RiftShaderLighting { END, NETHER, OVERWORLD }

    public static final ModConfigSpec.EnumValue<RiftShaderLighting> RIFT_SHADER_LIGHTING = BUILDER
            .comment("Shader packs (Iris) only: which of the pack's lighting profiles rifts are drawn with.",
                    "END: the pack's End lighting, a dim even light that needs no sky. The default.",
                    "NETHER: the pack's Nether lighting. Brighter and foggier in most packs.",
                    "OVERWORLD: what rifts used before 2026-11-18. The pack reads a rift as a sealed cave at",
                    "midnight, so it is near black away from torches.",
                    "Applies the next time you enter a rift. Changes nothing with shaders off.")
            .defineEnum("riftShaderLighting", RiftShaderLighting.END);

    public static final ModConfigSpec.EnumValue<RiftShaderLighting> HOME_SHADER_LIGHTING = BUILDER
            .comment("Shader packs (Iris) only: which of the pack's lighting profiles the Shattered Home is drawn with.",
                    "OVERWORLD: the pack's daylight: noon sun, shadows and sky light on the island. The default.",
                    "END: the pack's End lighting, dimmer and tinted (purple in Complementary).",
                    "NETHER: the pack's Nether lighting, with its fog.",
                    "The home's own starfield sky is drawn in every case.",
                    "Applies the next time you enter a home. Changes nothing with shaders off.")
            .defineEnum("homeShaderLighting", RiftShaderLighting.OVERWORLD);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private RiftfallClientConfig() {
    }
}
