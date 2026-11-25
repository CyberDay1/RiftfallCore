package com.cyberday1.riftfall.skill;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * The reaction triggers: what a spell passive reacts to beyond Spell Engine's own triggers, and the few terms a
 * reaction needs. Names no engine type, so the cast and dodge sides can report without loading Spell Engine, and
 * every rule here is testable on its own. {@link RiftPassiveSpells} and {@link RiftCounters} bind it.
 *
 * <ul>
 *   <li>{@link On}: an event Riftfall fires itself. MOVE is a movement-family cast landing, CAST any castable's.</li>
 *   <li>{@link Reaction#count}: a hit or cast counter. The proc fires on the Nth qualifying event, each within
 *       {@link Reaction#countWindowTicks} of the last, then the count starts again.</li>
 *   <li>{@link Surge}: the {@code riftfall:surge} impact's buff, a transient attribute bonus of the node's power for
 *       a few seconds.</li>
 *   <li>{@link #behind}: the facing test the {@code riftfall:from_behind} predicate reads.</li>
 *   <li>{@link #firing}: the recursion guard. Nothing a reaction delivers may fire another reaction.</li>
 * </ul>
 */
public final class Reactions {

    /** An event Riftfall fires for a spell passive, beside the engine's own triggers. */
    public enum On {
        /** A movement-family castable landed: a Movement lever, or the movement family (a dash, a blink, a roll). */
        MOVE,
        /** Any castable landed, from any route. */
        CAST,
        /**
         * A harmful effect was removed from the player (a cleanse, milk, a totem), or by a cast of the player's from
         * anyone. Natural expiry is no cleanse.
         */
        CLEANSE;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A passive's reaction terms. {@code on} null leaves the firing to the spell's own triggers. {@code count} 1 fires
     * on every event; above 1 it counts events, and a gap longer than {@code countWindowTicks} starts the count over.
     */
    public record Reaction(@Nullable On on, int count, int countWindowTicks) {

        public static final int NO_COUNT = 1;

        public Reaction {
            if (count < 1) {
                throw new IllegalArgumentException("a reaction counts at least one event, was " + count);
            }
            if (countWindowTicks < 0) {
                throw new IllegalArgumentException("a count window is 0 or more ticks, was " + countWindowTicks);
            }
            if (count == NO_COUNT && countWindowTicks != 0) {
                throw new IllegalArgumentException("a count window needs a count above 1");
            }
            if (count > NO_COUNT && countWindowTicks == 0) {
                throw new IllegalArgumentException("a count needs a window: events with no gap limit never reset");
            }
            if (on == null && count == NO_COUNT) {
                throw new IllegalArgumentException("a reaction names an event, a count, or both");
            }
        }

        public boolean counts() {
            return count > NO_COUNT;
        }
    }

    /** One attribute a surge raises, and how (the modifier's operation). */
    public record Term(ResourceLocation attribute, AttributeModifier.Operation operation) {
        public Term {
            if (attribute == null || operation == null) {
                throw new IllegalArgumentException("a surge term names an attribute and an operation");
            }
        }
    }

    /** The {@code riftfall:surge} buff: each term raised by the node's power for {@code seconds}. */
    public record Surge(double seconds, List<Term> terms) {
        public Surge {
            if (!(seconds > 0.0) || Double.isInfinite(seconds)) {
                throw new IllegalArgumentException("a surge lasts a positive time, was " + seconds);
            }
            if (terms == null || terms.isEmpty()) {
                throw new IllegalArgumentException("a surge raises at least one attribute");
            }
            terms = List.copyOf(terms);
        }

        public int ticks() {
            return (int) Math.round(seconds * 20.0);
        }
    }

    /** The default rear cone of {@code riftfall:from_behind}: within 60 degrees of straight behind. */
    public static final double BEHIND_HALF_ANGLE = 60.0;

    private record Tally(int count, long lastTick) {
    }

    private static final Map<UUID, Map<String, Tally>> TALLIES = new ConcurrentHashMap<>();
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private static volatile @Nullable BiConsumer<LivingEntity, SkillNode> castListener;
    private static volatile @Nullable Dodges dodgeListener;
    private static volatile @Nullable java.util.function.Consumer<ServerPlayer> cleanseListener;
    /** The caster whose cast is delivering on this thread, so an effect it removes is its cleanse. */
    private static final ThreadLocal<LivingEntity> CLEANSER = new ThreadLocal<>();

    /** A dodge as the two dodge rolls report it: who dodged, the hit's source, and its size (0 for a projectile). */
    @FunctionalInterface
    public interface Dodges {
        void dodged(LivingEntity dodger, DamageSource source, float amount);
    }

    private Reactions() {
    }

    // ---- the counter ----

    /**
     * One qualifying event for {@code node}: true when it completes the count (and the count starts over), false while
     * it is still counting. A gap above the window restarts the count at this event.
     */
    public static boolean count(UUID player, String node, Reaction reaction, long now) {
        if (!reaction.counts()) {
            return true;
        }
        Map<String, Tally> mine = TALLIES.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        Tally last = mine.get(node);
        int n = last == null || now - last.lastTick() > reaction.countWindowTicks() ? 1 : last.count() + 1;
        if (n >= reaction.count()) {
            mine.remove(node);
            return true;
        }
        mine.put(node, new Tally(n, now));
        return false;
    }

    /** The running count, 0 when none. Probe and test read. */
    public static int counted(UUID player, String node) {
        Map<String, Tally> mine = TALLIES.get(player);
        Tally t = mine == null ? null : mine.get(node);
        return t == null ? 0 : t.count();
    }

    public static void forget(UUID player) {
        TALLIES.remove(player);
    }

    // ---- facing ----

    /**
     * Whether the attacker stands behind the target: the target's facing ({@code targetYaw}, Minecraft degrees: 0 faces
     * +z, 90 faces -x) and the ground-plane offset from the target to the attacker are more than
     * {@code 180 - halfAngle} degrees apart. An attacker on the target's own spot is not behind it.
     */
    public static boolean behind(double targetYaw, double dx, double dz, double halfAngle) {
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-6) {
            return false;
        }
        double rad = Math.toRadians(targetYaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        double cos = (fx * dx + fz * dz) / length;
        return cos <= -Math.cos(Math.toRadians(halfAngle)) + 1.0e-9;
    }

    /** The predicate's reading: {@code param} is the half-angle in degrees, or null for the default. */
    public static boolean behind(Entity target, Entity attacker, @Nullable String param) {
        double half = BEHIND_HALF_ANGLE;
        if (param != null && !param.isBlank()) {
            try {
                half = Double.parseDouble(param.trim());
            } catch (NumberFormatException ignored) {
                half = BEHIND_HALF_ANGLE;
            }
        }
        return behind(target.getYRot(), attacker.getX() - target.getX(), attacker.getZ() - target.getZ(), half);
    }

    // ---- the movement family ----

    /** A movement-family castable: the Movement lever, or the movement family authored on the node. */
    public static boolean moves(SkillNode node) {
        return node.lever() instanceof SkillNode.Movement || node.category() == SkillNode.Category.MOVEMENT;
    }

    /** Whether {@code reaction} answers a landed cast of {@code node}. A cleanse reaction answers no cast. */
    public static boolean answers(Reaction reaction, SkillNode node) {
        if (reaction.on() == On.CAST) {
            return true;
        }
        return reaction.on() == On.MOVE && moves(node);
    }

    // ---- the cleanse ----

    /**
     * The players a removal of a harmful effect from {@code from} is a cleanse for: {@code from} itself if a player,
     * and the caster whose cast is delivering (it cleansed an ally, or itself), once each.
     */
    public static List<ServerPlayer> cleansedFor(LivingEntity from, @Nullable LivingEntity cleanser) {
        List<ServerPlayer> out = new java.util.ArrayList<>(2);
        if (from instanceof ServerPlayer p) {
            out.add(p);
        }
        if (cleanser instanceof ServerPlayer c && c != from) {
            out.add(c);
        }
        return out;
    }

    /** Runs {@code body} (a cast's delivery) with {@code caster} as the cleanser of anything it removes. */
    public static void cleansing(LivingEntity caster, Runnable body) {
        LivingEntity outer = CLEANSER.get();
        CLEANSER.set(caster);
        try {
            body.run();
        } finally {
            if (outer == null) {
                CLEANSER.remove();
            } else {
                CLEANSER.set(outer);
            }
        }
    }

    /** A harmful effect left {@code from} other than by running out. Ignored inside a reaction and on a client. */
    public static void cleansed(LivingEntity from) {
        java.util.function.Consumer<ServerPlayer> listener = cleanseListener;
        if (listener == null || firing() || from.level().isClientSide()) {
            return;
        }
        for (ServerPlayer player : cleansedFor(from, CLEANSER.get())) {
            listener.accept(player);
        }
    }

    static void listenCleanses(@Nullable java.util.function.Consumer<ServerPlayer> cleanses) {
        cleanseListener = cleanses;
    }

    // ---- the recursion guard ----

    /** True while a reaction is delivering: anything it causes fires no further reaction. */
    public static boolean firing() {
        return DEPTH.get()[0] > 0;
    }

    /** Runs {@code body} as a reaction's delivery. */
    public static void deliver(Runnable body) {
        int[] depth = DEPTH.get();
        depth[0]++;
        try {
            body.run();
        } finally {
            depth[0]--;
        }
    }

    // ---- the seams ----

    static void listenCasts(@Nullable BiConsumer<LivingEntity, SkillNode> casts) {
        castListener = casts;
    }

    static void listenDodges(@Nullable Dodges dodges) {
        dodgeListener = dodges;
    }

    /** A castable landed (SkillCasts#charged, every route; the listener keeps players). Ignored inside a reaction. */
    static void cast(LivingEntity caster, SkillNode node) {
        BiConsumer<LivingEntity, SkillNode> casts = castListener;
        if (casts != null && !firing()) {
            casts.accept(caster, node);
        }
    }

    /** A dodge roll cancelled a hit: Iron's and Apothic's report here (their mixins). Ignored inside a reaction. */
    public static void dodged(LivingEntity dodger, DamageSource source, float amount) {
        Dodges dodges = dodgeListener;
        if (dodges != null && dodger instanceof ServerPlayer && !firing() && !dodger.level().isClientSide()) {
            dodges.dodged(dodger, source, amount);
        }
    }

    /** A projectile Apothic dodged before it hit: the source is the projectile's, its owner the attacker. */
    public static void dodgedProjectile(LivingEntity dodger, Entity projectile) {
        if (dodgeListener == null || !(dodger instanceof ServerPlayer)) {
            return;
        }
        Entity owner = projectile instanceof net.minecraft.world.entity.projectile.Projectile p ? p.getOwner() : null;
        dodged(dodger, dodger.damageSources().mobProjectile(projectile, owner instanceof LivingEntity l ? l : null), 0.0F);
    }
}
