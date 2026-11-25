package com.cyberday1.riftfall.skill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A value stored between casts: a node's cast opens a store, the hits it lands add units to it
 * (souls, stacks), and the store is released either by a recast inside its window or when its window ends. The
 * release's power is the node's rank power times {@code 1 + perUnit x units}. Names no engine type, so every rule
 * here is testable on its own; {@link RiftStored} binds it.
 *
 * <ul>
 *   <li>One store per player per node. A cast of the node with a live, armed RECAST store is the recast: it releases
 *       the store, costs no mana and starts no timer (the opening cast started it).</li>
 *   <li>Units are capped, and decay one every {@code decayEveryTicks} once {@code decayAfterTicks} pass with no new
 *       one. Decay is read lazily off the clock, never ticked.</li>
 *   <li>Server only and in memory: a logout, a death or a change of level drops the store unreleased.</li>
 * </ul>
 */
public final class Stored {

    /** Release spells live at {@code riftfall:stored/<node>}, a pulse spell at {@code riftfall:stored/<node>_pulse}. */
    public static final String DIR = "stored/";
    public static final String PULSE_SUFFIX = "_pulse";

    /** How a store ends: a recast inside the window, or the window running out. */
    public enum Release {
        RECAST, EXPIRE;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What a RECAST store does when its window ends unspent: go off anyway, or fizzle (the units are lost). */
    public enum Expiry {
        RELEASE, FIZZLE;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Where the release lands: CASTER, the release spell's own targeting from the caster where it stands at release;
     * AIM, an area around the point the opening cast aimed at (a sphere, or a cone when {@code angle} is above 0).
     */
    public enum Anchor {
        CASTER, AIM;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A node's {@code stored} terms. {@code armTicks} is the earliest a store can be released (a recast before it is
     * refused; an AIM store's pulses start there). {@code pulseTicks} above 0 pulses the anchor's area while armed:
     * {@code pull} draws enemies toward the anchor, and a pulse spell, if one exists, lands on what is in it.
     */
    public record Spec(Release release, int windowTicks, int armTicks, Expiry onExpire, double perHit, int cap,
                       int decayAfterTicks, int decayEveryTicks, double perUnit, Anchor anchor, double aimRange,
                       double radius, double angle, int pulseTicks, double pull) {

        public Spec {
            if (release == null || onExpire == null || anchor == null) {
                throw new IllegalArgumentException("a store names its release, its expiry and its anchor");
            }
            if (windowTicks < 1) {
                throw new IllegalArgumentException("a store lives at least one tick, was " + windowTicks);
            }
            if (armTicks < 0 || armTicks >= windowTicks) {
                throw new IllegalArgumentException("a store arms inside its window, was " + armTicks + " of " + windowTicks);
            }
            SkillNode.nonNegative("perHit", perHit);
            SkillNode.nonNegative("perUnit", perUnit);
            SkillNode.nonNegative("aimRange", aimRange);
            SkillNode.nonNegative("radius", radius);
            SkillNode.nonNegative("pull", pull);
            if (cap < 0) {
                throw new IllegalArgumentException("a store's cap is 0 or more, was " + cap);
            }
            if (perHit > 0.0 && cap == 0) {
                throw new IllegalArgumentException("a store that gains on a hit needs a cap");
            }
            if (decayAfterTicks < 0 || decayEveryTicks < 0) {
                throw new IllegalArgumentException("decay ticks are 0 or more");
            }
            if (!(angle >= 0.0 && angle <= 360.0)) {
                throw new IllegalArgumentException("a cone's angle is 0 to 360 degrees, was " + angle);
            }
            if (anchor == Anchor.AIM && !(radius > 0.0 && aimRange > 0.0)) {
                throw new IllegalArgumentException("an aimed store needs an aim range and a radius");
            }
            if (pulseTicks < 0 || (pull > 0.0 && pulseTicks == 0)) {
                throw new IllegalArgumentException("a pull needs a pulse");
            }
            if (pulseTicks > 0 && anchor != Anchor.AIM) {
                throw new IllegalArgumentException("only an aimed store pulses");
            }
        }

        /** Whether the store's units can reach above zero. */
        public boolean gains() {
            return perHit > 0.0;
        }
    }

    /** One live store. Mutable only through {@link Stored}. */
    public static final class Held {
        private final String node;
        private final int rank;
        private final Spec spec;
        private final long openedAt;
        private long expiresAt;
        private long armedAt;
        private double gained;
        private long lastGain;
        private @Nullable Vec3 anchor;
        private @Nullable Vec3 thrown;
        private boolean opened;

        Held(String node, int rank, Spec spec, long now) {
            this.node = node;
            this.rank = rank;
            this.spec = spec;
            this.openedAt = now;
            this.lastGain = now;
            this.expiresAt = now + spec.windowTicks();
            this.armedAt = now + spec.armTicks();
        }

        public String node() {
            return node;
        }

        public int rank() {
            return rank;
        }

        public Spec spec() {
            return spec;
        }

        public long openedAt() {
            return openedAt;
        }

        public long expiresAt() {
            return expiresAt;
        }

        public long armedAt() {
            return armedAt;
        }

        public @Nullable Vec3 anchor() {
            return anchor;
        }

        /** The direction the opening cast threw its anchor (caster to anchor, flat), or null. */
        public @Nullable Vec3 thrown() {
            return thrown;
        }

        public boolean armed(long now) {
            return now >= armedAt;
        }

        public boolean live(long now) {
            return now < expiresAt;
        }

        /** The units held at {@code now}: gained, less the decay since the last gain, never below 0. */
        public double units(long now) {
            return Stored.units(gained, lastGain, now, spec);
        }

        /** The release's power factor at {@code now}. */
        public double factor(long now) {
            return 1.0 + spec.perUnit() * units(now);
        }
    }

    /** What the clock asks of the binding: a release (with its reason) or a pulse. */
    public record Due(UUID player, Held held, Kind kind) {
        public enum Kind { RELEASE, PULSE, FIZZLE }
    }

    /** The two seams in the cast path ({@code SkillCasts#charged}): the recast, and a kept cast. */
    public interface Hooks {
        /** True when this cast was the recast of a live store, which is then released in place of the cast. */
        boolean recast(ServerLevel level, LivingEntity caster, SkillNode node, int rank, long now);

        /** A cast was kept: open the node's store, if it names one. */
        void landed(ServerLevel level, LivingEntity caster, SkillNode node, int rank, long now);
    }

    private static final Map<UUID, Map<String, Held>> HELD = new ConcurrentHashMap<>();
    private static volatile @Nullable Hooks hooks;

    private static final AtomicLong OPENED = new AtomicLong();
    private static final AtomicLong GAINED = new AtomicLong();
    private static final AtomicLong RECAST = new AtomicLong();
    private static final AtomicLong EXPIRED = new AtomicLong();
    private static final AtomicLong FIZZLED = new AtomicLong();
    private static final AtomicLong PULSES = new AtomicLong();
    private static final AtomicLong DROPPED = new AtomicLong();

    private Stored() {
    }

    // ---- the rules ----

    /** Units after decay: one lost every {@code decayEveryTicks} once {@code decayAfterTicks} pass without a gain. */
    public static double units(double gained, long lastGain, long now, Spec spec) {
        double held = Math.min(gained, spec.cap() > 0 ? spec.cap() : gained);
        if (spec.decayEveryTicks() <= 0) {
            return held;
        }
        long idle = now - lastGain - spec.decayAfterTicks();
        if (idle <= 0) {
            return held;
        }
        long lost = idle / spec.decayEveryTicks();
        return Math.max(0.0, held - lost);
    }

    /**
     * The opening cast's hit on one enemy: {@code perHit} units into the node's store, up to the cap. A hit that comes
     * before the cast is kept (the same tick) makes the store; {@link #open} then keeps what it gained.
     */
    public static @Nullable Held gain(UUID player, String node, int rank, Spec spec, long now) {
        if (!spec.gains()) {
            return null;
        }
        Map<String, Held> mine = HELD.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        Held held = mine.get(node);
        if (held == null || !held.live(now) || (!held.opened && held.openedAt != now)) {
            held = new Held(node, rank, spec, now);
            mine.put(node, held);
        }
        double before = held.units(now);
        // Decay is settled into the count first, so a fresh soul restarts the idle clock from what is left.
        held.gained = Math.min(spec.cap(), before + spec.perHit());
        held.lastGain = now;
        GAINED.incrementAndGet();
        return held;
    }

    /**
     * The node's cast was kept: its store opens with what the cast's own hits gained this tick (or none), its anchor
     * and the direction it was thrown. Replaces any older store of the node.
     */
    public static Held open(UUID player, String node, int rank, Spec spec, long now, @Nullable Vec3 anchor,
                            @Nullable Vec3 thrown) {
        Map<String, Held> mine = HELD.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        Held pending = mine.get(node);
        Held held = new Held(node, rank, spec, now);
        if (pending != null && !pending.opened && pending.openedAt == now) {
            // The cast's own hits this tick: kept, at the cast's rank.
            held.gained = pending.gained;
            held.lastGain = pending.lastGain;
        }
        mine.put(node, held);
        held.opened = true;
        held.anchor = anchor;
        held.thrown = thrown;
        OPENED.incrementAndGet();
        return held;
    }

    /** A store held by no one, for a probe to release from a caster that opens none (a mob). */
    static Held loose(String node, int rank, Spec spec, long now, double units, @Nullable Vec3 anchor,
                      @Nullable Vec3 thrown) {
        Held held = new Held(node, rank, spec, now);
        held.gained = units;
        held.anchor = anchor;
        held.thrown = thrown;
        held.opened = true;
        return held;
    }

    /** The node's live, armed RECAST store, taken out to be released; null when this cast is no recast. */
    public static @Nullable Held takeRecast(UUID player, String node, long now) {
        Map<String, Held> mine = HELD.get(player);
        Held held = mine == null ? null : mine.get(node);
        if (held == null || !held.opened || held.spec.release() != Release.RECAST || !held.live(now)
                || !held.armed(now)) {
            return null;
        }
        mine.remove(node);
        RECAST.incrementAndGet();
        return held;
    }

    /**
     * The clock: every store whose window ended is released (EXPIRE, or RECAST that goes off anyway) or fizzles, and
     * every armed pulsing store whose pulse falls on {@code now} pulses. Released and fizzled stores are removed.
     */
    public static List<Due> tick(long now) {
        List<Due> due = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, Held>> e : HELD.entrySet()) {
            for (Held held : List.copyOf(e.getValue().values())) {
                if (!held.opened) {
                    // A hit with no kept cast after it (refused late): never a store.
                    if (now > held.openedAt) {
                        e.getValue().remove(held.node, held);
                    }
                    continue;
                }
                if (!held.live(now)) {
                    e.getValue().remove(held.node, held);
                    boolean fizzle = held.spec.release() == Release.RECAST && held.spec.onExpire() == Expiry.FIZZLE;
                    (fizzle ? FIZZLED : EXPIRED).incrementAndGet();
                    due.add(new Due(e.getKey(), held, fizzle ? Due.Kind.FIZZLE : Due.Kind.RELEASE));
                    continue;
                }
                if (pulses(held.spec, held.armedAt, now)) {
                    PULSES.incrementAndGet();
                    due.add(new Due(e.getKey(), held, Due.Kind.PULSE));
                }
            }
        }
        return due;
    }

    /** Whether a pulsing store pulses at {@code now}: from its arming, every {@code pulseTicks}. */
    public static boolean pulses(Spec spec, long armedAt, long now) {
        return spec.pulseTicks() > 0 && now >= armedAt && (now - armedAt) % spec.pulseTicks() == 0;
    }

    /** Every live store, a snapshot: the binding's marker particles. */
    public static List<Map.Entry<UUID, Held>> all() {
        List<Map.Entry<UUID, Held>> out = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, Held>> e : HELD.entrySet()) {
            for (Held held : e.getValue().values()) {
                if (held.opened) {
                    out.add(Map.entry(e.getKey(), held));
                }
            }
        }
        return out;
    }

    /** The store of {@code node}, or null. Probe and test read. */
    public static @Nullable Held held(UUID player, String node) {
        Map<String, Held> mine = HELD.get(player);
        return mine == null ? null : mine.get(node);
    }

    /** Drops every store of the player, unreleased (a logout, a death, a change of level, a probe's teardown). */
    public static void drop(UUID player) {
        Map<String, Held> mine = HELD.remove(player);
        if (mine != null && !mine.isEmpty()) {
            DROPPED.addAndGet(mine.size());
        }
    }

    // ---- the seams ----

    static void listen(@Nullable Hooks bound) {
        hooks = bound;
    }

    /** {@code SkillCasts#charged}, before the timer: whether this cast is a recast, released in its place. */
    static boolean recast(ServerLevel level, LivingEntity caster, SkillNode node, int rank, long now) {
        Hooks bound = hooks;
        return bound != null && bound.recast(level, caster, node, rank, now);
    }

    /** {@code SkillCasts#charged}, once a cast is kept. */
    static void landed(ServerLevel level, LivingEntity caster, SkillNode node, int rank, long now) {
        Hooks bound = hooks;
        if (bound != null) {
            bound.landed(level, caster, node, rank, now);
        }
    }

    // ---- probe reads ----

    public static long opened() {
        return OPENED.get();
    }

    public static long gained() {
        return GAINED.get();
    }

    public static long recasts() {
        return RECAST.get();
    }

    public static long expired() {
        return EXPIRED.get();
    }

    public static long fizzled() {
        return FIZZLED.get();
    }

    public static long pulsed() {
        return PULSES.get();
    }

    public static long dropped() {
        return DROPPED.get();
    }
}
