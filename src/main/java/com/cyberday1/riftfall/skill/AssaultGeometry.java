package com.cyberday1.riftfall.skill;

import net.minecraft.world.phys.Vec3;

/** Approaches a body from the caster's side; never asks a charge to pass through its victim. */
public final class AssaultGeometry {
    private AssaultGeometry() { }
    public static Vec3 approach(Vec3 from, Vec3 target, double casterWidth, double targetWidth) {
        Vec3 horizontal = new Vec3(from.x - target.x, 0, from.z - target.z);
        double clearance = (casterWidth + targetWidth) * .5 + .35;
        if (horizontal.length() <= clearance) return new Vec3(from.x, target.y, from.z);
        return target.add(horizontal.normalize().scale(clearance));
    }
}
