package com.cyberday1.riftfall.skill;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellExecution;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The stored value's binding. {@link Stored} holds the rules; this is where they touch the
 * engine and the world:
 *
 * <ul>
 *   <li>{@code riftfall:store_hit}, a HARMFUL impact on the opening spell: each enemy it lands on adds the node's units;</li>
 *   <li>a kept cast of a node with {@code stored} terms opens its store, aimed at the point the caster looks at (an AIM
 *       store) or on the caster;</li>
 *   <li>a recast of a live, armed RECAST store releases it instead of casting (free, on the running timer);</li>
 *   <li>the server tick releases a store whose window ended, fizzles a RECAST one that says so, and pulses an armed AIM
 *       store (a pull toward the anchor, and the node's pulse spell, if any, on what stands in it).</li>
 * </ul>
 *
 * <p>The release is {@code riftfall:stored/<node>} at the opening rank's power times the store's factor. A CASTER
 * release uses the spell's own targeting from where the caster stands; an AIM release strikes every living thing in
 * the anchor's sphere, or in its cone toward the caster's look (or the throw, when the window ran out).
 */
public final class RiftStored {

    public static final ResourceLocation STORE_HIT = ResourceLocation.fromNamespaceAndPath("riftfall", "store_hit");
    private static final TagKey<EntityType<?>> BOSSES =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "bosses"));
    /** How often an aimed store shows its anchor, in ticks. */
    static final int MARK_EVERY = 10;

    /** What a release did. Probe read. */
    public record Released(String node, String kind, double units, double factor, int targets, @Nullable Vec3 anchor) {
    }

    private static final Map<UUID, Released> LAST = new ConcurrentHashMap<>();
    private static final AtomicLong RELEASED = new AtomicLong();
    private static final AtomicLong PULLED = new AtomicLong();

    private RiftStored() {
    }

    static void register() {
        SpellHandlers.registerCustomImpact(STORE_HIT, RiftStored::storeHit);
        Stored.listen(new Stored.Hooks() {
            @Override
            public boolean recast(ServerLevel level, LivingEntity caster, SkillNode node, int rank, long now) {
                return RiftStored.recast(level, caster, node, now);
            }

            @Override
            public void landed(ServerLevel level, LivingEntity caster, SkillNode node, int rank, long now) {
                RiftStored.landed(caster, node, rank, now);
            }
        });
        NeoForge.EVENT_BUS.addListener(RiftStored::onServerTick);
        NeoForge.EVENT_BUS.addListener(RiftStored::onLogout);
        NeoForge.EVENT_BUS.addListener(RiftStored::onDeath);
        NeoForge.EVENT_BUS.addListener(RiftStored::onChangedLevel);
    }

    private static @Nullable Stored.Spec specOf(String node) {
        SkillDef def = SkillDefs.get(node);
        return def == null ? null : def.stored();
    }

    /** {@code riftfall:store_hit}: the opening spell landed on one enemy. */
    private static SpellHandlers.ImpactResult storeHit(Holder<Spell> spell, SpellPower.Result power, LivingEntity caster,
                                                       @Nullable Entity target, SpellExecution.ImpactContext context) {
        ResourceLocation ability = spell.unwrapKey().map(ResourceKey::location).orElse(null);
        if (!(caster instanceof ServerPlayer player) || ability == null || !(target instanceof LivingEntity)
                || target == caster) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        SkillDef def = RiftCounters.byAbility(SkillDefs.table(), ability);
        int rank = def == null ? 0 : SkillService.bookOf(player).rank(def.id());
        if (def == null || def.stored() == null || rank < 1) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        if (!TravelCast.beforeArrival(player.getUUID(), def.id(),
                () -> Stored.gain(player.getUUID(), def.id(), rank, def.stored(), player.level().getGameTime())))
            Stored.gain(player.getUUID(), def.id(), rank, def.stored(), player.level().getGameTime());
        return new SpellHandlers.ImpactResult(true, false);
    }

    /** A kept cast of a node that stores: its store opens, aimed or on the caster. */
    private static void landed(LivingEntity caster, SkillNode node, int rank, long now) {
        Stored.Spec spec = specOf(node.id());
        if (spec == null || !(caster instanceof ServerPlayer player)) {
            return;
        }
        Vec3 anchor = null;
        Vec3 thrown = null;
        if (spec.anchor() == Stored.Anchor.AIM) {
            anchor = aim(player, spec.aimRange());
            thrown = flat(anchor.subtract(player.position()), player.getViewVector(1.0F));
            mark(player.serverLevel(), anchor, spec, 12);
        }
        Stored.open(player.getUUID(), node.id(), rank, spec, now, anchor, thrown);
    }

    /** A cast of a node whose RECAST store is live and armed: the store goes off, toward where the caster looks now. */
    private static boolean recast(ServerLevel level, LivingEntity caster, SkillNode node, long now) {
        if (!(caster instanceof ServerPlayer player)) {
            return false;
        }
        Stored.Held held = Stored.takeRecast(player.getUUID(), node.id(), now);
        if (held == null) {
            return false;
        }
        release(level, player, held, now, flat(player.getViewVector(1.0F), held.thrown()), "recast");
        return true;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }

    /** One pass of the clock over every store. Public so a probe can run it after moving time on. */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Stored.Due due : Stored.tick(now)) {
            ServerPlayer player = find(server, due.player());
            if (player == null || !player.isAlive()) {
                continue;
            }
            switch (due.kind()) {
                case RELEASE -> release(player.serverLevel(), player, due.held(), now,
                        due.held().thrown() != null ? due.held().thrown() : flat(player.getViewVector(1.0F), null),
                        "expire");
                case PULSE -> pulse(player.serverLevel(), player, due.held());
                case FIZZLE -> LAST.put(player.getUUID(), new Released(due.held().node(), "fizzle",
                        due.held().units(now), due.held().factor(now), 0, due.held().anchor()));
            }
        }
        if (now % MARK_EVERY == 0) {
            for (Map.Entry<UUID, Stored.Held> e : Stored.all()) {
                Stored.Held held = e.getValue();
                ServerPlayer player = held.anchor() == null ? null : find(server, e.getKey());
                if (player != null) {
                    mark(player.serverLevel(), held.anchor(), held.spec(), 4);
                }
            }
        }
    }

    /** The release: the node's {@code stored/} spell at the opening rank, its power times the store's factor. */
    static void release(ServerLevel level, LivingEntity player, Stored.Held held, long now, Vec3 direction, String kind) {
        SkillDef def = SkillDefs.get(held.node());
        Holder<Spell> spell = def == null ? null : spellOf(level, def, "");
        if (spell == null) {
            return;
        }
        double factor = held.factor(now);
        double units = held.units(now);
        SkillCasts.Riders riders = SkillCasts.Riders.of(def.cast(), held.rank());
        int[] struck = {0};
        if (held.spec().anchor() == Stored.Anchor.CASTER || held.anchor() == null) {
            SkillCasts.atRank(player, def.cast(), held.rank(),
                    () -> RiftSpellEngine.release(level, player, def.cast(), spell, riders, factor));
            struck[0] = -1;
        } else {
            List<LivingEntity> in = inArea(level, held.anchor(), held.spec().radius(), held.spec().angle(), direction);
            struck[0] = in.size();
            SkillCasts.atRank(player, def.cast(), held.rank(), () -> {
                for (LivingEntity target : in) {
                    RiftSpellEngine.strike(level, player, def.cast(), spell, target, riders, factor);
                }
            });
            level.sendParticles(ParticleTypes.EXPLOSION, held.anchor().x, held.anchor().y + 0.5, held.anchor().z,
                    3, 0.5, 0.5, 0.5, 0.0);
        }
        RELEASED.incrementAndGet();
        LAST.put(player.getUUID(), new Released(held.node(), kind, units, factor, struck[0], held.anchor()));
    }

    /** One pulse of an armed aimed store: enemies in it drawn toward the anchor, and the pulse spell on what is in it. */
    static void pulse(ServerLevel level, ServerPlayer player, Stored.Held held) {
        Vec3 anchor = held.anchor();
        if (anchor == null) {
            return;
        }
        double pull = held.spec().pull();
        List<LivingEntity> in = inArea(level, anchor, held.spec().radius(), 0.0, Vec3.ZERO);
        if (pull > 0.0) {
            for (LivingEntity e : in) {
                if (!hostile(e, player) || e.getType().is(BOSSES)) {
                    continue;
                }
                Vec3 to = anchor.subtract(e.position());
                if (to.lengthSqr() < 0.25) {
                    continue;
                }
                Vec3 push = to.normalize().scale(pull);
                e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.1, push.z));
                e.hurtMarked = true;
                PULLED.incrementAndGet();
            }
        }
        SkillDef def = SkillDefs.get(held.node());
        Holder<Spell> spell = def == null ? null : spellOf(level, def, Stored.PULSE_SUFFIX);
        if (spell != null) {
            SkillCasts.atRank(player, def.cast(), held.rank(), () -> {
                for (LivingEntity target : in) {
                    RiftSpellEngine.strike(level, player, def.cast(), spell, target, SkillCasts.Riders.NONE, 1.0);
                }
            });
        }
        mark(level, anchor, held.spec(), 16);
    }

    private static @Nullable Holder<Spell> spellOf(ServerLevel level, SkillDef def, String suffix) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(def.cast().ability().getNamespace(),
                Stored.DIR + def.id() + suffix);
        return SpellRegistry.from(level).getHolder(id).orElse(null);
    }

    /** What an enemy is to a pull: a monster, or a mob hunting the caster. */
    static boolean hostile(LivingEntity e, ServerPlayer caster) {
        return e != caster && (e instanceof Enemy || (e instanceof Mob mob && mob.getTarget() == caster));
    }

    /**
     * Every living thing within {@code radius} of {@code at}; with {@code angle} above 0, only those inside the cone of
     * that full angle around {@code direction} (flat). One at the anchor itself is inside any cone.
     */
    static List<LivingEntity> inArea(ServerLevel level, Vec3 at, double radius, double angle, Vec3 direction) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> e.isAlive() && !e.isSpectator())) {
            Vec3 to = e.position().subtract(at);
            if (to.lengthSqr() > radius * radius) {
                continue;
            }
            if (angle > 0.0 && angle < 360.0 && !inCone(to, direction, angle)) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    /** Whether the flat offset {@code to} lies within half of {@code angle} degrees of the flat {@code direction}. */
    static boolean inCone(Vec3 to, Vec3 direction, double angle) {
        double tl = Math.hypot(to.x, to.z);
        double dl = Math.hypot(direction.x, direction.z);
        if (tl < 1.0e-6 || dl < 1.0e-6) {
            return true;
        }
        double cos = (to.x * direction.x + to.z * direction.z) / (tl * dl);
        return cos >= Math.cos(Math.toRadians(angle / 2.0)) - 1.0e-9;
    }

    /** The flat (ground-plane) unit of {@code v}, or of {@code fallback} when {@code v} has no ground component. */
    static Vec3 flat(Vec3 v, @Nullable Vec3 fallback) {
        Vec3 f = new Vec3(v.x, 0.0, v.z);
        if (f.lengthSqr() < 1.0e-8) {
            return fallback != null ? fallback : new Vec3(0.0, 0.0, 1.0);
        }
        return f.normalize();
    }

    /** Where the caster aims within {@code range}: the first living thing on the line, else the block, else the end. */
    static Vec3 aim(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player.level(), player, eye, stop,
                player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player);
        return entity != null ? entity.getEntity().position() : stop;
    }

    private static void mark(ServerLevel level, Vec3 at, Stored.Spec spec, int count) {
        SimpleParticleType type = spec.pulseTicks() > 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.END_ROD;
        level.sendParticles(type, at.x, at.y + 0.5, at.z, count, spec.radius() / 4.0, 0.3, spec.radius() / 4.0, 0.02);
    }

    private static @Nullable ServerPlayer find(MinecraftServer server, UUID id) {
        ServerPlayer listed = server.getPlayerList().getPlayer(id);
        if (listed != null) {
            return listed;
        }
        // A probe's fake players stand in a level's list only.
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer p : level.players()) {
                if (p.getUUID().equals(id)) {
                    return p;
                }
            }
        }
        return null;
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Stored.drop(event.getEntity().getUUID());
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Stored.drop(player.getUUID());
        }
    }

    private static void onChangedLevel(PlayerEvent.PlayerChangedDimensionEvent event) {
        Stored.drop(event.getEntity().getUUID());
    }

    /**
     * A probe's release of {@code node} from {@code caster} with {@code units} stored, now, as its recast would land it
     * (an AIM store anchored at {@code anchor}, toward where the caster looks). A mob opens no store of its own, so a
     * batch probe whose caster is a zombie lands the second half this way. False when the node stores nothing.
     */
    public static boolean releaseNow(ServerLevel level, LivingEntity caster, String node, int rank, double units,
                                     @Nullable Vec3 anchor) {
        Stored.Spec spec = specOf(node);
        if (spec == null) {
            return false;
        }
        long now = level.getGameTime();
        Vec3 look = flat(caster.getViewVector(1.0F), null);
        release(level, caster, Stored.loose(node, rank, spec, now, units, anchor, look), now, look, "probe");
        return true;
    }

    /** The player's last release (or fizzle), or null. Probe read. */
    public static @Nullable Released last(UUID player) {
        return LAST.get(player);
    }

    public static void forget(UUID player) {
        LAST.remove(player);
        Stored.drop(player);
    }

    public static long released() {
        return RELEASED.get();
    }

    public static long pulled() {
        return PULLED.get();
    }
}
