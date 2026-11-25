package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.network.TravelCuePayload;
import com.cyberday1.riftfall.network.TravelStartPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Short physical travel through normal entity movement/collision, never position teleports. */
public final class SkillTravel {
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private record Route(Vec3 end, Vec3 anchor) { }
    private SkillTravel() { }

    private static final class Run {
        final LivingEntity entity;
        final ServerLevel level;
        final Vec3 start, end;
        final boolean leap, grapple;
        Vec3 previous;
        int age, stalled;
        boolean movedOnce;
        boolean targeted, fullRoute = true;
        java.util.function.BooleanSupplier valid = () -> true;
        Runnable contact = () -> { };
        java.util.function.Consumer<Boolean> completion = arrived -> { };
        Run(LivingEntity entity, ServerLevel level, Vec3 end, boolean leap, boolean grapple) {
            this.entity = entity; this.level = level; this.start = entity.position(); this.end = end;
            this.previous = start; this.leap = leap; this.grapple = grapple;
        }
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(SkillTravel::onTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> { RUNS.clear(); TravelCast.clear(); });
    }

    public static boolean active(LivingEntity caster) { return RUNS.containsKey(caster.getUUID()); }

    /** Used before spell delivery so a missed hook cannot spend mana or grant its evade buff. */
    public static boolean canGrapple(LivingEntity caster, double distance) {
        return caster.level() instanceof ServerLevel level && route(caster, level, "grapple", distance) != null;
    }

    public static boolean start(LivingEntity caster, String mode, double distance) {
        if (!(caster.level() instanceof ServerLevel level) || !caster.isAlive() || caster.isSpectator()
                || caster.isPassenger() || caster.isFallFlying() || caster.isRemoved()) return false;
        // An AREA roll's apply-to-caster impact may be visited for several targets. Launch once.
        if (active(caster)) return true;
        Route route = route(caster, level, mode, distance);
        if (route == null) return false;
        Run run = new Run(caster, level, route.end(), mode.equals("leap"), route.anchor() != null);
        launch(run, route.anchor());
        return true;
    }

    /** A targeted route ends on the near side of its victim. Only genuine arrival may deliver its hit. */
    public static boolean startTo(LivingEntity caster, Vec3 requested, double range, boolean leap,
                                  java.util.function.BooleanSupplier valid, Runnable contact,
                                  java.util.function.Consumer<Boolean> completion) {
        if (!(caster.level() instanceof ServerLevel level) || active(caster) || !caster.isAlive()
                || caster.isRemoved() || caster.isSpectator() || caster.isPassenger() || caster.isFallFlying()) return false;
        Vec3 start = caster.position();
        Vec3 delta = requested.subtract(start);
        Vec3 bounded = start.add(delta.normalize().scale(Math.min(range, delta.length())));
        // Entity.move handles the route, including vanilla stepping for ground charges. A flat
        // body sweep here would shorten the skill at slabs/stairs before stepping can happen.
        Vec3 end = bounded;
        if (!level.hasChunkAt(BlockPos.containing(end)) || !level.getWorldBorder().isWithinBounds(
                caster.getBoundingBox().move(end.subtract(start)))) return false;
        Run run = new Run(caster, level, end, leap, false);
        run.targeted = true; run.fullRoute = end.distanceTo(requested) < .3;
        run.valid = valid; run.contact = contact; run.completion = completion;
        launch(run, null);
        return true;
    }

    private static void launch(Run run, Vec3 anchor) {
        LivingEntity caster = run.entity;
        RUNS.put(caster.getUUID(), run);
        Vec3 cue = anchor == null ? run.end : anchor;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster,
                new TravelCuePayload(caster.getId(), run.grapple ? 1 : 0, cue.x, cue.y, cue.z));
        if (caster instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player,
                new TravelStartPayload(run.grapple ? 2 : run.leap ? 1 : run.targeted ? 3 : 0, run.start, run.end));
        drive(run);
    }

    private static Route route(LivingEntity caster, ServerLevel level, String mode, double distance) {
        Vec3 start = caster.position();
        Vec3 look = caster.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < .001) flat = Vec3.directionFromRotation(0, caster.getYRot());
        Vec3 requested = start.add(flat.normalize().scale(distance * caster.getScale()));
        Vec3 anchor = null;
        java.util.function.Predicate<Vec3> fits = point -> {
            var box = caster.getBoundingBox().move(point.subtract(start));
            return level.hasChunkAt(BlockPos.containing(point)) && level.getWorldBorder().isWithinBounds(box)
                    && level.noCollision(caster, box);
        };
        double range = distance * caster.getScale();
        if (mode.equals("grapple")) {
            range *= 2; // Grapple Roll's six-block dash budget becomes a twelve-block cable.
            Vec3 eye = caster.getEyePosition();
            var hit = level.clip(new ClipContext(eye, eye.add(look.scale(range)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (hit.getType() != HitResult.Type.BLOCK) return null;
            anchor = hit.getLocation();
            requested = AimTravel.grappleDestination(anchor, hit.getDirection(), caster.getBbWidth(),
                    caster.getBbHeight(), caster.getEyeHeight());
            var shape = level.getBlockState(hit.getBlockPos()).getCollisionShape(level, hit.getBlockPos());
            if (!shape.isEmpty()) {
                Vec3 ledge = AimTravel.grappleLedge(anchor, hit.getDirection(), caster.getBbWidth(),
                        hit.getBlockPos().getY() + shape.max(net.minecraft.core.Direction.Axis.Y), requested, fits);
                if (ledge.distanceTo(start) <= range) requested = ledge;
            }
            // A tether has an anchor, not a ground dash endpoint. Clipping its route at the first
            // foot-height obstruction makes it release before the pull can lift over that block.
            // Check the destination here; ordinary movement and the bounded lift resolve the route.
            Vec3 end = start.add(requested.subtract(start).normalize().scale(Math.min(range, requested.distanceTo(start))));
            return end.distanceTo(start) < .25 || !fits.test(end) ? null : new Route(end, anchor);
        }
        Vec3 end = mode.equals("leap") ? AimTravel.landing(requested, 1.25, fits) : requested;
        if (!level.hasChunkAt(BlockPos.containing(end)) || !level.getWorldBorder().isWithinBounds(
                caster.getBoundingBox().move(end.subtract(start)))) return null;
        return end.distanceTo(start) < .25 ? null : new Route(end, anchor);
    }

    private static void onTick(ServerTickEvent.Post event) {
        tick();
    }

    /** One server step; also used by isolated physics probes. */
    public static void tick() {
        for (Run run : java.util.List.copyOf(RUNS.values())) {
            LivingEntity caster = run.entity;
            boolean invalid = !caster.isAlive() || caster.isRemoved() || caster.level() != run.level
                    || caster.isSpectator() || caster.isPassenger() || caster.isFallFlying() || !run.valid.getAsBoolean();
            double moved = caster.position().distanceTo(run.previous);
            boolean arrived = run.targeted && !run.leap ? TravelMotion.arrivedOnGround(caster.position(), run.end)
                    : TravelMotion.arrived(caster.position(), run.end, run.grapple || run.leap);
            if (!invalid && !caster.isShiftKeyDown() && moved <= 3) run.contact.run();
            // External teleports cancel the pull, and collisions must not hold a player against a wall.
            // Wait for the owning client's first movement; network latency must not count as a wall.
            if (invalid || caster.isShiftKeyDown() || moved > 3 || ++run.age > 65
                    || arrived
                    || (run.movedOnce && moved < .015 && ++run.stalled >=
                        TravelMotion.stallTicks(run.grapple || run.leap))) {
                RUNS.remove(caster.getUUID());
                if (!invalid && moved <= 3) motion(caster, new Vec3(0, Math.min(0, caster.getDeltaMovement().y), 0));
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster,
                        new TravelCuePayload(caster.getId(), -1, 0, 0, 0));
                run.completion.accept(!invalid && !caster.isShiftKeyDown() && moved <= 3 && arrived && run.fullRoute);
                continue;
            }
            if (moved >= .015) { run.stalled = 0; run.movedOnce = true; }
            run.previous = caster.position();
            drive(run);
        }
    }

    private static void drive(Run run) {
        LivingEntity caster = run.entity;
        Vec3 velocity = TravelMotion.velocity(caster.position(), run.start, run.end, run.age,
                run.leap, run.grapple, caster.getDeltaMovement().y);
        if (run.grapple || run.leap) velocity = TravelMotion.airborneClearance(velocity, offset ->
                run.level.hasChunkAt(BlockPos.containing(caster.position().add(offset)))
                        && run.level.getWorldBorder().isWithinBounds(caster.getBoundingBox().move(offset))
                        && run.level.noCollision(caster, caster.getBoundingBox().move(offset)));
        // Entity.move resolves walls AND floors per axis. A noCollision test on the entire
        // velocity would reject horizontal travel whenever gravity points into the floor.
        Vec3 next = caster.position().add(velocity);
        if (!run.level.hasChunkAt(BlockPos.containing(next))
                || !run.level.getWorldBorder().isWithinBounds(caster.getBoundingBox().move(velocity)))
            velocity = Vec3.ZERO;
        motion(caster, velocity);
        if (run.grapple || run.leap) caster.fallDistance = 0;
    }

    private static void motion(LivingEntity caster, Vec3 velocity) {
        caster.setDeltaMovement(velocity);
        // Players receive one route, not a stream of velocity packets that arrives out of phase
        // with local gravity/friction. Mobs still use their normal server-side physics.
        if (!(caster instanceof ServerPlayer)) {
            caster.hasImpulse = true;
            caster.hurtMarked = true;
        }
    }
}
