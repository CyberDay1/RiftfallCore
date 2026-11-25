package com.cyberday1.riftfall.gear;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Springy Boot's Double Jump: one air jump per landing. The client predicts it and sends {@code RelicJumpPayload};
 * the server runs the same {@link #check} on its own view and, if allowed, sets the velocity and zeroes the fall.
 * Not a cast: it never reaches the skill path, cooldowns or reactions.
 */
public final class RelicJump {

    /** Air jumps per landing. */
    public static final int AIR_JUMPS = 1;

    /** The answer, in the order {@link #check} asks. */
    public enum Verdict {
        ALLOWED, UNABLE, NOT_WORN, GROUNDED, FLYING, GLIDING, RIDING, SWIMMING, IN_FLUID, CLIMBING, SPENT
    }

    /** What the rule reads off a player, so it is testable without one. */
    public record State(boolean able, boolean worn, boolean onGround, boolean flying, boolean gliding,
                        boolean riding, boolean swimming, boolean inFluid, boolean climbing, int used) {
    }

    private static final Map<UUID, Integer> USED = new ConcurrentHashMap<>();

    private RelicJump() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RelicJump::onTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                USED.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> USED.clear());
    }

    public static Verdict check(State s) {
        if (!s.able()) return Verdict.UNABLE;
        if (!s.worn()) return Verdict.NOT_WORN;
        if (s.onGround()) return Verdict.GROUNDED;
        if (s.flying()) return Verdict.FLYING;
        if (s.gliding()) return Verdict.GLIDING;
        if (s.riding()) return Verdict.RIDING;
        if (s.swimming()) return Verdict.SWIMMING;
        if (s.inFluid()) return Verdict.IN_FLUID;
        if (s.climbing()) return Verdict.CLIMBING;
        if (s.used() >= AIR_JUMPS) return Verdict.SPENT;
        return Verdict.ALLOWED;
    }

    /** Either side: the client asks with its own count and worn read, the server with its own. */
    public static State state(Player p, boolean worn, int used) {
        return new State(p.isAlive() && !p.isSpectator() && !p.isSleeping(), worn, p.onGround(),
                p.getAbilities().flying, p.isFallFlying(), p.isPassenger(), p.isSwimming(),
                p.isInWater() || p.isInLava() || p.isInFluidType(), p.onClimbable(), used);
    }

    /** Vanilla's jump power in open air: jump strength (block factor 1) plus Jump Boost. */
    public static double power(double jumpStrength, float jumpBoost) {
        return jumpStrength + jumpBoost;
    }

    public static double power(Player p) {
        return power(p.getAttributeValue(Attributes.JUMP_STRENGTH), p.getJumpBoostPower());
    }

    /** The jump itself, the same on both sides: upward velocity at jump power, fall distance cleared. */
    public static void apply(Player p) {
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x, power(p), v.z);
        p.hasImpulse = true;
        p.resetFallDistance();
    }

    /** The payload, validated. No motion packet goes back: the client already moved. */
    public static Verdict handle(ServerPlayer player) {
        int used = USED.getOrDefault(player.getUUID(), 0);
        Verdict verdict = check(state(player, RelicSignatures.wears(player, RelicSignatures.Signature.DOUBLE_JUMP),
                used));
        if (verdict != Verdict.ALLOWED) return verdict;
        USED.put(player.getUUID(), used + 1);
        apply(player);
        player.awardStat(Stats.JUMP);
        player.causeFoodExhaustion(player.isSprinting() ? 0.2F : 0.05F);
        return verdict;
    }

    public static int used(ServerPlayer player) {
        return USED.getOrDefault(player.getUUID(), 0);
    }

    /** Landing gives the jump back. The server tick, and the probe on a stand-in that does not tick. */
    public static void landed(ServerPlayer player) {
        if (player.onGround()) USED.remove(player.getUUID());
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !USED.isEmpty()) landed(player);
    }
}
