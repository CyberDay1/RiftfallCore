package com.cyberday1.riftfall.skill;

import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import java.util.function.Predicate;

/** Sweep the caster's whole body along the route, including thin obstacles between endpoints. */
public final class AimTravel {
    private AimTravel() { }

    /** Place the whole body on the exposed side of a hook, rather than inside the wall. */
    public static Vec3 grappleDestination(Vec3 anchor, Direction face, double width, double height, double eyeHeight) {
        if (face == Direction.UP) return anchor.add(0, .15, 0);
        if (face == Direction.DOWN) return anchor.add(0, -height - .15, 0);
        return anchor.add(Vec3.atLowerCornerOf(face.getNormal()).scale(width * .5 + .2))
                .add(0, -eyeHeight * .5, 0);
    }

    /** A side hook near an exposed lip can land on top instead of stopping below the edge. */
    public static Vec3 grappleLedge(Vec3 anchor, Direction face, double width, double top,
                                     Vec3 hanging, Predicate<Vec3> fits) {
        if (face.getAxis() == Direction.Axis.Y || top + .05 - hanging.y > 1.25
                || top < anchor.y) return hanging;
        Vec3 landing = anchor.subtract(Vec3.atLowerCornerOf(face.getNormal()).scale(width * .5 + .2));
        landing = new Vec3(landing.x, top + .05, landing.z);
        Vec3 approach = new Vec3(hanging.x, landing.y, hanging.z);
        if (!fits.test(approach) || !fits.test(landing)) return hanging;
        return destination(approach, landing, approach.distanceTo(landing), fits).distanceTo(landing) < .001
                ? landing : hanging;
    }

    /** Forward leaps aimed at feet height may end on a low platform rather than inside it. */
    public static Vec3 landing(Vec3 requested, double clearance, Predicate<Vec3> fits) {
        for (double rise = 0; rise <= clearance; rise += .125) {
            Vec3 candidate = requested.add(0, rise, 0);
            if (fits.test(candidate)) return candidate;
        }
        // Keep the intended endpoint if blocked; physics will stop short, without teleporting.
        return requested;
    }

    public static Vec3 destination(Vec3 start, Vec3 requested, double range, Predicate<Vec3> fits) {
        Vec3 delta = requested.subtract(start);
        double length = delta.length();
        if (length < 1.0e-6 || range <= 0) return start;
        delta = delta.scale(Math.min(length, range) / length);
        int steps = Math.max(1, (int) Math.ceil(delta.length() / .125));
        Vec3 safe = start;
        for (int i = 1; i <= steps; i++) {
            Vec3 next = start.add(delta.scale((double) i / steps));
            if (!fits.test(next)) break;
            safe = next;
        }
        return safe;
    }
}
