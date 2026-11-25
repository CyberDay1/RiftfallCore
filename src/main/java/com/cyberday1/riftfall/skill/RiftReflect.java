package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.init.RiftfallEffects;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.internals.SpellExecution;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The reflect window's binding. An active runs {@code riftfall:reflect_window} on its caster:
 * the node's {@code reflect} terms open a window for their seconds, shown by {@code riftfall:reflecting}. Inside it:
 *
 * <ul>
 *   <li>a hit a living attacker lands (not cancelled: a dodge or a parry reflects nothing) sends the node's share of
 *       its incoming amount back to that attacker, as thorns from the caster, at most once per
 *       {@link Reflects#ICD_TICKS} and only from within {@link Reflects#REACH};</li>
 *   <li>with {@code projectiles}, a vanilla projectile that would hit the caster is turned back at whoever shot it
 *       and becomes the caster's. Spell Engine's own projectiles fire no impact event, so they take the share
 *       instead.</li>
 * </ul>
 *
 * <p>The reflect is a reaction's delivery: inside it nothing reflects, counters or procs again (two reflecting players
 * cannot ping-pong), and a turned projectile is marked so a second reflector lets it through.
 */
public final class RiftReflect {

    public static final ResourceLocation WINDOW = ResourceLocation.fromNamespaceAndPath("riftfall", "reflect_window");
    /** The tag a turned projectile carries: it is turned once, never back again. */
    public static final String TURNED_TAG = "riftfall_reflected";

    private static final AtomicLong OPENED = new AtomicLong();
    private static final AtomicLong REFLECTED = new AtomicLong();
    private static final AtomicLong COOLING = new AtomicLong();
    private static final AtomicLong OUT_OF_REACH = new AtomicLong();
    private static final AtomicLong TURNED = new AtomicLong();
    private static final AtomicLong GUARDED = new AtomicLong();
    private static final java.util.Map<java.util.UUID, Double> LAST_HIT = new java.util.concurrent.ConcurrentHashMap<>();

    private RiftReflect() {
    }

    static void register() {
        SpellHandlers.registerCustomImpact(WINDOW, RiftReflect::open);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RiftReflect::onIncoming);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, RiftReflect::onProjectile);
        NeoForge.EVENT_BUS.addListener(RiftReflect::onLogout);
    }

    /** The {@code riftfall:reflect_window} impact: opens the node's window on its caster, never on an ally in the area. */
    private static SpellHandlers.ImpactResult open(Holder<Spell> spell, SpellPower.Result power, LivingEntity caster,
                                                   @Nullable Entity target, SpellExecution.ImpactContext context) {
        ResourceLocation ability = spell.unwrapKey().map(ResourceKey::location).orElse(null);
        if (!(caster instanceof ServerPlayer player) || ability == null || (target != null && target != caster)) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        SkillDef def = RiftCounters.byAbility(SkillDefs.table(), ability);
        int rank = def == null ? 0 : SkillService.bookOf(player).rank(def.id());
        if (def == null || def.reflect() == null || rank < 1) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        Reflects.Spec spec = def.reflect();
        player.addEffect(new MobEffectInstance(RiftfallEffects.REFLECTING, spec.ticks(), 0, false, true, true));
        Reflects.open(player.getUUID(), new Reflects.Window(def.id(), rank, spec,
                player.level().getGameTime() + spec.ticks()));
        OPENED.incrementAndGet();
        return new SpellHandlers.ImpactResult(true, false);
    }

    /** A hit the caster takes inside its window: the share goes back to a living attacker in reach. */
    private static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = player.level().getGameTime();
        Reflects.Window window = Reflects.window(player.getUUID(), now);
        if (window == null) {
            return;
        }
        if (Reactions.firing()) {
            GUARDED.incrementAndGet();
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == player || !attacker.isAlive()
                || event.getSource().is(DamageTypeTags.IS_FALL) || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.THORNS)) {
            return;
        }
        if (attacker.distanceToSqr(player) > Reflects.REACH * Reflects.REACH) {
            OUT_OF_REACH.incrementAndGet();
            return;
        }
        if (!Reflects.ready(player.getUUID(), now)) {
            COOLING.incrementAndGet();
            return;
        }
        double back = Reflects.reflected(event.getAmount(), window.spec().share());
        if (!(back > 0.0)) {
            return;
        }
        Reflects.spent(player.getUUID(), now);
        LAST_HIT.put(player.getUUID(), (double) event.getAmount());
        Reactions.deliver(() -> SkillDamage.dealing(() -> attacker.hurt(player.damageSources().thorns(player), (float) back)));
        REFLECTED.incrementAndGet();
    }

    /** A vanilla projectile about to hit a caster whose window turns projectiles: sent back at its shooter. */
    private static void onProjectile(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Projectile projectile = event.getProjectile();
        Reflects.Window window = Reflects.window(player.getUUID(), player.level().getGameTime());
        if (window == null || !window.spec().projectiles() || projectile.getTags().contains(TURNED_TAG)
                || !(projectile.getOwner() instanceof LivingEntity shooter) || shooter == player) {
            return;
        }
        event.setCanceled(true);
        turn(projectile, shooter, player);
        TURNED.incrementAndGet();
    }

    /** Aims {@code projectile} back at {@code shooter} at its own speed and makes it the caster's. */
    static void turn(Projectile projectile, LivingEntity shooter, ServerPlayer caster) {
        double speed = Math.max(0.5, projectile.getDeltaMovement().length());
        Vec3 to = shooter.getEyePosition().subtract(projectile.position());
        Vec3 velocity = to.lengthSqr() < 1.0e-6 ? projectile.getDeltaMovement().scale(-1.0) : to.normalize().scale(speed);
        projectile.setDeltaMovement(velocity);
        double flat = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        projectile.setYRot((float) (Math.atan2(velocity.x, velocity.z) * (180.0 / Math.PI)));
        projectile.setXRot((float) (Math.atan2(velocity.y, flat) * (180.0 / Math.PI)));
        projectile.yRotO = projectile.getYRot();
        projectile.xRotO = projectile.getXRot();
        projectile.hasImpulse = true;
        projectile.setOwner(caster);
        projectile.addTag(TURNED_TAG);
    }

    /** Whether the player's reflect window is open now. Probe read. */
    public static boolean open(ServerPlayer player) {
        return Reflects.window(player.getUUID(), player.level().getGameTime()) != null;
    }

    public static void close(ServerPlayer player) {
        Reflects.close(player.getUUID());
        player.removeEffect(RiftfallEffects.REFLECTING);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Reflects.close(event.getEntity().getUUID());
    }

    /** Windows opened, hits reflected (and their sum), refused by the ICD, out of reach, projectiles turned. */
    public static long opened() {
        return OPENED.get();
    }

    public static long reflected() {
        return REFLECTED.get();
    }

    public static long cooling() {
        return COOLING.get();
    }

    public static long outOfReach() {
        return OUT_OF_REACH.get();
    }

    public static long turned() {
        return TURNED.get();
    }

    /** The incoming amount of the player's last reflected hit, or -1. Probe read. */
    public static double lastHit(java.util.UUID player) {
        return LAST_HIT.getOrDefault(player, -1.0);
    }

    /** Hits inside a reaction's delivery, which reflect nothing. */
    public static long guarded() {
        return GUARDED.get();
    }
}
