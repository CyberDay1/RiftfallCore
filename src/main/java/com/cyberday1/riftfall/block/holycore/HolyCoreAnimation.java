package com.cyberday1.riftfall.block.holycore;

/** Per-view animation state. Network updates change destinations, never the current pose. */
public final class HolyCoreAnimation {
    private static final int[] CORNERS = {0, 2, 7, 5};
    private final Value phase = new Value(50);
    private final Value[] ribs = {new Value(16), new Value(16), new Value(16), new Value(16)};
    private boolean initialized, running;
    private double lastTime, motionTicks, haloOrbit;

    public record Frame(double phase, double ticks, double haloOrbit, double[] ribs) {
        public int lowerStage() { return (int) Math.floor(phase); }
        public int upperStage() { return Math.min(4, lowerStage() + 1); }
        public double blend() { return phase - lowerStage(); }
    }

    public Frame sample(double now, double initialPhase, int targetPhase, boolean formed, int mask) {
        int target = formed ? Math.clamp(targetPhase, 0, 4) : 0;
        // A second render pass or a clock correction can briefly report an earlier partial tick.
        // Hold the visible frame rather than replaying initialization at the new repair stage.
        if (initialized) now = Math.max(now, lastTime);
        if (!initialized) {
            initialized = true;
            phase.reset(now, formed ? Math.clamp(initialPhase, 0, 4) : 0);
            motionTicks = formed ? now : 0;
            haloOrbit = 2 * Math.PI * (motionTicks % HolyCoreMotion.LOOP) / HolyCoreMotion.LOOP
                    * Math.min(1, phase.at(now));
            for (int i = 0; i < 4; i++) ribs[i].reset(now, present(mask, i));
            lastTime = now;
            running = formed;
        }
        if (running) {
            motionTicks += now - lastTime;
            // Integrate angular speed. Multiplying world time by repair progress jumps at loop wraps
            // and spins faster whenever another shard changes the progress.
            haloOrbit += phase.cappedIntegral(lastTime, now) * 2 * Math.PI / HolyCoreMotion.LOOP;
            haloOrbit %= 2 * Math.PI;
        }
        phase.target(now, target);
        double[] visible = new double[4];
        for (int i = 0; i < 4; i++) {
            ribs[i].target(now, present(mask, i));
            visible[i] = ribs[i].at(now);
        }
        lastTime = now;
        running = formed;
        return new Frame(Math.clamp(phase.at(now), 0, 4), motionTicks, haloOrbit, visible);
    }

    private static int present(int mask, int rib) { return (mask & (1 << CORNERS[rib])) == 0 ? 0 : 1; }

    /** Monotone cubic Hermite easing, retaining velocity when an in-progress repair is extended. */
    private static final class Value {
        private final double duration;
        private double start, from, to, velocity;
        Value(double duration) { this.duration = duration; }
        void reset(double now, double value) { start = now; from = to = value; velocity = 0; }
        double u(double now) { return Math.clamp((now - start) / duration, 0, 1); }
        double at(double now) {
            double u = u(now);
            return from + (to - from) * u * u * (3 - 2 * u)
                    + velocity * duration * u * (1 - u) * (1 - u);
        }
        double speed(double now) {
            double u = u(now);
            return (to - from) * 6 * u * (1 - u) / duration + velocity * (3 * u * u - 4 * u + 1);
        }
        void target(double now, double next) {
            if (to == next) return;
            double value = at(now), speed = speed(now), distance = next - value;
            from = value;
            to = next;
            start = now;
            double direction = Math.signum(distance);
            velocity = direction * Math.clamp(speed * direction, 0, 3 * Math.abs(distance) / duration);
        }
        double integral(double now) {
            double u = u(now), u2 = u * u, u3 = u2 * u, u4 = u3 * u;
            return duration * (from * u + (to - from) * (u3 - u4 / 2)
                    + velocity * duration * (u2 / 2 - 2 * u3 / 3 + u4 / 4))
                    + Math.max(0, now - start - duration) * to;
        }
        double cappedIntegral(double a, double b) {
            double va = at(a), vb = at(b);
            if (va >= 1 && vb >= 1) return b - a;
            if (va <= 1 && vb <= 1) return integral(b) - integral(a);
            double lo = a, hi = b;
            boolean increasing = vb > va;
            for (int i = 0; i < 45; i++) {
                double mid = (lo + hi) / 2;
                if ((at(mid) < 1) == increasing) lo = mid; else hi = mid;
            }
            double crossing = (lo + hi) / 2;
            return increasing ? integral(crossing) - integral(a) + b - crossing
                    : crossing - a + integral(b) - integral(crossing);
        }
    }
}
