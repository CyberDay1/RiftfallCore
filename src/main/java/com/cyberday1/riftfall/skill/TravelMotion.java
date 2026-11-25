package com.cyberday1.riftfall.skill;

import net.minecraft.world.phys.Vec3;

/** Bounded acceleration and braking, independent of server/player packet timing. */
public final class TravelMotion {
    public static final double MAX_SPEED = .65;
    public static final int GRAPPLE_STALL_TICKS = 12;
    public static final int GRAPPLE_MAX_TICKS = 65;
    public static int stallTicks(boolean airborne) { return airborne ? GRAPPLE_STALL_TICKS : 8; }
    private TravelMotion() { }

    public static Vec3 velocity(Vec3 position, Vec3 start, Vec3 end, int age, boolean leap,
                                boolean grapple, double fallingSpeed) {
        Vec3 remaining = end.subtract(position);
        double distance = grapple ? remaining.length() : remaining.horizontalDistance();
        double speed = Math.min(MAX_SPEED, Math.min(.18 + age * .09, distance * .45));
        if (grapple) return remaining.normalize().scale(speed);
        Vec3 flat = new Vec3(remaining.x, 0, remaining.z).normalize().scale(speed);
        double y = Math.min(0, fallingSpeed);
        if (leap) {
            double total = end.subtract(start).horizontalDistance();
            double progress = total < .001 ? 1 : Math.clamp(1 - (distance - speed) / total, 0, 1);
            double desiredY = start.y + (end.y - start.y) * progress
                    + Math.min(1.6, total * .16) * Math.sin(Math.PI * progress);
            y = Math.clamp(desiredY - position.y, -.5, .45);
        }
        return new Vec3(flat.x, y, flat.z);
    }

    public static boolean arrived(Vec3 position, Vec3 end, boolean vertical) {
        Vec3 delta = end.subtract(position);
        return (vertical ? delta.length() : delta.horizontalDistance()) < .22;
    }

    /**
     * A ground charge's end is its victim's feet at the cast. It cannot rise to an end above it (a jump, a hover, a
     * step), so it arrives over one; an end below it is fallen to before the route lets go.
     */
    public static boolean arrivedOnGround(Vec3 position, Vec3 end) {
        return arrived(position, end, false) && position.y - end.y < .22;
    }

    /** Shared by leaps and tethers. Ground travel retains vanilla's step-height behavior. */
    public static Vec3 airborneClearance(Vec3 velocity, java.util.function.Predicate<Vec3> fits) {
        Vec3 ahead = new Vec3(velocity.x, Math.max(0, velocity.y), velocity.z);
        if (ahead.horizontalDistance() < .01) return velocity;
        if (AimTravel.destination(Vec3.ZERO, ahead, ahead.length(), fits).distanceTo(ahead) < .001) {
            // After lifting, don't immediately follow the old descending arc back into the lip.
            // Hold feet height only while the next downward step would collide with that surface.
            if (velocity.y < 0 && AimTravel.destination(Vec3.ZERO, velocity, velocity.length(), fits)
                    .distanceTo(velocity) >= .001) return new Vec3(velocity.x, 0, velocity.z);
            return velocity;
        }
        for (double rise = .25; rise <= 1.25; rise += .25) {
            Vec3 up = new Vec3(0, rise, 0);
            // Sweep both legs so a low ceiling, thin wall or overhang cannot be skipped.
            if (AimTravel.destination(Vec3.ZERO, up, rise, fits).distanceTo(up) > .001) break;
            Vec3 over = up.add(velocity.x, 0, velocity.z);
            if (AimTravel.destination(up, over, velocity.horizontalDistance(), fits).distanceTo(over) < .001)
                return new Vec3(0, Math.min(.4, rise), 0);
        }
        return velocity;
    }
}
