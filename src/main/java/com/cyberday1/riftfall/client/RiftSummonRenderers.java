package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.skill.RiftSummon;
import com.cyberday1.riftfall.skill.RiftSummons;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.BlazeModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Matching silhouettes using Minecraft's existing meshes and textures. No hostile entity renderer casts. */
final class RiftSummonRenderers {
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        RiftSummons.ALL.forEach((kind, type) -> {
            if (kind.endsWith("elemental")) event.registerEntityRenderer(type.get(), c -> new Elemental(c, kind));
            else if (kind.equals("imp") || kind.equals("sprite") || kind.equals("wraith")
                    || kind.equals("ancestral_spirit") || kind.equals("spirit_healer") || kind.equals("guardian_spirit"))
                event.registerEntityRenderer(type.get(), c -> new Spirit(c, kind));
            else event.registerEntityRenderer(type.get(), c -> new Humanoid(c, kind));
        });
    }
    private static ResourceLocation texture(String path) { return ResourceLocation.parse("minecraft:textures/" + path + ".png"); }
    private static final class Humanoid extends MobRenderer<RiftSummon, HumanoidModel<RiftSummon>> {
        private final String kind;
        Humanoid(EntityRendererProvider.Context c, String kind) {
            super(c, new HumanoidModel<RiftSummon>(c.bakeLayer(kind.equals("ghoul") || kind.equals("plague_servant")
                    ? ModelLayers.ZOMBIE : ModelLayers.SKELETON)) {
                @Override public void setupAnim(RiftSummon e, float limb, float amount, float age, float yaw, float pitch) {
                    rightArmPose = e.kind().equals("skeleton") && (e.spellCastAnimationState.isStarted() || e.spellReleaseAnimationState.isStarted())
                            ? ArmPose.BOW_AND_ARROW : ArmPose.ITEM;
                    super.setupAnim(e, limb, amount, age, yaw, pitch);
                }
            }, .4F);
            this.kind = kind;
            addLayer(new ItemInHandLayer<>(this, c.getItemInHandRenderer()));
        }
        @Override public ResourceLocation getTextureLocation(RiftSummon entity) {
            return texture(switch (kind) {
                case "ghoul" -> "entity/zombie/zombie";
                case "plague_servant" -> "entity/zombie/husk";
                case "demon_knight" -> "entity/skeleton/wither_skeleton";
                case "lich", "guardian_spirit" -> "entity/skeleton/stray";
                default -> "entity/skeleton/skeleton";
            });
        }
        @Override protected void scale(RiftSummon e, PoseStack pose, float partial) {
            if (kind.equals("demon_knight")) pose.scale(1.1F, 1.1F, 1.1F);
        }
    }
    private static final class Spirit extends MobRenderer<RiftSummon, RiftSpiritModel> {
        private final String kind;
        Spirit(EntityRendererProvider.Context c, String kind) {
            super(c, new RiftSpiritModel(c.bakeLayer(ModelLayers.VEX)), .2F);
            this.kind = kind;
            addLayer(new ItemInHandLayer<>(this, c.getItemInHandRenderer()));
        }
        @Override public ResourceLocation getTextureLocation(RiftSummon e) {
            return texture(kind.equals("imp") ? "entity/illager/vex_charging" : "entity/illager/vex");
        }
        @Override protected void scale(RiftSummon e, PoseStack pose, float partial) {
            float size = kind.equals("imp") || kind.equals("sprite") ? 1 : 1.7F;
            pose.scale(size, size, size);
        }
    }
    private static final class Elemental extends MobRenderer<RiftSummon, BlazeModel<RiftSummon>> {
        private final String kind;
        Elemental(EntityRendererProvider.Context c, String kind) {
            super(c, new BlazeModel<>(c.bakeLayer(ModelLayers.BLAZE)), .4F);
            this.kind = kind;
        }
        @Override public ResourceLocation getTextureLocation(RiftSummon e) {
            return texture(kind.equals("fire_elemental") ? "entity/blaze" : "block/quartz_block_side");
        }
    }
}
