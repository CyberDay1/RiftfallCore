package com.cyberday1.riftfall.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.Comparator;
import java.util.function.Predicate;

/** Crosshair-selected blink landings; independent of the optional spell engine. */
public final class BlinkTravel {
    private BlinkTravel() { }

    public static Vec3 destination(LivingEntity caster, Vec3 look, double range) {
        var start = caster.position();
        if (!Double.isFinite(range) || range <= 0 || look.lengthSqr() < .000001) return start;
        var world = caster.level();
        var direction = look.normalize();
        var eye = caster.getEyePosition();
        var hit = world.clip(new ClipContext(eye, eye.add(direction.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Predicate<Vec3> fits = point -> {
            var body = caster.getBoundingBox().move(point.subtract(start));
            return body.minY >= world.getMinBuildHeight() && body.maxY <= world.getMaxBuildHeight()
                    && world.hasChunksAt(BlockPos.containing(body.minX, body.minY, body.minZ),
                            BlockPos.containing(body.maxX, body.maxY, body.maxZ))
                    && world.getWorldBorder().isWithinBounds(body) && world.noCollision(caster, body)
                    && !world.containsAnyLiquid(body);
        };
        if (hit.getType() == HitResult.Type.BLOCK) {
            var pos = hit.getBlockPos();
            var shape = world.getBlockState(pos).getCollisionShape(world, pos, CollisionContext.of(caster));
            // Inspect actual surfaces: slabs, stairs and negative coordinates must not round to a full block.
            var surfaces = shape.toAabbs().stream().sorted(Comparator.comparingDouble((AABB b) -> b.maxY).reversed()).toList();
            for (var surface : surfaces) {
                var top = onSurface(hit.getLocation(), surface.move(pos), caster.getBbWidth());
                if (inRange(start, top, range) && fits.test(top)) return top;
            }
            // No room above the selected block: stay on its visible side, never search beneath it.
            return nearestLanding(start, hit.getLocation(), range, fits);
        }
        // Empty-space casts follow pitch without an unrelated downward ground snap.
        return nearestLanding(start, start.add(direction.scale(range)), range, fits);
    }

    /** Blinks can clear low obstacles beneath the eye ray; only the landing body needs space. */
    public static Vec3 nearestLanding(Vec3 start, Vec3 requested, double range, Predicate<Vec3> fits) {
        var delta = requested.subtract(start);
        double length = delta.length();
        if (length < .000001 || range <= 0) return start;
        delta = delta.scale(Math.min(length, range) / length);
        int steps = Math.max(1, (int) Math.ceil(delta.length() / .125));
        for (int i = steps; i > 0; i--) {
            var point = start.add(delta.scale((double) i / steps));
            if (fits.test(point)) return point;
        }
        return start;
    }

    public static boolean inRange(Vec3 start, Vec3 destination, double range) {
        return start.distanceToSqr(destination) <= range * range + .000001;
    }

    public static Vec3 onSurface(Vec3 hit, AABB surface, double width) {
        return new Vec3(inset(hit.x, surface.minX, surface.maxX, width / 2 + .01), surface.maxY + .01,
                inset(hit.z, surface.minZ, surface.maxZ, width / 2 + .01));
    }

    private static double inset(double value, double min, double max, double margin) {
        return max - min <= margin * 2 ? (min + max) / 2 : Math.clamp(value, min + margin, max - margin);
    }
}
