package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.network.TravelCuePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cable follows the rendered caster every frame; dust follows actual client movement. */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class TravelCue {
    private record Cue(ClientLevel level, Vec3 anchor, boolean cable, long expires) { }
    private static final Map<Integer, Cue> CUES = new HashMap<>();
    private static final Map<UUID, Long> QUIET_GRAPPLES = new HashMap<>();
    private static ClientLevel quietLevel;
    private TravelCue() { }

    public static void accept(TravelCuePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (payload.kind() < 0 || level == null) {
            CUES.remove(payload.entity());
            var player = Minecraft.getInstance().player;
            if (player == null || payload.entity() == player.getId()) ClientSkillTravel.stop(true);
            return;
        }
        CUES.put(payload.entity(), new Cue(level, new Vec3(payload.x(), payload.y(), payload.z()),
                payload.kind() == 1, level.getGameTime() + 50));
        if (payload.kind() == 1) {
            if (quietLevel != level) { QUIET_GRAPPLES.clear(); quietLevel = level; }
            var entity = level.getEntity(payload.entity());
            // The evade buff lasts two seconds, including the tail after travel ends.
            if (entity != null) QUIET_GRAPPLES.put(entity.getUUID(), level.getGameTime() + 40);
        }
    }

    static boolean quietGrapple(net.minecraft.world.entity.LivingEntity entity) {
        return entity.level() == quietLevel && QUIET_GRAPPLES.getOrDefault(entity.getUUID(), 0L)
                > entity.level().getGameTime();
    }

    @SubscribeEvent
    private static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (quietLevel != level) { QUIET_GRAPPLES.clear(); quietLevel = level; }
        if (level != null) QUIET_GRAPPLES.entrySet().removeIf(e -> e.getValue() <= level.getGameTime());
        if (level != null && net.neoforged.fml.ModList.get().isLoaded("spell_engine"))
            RiftTravelPresentation.install();
        if (level == null) ClientSkillTravel.stop(false);
        CUES.entrySet().removeIf(entry -> level == null || entry.getValue().level() != level
                || level.getGameTime() >= entry.getValue().expires() || level.getEntity(entry.getKey()) == null);
        if (level == null || Minecraft.getInstance().isPaused()) return;
        for (var entry : CUES.entrySet()) {
            var entity = level.getEntity(entry.getKey());
            if (entity != null && entity.position().distanceToSqr(new Vec3(entity.xo, entity.yo, entity.zo)) > .0025) {
                level.addParticle(ParticleTypes.CLOUD, entity.getX(), entity.getY() + .12, entity.getZ(), 0, .005, 0);
            }
        }
    }

    @SubscribeEvent
    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        var buffers = minecraft.renderBuffers().bufferSource();
        var pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean drawn = false;
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (var entry : CUES.entrySet()) {
            Cue cue = entry.getValue();
            if (!cue.cable() || cue.level() != minecraft.level) continue;
            var entity = minecraft.level.getEntity(entry.getKey());
            if (entity == null) continue;
            Vec3 forward = entity.getViewVector(partial);
            Vec3 side = Vec3.directionFromRotation(0, entity.getYRot()).cross(new Vec3(0, 1, 0)).normalize().scale(.24);
            Vec3 hand = entity.getPosition(partial).add(0, entity.getEyeHeight() * .75, 0).add(side);
            if (entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson())
                hand = camera.add(forward.scale(.4)).add(side).add(0, -.28, 0);
            int light = LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(hand));
            GrappleRope.render(buffers.getBuffer(RenderType.leash()), pose.last(), hand, cue.anchor(), light);
            drawn = true;
        }
        pose.popPose();
        if (drawn) buffers.endBatch(RenderType.leash());
    }
}
