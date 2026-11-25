package com.cyberday1.riftfall.boss;

import com.cyberday1.riftfall.RiftfallConfig;
import com.cyberday1.riftfall.compat.LedgerBosses;
import com.cyberday1.riftfall.init.RiftfallAttributes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Where threat comes from, and where {@link BossScaling#damageTakenMultiplier} &mdash; the
 * effective-health half of the tier curve &mdash; is applied. Both from outside the boss, through
 * NeoForge's own events, which is what lets this take a mob from a mod it has never heard of.
 *
 * <p>
 * The multiplier goes on before the threat is credited, so a player is paid for the damage the boss
 * took rather than the damage they would have done at tier 1. Threat does not pick targets
 * ({@link TargetRotation} does); it starts the fight and colours the {@link ThreatRing}s.
 */
public final class ThreatEvents {

    /**
     * Threat earned per point of damage a boss actually loses. The unit everything else is expressed
     * in: leave it alone and tune the others against it. Raising it only rescales the table, since
     * every reading downstream is a ratio.
     */
    static final double THREAT_PER_DAMAGE = 1.0;

    /**
     * Threat earned per point of health restored. Under {@link #THREAT_PER_DAMAGE} on purpose: healing
     * is larger in raw points, and a support player belongs on the table rather than at the top of it.
     */
    static final double THREAT_PER_HEALING = 0.35;

    private ThreatEvents() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(ThreatEvents::onBossHurt);
        NeoForge.EVENT_BUS.addListener(ThreatEvents::onHealed);
        NeoForge.EVENT_BUS.addListener(ThreatEvents::onEffectApplied);
        NeoForge.EVENT_BUS.addListener(ThreatEvents::onTargetChange);
        NeoForge.EVENT_BUS.addListener(ThreatEvents::onBossSwing);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ThreatEvents::onLedgerIncoming);
    }

    /**
     * A boss's hit reaching a player ends its swing, and the next target is drawn. Incoming rather
     * than {@code LivingDamageEvent}, so a shielded or fully blocked swing still counts as a swing.
     * {@code getEntity} is the owner, so a boss's projectile counts as the boss.
     */
    private static void onBossSwing(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer
                && event.getSource().getEntity() instanceof LivingEntity attacker) {
            RiftBosses.Tracked boss = RiftBosses.tracked(attacker);
            if (boss != null) {
                boss.rotation().swung();
            }
        }
    }

    /**
     * Refuses a rift boss any target its {@link ThreatGoal} did not choose. Emptying {@code targetSelector}
     * misses a boss that calls {@code setTarget} from outside a targeting goal, and two such shapes
     * exist among the mods this was built for: one re-picks from its own attack logic, one has a ranged
     * goal in {@code goalSelector} whose {@code stop()} clears the target outright.
     *
     * <p>
     * Every target write in the game goes through {@code Mob#setTarget}, which fires this event and
     * then assigns whatever the event is left holding, so a rewrite here gates every route in.
     *
     * <p>
     * Rewritten, not cancelled: cancelling leaves the previous target in place, and the case that
     * matters is the boss dropping a target we still want it to hold. Writing null on an empty table is
     * deliberate too -- a boss nobody has attacked waits to be pulled rather than turning on whoever
     * wandered nearest.
     */
    private static void onTargetChange(LivingChangeTargetEvent event) {
        RiftBosses.Tracked boss = RiftBosses.tracked(event.getEntity());
        if (boss == null) {
            return;
        }
        ServerPlayer held = boss.goal().held();
        if (event.getNewAboutToBeSetTarget() != held) {
            event.setNewAboutToBeSetTarget(held);
        }
    }

    /**
     * Scales incoming damage to the boss's tier, then credits whoever caused it.
     *
     * <p>
     * ⚠ {@code LivingDamageEvent.Pre} is the only one of the three damage events that will do. Its
     * sibling {@code LivingIncomingDamageEvent} fires before mitigation, so it reports what the attacker
     * swung rather than what the boss lost; {@code .Post} carries the final figure but is immutable, so
     * the multiplier cannot be applied there. Here the same variable is read twice, which is what makes
     * the threat credited and the damage dealt provably one number.
     *
     * <p>
     * The register is the filter, one map lookup, so an unengaged server pays one hash per hit anywhere.
     * No dimension test is needed: a mob is only in the register if {@link RiftBosses#take} put it there.
     */
    private static void onBossHurt(LivingDamageEvent.Pre event) {
        RiftBosses.Tracked boss = RiftBosses.tracked(event.getEntity());
        if (boss == null || !(event.getEntity().level() instanceof ServerLevel)) {
            return;
        }
        // A ledger boss was scaled on the way in; scaling again would only skew the threat credit.
        float taken = LedgerBosses.isLedger(event.getEntity()) ? event.getNewDamage()
                : event.getNewDamage() * BossScaling.damageTakenMultiplier(boss.tier(), boss.headcount());
        taken *= BeginnerBosses.arrowMultiplier((net.minecraft.world.entity.Mob)event.getEntity(), boss.tier(), event.getSource());
        taken *= BeginnerBosses.classMultiplier((net.minecraft.world.entity.Mob)event.getEntity(), boss.tier(), event.getSource());
        event.setNewDamage(taken);

        ServerPlayer attacker = credited(event.getSource().getEntity());
        if (attacker != null) {
            boss.threat().add(attacker.getUUID(), taken * THREAT_PER_DAMAGE * generation(attacker));
        }
    }

    /**
     * The multiplier for a boss whose health is a ledger {@code setHealth} cannot move, applied to
     * the incoming amount before the boss's own NORMAL-priority listener books it.
     */
    private static void onLedgerIncoming(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        RiftBosses.Tracked boss = RiftBosses.tracked(entity);
        if (boss == null || !LedgerBosses.isLedger(entity) || !(entity.level() instanceof ServerLevel)
                || event.getAmount() <= 0.0F) {
            return;
        }
        float multiplier = BossScaling.damageTakenMultiplier(boss.tier(), boss.headcount());
        event.setAmount(LedgerBosses.scaledIncoming(entity, event.getSource(), event.getAmount(), multiplier));
    }

    /**
     * Puts healing on the table, credited to every boss in the level because healing names no target.
     *
     * <p>
     * ⚠ Credited to the player <em>healed</em>, not the healer. {@code LivingHealEvent} carries the
     * entity and the amount and nothing else, and no event in NeoForge 21.1 carries a healer, so one
     * player healing another is the case this cannot see. Getting it needs
     * {@link RiftBosses#creditLevel} called from a healing effect of our own, where the healer is known
     * rather than inferred.
     */
    private static void onHealed(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || event.getAmount() <= 0.0F) {
            return;
        }
        RiftBosses.creditLevel(level, player.getUUID(), event.getAmount() * THREAT_PER_HEALING * generation(player));
    }

    /**
     * Puts a cast skill effect on the table, credited to whoever applied it.
     *
     * <p>
     * The one leg of the three whose attribution is not a guess. {@code MobEffectEvent.Added} carries
     * the applying entity, and the pack's whole class layer reaches it through a single call site in
     * Spell Engine that passes the caster -- so 39% of the pack's spells arrive here already named.
     * An effect with no source, which is most of them by count, is a potion or a beacon or a mob's
     * own aura and earns nobody anything.
     *
     * <p>
     * ⛔ Paid on the increment rather than per event. This fires before the merge and fires again on
     * a re-application that changes nothing, so a ticking aura or a spell cloud would otherwise pay
     * its caster twenty times a second for one effect.
     *
     * <p>
     * Credited to every boss in the level, on {@code onHealed}'s reasoning: a debuff names its target
     * but a buff or a self-cast names nothing, and telling those apart would need to know what each
     * of the pack's effects is for.
     */
    private static void onEffectApplied(MobEffectEvent.Added event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer caster = credited(event.getEffectSource());
        if (caster == null) {
            return;
        }
        double worth = SkillThreat.increment(magnitudeOf(event.getEffectInstance()),
                magnitudeOf(event.getOldEffectInstance()));
        if (worth > 0.0) {
            RiftBosses.creditLevel(level, caster.getUUID(),
                    worth * SkillThreat.weight(RiftfallConfig.SKILL_THREAT_RATIO.get()) * generation(caster));
        }
    }

    /** 1 + the player's {@code riftfall:threat_generation}: every credit on the table is this much larger. */
    static double generation(LivingEntity player) {
        AttributeInstance instance = player.getAttribute(RiftfallAttributes.THREAT_GENERATION);
        return multiplier(instance == null ? 0.0 : instance.getValue());
    }

    static double multiplier(double generation) {
        return 1.0 + Math.max(0.0, generation);
    }

    /** An instance's worth in effect-seconds; nothing held is worth nothing. */
    private static double magnitudeOf(@Nullable MobEffectInstance instance) {
        return instance == null ? 0.0
                : SkillThreat.magnitude(instance.getAmplifier(), instance.getDuration());
    }

    /**
     * The player a hit is paid to. Callers pass {@code DamageSource#getEntity}, which is already the
     * owner-not-projectile distinction: the archer, where {@code getDirectEntity} is the arrow.
     * Anything else -- summons, a fall, two mobs fighting -- earns nobody anything.
     */
    @Nullable
    private static ServerPlayer credited(@Nullable Entity source) {
        return source instanceof ServerPlayer player ? player : null;
    }
}
