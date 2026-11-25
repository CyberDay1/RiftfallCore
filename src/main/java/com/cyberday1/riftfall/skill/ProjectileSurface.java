package com.cyberday1.riftfall.skill;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The explosion origin must be on the air side of a struck surface for line-of-sight checks. A projectile whose
 * last step sank into the block reports an inside hit there, so its origin goes back to where that step began.
 */
public final class ProjectileSurface {
    private ProjectileSurface() { }

    public static Vec3 outside(BlockHitResult hit, Vec3 point) {
        return outside(hit, point, Vec3.ZERO);
    }

    public static Vec3 outside(BlockHitResult hit, Vec3 point, Vec3 lastStep) {
        if (hit.isInside() && lastStep.lengthSqr() > 0.0) return point.subtract(lastStep);
        return point.add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.01));
    }
}
