package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.breaker.WorldBreakerBlock;
import com.cyberday1.riftfall.block.breaker.WorldBreakerBlockEntity;
import com.cyberday1.riftfall.block.breaker.WorldBreakerMotion;
import com.cyberday1.riftfall.block.breaker.WorldBreakerPhase;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The World Breaker's moving parts, drawn only while it has a charge store: the key that went in,
 * turning and then held; and the ring of broken shards lifting off, drifting, and flying home.
 *
 * <p>
 * Everything that does not move is the baked model and its animated textures. The motion itself is
 * {@link WorldBreakerMotion}, eased off the store's saved game times, so a reloaded chunk picks the
 * motion up where it is instead of replaying it.
 */
public class WorldBreakerRenderer implements BlockEntityRenderer<WorldBreakerBlockEntity> {

    public static final ModelResourceLocation TIP = model("block/world_breaker_shard_tip");
    public static final ModelResourceLocation CHIP_LARGE = model("block/world_breaker_shard_chip_large");
    public static final ModelResourceLocation CHIP_SMALL = model("block/world_breaker_shard_chip_small");
    public static final ModelResourceLocation CHARGE_CRYSTALS = model("block/world_breaker_charge_crystals");

    /** The charge glow: the lit fractures and keyhole, faded in over the baked charging look. */
    private static final Material INLAY =
            new Material(InventoryMenu.BLOCK_ATLAS, RiftfallCore.id("block/world_breaker_inlay_lit"));
    private static final Material SOCKET =
            new Material(InventoryMenu.BLOCK_ATLAS, RiftfallCore.id("block/world_breaker_socket_top_lit"));
    private static final Material BRIDGE =
            new Material(InventoryMenu.BLOCK_ATLAS, RiftfallCore.id("block/world_breaker_bridge_lit"));

    private final BlockEntityRendererProvider.Context context;
    private final RiftTearRenderer tearRenderer;

    public WorldBreakerRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
        this.tearRenderer = new RiftTearRenderer(context);
    }

    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(RiftfallCore.id(path));
    }

    @Override
    public void render(WorldBreakerBlockEntity store, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = store.getLevel();
        if (level == null) {
            return;
        }
        WorldBreakerPhase phase = store.getBlockState().getValue(WorldBreakerBlock.PHASE);
        double now = level.getGameTime() + partialTick;
        switch (phase) {
            case CHARGING -> {
                // The block-state update can arrive before its timing packet: never flash a finished tear.
                if (store.chargedAt() == WorldBreakerBlockEntity.UNSET) return;
                double t = since(now, store.chargedAt(), WorldBreakerPhase.CHARGING.ticks());
                float[] key = WorldBreakerMotion.key(t);
                key(store, pose, buffers, light, overlay, key[0], key[1]);
                glow(pose, buffers, WorldBreakerMotion.glow(t));
                crystals(pose, buffers, WorldBreakerMotion.crystalGlow(t));
                tearRenderer.renderOpening(store, t, pose, buffers);
            }
            case OPEN -> {
                key(store, pose, buffers, light, overlay, 0.0F, 90.0F);
                double t = since(now, store.openedAt(), WorldBreakerMotion.EASE);
                ring(pose, buffers, WorldBreakerMotion.ring(now, t / WorldBreakerMotion.EASE, -1.0));
            }
            case HOMING -> {
                double t = since(now, store.coolingAt(), WorldBreakerMotion.EASE);
                ring(pose, buffers, WorldBreakerMotion.ring(now, -1.0, t / WorldBreakerMotion.EASE));
            }
            default -> {
            }
        }
    }

    /** Ticks since {@code start}, or {@code full} if there is no start: a store made without one is drawn settled. */
    private static double since(double now, long start, double full) {
        return start == WorldBreakerBlockEntity.UNSET ? full : Math.max(0.0, now - start);
    }

    /** An emissive skin on the model's actual amethyst faces, faded over the cold baked crystals. */
    private static void crystals(PoseStack pose, MultiBufferSource buffers, float alpha) {
        if (alpha <= 0) return;
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(CHARGE_CRYSTALS);
        VertexConsumer into = buffers.getBuffer(RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS));
        var random = net.minecraft.util.RandomSource.create(42);
        for (var quad : model.getQuads(null, null, random, ModelData.EMPTY, null)) {
            into.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, alpha,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }

    /**
     * The key that went in, as its own item model (so its tier's look), flipped tip-down into the
     * socket, at 0.6, turned about its shaft.
     */
    private static void key(WorldBreakerBlockEntity store, PoseStack pose, MultiBufferSource buffers, int light,
                            int overlay, float drop, float turn) {
        ItemStack key = store.key();
        if (key.isEmpty()) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5F, (WorldBreakerMotion.KEY_TIP_Y + drop) / 16.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.scale(WorldBreakerMotion.KEY_SCALE, WorldBreakerMotion.KEY_SCALE, WorldBreakerMotion.KEY_SCALE);
        // ItemRenderer centres the model on its middle (8, 8, 8); this puts the shaft tip there instead.
        pose.translate(0.0F, -(WorldBreakerMotion.KEY_PIVOT_Y - 8.0F) / 16.0F, 0.0F);
        Minecraft.getInstance().getItemRenderer().renderStatic(key, ItemDisplayContext.NONE, light, overlay, pose,
                buffers, store.getLevel(), (int) store.getBlockPos().asLong());
        pose.popPose();
    }

    /** The floating shards, full bright: they are lit by the rift, not the room. */
    private void ring(PoseStack pose, MultiBufferSource buffers, List<WorldBreakerMotion.Piece> pieces) {
        ModelBlockRenderer renderer = context.getBlockRenderDispatcher().getModelRenderer();
        var models = Minecraft.getInstance().getModelManager();
        VertexConsumer into = buffers.getBuffer(Sheets.cutoutBlockSheet());
        for (WorldBreakerMotion.Piece piece : pieces) {
            if (!piece.visible()) {
                continue;
            }
            BakedModel model = models.getModel(switch (piece.kind()) {
                case TIP -> TIP;
                case CHIP_LARGE -> CHIP_LARGE;
                case CHIP_SMALL -> CHIP_SMALL;
            });
            pose.pushPose();
            pose.translate((8.0 + piece.x()) / 16.0, piece.y() / 16.0, (8.0 + piece.z()) / 16.0);
            pose.mulPose(Axis.YP.rotationDegrees(piece.yaw()));
            pose.mulPose(Axis.XP.rotationDegrees(piece.tilt()));
            pose.mulPose(Axis.ZP.rotationDegrees(piece.roll()));
            pose.scale((float) piece.scale(), (float) piece.scale(), (float) piece.scale());
            // The piece models are centred on (8, 0, 8) of their own block.
            pose.translate(-0.5F, 0.0F, -0.5F);
            renderer.renderModel(pose.last(), into, null, model, 1.0F, 1.0F, 1.0F,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
            pose.popPose();
        }
    }

    /** The lit fractures and keyhole, faded in across the charge over the baked cold ones. */
    private static void glow(PoseStack pose, MultiBufferSource buffers, float alpha) {
        if (alpha <= 0.0F) {
            return;
        }
        TextureAtlasSprite inlay = INLAY.sprite();
        TextureAtlasSprite socket = SOCKET.sprite();
        VertexConsumer into = buffers.getBuffer(RenderType.entityTranslucentEmissive(inlay.atlasLocation()));
        Matrix4f m = pose.last().pose();
        flat(into, m, inlay, 1.0F, 15.0F, 4.02F, 1.0F, 15.0F, alpha);
        flat(into, m, socket, 5.0F, 11.0F, (float) WorldBreakerBlock.THICKNESS + 0.02F,
                0.0F, 16.0F, alpha);
        // Match the eight baked exits authored in tools/models/world_breaker_fractures.py.
        for (int quarter = 0; quarter < 4; quarter++) {
            pose.pushPose();
            pose.translate(0.5F, 0.0F, 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees(-90.0F * quarter));
            pose.translate(-0.5F, 0.0F, -0.5F);
            for (float x : new float[]{5.0F, 11.0F}) {
                bridge(into, pose.last().pose(), BRIDGE.sprite(), x, alpha);
            }
            pose.popPose();
        }
    }

    /** A square face-up quad with independent geometry and texture bounds. */
    private static void flat(VertexConsumer into, Matrix4f m, TextureAtlasSprite s, float a, float b, float y,
                             float uv0, float uv1, float alpha) {
        float u0 = s.getU(uv0 / 16.0F);
        float u1 = s.getU(uv1 / 16.0F);
        float v0 = s.getV(uv0 / 16.0F);
        float v1 = s.getV(uv1 / 16.0F);
        float lo = a / 16.0F;
        float hi = b / 16.0F;
        float h = y / 16.0F;
        vertex(into, m, lo, h, lo, u0, v0, alpha);
        vertex(into, m, lo, h, hi, u0, v1, alpha);
        vertex(into, m, hi, h, hi, u1, v1, alpha);
        vertex(into, m, hi, h, lo, u1, v0, alpha);
    }

    /** One continuous strip down the body, over the ledge, and down to the floor. */
    private static void bridge(VertexConsumer into, Matrix4f m, TextureAtlasSprite s, float x, float alpha) {
        float left = (x - 1.0F) / 16.0F, right = (x + 1.0F) / 16.0F;
        float u0 = s.getU(0.0F), u1 = s.getU(1.0F);
        float v0 = s.getV(0.0F), v1 = s.getV(0.4F), v2 = s.getV(0.6F), v3 = s.getV(1.0F);
        vertex(into, m, right, 4.0F / 16, 0.98F / 16, u0, v0, alpha);
        vertex(into, m, right, 2.0F / 16, 0.98F / 16, u0, v1, alpha);
        vertex(into, m, left, 2.0F / 16, 0.98F / 16, u1, v1, alpha);
        vertex(into, m, left, 4.0F / 16, 0.98F / 16, u1, v0, alpha);
        vertex(into, m, right, 2.02F / 16, 1.0F / 16, u0, v1, alpha);
        vertex(into, m, right, 2.02F / 16, 0.0F, u0, v2, alpha);
        vertex(into, m, left, 2.02F / 16, 0.0F, u1, v2, alpha);
        vertex(into, m, left, 2.02F / 16, 1.0F / 16, u1, v1, alpha);
        vertex(into, m, right, 2.0F / 16, -0.02F / 16, u0, v2, alpha);
        vertex(into, m, right, 0.0F, -0.02F / 16, u0, v3, alpha);
        vertex(into, m, left, 0.0F, -0.02F / 16, u1, v3, alpha);
        vertex(into, m, left, 2.0F / 16, -0.02F / 16, u1, v2, alpha);
    }

    private static void vertex(VertexConsumer into, Matrix4f m, float x, float y, float z, float u, float v,
                               float alpha) {
        into.addVertex(m, x, y, z)
                .setColor(1.0F, 1.0F, 1.0F, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    /** Out to the player's own render distance, as the tear is (ruled 2026-10-12). */
    @Override
    public int getViewDistance() {
        return RiftfallClientDistances.renderBlocks();
    }

    /** Includes the opening tear above the pedestal, even while its carrier is off screen. */
    @Override
    public AABB getRenderBoundingBox(WorldBreakerBlockEntity store) {
        return new AABB(store.getBlockPos()).inflate(RiftTearCrack.WIDTH * 0.5, 0.0, RiftTearCrack.WIDTH * 0.5)
                .expandTowards(0.0, RiftTearCrack.HEIGHT + 1.0, 0.0);
    }
}
