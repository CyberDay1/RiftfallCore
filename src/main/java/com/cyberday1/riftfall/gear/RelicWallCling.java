package com.cyberday1.riftfall.gear;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Phantom Boot's Wall Cling. The slide is client movement ({@code RelicJumpClient}): airborne, against a wall,
 * sneak held, the fall is clamped for up to two seconds an airborne span, and a jump kicks off once a contact. The
 * server sees only the kick-off ({@code RelicJumpPayload} {@code WALL_KICK}): it checks the wearer and clears the
 * fall, and landing gives the timer back. Not a cast.
 */
public final class RelicWallCling {

    /** The fastest fall while clinging, in blocks a tick. */
    public static final double MAX_FALL = 0.05;
    /** Cling ticks an airborne span: two seconds. */
    public static final int MAX_TICKS = 40;
    /** The kick: this share of jump power up, and this much push off the wall. */
    public static final double KICK_UP = 0.9;
    public static final double KICK_PUSH = 0.35;

    /** Cling ticks spent this airborne span, whether a wall is touched now, and whether this contact kicked. */
    public record State(int ticks, boolean contact, boolean kicked) {
        public static final State NONE = new State(0, false, false);
    }

    /** What the rule reads each tick. {@code blocked}: flying, gliding, riding, swimming, in a fluid or climbing. */
    public record Input(boolean worn, boolean airborne, boolean wall, boolean sneaking, boolean blocked) {
    }

    public record Step(State state, boolean clinging) {
    }

    private static final Map<UUID, Integer> KICKS = new ConcurrentHashMap<>();

    private RelicWallCling() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RelicWallCling::onTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                KICKS.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> KICKS.clear());
    }

    /** One tick of the rule. Landing gives everything back; leaving the wall gives the kick back. */
    public static Step step(State s, Input in) {
        if (!in.airborne()) return new Step(State.NONE, false);
        boolean kicked = in.wall() && s.kicked();
        boolean cling = in.worn() && in.wall() && in.sneaking() && !in.blocked() && s.ticks() < MAX_TICKS;
        return new Step(new State(cling ? s.ticks() + 1 : s.ticks(), in.wall(), kicked), cling);
    }

    /** The clamp a clinging tick puts on vertical motion: falls no faster than {@link #MAX_FALL}, rises untouched. */
    public static double clamp(double dy) {
        return Math.max(dy, -MAX_FALL);
    }

    /** A kick: worn, airborne, on a wall, nothing in the way, and this contact's kick unused. */
    public static boolean canKick(State s, Input in) {
        return in.worn() && in.airborne() && in.wall() && !in.blocked() && !s.kicked();
    }

    public static State kicked(State s) {
        return new State(s.ticks(), s.contact(), true);
    }

    /** The kick's velocity, off the wall at {@code away} (a unit horizontal), at this jump power. */
    public static Vec3 kick(Vec3 away, double jumpPower) {
        return new Vec3(away.x * KICK_PUSH, jumpPower * KICK_UP, away.z * KICK_PUSH);
    }

    /** The side a wall touches, or null: the first horizontal face whose small step is blocked. Either side. */
    public static @Nullable Direction wallSide(Player p) {
        AABB box = p.getBoundingBox();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!p.level().noCollision(p, box.move(d.getStepX() * 0.05, 0.0, d.getStepZ() * 0.05))) return d;
        }
        return null;
    }

    /** What the rule reads off a player. Either side. */
    public static Input input(Player p, boolean worn, boolean sneaking) {
        RelicJump.State j = RelicJump.state(p, worn, 0);
        boolean blocked = !j.able() || j.flying() || j.gliding() || j.riding() || j.swimming() || j.inFluid()
                || j.climbing();
        return new Input(worn, !p.onGround(), p.horizontalCollision || wallSide(p) != null, sneaking, blocked);
    }

    /**
     * The kick-off, validated by the jump rule (worn, airborne, nothing in the way). The fall is cleared and a
     * jump's exhaustion and stat charged; no motion goes back, the client already moved.
     */
    public static RelicJump.Verdict handle(ServerPlayer player) {
        boolean worn = RelicSignatures.wears(player, RelicSignatures.Signature.WALL_CLING);
        RelicJump.Verdict verdict = RelicJump.check(RelicJump.state(player, worn, 0));
        if (verdict != RelicJump.Verdict.ALLOWED) return verdict;
        KICKS.merge(player.getUUID(), 1, Integer::sum);
        player.resetFallDistance();
        player.awardStat(Stats.JUMP);
        player.causeFoodExhaustion(0.05F);
        return verdict;
    }

    public static int kicks(ServerPlayer player) {
        return KICKS.getOrDefault(player.getUUID(), 0);
    }

    /** Landing resets the server's count. The server tick, and the probe on a stand-in. */
    public static void landed(ServerPlayer player) {
        if (player.onGround()) KICKS.remove(player.getUUID());
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !KICKS.isEmpty()) landed(player);
    }
}
