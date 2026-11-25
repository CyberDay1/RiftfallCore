package com.cyberday1.riftfall.skill;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.spell_engine.api.entity.SpellEntityPredicates;
import net.spell_engine.internals.SpellTriggers;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.utils.PatternMatching;
import net.spell_engine.utils.WorldScheduler;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Spell Engine's half of the passive-spell grant (H4). A container source hands the engine every spell
 * passive the player owns, and the engine's own triggers fire them. A fired passive is delivered at
 * the node's power and on Riftfall's internal cooldown, never on mana. The offer filter still strips
 * any passive an item or another mod carries that Riftfall did not grant.
 */
public final class RiftPassiveSpells {

    /** The container source's name, and so its key in the engine's per-player cache. */
    public static final String SOURCE = "riftfall_passives";

    private record Pending(long gameTime, double fraction) {
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final AtomicLong PROCS = new AtomicLong();
    private static final AtomicLong REFUSED = new AtomicLong();
    private static final AtomicLong COOLING = new AtomicLong();
    private static final AtomicLong GUARDS_ARMED = new AtomicLong();
    private static final AtomicLong GUARDED = new AtomicLong();
    private static final AtomicLong OUTSIDE_WINDOW = new AtomicLong();
    private static final AtomicLong HASTENED = new AtomicLong();
    private static final AtomicLong HASTE_TICKS = new AtomicLong();
    private static final AtomicLong MANA_RESTORES = new AtomicLong();
    private static final AtomicLong FOREIGN_HITS = new AtomicLong();
    private static final AtomicLong FOREIGN_FIRED = new AtomicLong();
    // The reaction triggers.
    private static final AtomicLong COUNTING = new AtomicLong();
    private static final AtomicLong REACTED = new AtomicLong();
    private static final AtomicLong DODGES = new AtomicLong();
    private static final AtomicLong SURGES = new AtomicLong();
    private static final AtomicLong SURGES_ENDED = new AtomicLong();
    private static final AtomicLong CLEANSES = new AtomicLong();

    /** The {@code riftfall:from_behind} entity predicate: the tested entity (the target) has the other at its back. */
    public static final ResourceLocation FROM_BEHIND = ResourceLocation.fromNamespaceAndPath("riftfall", "from_behind");

    private record Surging(ResourceLocation modifier, List<Holder<Attribute>> attributes, long endsAt) {
    }

    /** Each player's running surges, by node: the modifiers to take off when they end. */
    private static final Map<UUID, Map<String, Surging>> SURGING = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

    private RiftPassiveSpells() {
    }

    /** Inside {@code RiftSpellGate#register}'s {@code enqueueWork}: the source list is a bare ArrayList. */
    static void register() {
        SpellContainerSource.addSource(new SpellContainerSource.Entry(SOURCE, RiftPassiveSpells::containers,
                RiftPassiveSpells::state));
        SpellHandlers.registerCustomImpact(PassiveSpells.GUARD, RiftPassiveSpells::guard);
        SpellHandlers.registerCustomImpact(PassiveSpells.HASTEN, RiftPassiveSpells::hasten);
        SpellHandlers.registerCustomImpact(PassiveSpells.RESTORE_MANA, RiftPassiveSpells::restoreMana);
        NeoForge.EVENT_BUS.addListener(RiftPassiveSpells::onDamagePre);
        SkillHits.listen(RiftPassiveSpells::onForeignHit);
        // The reaction triggers.
        SpellEntityPredicates.register(FROM_BEHIND,
                args -> args.other() != null && Reactions.behind(args.entity(), args.other(), args.param()));
        SpellEntityPredicates.register(ResourceLocation.fromNamespaceAndPath("riftfall", "is_caster"),
                args -> args.entity() == args.other());
        SpellHandlers.registerCustomImpact(PassiveSpells.SURGE, RiftPassiveSpells::surge);
        Reactions.listenCasts(RiftPassiveSpells::onCast);
        Reactions.listenDodges(RiftPassiveSpells::onDodge);
        NeoForge.EVENT_BUS.addListener(RiftPassiveSpells::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(RiftPassiveSpells::onLogout);
        RiftCounters.register();
        // Reflect windows, stored values and the cleanse event.
        Reactions.listenCleanses(RiftPassiveSpells::onCleanse);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, RiftPassiveSpells::onEffectRemoved);
        RiftReflect.register();
        RiftStored.register();
    }

    /**
     * A harmful effect removed from a living entity (a cleanse impact, milk, a totem, a command), never its natural
     * expiry (that is {@code MobEffectEvent.Expired}). Only a removal that goes through: a cancelled one is no cleanse,
     * and one of an effect the entity does not have is nothing.
     */
    private static void onEffectRemoved(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Remove event) {
        if (event.getEffectInstance() == null || event.getEntity().level().isClientSide()
                || event.getEffect().value().getCategory() != net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
            return;
        }
        CLEANSES.incrementAndGet();
        Reactions.cleansed(event.getEntity());
    }

    /**
     * A cleanse for {@code player}: the granted passives whose reaction is CLEANSE fire at it, at the end of the tick,
     * since the removal may be one of several inside {@code removeAllEffects}' own loop over the effects.
     */
    private static void onCleanse(ServerPlayer player) {
        ((WorldScheduler) player.level()).schedule(0, () -> fireCleanse(player));
    }

    private static void fireCleanse(ServerPlayer player) {
        for (Holder<Spell> entry : SpellContainerSource.passiveSpellsOf(player)) {
            PassiveSpells.Grant grant = grantOf(player, entry);
            Reactions.Reaction reaction = grant == null ? null : grant.def().reaction();
            if (reaction == null || reaction.on() != Reactions.On.CLEANSE) {
                continue;
            }
            REACTED.incrementAndGet();
            SpellExecution.performSpell(player.level(), player, entry, SpellTarget.SearchResult.of(List.<Entity>of(player)),
                    SpellCast.Action.TRIGGER, 1.0F);
        }
    }

    /**
     * A castable landed: the granted passives whose reaction answers it (MOVE for a movement-family cast, CAST for
     * any) fire at the caster, through the same delivery, counter and timer as an engine trigger.
     */
    private static void onCast(LivingEntity caster, SkillNode node) {
        if (!(caster instanceof ServerPlayer player)) {
            return;
        }
        for (Holder<Spell> entry : SpellContainerSource.passiveSpellsOf(player)) {
            PassiveSpells.Grant grant = grantOf(player, entry);
            Reactions.Reaction reaction = grant == null ? null : grant.def().reaction();
            if (reaction == null || reaction.on() == null || !Reactions.answers(reaction, node)) {
                continue;
            }
            REACTED.incrementAndGet();
            SpellExecution.performSpell(player.level(), player, entry, SpellTarget.SearchResult.of(List.<Entity>of(player)),
                    SpellCast.Action.TRIGGER, 1.0F);
        }
    }

    /**
     * A dodge from Iron's or Apothic's roll: Spell Engine's own EVASION trigger, which until now only its own
     * {@code evasion_chance} fired. A hit with no attacker (a fall, a fire) is no dodge to react to.
     */
    private static void onDodge(LivingEntity dodger, DamageSource source, float amount) {
        if (source.getEntity() == null) {
            return;
        }
        DODGES.incrementAndGet();
        SpellTriggers.onEvasion(dodger, amount, source);
    }

    /**
     * A skill hit no engine trigger reports (an Iron's cast). It fires the granted passives' skill-hit
     * triggers ({@code SPELL_IMPACT_SPECIFIC}, stage POST) as the engine would for a spell's damage impact:
     * at the end of the tick, on the target as it stands after the hit, and through the same delivery.
     */
    private static void onForeignHit(ServerPlayer player, LivingEntity target) {
        FOREIGN_HITS.incrementAndGet();
        ((WorldScheduler) player.level()).schedule(0, () -> fireForeign(player, target));
    }

    private static void fireForeign(ServerPlayer player, LivingEntity target) {
        SpellCaster.Player caster = (SpellCaster.Player) player;
        for (Holder<Spell> entry : SpellContainerSource.passiveSpellsOf(player)) {
            Spell spell = entry.value();
            if (spell.passive == null || caster.getCooldownManager().isCoolingDown(entry)) {
                continue;
            }
            for (Spell.Trigger trigger : spell.passive.triggers) {
                if (foreignMatches(trigger, player, target)) {
                    SpellTarget.SearchResult targets = spell.target.type == Spell.Target.Type.FROM_TRIGGER
                            ? SpellTarget.SearchResult.of(List.<Entity>of(target))
                            : SpellTarget.findTargets(player, entry, SpellTarget.SearchResult.empty(), true);
                    FOREIGN_FIRED.incrementAndGet();
                    SpellExecution.performSpell(player.level(), player, entry, targets, SpellCast.Action.TRIGGER, 1.0F);
                    break;
                }
            }
        }
    }

    /**
     * The engine's {@code SPELL_IMPACT_SPECIFIC} rules for a damage hit by an active skill that is not a
     * Spell Engine spell. A condition only a Spell Engine spell can meet (a school, an id, an authored
     * cooldown, a critical) or that names an item refuses, rather than guessing.
     */
    static boolean foreignMatches(Spell.Trigger trigger, ServerPlayer player, LivingEntity target) {
        if (trigger.type != Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC || trigger.stage != Spell.Trigger.Stage.POST
                || trigger.equipment_condition != null || trigger.weapon_condition != null) {
            return false;
        }
        Spell.Trigger.SpellCondition cast = trigger.spell;
        if (cast != null && (cast.school != null || cast.id != null || cast.archetype != null || cast.cooldown_min > 0.0F
                || (cast.type != null && cast.type != Spell.Type.ACTIVE))) {
            return false;
        }
        Spell.Trigger.ImpactCondition impact = trigger.impact;
        if (impact != null && ((impact.impact_type != null
                && !PatternMatching.regexMatches(impact.impact_type.toLowerCase(Locale.ROOT), "damage"))
                || Boolean.TRUE.equals(impact.critical))) {
            return false;
        }
        if (trigger.damage != null) {
            return false;
        }
        if (trigger.caster_conditions != null) {
            for (Spell.TargetCondition condition : trigger.caster_conditions) {
                if (!SpellTarget.evaluate(player, target, condition)) {
                    return false;
                }
            }
        }
        if (trigger.target_conditions != null) {
            for (Spell.TargetCondition condition : trigger.target_conditions) {
                if (!SpellTarget.evaluate(target, player, condition)) {
                    return false;
                }
            }
        }
        return trigger.chance >= 1.0F || RANDOM.nextFloat() <= trigger.chance;
    }

    /** The granted spell passives, server side only: the engine fires triggers there and nowhere else. */
    private static List<SpellContainerSource.SourcedContainer> containers(Player player, String name) {
        List<String> ids = new ArrayList<>();
        for (ResourceLocation spell : granted(player).keySet()) {
            ids.add(spell.toString());
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        ids.sort(null);
        return List.of(new SpellContainerSource.SourcedContainer(name, null,
                new SpellContainer(SpellContainer.ContentType.NONE, "", "", "", 0, ids, 0)));
    }

    /** The engine's dirty check, every player tick: a buy, a sale or a reload rebuilds the source. */
    private static Object state(Player player) {
        return List.copyOf(granted(player).keySet());
    }

    private static Map<ResourceLocation, PassiveSpells.Grant> granted(Player player) {
        if (!(player instanceof ServerPlayer server)) {
            return Map.of();
        }
        try {
            return PassiveSpells.of(SkillService.bookOf(server), SkillDefs.table()::passive);
        } catch (RuntimeException failure) {
            return Map.of();
        }
    }

    static boolean isPassive(Holder<Spell> spell) {
        return spell.value().type == Spell.Type.PASSIVE;
    }

    /**
     * A fired passive's delivery: refused unless Riftfall granted it and its timer is up, then delivered
     * with the node's power as a multiplier. A node with a hit window refuses a target above it, before the
     * timer. Every let-through starts the timer; nothing costs mana.
     */
    static boolean deliver(ServerPlayer player, Holder<Spell> spell, SpellExecution.ImpactContext context,
                           List<Entity> targets, Function<SpellExecution.ImpactContext, Boolean> deliver) {
        ResourceLocation id = spell.unwrapKey().map(ResourceKey::location).orElse(null);
        PassiveSpells.Grant grant = id == null ? null : granted(player).get(id);
        if (grant == null) {
            REFUSED.incrementAndGet();
            return false;
        }
        // The node's hit window, read before the timer so a hit outside it costs no proc.
        for (Entity target : targets) {
            if (target instanceof LivingEntity living && !grant.def().inWindow(living.getHealth() / living.getMaxHealth())) {
                OUTSIDE_WINDOW.incrementAndGet();
                return false;
            }
        }
        UUID who = player.getUUID();
        String timer = grant.def().id();
        long now = player.level().getGameTime();
        if (!SkillCooldowns.ready(who, timer, now)) {
            COOLING.incrementAndGet();
            return false;
        }
        // A hit or cast counter: only an event the timer would let through counts, and only the Nth fires.
        Reactions.Reaction reaction = grant.def().reaction();
        if (reaction != null && !Reactions.count(who, timer, reaction, now)) {
            COUNTING.incrementAndGet();
            return false;
        }
        // The timer starts on the let-through, not on the engine's answer: an AREA delivery always answers false.
        PROCS.incrementAndGet();
        SkillCooldowns.start(who, timer, now, grant.def().cooldownTicks(spell.value().cost.cooldown.duration));
        Spell.Modifier power = powerModifier(grant.power());
        boolean[] delivered = {false};
        // Nothing a passive delivers fires a reaction (its surge, its bonus hit, its hasten).
        Reactions.deliver(() -> delivered[0] = Boolean.TRUE.equals(
                deliver.apply(power == null ? context : context.chargeModifier(power))));
        return delivered[0];
    }

    /** The engine adds a modifier's multiplier to 1, so k is written as k - 1. Null for k = 1. */
    static @Nullable Spell.Modifier powerModifier(double k) {
        if (k == 1.0) {
            return null;
        }
        Spell.Modifier modifier = new Spell.Modifier();
        modifier.power_modifier = new Spell.Impact.Modifier();
        modifier.power_modifier.power_multiplier = (float) (k - 1.0);
        return modifier;
    }

    /** The {@code riftfall:guard} impact: arms a cut of this tick's hit on the caster, sized by the node. */
    private static SpellHandlers.ImpactResult guard(Holder<Spell> spell, SpellPower.Result power, LivingEntity caster,
                                                    @Nullable Entity target, SpellExecution.ImpactContext context) {
        if (!(caster instanceof ServerPlayer player)) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        ResourceLocation id = spell.unwrapKey().map(ResourceKey::location).orElse(null);
        PassiveSpells.Grant grant = id == null ? null : granted(player).get(id);
        if (grant == null) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        double fraction = Math.max(0.0, Math.min(1.0, grant.power()));
        PENDING.put(player.getUUID(), new Pending(player.level().getGameTime(), fraction));
        GUARDS_ARMED.incrementAndGet();
        return new SpellHandlers.ImpactResult(true, false);
    }

    /**
     * The {@code riftfall:hasten} impact: the node's power, in seconds, off every running cooldown of the
     * caster's castable nodes. A passive's proc timer is not a skill cooldown and is left alone, so the
     * passive's own internal cooldown still caps how often this lands.
     */
    private static SpellHandlers.ImpactResult hasten(Holder<Spell> spell, SpellPower.Result power, LivingEntity caster,
                                                     @Nullable Entity target, SpellExecution.ImpactContext context) {
        PassiveSpells.Grant grant = grantOf(caster, spell);
        if (grant == null) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        ServerPlayer player = (ServerPlayer) caster;
        int ticks = PassiveSpells.ticksOf(grant.power());
        SkillTable table = SkillDefs.table();
        Set<String> moved = new HashSet<>(SkillCooldowns.shorten(player.getUUID(), player.level().getGameTime(), ticks,
                node -> PassiveSpells.hastens(table, node)));
        HASTENED.incrementAndGet();
        HASTE_TICKS.addAndGet((long) ticks * moved.size());
        SkillBook book = SkillService.bookOf(player);
        for (int slot = 0; slot < SkillBook.SLOTS; slot++) {
            if (moved.contains(book.slot(slot))) {
                SkillSync.cooldown(player, slot);
            }
        }
        return new SpellHandlers.ImpactResult(true, false);
    }

    /** The {@code riftfall:restore_mana} impact: the node's power, as a fraction of max mana, back to the caster. */
    private static SpellHandlers.ImpactResult restoreMana(Holder<Spell> spell, SpellPower.Result power, LivingEntity caster,
                                                          @Nullable Entity target, SpellExecution.ImpactContext context) {
        PassiveSpells.Grant grant = grantOf(caster, spell);
        if (grant == null) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        ServerPlayer player = (ServerPlayer) caster;
        double max = RiftMana.max(player);
        RiftMana.refund(player.getUUID(), grant.power() * max, max);
        SkillSync.mana(player);
        MANA_RESTORES.incrementAndGet();
        return new SpellHandlers.ImpactResult(true, false);
    }

    private static PassiveSpells.@Nullable Grant grantOf(LivingEntity caster, Holder<Spell> spell) {
        if (!(caster instanceof ServerPlayer player)) {
            return null;
        }
        ResourceLocation id = spell.unwrapKey().map(ResourceKey::location).orElse(null);
        return id == null ? null : granted(player).get(id);
    }

    /**
     * The {@code riftfall:surge} impact: the node's surge terms, each raised by the node's power for its seconds, as
     * transient modifiers on the caster. A fresh proc replaces a running one (refreshed, never stacked).
     */
    private static SpellHandlers.ImpactResult surge(Holder<Spell> spell, SpellPower.Result power, LivingEntity caster,
                                                    @Nullable Entity target, SpellExecution.ImpactContext context) {
        PassiveSpells.Grant grant = grantOf(caster, spell);
        Reactions.Surge surge = grant == null ? null : grant.def().surge();
        if (surge == null) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        ServerPlayer player = (ServerPlayer) caster;
        String node = grant.def().id();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("riftfall", "surge/" + node);
        endSurge(player, node);
        List<Holder<Attribute>> raised = new ArrayList<>();
        for (Reactions.Term term : surge.terms()) {
            Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(term.attribute()).orElse(null);
            AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            instance.addTransientModifier(new AttributeModifier(id, grant.power(), term.operation()));
            raised.add(attribute);
        }
        if (raised.isEmpty()) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        SURGING.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
                .put(node, new Surging(id, raised, player.level().getGameTime() + surge.ticks()));
        SURGES.incrementAndGet();
        return new SpellHandlers.ImpactResult(true, false);
    }

    private static void endSurge(ServerPlayer player, String node) {
        Map<String, Surging> mine = SURGING.get(player.getUUID());
        Surging running = mine == null ? null : mine.remove(node);
        if (running == null) {
            return;
        }
        for (Holder<Attribute> attribute : running.attributes()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(running.modifier());
            }
        }
        SURGES_ENDED.incrementAndGet();
    }

    /** Ends the player's surges whose time is up. */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            endSurges(player, false);
        }
    }

    /** Ends the player's surges whose time is up; {@code all}, every one (a logout, a probe's teardown). */
    public static void endSurges(ServerPlayer player, boolean all) {
        Map<String, Surging> mine = SURGING.get(player.getUUID());
        if (mine == null || mine.isEmpty()) {
            return;
        }
        long now = player.level().getGameTime();
        for (Map.Entry<String, Surging> e : List.copyOf(mine.entrySet())) {
            if (all || now >= e.getValue().endsAt()) {
                endSurge(player, e.getKey());
            }
        }
    }

    /** When the surge of {@code node} ends, or -1 when none runs. Probe read. */
    public static long surgeEndsAt(ServerPlayer player, String node) {
        Map<String, Surging> mine = SURGING.get(player.getUUID());
        Surging running = mine == null ? null : mine.get(node);
        return running == null ? -1L : running.endsAt();
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            endSurges(player, true);
            SURGING.remove(player.getUUID());
            Reactions.forget(player.getUUID());
        }
    }

    /** Spends an armed guard on the hit that armed it (same tick), after armour, inside {@code actuallyHurt}. */
    private static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Pending armed = PENDING.remove(player.getUUID());
        if (armed == null || armed.gameTime() != player.level().getGameTime()) {
            return;
        }
        event.setNewDamage((float) (event.getNewDamage() * (1.0 - armed.fraction())));
        GUARDED.incrementAndGet();
    }

    /** Procs delivered, refused as ungranted, and refused on the timer. Probe reads. */
    public static long procs() {
        return PROCS.get();
    }

    public static long refused() {
        return REFUSED.get();
    }

    public static long cooling() {
        return COOLING.get();
    }

    /** Guards armed by the impact, and hits they cut. Probe reads. */
    public static long guardsArmed() {
        return GUARDS_ARMED.get();
    }

    public static long guarded() {
        return GUARDED.get();
    }

    /** Procs refused by a node's hit window. Probe read. */
    public static long outsideWindow() {
        return OUTSIDE_WINDOW.get();
    }

    /** Hasten impacts that ran, and ticks they took off (per timer moved). Probe reads. */
    public static long hastened() {
        return HASTENED.get();
    }

    public static long hasteTicks() {
        return HASTE_TICKS.get();
    }

    /** Skill hits reported from outside the engine (Iron's), and passives they fired. Probe reads. */
    public static long foreignHits() {
        return FOREIGN_HITS.get();
    }

    public static long foreignFired() {
        return FOREIGN_FIRED.get();
    }

    /** Reaction reads: events counted short of their count, cast/move reactions fired, dodges fed, surges. */
    public static long counting() {
        return COUNTING.get();
    }

    public static long reacted() {
        return REACTED.get();
    }

    public static long dodges() {
        return DODGES.get();
    }

    public static long surges() {
        return SURGES.get();
    }

    public static long surgesEnded() {
        return SURGES_ENDED.get();
    }

    /** Harmful effects removed (not expired) from anything: the cleanse event's raw count. Probe read. */
    public static long cleanses() {
        return CLEANSES.get();
    }

    /** Mana-restore impacts that ran. Probe read. */
    public static long manaRestores() {
        return MANA_RESTORES.get();
    }
}
