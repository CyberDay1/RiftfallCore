package com.cyberday1.riftfall.gear;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import com.cyberday1.riftfall.gear.RelicSignatures.Signature;
import com.cyberday1.riftfall.init.RiftfallEffects;

/**
 * Batch C: the signatures whose cost lives in another system. Last Stand stops food
 * regeneration ({@code MixinFoodDataRegen}); Berserk refuses casts ({@code SkillCasts#eligible}); Unstable Step
 * surcharges movement casts ({@code SkillService}); Frostbound cuts close hits ({@code CombatStats}) and slows
 * nearby mobs; Greed adds a mob to the spawners it trips ({@code RiftMobSpawnerBlockEntity}).
 */
public final class RelicSignatureCosts {

    /** Last Stand: under this share of max health, the bucket term and the resistance line are on. */
    public static final double STAND_LINE = 0.30;
    public static final double STAND_TERM = 0.25;
    public static final double STAND_RESISTANCE = 0.20;

    /** Berserk: the chance on a melee hit, its internal cooldown, the effect's length and its attack speed. */
    public static final double BERSERK_CHANCE = 0.10;
    public static final int BERSERK_ICD_TICKS = 300;
    public static final int BERSERK_TICKS = 120;
    public static final double BERSERK_SPEED = 0.30;
    public static final String BERSERK_REFUSAL = "message.riftfall.berserk_no_cast";

    /** Unstable Step: mana on top of every movement-family cast. */
    public static final double STEP_SURCHARGE = 10.0;

    /** Frostbound: how close an attacker must be, what it loses, and the slow it is kept under. */
    public static final double FROST_RANGE = 4.0;
    public static final double FROST_TURNED_AWAY = 0.12;
    public static final int FROST_SLOW_TICKS = 30;

    /** Greed: the extra mob a spawner adds for its nearest wearer. */
    public static final int GREED_EXTRA = 1;

    private static final Map<UUID, Long> BERSERK_AT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> REFUSED_AT = new ConcurrentHashMap<>();
    private static final AtomicLong REFUSALS = new AtomicLong();

    private RelicSignatureCosts() {
    }

    public static void register() {
        RelicSignatures.damageTerm(Signature.LAST_STAND, p -> low(p) ? STAND_TERM : 0.0D);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            BERSERK_AT.remove(event.getEntity().getUUID());
            REFUSED_AT.remove(event.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            BERSERK_AT.clear();
            REFUSED_AT.clear();
        });
    }

    /** Last Stand's line: strictly under 30% of max health. */
    public static boolean low(float health, float maxHealth) {
        return health > 0.0F && health < STAND_LINE * maxHealth;
    }

    static boolean low(ServerPlayer player) {
        return low(player.getHealth(), player.getMaxHealth());
    }

    /** Last Stand's cost, read by {@code MixinFoodDataRegen}: no food regeneration while it is active. Server. */
    public static boolean blocksRegen(Player player) {
        return player instanceof ServerPlayer server && RelicSignatures.active(server).isActive(Signature.LAST_STAND);
    }

    /** Berserk's cost, read by {@code SkillCasts#eligible}: a refusal, said on the action bar once a second. */
    public static boolean refusesCast(LivingEntity caster) {
        if (!caster.hasEffect(RiftfallEffects.BERSERK)) return false;
        REFUSALS.incrementAndGet();
        if (caster instanceof ServerPlayer player) {
            long now = player.level().getGameTime();
            Long last = REFUSED_AT.get(player.getUUID());
            if (last == null || now - last >= 20) {
                REFUSED_AT.put(player.getUUID(), now);
                player.displayClientMessage(Component.translatable(BERSERK_REFUSAL), true);
            }
        }
        return true;
    }

    /** Casts Berserk refused. Probe read. */
    public static long refusals() {
        return REFUSALS.get();
    }

    /** Unstable Step's surcharge on a cast of this node family, for this caster. */
    public static double surcharge(LivingEntity caster, boolean movementFamily) {
        return movementFamily && caster instanceof ServerPlayer player
                && RelicSignatures.active(player).isActive(Signature.UNSTABLE_STEP) ? STEP_SURCHARGE : 0.0D;
    }

    /** Frostbound on the victim: the share of a close attacker's hit it turns away, or 0. */
    public static double frostTurnedAway(LivingEntity victim, DamageSource source) {
        if (!(victim instanceof ServerPlayer player) || !(source.getEntity() instanceof LivingEntity attacker)) return 0.0D;
        if (attacker == victim || source.is(DamageTypeTags.BYPASSES_EFFECTS)) return 0.0D;
        if (!RelicSignatures.active(player).isActive(Signature.FROSTBOUND)) return 0.0D;
        return attacker.distanceToSqr(victim) <= FROST_RANGE * FROST_RANGE ? FROST_TURNED_AWAY : 0.0D;
    }

    /** Once a second: Frostbound keeps the enemies near its wearer slowed (Slowness I, 15%). */
    static void second(ServerPlayer player, RelicSignatures.ActiveSet set) {
        if (!set.isActive(Signature.FROSTBOUND)) return;
        for (LivingEntity e : RelicSignatureProcs.enemies(player, player.position(), FROST_RANGE, null)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FROST_SLOW_TICKS, 0), player);
        }
    }

    /** A melee hit by a Berserk wearer: 10% for Berserk, once in fifteen seconds. */
    static void onHitDealt(ServerPlayer player, DamageSource source, RelicSignatures.ActiveSet set, long now,
                           double roll) {
        if (!set.isActive(Signature.BERSERK) || !melee(player, source)) return;
        Long last = BERSERK_AT.get(player.getUUID());
        if (last != null && !RelicSignatureProcs.ready(last, now, BERSERK_ICD_TICKS)) return;
        if (roll >= BERSERK_CHANCE) return;
        BERSERK_AT.put(player.getUUID(), now);
        player.addEffect(new MobEffectInstance(RiftfallEffects.BERSERK, BERSERK_TICKS, 0, false, true, true));
    }

    /** A melee hit: the wearer's own swing, not an arrow, a spell or a proc. */
    static boolean melee(ServerPlayer player, DamageSource source) {
        return source.getDirectEntity() == player && source.is(DamageTypes.PLAYER_ATTACK);
    }

    /** Probe only: Berserk's internal cooldown back to none. */
    public static void reset(ServerPlayer player) {
        BERSERK_AT.remove(player.getUUID());
        REFUSED_AT.remove(player.getUUID());
    }

    /** Greed's cost: a rolled batch, one larger when the spawner's nearest player wears an active Greed. */
    public static int batch(int rolled, @Nullable Player nearest) {
        return nearest instanceof ServerPlayer player
                && RelicSignatures.active(player).isActive(Signature.GREED) ? rolled + GREED_EXTRA : rolled;
    }

    /** The player a spawner fires for: the nearest one in its trigger range, if any. */
    public static @Nullable Player nearest(Level level, Vec3 at, double range) {
        return level.getNearestPlayer(at.x, at.y, at.z, range, false);
    }
}
