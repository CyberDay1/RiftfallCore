package com.cyberday1.riftfall.skill;

import net.minecraft.world.phys.Vec3;

/** Per-physics-step controller; gravity/friction from the previous step cannot flatten a pull. */
public final class TravelController {
    private final Vec3 start, end;
    private final boolean leap, grapple, targetedGround;
    private Vec3 previous;
    private int age, stalled;
    private boolean finished;

    public TravelController(Vec3 start, Vec3 end, int mode) {
        this.start = start;
        this.end = end;
        this.previous = start;
        this.leap = mode == 1;
        this.grapple = mode == 2;
        this.targetedGround = mode == 3;
    }

    /** Null relinquishes movement to vanilla, including after an external teleport. */
    public Vec3 step(Vec3 position, double fallingSpeed) {
        return step(position, fallingSpeed, offset -> true);
    }

    public Vec3 step(Vec3 position, double fallingSpeed, java.util.function.Predicate<Vec3> fits) {
        if (finished) return null;
        double moved = position.distanceTo(previous);
        if (moved > 3) { finished = true; return null; }
        stalled = age > 0 && moved < .015 ? stalled + 1 : 0;
        if (age >= (grapple ? TravelMotion.GRAPPLE_MAX_TICKS : 45)
                || stalled >= TravelMotion.stallTicks(leap || grapple)
                || (targetedGround ? TravelMotion.arrivedOnGround(position, end)
                        : TravelMotion.arrived(position, end, leap || grapple))) {
            finished = true;
            return new Vec3(0, Math.min(0, fallingSpeed), 0);
        }
        previous = position;
        Vec3 velocity = TravelMotion.velocity(position, start, end, age++, leap, grapple, fallingSpeed);
        return grapple || leap ? TravelMotion.airborneClearance(velocity, fits) : velocity;
    }

    public boolean finished() { return finished; }
    public boolean airborne() { return leap || grapple; }
}
