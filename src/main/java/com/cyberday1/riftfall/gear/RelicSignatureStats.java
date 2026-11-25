package com.cyberday1.riftfall.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.RelicSignatures.ActiveSet;
import com.cyberday1.riftfall.gear.RelicSignatures.Signature;

/**
 * Batch A: the signatures that are attribute lines, some on a condition. Transient
 * modifiers, put right on every active-set refresh and whenever a condition moves: Momentum every tick, burning
 * every tick, light and the wet cost once a second. Fireborn's fire cut is in {@code CombatStats}. Slice 10's lines
 * (Unstable Step, Frostbound, Last Stand's resistance under 30%) ride the same path.
 */
public final class RelicSignatureStats {

    /**
     * Nightstalker: at or under {@code NIGHT_DARK} is dark, at or over {@code NIGHT_BRIGHT} is bright. Re-set from the
     * riftlight survey once rooms were lit (2026-11-21): 3/9 left 2% dark and 87% bright.
     */
    public static final int NIGHT_DARK = 9;
    public static final int NIGHT_BRIGHT = 12;
    public static final double NIGHT_CRIT_DAMAGE = 0.20;
    public static final double NIGHT_BRIGHT_TERM = -0.10;

    /** Fireborn: the share of fire damage turned away, the bucket term while burning, the wet cost a second. */
    public static final double FIRE_TURNED_AWAY = 0.75;
    public static final double FIRE_TERM = 0.15;
    public static final double WET_SHARE = 0.02;

    /** Momentum: one step a half second of grounded sprint, six at most, lost after a second airborne. */
    public static final int MOMENTUM_STEP_TICKS = 10;
    public static final int MOMENTUM_MAX = 6;
    public static final double MOMENTUM_PER = 0.02;
    public static final int MOMENTUM_AIR_TICKS = 20;

    public enum Light {
        DARK, NEUTRAL, BRIGHT
    }

    /** One attribute line; an amount of 0 means the line is off. */
    public record Line(ResourceLocation attribute, double amount, Operation operation) {
        Line off() {
            return new Line(attribute, 0.0D, operation);
        }
    }

    /** The conditions the lines read. */
    public record Conditions(int momentum, Light light, boolean low) {
        public static final Conditions NONE = new Conditions(0, Light.NEUTRAL, false);

        public Conditions(int momentum, Light light) {
            this(momentum, light, false);
        }
    }

    /** Grounded sprint ticks and ticks airborne since; a stop ends it, a hit ends it. */
    public record Momentum(int ticks, int air) {
        public static final Momentum NONE = new Momentum(0, 0);

        public Momentum next(boolean sprinting, boolean onGround) {
            if (!sprinting) return NONE;
            if (onGround) return new Momentum(ticks + 1, 0);
            return air + 1 >= MOMENTUM_AIR_TICKS ? NONE : new Momentum(ticks, air + 1);
        }

        public int stacks() {
            return Math.min(MOMENTUM_MAX, ticks / MOMENTUM_STEP_TICKS);
        }
    }

    private static final class State {
        Momentum momentum = Momentum.NONE;
        Light light = Light.NEUTRAL;
        boolean burning;
        boolean low;
    }

    private static final ResourceLocation MAX_HEALTH = mc("generic.max_health");
    private static final ResourceLocation KNOCKBACK_RESISTANCE = mc("generic.knockback_resistance");
    private static final ResourceLocation MOVEMENT_SPEED = mc("generic.movement_speed");
    private static final ResourceLocation SWIM_SPEED = ResourceLocation.fromNamespaceAndPath("neoforge", "swim_speed");
    private static final ResourceLocation CRIT_DAMAGE = apothic("crit_damage");
    private static final ResourceLocation EXPERIENCE = apothic("experience_gained");

    private static final Map<UUID, State> STATE = new ConcurrentHashMap<>();

    private RelicSignatureStats() {
    }

    public static void register() {
        RelicSignatures.damageTerm(Signature.FIREBORN, p -> state(p).burning ? FIRE_TERM : 0.0D);
        RelicSignatures.damageTerm(Signature.NIGHTSTALKER,
                p -> state(p).light == Light.BRIGHT ? NIGHT_BRIGHT_TERM : 0.0D);
        NeoForge.EVENT_BUS.addListener(RelicSignatureStats::onTick);
        NeoForge.EVENT_BUS.addListener(RelicSignatureStats::onHurt);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                STATE.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> STATE.clear());
    }

    public static Light band(int light) {
        if (light <= NIGHT_DARK) return Light.DARK;
        return light >= NIGHT_BRIGHT ? Light.BRIGHT : Light.NEUTRAL;
    }

    /** The light Nightstalker reads: block or sky, whichever is brighter, at that block. */
    public static int lightAt(Level level, BlockPos pos) {
        return level.getMaxLocalRawBrightness(pos);
    }

    /**
     * A signature's lines, every one present whatever the conditions so the ids stay put; a line whose condition
     * is off has amount 0. Fireborn has none: its terms are the bucket and the damage hook.
     */
    public static List<Line> lines(Signature signature, Conditions c) {
        return switch (signature) {
            case DEAD_WEIGHT -> List.of(
                    new Line(MAX_HEALTH, 0.15, Operation.ADD_MULTIPLIED_TOTAL),
                    new Line(KNOCKBACK_RESISTANCE, 0.25, Operation.ADD_VALUE),
                    new Line(MOVEMENT_SPEED, -0.10, Operation.ADD_MULTIPLIED_TOTAL),
                    new Line(SWIM_SPEED, -0.30, Operation.ADD_MULTIPLIED_TOTAL));
            case PACK_LEADER -> List.of(
                    new Line(riftfall("summon_power"), 0.25, Operation.ADD_VALUE),
                    new Line(riftfall("summon_duration"), 10.0, Operation.ADD_VALUE),
                    new Line(MAX_HEALTH, -0.10, Operation.ADD_MULTIPLIED_TOTAL));
            case GREED -> List.of(
                    new Line(riftfall("scavenging"), 0.05, Operation.ADD_VALUE),
                    new Line(EXPERIENCE, 0.15, Operation.ADD_MULTIPLIED_BASE),
                    new Line(riftfall("threat_generation"), 0.25, Operation.ADD_VALUE));
            case MOMENTUM -> List.of(
                    new Line(MOVEMENT_SPEED, MOMENTUM_PER * Math.max(0, Math.min(MOMENTUM_MAX, c.momentum())),
                            Operation.ADD_MULTIPLIED_BASE));
            case UNSTABLE_STEP -> List.of(new Line(riftfall("movement_haste"), 30.0, Operation.ADD_VALUE));
            case FROSTBOUND -> List.of(new Line(MOVEMENT_SPEED, -0.10, Operation.ADD_MULTIPLIED_TOTAL));
            case LAST_STAND -> {
                Line resist = new Line(riftfall("damage_resistance"), RelicSignatureCosts.STAND_RESISTANCE,
                        Operation.ADD_VALUE);
                yield List.of(c.low() ? resist : resist.off());
            }
            case NIGHTSTALKER -> {
                Line crit = new Line(CRIT_DAMAGE, NIGHT_CRIT_DAMAGE, Operation.ADD_VALUE);
                yield List.of(c.light() == Light.DARK ? crit : crit.off());
            }
            default -> List.of();
        };
    }

    /** {@code riftfall:relic_signature/<key>/<n>}. */
    public static ResourceLocation id(Signature signature, int index) {
        return RiftfallCore.id("relic_signature/" + signature.key() + "/" + index);
    }

    /** The lines the set wants on now: only active signatures, only lines that are on. */
    public static Map<ResourceLocation, Line> desired(ActiveSet set, Conditions c) {
        Map<ResourceLocation, Line> out = new java.util.LinkedHashMap<>();
        for (Signature s : Signature.values()) {
            if (!set.isActive(s)) continue;
            List<Line> lines = lines(s, c);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).amount() != 0.0D) out.put(id(s, i), lines.get(i));
            }
        }
        return out;
    }

    /** Momentum's steps, without starting a state for a player who has none. */
    public static int momentum(ServerPlayer player) {
        State s = STATE.get(player.getUUID());
        return s == null ? 0 : s.momentum.stacks();
    }

    public static Conditions conditions(ServerPlayer player) {
        State s = state(player);
        return new Conditions(s.momentum.stacks(), s.light, s.low);
    }

    /** Puts every signature line right for the cached active set and the current conditions. Server. */
    public static void apply(ServerPlayer player) {
        ActiveSet set = RelicSignatures.active(player);
        Map<ResourceLocation, Line> want = RelicSignatures.suspended() ? Map.of() : desired(set, conditions(player));
        for (Signature s : Signature.values()) {
            List<Line> lines = lines(s, Conditions.NONE);
            for (int i = 0; i < lines.size(); i++) {
                ResourceLocation id = id(s, i);
                AttributeInstance instance = instance(player, lines.get(i).attribute());
                if (instance == null) continue;
                Line line = want.get(id);
                AttributeModifier now = instance.getModifier(id);
                if (line == null) {
                    if (now != null) instance.removeModifier(id);
                } else if (now == null || now.amount() != line.amount()) {
                    instance.addOrUpdateTransientModifier(new AttributeModifier(id, line.amount(), line.operation()));
                }
            }
        }
        RelicStackSync.check(player);
    }

    /** A respawn or a changed relic: conditions start over. */
    public static void reset(ServerPlayer player) {
        STATE.remove(player.getUUID());
    }

    /**
     * One player tick. {@code second} is the once-a-second half: light, and the wet cost. The server tick, and the
     * probe on a stand-in that does not tick.
     */
    public static void update(ServerPlayer player, boolean second) {
        ActiveSet set = RelicSignatures.active(player);
        if (set.active().isEmpty() && !STATE.containsKey(player.getUUID())) return;
        State s = state(player);
        boolean lines = false;
        boolean bucket = false;
        Momentum momentum = set.isActive(Signature.MOMENTUM)
                ? s.momentum.next(player.isSprinting(), player.onGround()) : Momentum.NONE;
        if (momentum.stacks() != s.momentum.stacks()) lines = true;
        s.momentum = momentum;
        boolean low = set.isActive(Signature.LAST_STAND) && RelicSignatureCosts.low(player);
        if (low != s.low) {
            s.low = low;
            lines = true;
            bucket = true;
        }
        boolean burning = set.isActive(Signature.FIREBORN) && player.isOnFire();
        if (burning != s.burning) {
            s.burning = burning;
            bucket = true;
        }
        if (second) {
            Light light = set.isActive(Signature.NIGHTSTALKER)
                    ? band(lightAt(player.level(), player.blockPosition())) : Light.NEUTRAL;
            if (light != s.light) {
                s.light = light;
                lines = true;
                bucket = true;
            }
            if (set.isActive(Signature.FIREBORN) && player.isInWaterOrRain()) wet(player);
            RelicSignatureCosts.second(player, set);
        }
        if (lines) apply(player);
        if (bucket) RelicSignatures.rebucket(player);
    }

    /** Fireborn's cost: water or rain takes {@link #WET_SHARE} of max health, as relic damage. */
    static void wet(ServerPlayer player) {
        player.hurt(RelicSignatures.damage(player.level(), null), (float) (player.getMaxHealth() * WET_SHARE));
    }

    /** Fireborn on the victim: the share of a fire hit it turns away, or 0. */
    public static double fireTurnedAway(LivingEntity victim, DamageSource source) {
        return victim instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FIRE)
                && RelicSignatures.active(player).isActive(Signature.FIREBORN) ? FIRE_TURNED_AWAY : 0.0D;
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) update(player, player.tickCount % 20 == 0);
    }

    /** A hit taken ends Momentum. */
    private static void onHurt(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F || !(event.getEntity() instanceof ServerPlayer player)) return;
        State s = STATE.get(player.getUUID());
        if (s == null || s.momentum.ticks() == 0) return;
        boolean had = s.momentum.stacks() > 0;
        s.momentum = Momentum.NONE;
        if (had) apply(player);
    }

    private static State state(ServerPlayer player) {
        return STATE.computeIfAbsent(player.getUUID(), id -> new State());
    }

    private static AttributeInstance instance(LivingEntity entity, ResourceLocation attribute) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE
                .getHolder(ResourceKey.create(Registries.ATTRIBUTE, attribute)).orElse(null);
        return holder == null ? null : entity.getAttribute(holder);
    }

    /** Every attribute a batch A line names, for the probe and the test. */
    public static List<ResourceLocation> attributes() {
        List<ResourceLocation> out = new ArrayList<>();
        for (Signature s : Signature.values()) {
            for (Line l : lines(s, Conditions.NONE)) if (!out.contains(l.attribute())) out.add(l.attribute());
        }
        return out;
    }

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private static ResourceLocation apothic(String path) {
        return ResourceLocation.fromNamespaceAndPath("apothic_attributes", path);
    }

    private static ResourceLocation riftfall(String path) {
        return RiftfallCore.id(path);
    }
}
