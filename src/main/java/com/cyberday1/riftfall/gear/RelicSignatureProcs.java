package com.cyberday1.riftfall.gear;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.RelicSignatures.Signature;
import com.cyberday1.riftfall.skill.DotType;
import com.cyberday1.riftfall.skill.Dots;

/**
 * Batch B: the signatures a hit, a kill, a fall or a low-health moment starts. Every
 * proc is {@code riftfall:relic} damage with no direct entity, so it never crits, never feeds life steal and never
 * starts another relic. Torment ticks here, outside the DoT family.
 */
public final class RelicSignatureProcs {

    /** Berserker Rage: a stack a hit, five at most, gone three seconds after the last; damage in, damage out. */
    public static final int RAGE_MAX = 5;
    public static final int RAGE_WINDOW_TICKS = 60;
    public static final double RAGE_PER = 0.05;
    public static final double RAGE_TAKEN_PER = 0.04;

    /** Ricochet: the chance, its internal cooldown, how far and how many it bounces, and the share each takes. */
    public static final double RICOCHET_CHANCE = 0.15;
    public static final int RICOCHET_ICD_TICKS = 20;
    public static final double RICOCHET_RANGE = 8.0;
    public static final int RICOCHET_TARGETS = 3;
    public static final double RICOCHET_SHARE = 0.25;

    /** Spore Burst: the health line, the reach, the venom and anti-heal length, the cooldown. */
    public static final double SPORE_LINE = 0.35;
    public static final double SPORE_RANGE = 5.0;
    public static final int SPORE_TICKS = 100;
    public static final int SPORE_COOLDOWN_TICKS = 1200;
    /** The venom's total over its five seconds, in the wearer's attack damage (venom passives raise it). */
    public static final double SPORE_VENOM = 1.0;
    public static final double SPORE_ANTI_HEAL = -0.5;

    /** Last Rites: a kill heals this share of max health, twice for the undead. */
    public static final double RITES_HEAL = 0.03;

    /** Soul Stacks: a stack a kill, ten at most, spell damage each; a hit of this share of max health halves them. */
    public static final int SOUL_MAX = 10;
    public static final double SOUL_PER = 0.02;
    public static final double SOUL_BIG_HIT = 0.15;

    /** Torment: three seconds of 1% max health a second, at most the owner's attack damage a second. */
    public static final int TORMENT_TICKS = 60;
    public static final double TORMENT_SHARE = 0.01;

    /** Ground Pound: the fall it needs, its reach, damage per block fallen (of attack damage), the block cap. */
    public static final double POUND_MIN_FALL = 4.0;
    public static final double POUND_RANGE = 3.0;
    public static final double POUND_PER_BLOCK = 0.20;
    public static final double POUND_MAX_BLOCKS = 10.0;
    public static final int POUND_COOLDOWN_TICKS = 160;
    public static final double POUND_KNOCKBACK = 0.8;
    /** Ruled 2026-11-20: the pound deals damage. False is the knockback-only fallback. */
    public static final boolean POUND_DAMAGES = true;

    /** Hits that start Torment: the magic schools (Riftfall's class schools ride these). */
    public static final TagKey<DamageType> MAGIC_SCHOOL =
            TagKey.create(Registries.DAMAGE_TYPE, RiftfallCore.id("magic_school"));

    public static final ResourceLocation ANTI_HEAL_ID = RiftfallCore.id("relic_signature/spore_burst/anti_heal");
    private static final ResourceLocation HEALING_RECEIVED =
            ResourceLocation.fromNamespaceAndPath("apothic_attributes", "healing_received");

    /** Rage stacks, and the game time of the hit that set them. */
    public record Rage(int stacks, long last) {
        public static final Rage NONE = new Rage(0, Long.MIN_VALUE / 2);

        public Rage hit(long now) {
            return new Rage(Math.min(RAGE_MAX, at(now).stacks() + 1), now);
        }

        public Rage at(long now) {
            return now - last > RAGE_WINDOW_TICKS ? NONE : this;
        }
    }

    /** Soul Stacks: a kill adds, a big hit halves (rounding down), a death clears. */
    public static int soulAfterKill(int stacks) {
        return Math.min(SOUL_MAX, stacks + 1);
    }

    public static int soulAfterHit(int stacks, float damage, float maxHealth) {
        return damage >= SOUL_BIG_HIT * maxHealth ? stacks / 2 : stacks;
    }

    /** A cooldown that started at {@code since} is over at {@code now}. */
    public static boolean ready(long since, long now, int ticks) {
        return now - since >= ticks;
    }

    /** Spore Burst's line: this hit took health from at or above it to below it. */
    public static boolean crossed(float before, float after, float maxHealth) {
        float line = (float) (SPORE_LINE * maxHealth);
        return before >= line && after < line && after > 0.0F;
    }

    /** Ground Pound's damage for a fall: per block, capped, of the wearer's attack damage. 0 in the fallback. */
    public static double poundDamage(double attackDamage, double fallen) {
        if (!POUND_DAMAGES) return 0.0D;
        return attackDamage * POUND_PER_BLOCK * Math.min(POUND_MAX_BLOCKS, Math.max(0.0D, fallen));
    }

    /** One Torment tick: 1% of the target's max health, at most the owner's attack damage. */
    public static double tormentTick(double targetMaxHealth, double ownerAttackDamage) {
        return Math.min(targetMaxHealth * TORMENT_SHARE, Math.max(0.0D, ownerAttackDamage));
    }

    private static final class State {
        Rage rage = Rage.NONE;
        int souls;
        long ricochetAt = Long.MIN_VALUE / 2;
        long sporeAt = Long.MIN_VALUE / 2;
        long poundAt = Long.MIN_VALUE / 2;
    }

    /** Who torments, ticks left, and ticks since it began: a refresh extends it and keeps its once-a-second beat. */
    private record Torment(WeakReference<ServerPlayer> owner, int left, int age) {
    }

    private static final Map<UUID, State> STATE = new ConcurrentHashMap<>();
    private static final Map<LivingEntity, Torment> TORMENTED = new WeakHashMap<>();
    private static final Map<LivingEntity, Integer> ANTI_HEALED = new WeakHashMap<>();

    /** The proc roll; the probe pins it. */
    private static volatile DoubleSupplier roll = Math::random;

    private RelicSignatureProcs() {
    }

    public static void register() {
        RelicSignatures.damageTerm(Signature.BERSERKER_RAGE, p -> RAGE_PER * state(p).rage.stacks());
        NeoForge.EVENT_BUS.addListener(RelicSignatureProcs::onHit);
        NeoForge.EVENT_BUS.addListener(RelicSignatureProcs::onDeath);
        NeoForge.EVENT_BUS.addListener(RelicSignatureProcs::onFall);
        NeoForge.EVENT_BUS.addListener(RelicSignatureProcs::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(RelicSignatureProcs::onServerTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                STATE.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            STATE.clear();
            synchronized (TORMENTED) {
                TORMENTED.clear();
            }
            synchronized (ANTI_HEALED) {
                ANTI_HEALED.clear();
            }
        });
    }

    /** Probe only: every roll lands at {@code value} (0 always procs, 1 never), or back to random with null. */
    public static void rollWith(Double value) {
        roll = value == null ? Math::random : () -> value;
    }

    public static int rage(ServerPlayer player) {
        State s = STATE.get(player.getUUID());
        return s == null ? 0 : s.rage.stacks();
    }

    public static int souls(ServerPlayer player) {
        State s = STATE.get(player.getUUID());
        return s == null ? 0 : s.souls;
    }

    /** The game tick Spore Burst is ready again; long past when it never fired. */
    public static long sporeReady(ServerPlayer player) {
        State s = STATE.get(player.getUUID());
        return s == null ? Long.MIN_VALUE : s.sporeAt + SPORE_COOLDOWN_TICKS;
    }

    /** The game tick Ground Pound is ready again; long past when it never fired. */
    public static long poundReady(ServerPlayer player) {
        State s = STATE.get(player.getUUID());
        return s == null ? Long.MIN_VALUE : s.poundAt + POUND_COOLDOWN_TICKS;
    }

    /** Probe only: internal cooldowns and stacks back to none. */
    public static void reset(ServerPlayer player) {
        STATE.remove(player.getUUID());
        applySouls(player, 0);
        RelicSignatures.rebucket(player);
    }

    /** Rage's cost on a hit taken, applied after mitigation in {@code CombatStats}: 1 + 4% a stack. */
    public static float rageTaken(LivingEntity victim, float damage) {
        if (!(victim instanceof ServerPlayer player)) return damage;
        State s = STATE.get(player.getUUID());
        if (s == null || s.rage.stacks() == 0 || !active(player, Signature.BERSERKER_RAGE)) return damage;
        return (float) (damage * (1.0D + RAGE_TAKEN_PER * s.rage.at(player.level().getGameTime()).stacks()));
    }

    private static void onHit(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (victim instanceof ServerPlayer wearer) taken(wearer, event);
        if (event.getNewDamage() <= 0.0F || !RelicSignatures.triggers(event.getSource())) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || player == victim) return;
        RelicSignatures.ActiveSet set = RelicSignatures.active(player);
        if (set.active().isEmpty()) return;
        long now = player.level().getGameTime();
        State s = state(player);
        if (set.isActive(Signature.BERSERKER_RAGE)) {
            s.rage = s.rage.hit(now);
            RelicSignatures.rebucket(player);
        }
        if (set.isActive(Signature.RICOCHET) && ready(s.ricochetAt, now, RICOCHET_ICD_TICKS)
                && roll.getAsDouble() < RICOCHET_CHANCE) {
            s.ricochetAt = now;
            ricochet(player, victim, (float) (event.getNewDamage() * RICOCHET_SHARE));
        }
        if (set.isActive(Signature.TORMENT) && event.getSource().is(MAGIC_SCHOOL) && victim.isAlive()) {
            synchronized (TORMENTED) {
                Torment was = TORMENTED.get(victim);
                TORMENTED.put(victim, new Torment(new WeakReference<>(player), TORMENT_TICKS,
                        was == null ? 0 : was.age()));
            }
        }
        if (set.isActive(Signature.BERSERK)) {
            RelicSignatureCosts.onHitDealt(player, event.getSource(), set, now, roll.getAsDouble());
        }
    }

    /** The wearer took a hit: Spore Burst's line, Soul Stacks' halving. */
    private static void taken(ServerPlayer wearer, LivingDamageEvent.Post event) {
        float damage = event.getNewDamage();
        if (damage <= 0.0F) return;
        RelicSignatures.ActiveSet set = RelicSignatures.active(wearer);
        if (set.active().isEmpty()) return;
        long now = wearer.level().getGameTime();
        State s = state(wearer);
        if (set.isActive(Signature.SOUL_STACKS) && s.souls > 0) {
            int after = soulAfterHit(s.souls, damage, wearer.getMaxHealth());
            if (after != s.souls) {
                s.souls = after;
                applySouls(wearer, after);
            }
        }
        if (set.isActive(Signature.SPORE_BURST) && ready(s.sporeAt, now, SPORE_COOLDOWN_TICKS)
                && crossed(wearer.getHealth() + damage, wearer.getHealth(), wearer.getMaxHealth())) {
            s.sporeAt = now;
            sporeBurst(wearer);
            RelicStackSync.check(wearer);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) return;
        if (dead instanceof ServerPlayer wearer) {
            State s = STATE.get(wearer.getUUID());
            if (s != null && s.souls > 0) {
                s.souls = 0;
                applySouls(wearer, 0);
            }
            return;
        }
        if (!RelicSignatures.triggers(event.getSource())) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        RelicSignatures.ActiveSet set = RelicSignatures.active(player);
        if (set.isActive(Signature.LAST_RITES)) {
            boolean undead = dead.getType().is(EntityTypeTags.UNDEAD);
            player.heal((float) (player.getMaxHealth() * RITES_HEAL * (undead ? 2.0D : 1.0D)));
        }
        if (set.isActive(Signature.SOUL_STACKS)) {
            State s = state(player);
            s.souls = soulAfterKill(s.souls);
            applySouls(player, s.souls);
        }
    }

    /** Sneaking into a fall of four blocks or more: no fall damage, and the landing hits what is close. */
    private static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown()) return;
        if (event.getDistance() < POUND_MIN_FALL || !active(player, Signature.GROUND_POUND)) return;
        long now = player.level().getGameTime();
        State s = state(player);
        if (!ready(s.poundAt, now, POUND_COOLDOWN_TICKS)) return;
        s.poundAt = now;
        RelicStackSync.check(player);
        event.setDamageMultiplier(0.0F);
        double damage = poundDamage(player.getAttributeValue(Attributes.ATTACK_DAMAGE), event.getDistance());
        Vec3 at = player.position();
        for (LivingEntity target : enemies(player, at, POUND_RANGE, null)) {
            if (damage > 0.0D) proc(target, player, (float) damage);
            Vec3 away = target.position().subtract(at);
            target.knockback(POUND_KNOCKBACK, -away.x, -away.z);
        }
    }

    private static void ricochet(ServerPlayer player, LivingEntity from, float amount) {
        if (amount <= 0.0F) return;
        List<LivingEntity> near = enemies(player, from.position(), RICOCHET_RANGE, from);
        near.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        for (LivingEntity target : near.subList(0, Math.min(RICOCHET_TARGETS, near.size()))) {
            proc(target, player, amount);
        }
    }

    private static void sporeBurst(ServerPlayer wearer) {
        double perTick = Dots.perTick(wearer.getAttributeValue(Attributes.ATTACK_DAMAGE), SPORE_VENOM,
                SPORE_TICKS / 20.0D, Dots.bonus(wearer, DotType.VENOM));
        for (LivingEntity target : enemies(wearer, wearer.position(), SPORE_RANGE, null)) {
            Dots.apply(target, wearer, DotType.VENOM, perTick, SPORE_TICKS);
            AttributeInstance heal = instance(target, HEALING_RECEIVED);
            if (heal != null) {
                heal.addOrUpdateTransientModifier(new AttributeModifier(ANTI_HEAL_ID, SPORE_ANTI_HEAL,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                synchronized (ANTI_HEALED) {
                    ANTI_HEALED.put(target, SPORE_TICKS);
                }
            }
        }
    }

    /** A proc's hit: relic damage, leaving the target's hurt timer as it was so the next real hit still lands. */
    static void proc(LivingEntity target, ServerPlayer owner, float amount) {
        if (amount <= 0.0F || !target.isAlive()) return;
        int invulnerable = target.invulnerableTime;
        target.hurt(RelicSignatures.damage(target.level(), owner), amount);
        target.invulnerableTime = invulnerable;
    }

    /** Monsters, or mobs after the wearer, within {@code range} of {@code at}; never the wearer or {@code skip}. */
    static List<LivingEntity> enemies(ServerPlayer player, Vec3 at, double range, LivingEntity skip) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(at, at).inflate(range), e -> e.isAlive() && e != player && e != skip)) {
            if (e.distanceToSqr(at) > range * range) continue;
            if (e instanceof Enemy || (e instanceof Mob mob && mob.getTarget() == player)) out.add(e);
        }
        return out;
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        expire(player, now);
        RelicStackSync.lapse(player, now);
    }

    /** Rage falls off three seconds after the last hit. The player tick, and the probe with a later clock. */
    public static void expire(ServerPlayer player, long now) {
        State s = STATE.get(player.getUUID());
        if (s == null || s.rage.stacks() == 0) return;
        if (s.rage.at(now).stacks() == 0) {
            s.rage = Rage.NONE;
            RelicSignatures.rebucket(player);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        tickAll();
    }

    /** One tick of every Torment and anti-heal. The server tick, and the probe. */
    public static void tickAll() {
        List<Map.Entry<LivingEntity, Torment>> due = new ArrayList<>();
        synchronized (TORMENTED) {
            Iterator<Map.Entry<LivingEntity, Torment>> it = TORMENTED.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<LivingEntity, Torment> e = it.next();
                Torment t = e.getValue();
                Torment next = new Torment(t.owner(), t.left() - 1, t.age() + 1);
                if (next.left() <= 0 || !e.getKey().isAlive()) it.remove();
                else e.setValue(next);
                if (next.age() % 20 == 0) due.add(Map.entry(e.getKey(), t));
            }
        }
        for (Map.Entry<LivingEntity, Torment> e : due) {
            ServerPlayer owner = e.getValue().owner().get();
            LivingEntity target = e.getKey();
            if (owner == null || owner.isRemoved() || !target.isAlive()) continue;
            proc(target, owner, (float) tormentTick(target.getMaxHealth(),
                    owner.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        }
        synchronized (ANTI_HEALED) {
            Iterator<Map.Entry<LivingEntity, Integer>> it = ANTI_HEALED.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<LivingEntity, Integer> e = it.next();
                int left = e.getValue() - 1;
                if (left > 0 && e.getKey().isAlive()) {
                    e.setValue(left);
                    continue;
                }
                AttributeInstance heal = instance(e.getKey(), HEALING_RECEIVED);
                if (heal != null) heal.removeModifier(ANTI_HEAL_ID);
                it.remove();
            }
        }
    }

    public static boolean tormented(LivingEntity target) {
        synchronized (TORMENTED) {
            return TORMENTED.containsKey(target);
        }
    }

    private static void applySouls(ServerPlayer player, int stacks) {
        AttributeInstance spell = instance(player, RiftfallCore.id("spell_damage"));
        ResourceLocation id = RelicSignatureStats.id(Signature.SOUL_STACKS, 0);
        if (spell != null && stacks <= 0) {
            spell.removeModifier(id);
        } else if (spell != null) {
            spell.addOrUpdateTransientModifier(new AttributeModifier(id, SOUL_PER * stacks,
                    AttributeModifier.Operation.ADD_VALUE));
        }
        RelicStackSync.check(player);
    }

    /** A changed relic: stacks of a signature no longer active go, and so does their line. */
    static void refreshed(ServerPlayer player) {
        State s = STATE.get(player.getUUID());
        if (s == null) return;
        RelicSignatures.ActiveSet set = RelicSignatures.active(player);
        if (!set.isActive(Signature.SOUL_STACKS) && s.souls > 0) {
            s.souls = 0;
            applySouls(player, 0);
        }
        if (!set.isActive(Signature.BERSERKER_RAGE)) s.rage = Rage.NONE;
    }

    private static boolean active(ServerPlayer player, Signature signature) {
        return RelicSignatures.active(player).isActive(signature);
    }

    private static State state(ServerPlayer player) {
        return STATE.computeIfAbsent(player.getUUID(), id -> new State());
    }

    static AttributeInstance instance(LivingEntity entity, ResourceLocation attribute) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE
                .getHolder(ResourceKey.create(Registries.ATTRIBUTE, attribute)).orElse(null);
        return holder == null ? null : entity.getAttribute(holder);
    }
}
