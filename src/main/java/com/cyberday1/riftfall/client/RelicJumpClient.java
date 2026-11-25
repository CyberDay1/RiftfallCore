package com.cyberday1.riftfall.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.RelicJump;
import com.cyberday1.riftfall.gear.RelicSignatures;
import com.cyberday1.riftfall.gear.RelicWallCling;
import com.cyberday1.riftfall.network.RelicJumpPayload;

/**
 * The relic movements' client half. Double Jump: a fresh jump press in mid-air, with Springy Boot worn and a jump
 * left, jumps here at once and tells the server, which checks the same rule. Wall Cling: with Phantom Boot worn,
 * sneaking against a wall in the air clamps the fall, and a fresh press there kicks off instead. A press made on the
 * ground is vanilla's jump, not this.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class RelicJumpClient {

    private static boolean groundedAtStart;
    private static boolean wasJumping;
    private static int used;
    private static RelicWallCling.State cling = RelicWallCling.State.NONE;

    private RelicJumpClient() {
    }

    @SubscribeEvent
    static void before(ClientTickEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        groundedAtStart = player == null || player.onGround();
    }

    @SubscribeEvent
    static void after(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.getConnection() == null) {
            wasJumping = false;
            used = 0;
            cling = RelicWallCling.State.NONE;
            return;
        }
        if (player.onGround()) used = 0;
        boolean clingWorn = RelicSignatures.wears(player, RelicSignatures.Signature.WALL_CLING);
        RelicWallCling.Input wall = RelicWallCling.input(player, clingWorn,
                player.input != null && player.input.shiftKeyDown);
        RelicWallCling.Step step = RelicWallCling.step(cling, wall);
        cling = step.state();
        if (step.clinging()) {
            // Applied after the tick's move, so the next move starts from the clamped speed.
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, RelicWallCling.clamp(v.y), v.z);
        }
        boolean jumping = player.input != null && player.input.jumping;
        boolean pressed = jumping && !wasJumping;
        wasJumping = jumping;
        if (!pressed || groundedAtStart || client.screen != null) return;
        if (RelicWallCling.canKick(cling, wall) && kick(player)) {
            cling = RelicWallCling.kicked(cling);
            PacketDistributor.sendToServer(new RelicJumpPayload(RelicJumpPayload.Move.WALL_KICK));
            puff(player);
            return;
        }
        boolean worn = RelicSignatures.wears(player, RelicSignatures.Signature.DOUBLE_JUMP);
        if (RelicJump.check(RelicJump.state(player, worn, used)) != RelicJump.Verdict.ALLOWED) return;
        used++;
        RelicJump.apply(player);
        PacketDistributor.sendToServer(new RelicJumpPayload(RelicJumpPayload.Move.AIR_JUMP));
        puff(player);
    }

    /** Off the wall the player touches, at the jump's power; false when no wall face is found. */
    private static boolean kick(LocalPlayer player) {
        Direction side = RelicWallCling.wallSide(player);
        if (side == null) return false;
        Vec3 away = new Vec3(-side.getStepX(), 0.0, -side.getStepZ());
        player.setDeltaMovement(RelicWallCling.kick(away, RelicJump.power(player)));
        player.hasImpulse = true;
        return true;
    }

    private static void puff(LocalPlayer player) {
        for (int i = 0; i < 6; i++) {
            player.level().addParticle(ParticleTypes.CLOUD, player.getX() + (player.getRandom().nextDouble() - 0.5),
                    player.getY(), player.getZ() + (player.getRandom().nextDouble() - 0.5), 0.0, -0.05, 0.0);
        }
    }
}
