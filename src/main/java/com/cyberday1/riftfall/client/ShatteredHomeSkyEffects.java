package com.cyberday1.riftfall.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Animated nebula and spatial tears with a distant starfield in a dark void.
 */
public final class ShatteredHomeSkyEffects extends DimensionSpecialEffects {

    public static final ResourceLocation EFFECTS_ID =
            ResourceLocation.fromNamespaceAndPath("riftfall", "shattered_home");

    private static final int STAR_COUNT = 180;
    private static final long STAR_SEED = 0x5F3759DFL;

    private static volatile VertexBuffer starBuffer;
    private static final Object STAR_LOCK = new Object();

    /** Read once per lightmap rebuild, at its first pixel. */
    private boolean flatSkyLight = true;

    public ShatteredHomeSkyEffects() {
        super(Float.NaN, false, SkyType.NONE, false, true);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return new Vec3(0.02, 0.02, 0.06);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTick) {
        return null;
    }

    /** Shaders off: the home's sky light is kept off the lightmap; a shader pack gets it. */
    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker,
                                     float skyLight, int pixelX, int pixelY, Vector3f colors) {
        if (pixelX == 0 && pixelY == 0) flatSkyLight = !ShatteredSkyRenderer.shaderPackActive();
        if (!flatSkyLight || pixelY == 0) return;
        DimensionType type = level.dimensionType();
        float atZero = skyLight * LightTexture.getBrightness(type, 0) / LightTexture.getBrightness(type, pixelY);
        HomeLightmap.dropSkyLight(colors, skyDarken, skyLight, atZero,
                Minecraft.getInstance().gameRenderer.getDarkenWorldAmount(partialTicks));
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick,
                             Matrix4f modelViewMatrix, Camera camera,
                             Matrix4f projectionMatrix, boolean isFoggy,
                             Runnable setupFog) {
        // Shader packs composite their own atmosphere after this hook. Draw the
        // authored sky at AFTER_LEVEL instead, before the hand clears world depth.
        if (ShatteredSkyRenderer.shaderPackActive()) return true;
        setupFog.run();

        // Sky renders before terrain; do not write depth or clip the celestial sphere.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // The supplied matrix already represents camera rotation for sky rendering.
        // Subtracting the world position makes the celestial sphere drift as players walk.
        Matrix4f skyMV = new Matrix4f(modelViewMatrix);
        RenderSystem.disableCull();

        try {
            ShatteredSkyRenderer.draw(skyMV, projectionMatrix, ticks / 20.0 + partialTick / 20.0);

        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
        return true;
    }

    private void drawStarfield(Matrix4f modelView, Matrix4f projection) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();

        VertexBuffer buffer = getOrCreateStarBuffer();
        buffer.bind();
        buffer.drawWithShader(modelView, projection, RenderSystem.getShader());
        VertexBuffer.unbind();
    }

    private static VertexBuffer getOrCreateStarBuffer() {
        VertexBuffer existing = starBuffer;
        if (existing != null) return existing;
        synchronized (STAR_LOCK) {
            if (starBuffer != null) return starBuffer;

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder = tesselator.begin(
                    VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            Random rng = new Random(STAR_SEED);
            float radius = 95.0F;

            for (int i = 0; i < STAR_COUNT; i++) {
                float x1, x2, s;
                do {
                    x1 = rng.nextFloat() * 2.0F - 1.0F;
                    x2 = rng.nextFloat() * 2.0F - 1.0F;
                    s = x1 * x1 + x2 * x2;
                } while (s >= 1.0F || s == 0.0F);
                float sqrtTerm = (float) Math.sqrt(1.0F - s);
                float nx = 2.0F * x1 * sqrtTerm;
                float ny = 2.0F * x2 * sqrtTerm;
                float nz = 1.0F - 2.0F * s;

                float sx = nx * radius;
                float sy = ny * radius;
                float sz = nz * radius;

                float r, g, b;
                int tint = rng.nextInt(10);
                if (tint < 6) {
                    r = 0.85F + rng.nextFloat() * 0.15F;
                    g = 0.85F + rng.nextFloat() * 0.15F;
                    b = 0.90F + rng.nextFloat() * 0.10F;
                } else if (tint < 8) {
                    r = 0.6F + rng.nextFloat() * 0.2F;
                    g = 0.7F + rng.nextFloat() * 0.2F;
                    b = 0.9F + rng.nextFloat() * 0.1F;
                } else if (tint < 9) {
                    r = 0.9F + rng.nextFloat() * 0.1F;
                    g = 0.8F + rng.nextFloat() * 0.15F;
                    b = 0.5F + rng.nextFloat() * 0.2F;
                } else {
                    r = 0.9F + rng.nextFloat() * 0.1F;
                    g = 0.4F + rng.nextFloat() * 0.2F;
                    b = 0.3F + rng.nextFloat() * 0.15F;
                }

                float bright = 0.4F + rng.nextFloat() * 0.6F;
                int ri = (int) (r * bright * 255);
                int gi = (int) (g * bright * 255);
                int bi = (int) (b * bright * 255);

                float halfSize = 0.035F + rng.nextFloat() * 0.10F;
                Vector3f normal = new Vector3f(nx, ny, nz);
                Vector3f right = tangent(normal).mul(halfSize);
                Vector3f up = new Vector3f(normal).cross(right).normalize().mul(halfSize);
                Vector3f center = new Vector3f(sx, sy, sz);
                vertex(builder, new Vector3f(center).sub(right).sub(up), ri, gi, bi, 255);
                vertex(builder, new Vector3f(center).add(right).sub(up), ri, gi, bi, 255);
                vertex(builder, new Vector3f(center).add(right).add(up), ri, gi, bi, 255);
                vertex(builder, new Vector3f(center).sub(right).add(up), ri, gi, bi, 255);
            }

            MeshData mesh = builder.build();
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(mesh);
            VertexBuffer.unbind();
            starBuffer = vb;
            return vb;
        }
    }

    public static void releaseBuffers() {
        if (starBuffer != null) { starBuffer.close(); starBuffer = null; }
        ShatteredSkyRenderer.releaseBuffers();
    }

    private static Vector3f tangent(Vector3f normal) {
        return new Vector3f(normal).cross(Math.abs(normal.y) < .9F
                ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize();
    }

    private static void vertex(BufferBuilder builder, Vector3f p, int r, int g, int b, int alpha) {
        builder.addVertex(p.x, p.y, p.z).setColor(r, g, b, alpha);
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick,
                                com.mojang.blaze3d.vertex.PoseStack poseStack,
                                double camX, double camY, double camZ,
                                Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        return true;
    }
}
