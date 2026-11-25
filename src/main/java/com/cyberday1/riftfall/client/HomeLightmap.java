package com.cyberday1.riftfall.client;

import org.joml.Vector3f;

/**
 * With shaders off a home keeps the look it had before it had sky light: each sky level of the lightmap is given
 * sky level 0's colour, so only the dimension's ambient floor and block light show.
 */
final class HomeLightmap {
    private HomeLightmap() {}

    /**
     * Moves one pixel from {@code skyLight} to {@code skyLightAtZero}. Vanilla adds {@code skyColour * skyLight}, then
     * lerps 4% to grey and darkens by (0.3, 0.4, 0.4) * {@code darkenWorld} (LightTexture#updateLightTexture), all
     * linear in the sky term, so taking the difference back out is exact.
     */
    static void dropSkyLight(Vector3f colors, float skyDarken, float skyLight, float skyLightAtZero, float darkenWorld) {
        float sky = 0.96F * (skyLight - skyLightAtZero);
        float skyRedGreen = skyDarken + (1 - skyDarken) * 0.35F;
        colors.sub(sky * skyRedGreen * (1 - 0.3F * darkenWorld), sky * skyRedGreen * (1 - 0.4F * darkenWorld),
                sky * (1 - 0.4F * darkenWorld));
    }
}
