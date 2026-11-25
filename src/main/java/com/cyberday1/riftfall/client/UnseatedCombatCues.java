package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.boss.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.Comparator;
import static com.cyberday1.riftfall.boss.UnseatedEncounter.*;

/** Depth-tested, full-bright floor markings independent of particle settings and boss frustum culling. */
@EventBusSubscriber(modid=RiftfallCore.MODID,value=Dist.CLIENT)
public final class UnseatedCombatCues {
    private UnseatedCombatCues(){}
    @SubscribeEvent public static void floor(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null || !UnseatedArena.isArena(mc.level.dimension()))return;
        var buffers=mc.renderBuffers().bufferSource();var poses=event.getPoseStack();var camera=event.getCamera().getPosition();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean drawn=false;
        for(var boss:mc.level.getEntitiesOfClass(UnseatedEntity.class,mc.player.getBoundingBox().inflate(64))){
            if(!boss.engaged() && !isAttack(boss.action()))continue;
            poses.pushPose();poses.translate(boss.getX()-camera.x,boss.getY()-camera.y+.045,boss.getZ()-camera.z);
            var out=buffers.getBuffer(Layer.FLOOR);var pose=poses.last();
            UnseatedTelegraph.Quads sink=(x0,z0,x1,z1,x2,z2,x3,z3,rgb,alpha)->{
                vertex(out,pose,x0,z0,rgb,alpha);vertex(out,pose,x1,z1,rgb,alpha);
                vertex(out,pose,x2,z2,rgb,alpha);vertex(out,pose,x3,z3,rgb,alpha);
            };
            // The god attacks through the binding, so the ring and the attack marking can show together.
            if(boss.phase()==Phase.BINDING)UnseatedTelegraph.binding(sink);
            double ticks=boss.attackTicks(partial);
            UnseatedTelegraph.emit(boss.action(),ticks,boss.attackYaw(),boss.reach(),sink);
            // Two crossed ribbons per stroke read from any side.
            UnseatedTelegraph.Strokes strokes=(x,z,height,half,rgb,alpha)->{
                wall(out,pose,x-half,z,x+half,z,height,rgb,alpha);wall(out,pose,x,z-half,x,z+half,height,rgb,alpha);
            };
            UnseatedTelegraph.emitVertical(boss.action(),ticks,boss.attackYaw(),strokes);
            poses.popPose();
            // The Crown's circles and the glide's lane stand on the court, not around the god.
            for(var part:parts(boss.action())){
                double t=ticks-part.offset();
                if(part.action()==Action.CROWN)for(var mark:boss.crownMarks()){
                    double x=mark.x(),z=mark.z();
                    // Until the lock the circle rides on its player, interpolated like the player is drawn.
                    if(!mark.locked() && mc.level.getEntity(mark.entity()) instanceof net.minecraft.world.entity.Entity e){var at=e.getPosition(partial);x=at.x;z=at.z;}
                    court(poses,camera,x,boss.getY(),z,(o,p)->{UnseatedTelegraph.crown(t,quads(o,p));UnseatedTelegraph.crownVertical(t,walls(o,p));},buffers);
                }
                if(part.action()==Action.RESEAT){
                    double[] a=boss.laneFrom(),b=seat(boss.seat());
                    court(poses,camera,UnseatedArena.CENTER.x,boss.getY(),UnseatedArena.CENTER.z,(o,p)->{
                        UnseatedTelegraph.lane(t,a[0],a[1],b[0],b[1],quads(o,p));UnseatedTelegraph.laneVertical(t,a[0],a[1],b[0],b[1],walls(o,p));},buffers);
                }
            }
            drawn=true;
        }
        if(drawn)buffers.endBatch(Layer.FLOOR);
    }
    /** Shapes drawn around a point on the court floor rather than around the god. */
    private static void court(PoseStack poses,net.minecraft.world.phys.Vec3 camera,double x,double y,double z,
                              java.util.function.BiConsumer<VertexConsumer,PoseStack.Pose> draw,MultiBufferSource.BufferSource buffers){
        poses.pushPose();poses.translate(x-camera.x,y-camera.y+.05,z-camera.z);
        draw.accept(buffers.getBuffer(Layer.FLOOR),poses.last());poses.popPose();
    }
    private static UnseatedTelegraph.Quads quads(VertexConsumer out,PoseStack.Pose pose){
        return (x0,z0,x1,z1,x2,z2,x3,z3,rgb,alpha)->{
            vertex(out,pose,x0,z0,rgb,alpha);vertex(out,pose,x1,z1,rgb,alpha);
            vertex(out,pose,x2,z2,rgb,alpha);vertex(out,pose,x3,z3,rgb,alpha);
        };
    }
    private static UnseatedTelegraph.Strokes walls(VertexConsumer out,PoseStack.Pose pose){
        return (x,z,height,half,rgb,alpha)->{wall(out,pose,x-half,z,x+half,z,height,rgb,alpha);wall(out,pose,x,z-half,x,z+half,height,rgb,alpha);};
    }
    private static void wall(VertexConsumer out,PoseStack.Pose pose,double x0,double z0,double x1,double z1,double height,int rgb,int alpha){
        vertex(out,pose,x0,0,z0,rgb,alpha);vertex(out,pose,x1,0,z1,rgb,alpha);
        vertex(out,pose,x1,height,z1,rgb,alpha);vertex(out,pose,x0,height,z0,rgb,alpha);
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,double x,double z,int rgb,int alpha){vertex(out,pose,x,0,z,rgb,alpha);}
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,double x,double y,double z,int rgb,int alpha){
        out.addVertex(pose,(float)x,(float)y,(float)z).setColor((rgb>>16)&255,(rgb>>8)&255,rgb&255,alpha)
                .setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose,0,1,0);
    }
    /** Under the boss bars: the gap below them, and the room the panel keeps above the crosshair. */
    static final int GAP = 4, CROSSHAIR_CLEAR = 10;
    /** The first vanilla bar's reach: the panel starts there with no bar, so it never jumps when one appears. */
    static final int FIRST_BAR = BossBarExtent.vanillaBottom(12, 19);
    /** Kept clear each side: two of vanilla's 25 px status effect icons, which stack in from the top right. */
    static final int SIDE_CLEAR = 50;

    static int panelWidth(int guiWidth) {
        return Math.min(390, guiWidth - 2 * SIDE_CLEAR);
    }

    /**
     * The cue panel's top: under the boss bars ({@code bossBottom}, from {@link BossBarExtent}) when it then ends
     * {@link #CROSSHAIR_CLEAR} above the crosshair, so it covers neither them nor the docked bars at the foot. With
     * too many bars for that, its old place under the crosshair ({@link #belowCrosshair}).
     */
    static int panelTop(int guiHeight, int bossBottom, int columns, int height) {
        int top = Math.max(bossBottom, FIRST_BAR) + GAP;
        return top + height <= guiHeight / 2 - CROSSHAIR_CLEAR ? top : belowCrosshair(guiHeight, columns, height);
    }

    /**
     * Below the crosshair, lifted to clear the HUD columns (vanilla rows and the docked mana, Charge and shield bars,
     * which the layers before this one added to them), but never closer than ten pixels under the crosshair.
     * {@code columns} is the taller of {@code Gui#leftHeight} and {@code rightHeight}.
     */
    static int belowCrosshair(int guiHeight, int columns, int height) {
        int below = Math.min(guiHeight - 94, guiHeight / 2 + 38);
        int clear = guiHeight - columns + 8 - height;
        return Math.min(below, Math.max(clear, guiHeight / 2 + 10));
    }

    public static void hud(GuiGraphics gui,DeltaTracker delta){
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null || mc.options.hideGui || !UnseatedArena.isArena(mc.level.dimension()))return;
        var boss=mc.level.getEntitiesOfClass(UnseatedEntity.class,mc.player.getBoundingBox().inflate(60)).stream()
                .min(Comparator.comparingDouble(b->b.distanceToSqr(mc.player))).orElse(null);
        if(boss==null)return;
        double ticks=boss.attackTicks(delta.getGameTimeDeltaPartialTick(false));
        Component title,hint;int color=UnseatedTelegraph.CYAN;float progress=-1;
        if(boss.phase()==Phase.CONTAINED){title=Component.translatable(UnseatedText.HUD_CONTAINED);hint=Component.translatable(UnseatedText.HUD_CONTAINED_HINT);}
        else if(boss.phase()==Phase.BINDING && !(isAttack(boss.action()) && ticks<boss.action().activeEnd)){
            // Between the god's attacks: the shared bar and how many are binding. An attack takes the panel back.
            int binders=boss.bindersNow();
            title=binders>1?Component.translatable(UnseatedText.HUD_BINDING_PARTY,binders):
                    Component.translatable(binders==1?UnseatedText.HUD_BINDING:UnseatedText.HUD_EXPOSED);
            hint=Component.translatable(boss.action()==Action.TRANSITION?UnseatedText.HUD_STAGGER_HINT:UnseatedText.HUD_BIND_HINT);
            if(binders>0 || boss.bindProgress()>0)progress=boss.bindProgress();
        }else if(!boss.engaged()){title=Component.translatable(UnseatedText.HUD_DORMANT);hint=Component.translatable(UnseatedText.HUD_DORMANT_HINT);}
        // A practiced roster wakes the god straight into live combat (lesson 0): no trials to announce.
        else if(boss.action()==Action.AWAKEN && boss.lesson()!=0){title=Component.translatable(UnseatedText.HUD_LEARN);hint=Component.translatable(UnseatedText.HUD_LEARN_HINT);}
        else if(isAttack(boss.action()) && ticks<boss.action().activeEnd){
            boolean warning=ticks<boss.action().windup;
            Component name=boss.practicing()?Component.translatable(UnseatedText.HUD_ATTACK_PRACTICE,boss.lesson(),UnseatedText.attack(boss.action())):UnseatedText.attack(boss.action());
            title=warning?Component.translatable(UnseatedText.HUD_ATTACK_WARNING,name,String.format(java.util.Locale.ROOT,"%.1f",(boss.action().windup-ticks)/20))
                    :Component.translatable(UnseatedText.HUD_ATTACK_NOW,name);
            hint=boss.practicing()?Component.translatable(UnseatedText.HUD_NO_DAMAGE,UnseatedText.response(boss.action())):UnseatedText.response(boss.action());
            color=switch(boss.action()){
                case PULL,REACH_RUPTURE -> UnseatedTelegraph.VIOLET;case PULSE -> UnseatedTelegraph.CYAN;
                case CROWN -> UnseatedTelegraph.WHITE;case THRONE_FALL -> UnseatedTelegraph.DANGER;default -> UnseatedTelegraph.AMBER;
            };
            progress=warning?(float)Math.clamp((ticks+(boss.practicing()?PRACTICE_LEAD:0))/(boss.action().windup+(boss.practicing()?PRACTICE_LEAD:0)),0,1):1;
        }else if(boss.action()==Action.TRANSITION || boss.action()==Action.AWAKEN){title=Component.translatable(UnseatedText.HUD_PREPARE);hint=Component.translatable(UnseatedText.HUD_PREPARE_HINT);}
        else if(boss.practicing()){title=Component.translatable(UnseatedText.HUD_RECOVERY);hint=Component.translatable(UnseatedText.HUD_RECOVERY_HINT);}
        else {title=Component.translatable(UnseatedText.HUD_OPENING);hint=Component.translatable(UnseatedText.HUD_OPENING_HINT);}
        int width=panelWidth(gui.guiWidth()),left=(gui.guiWidth()-width)/2;
        var lines=mc.font.split(hint,width-18);
        int height=27+lines.size()*10;
        int top=panelTop(gui.guiHeight(),BossBarExtent.bottom(),Math.max(mc.gui.leftHeight,mc.gui.rightHeight),height);
        gui.fill(left,top,left+width,top+height,0xC0100C19);
        gui.fill(left,top,left+3,top+height,0xFF000000|color);
        gui.drawCenteredString(mc.font,title,gui.guiWidth()/2,top+5,0xFF000000|color);
        for(int i=0;i<lines.size();i++)gui.drawString(mc.font,lines.get(i),left+9,top+18+i*10,0xFFF2F0F5,false);
        if(progress>=0){gui.fill(left+8,top+height-5,left+width-8,top+height-3,0xFF403A49);
            gui.fill(left+8,top+height-5,left+8+(int)((width-16)*progress),top+height-3,0xFF000000|color);}
    }
    private static final class Layer extends RenderType {
        static final RenderType FLOOR=create("riftfall_unseated_floor",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,65536,false,true,
                CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(new TextureStateShard(RiftfallCore.id("textures/block/world_core/white.png"),false,false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));
        private Layer(){super("riftfall_unseated_floor",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
    }
}
