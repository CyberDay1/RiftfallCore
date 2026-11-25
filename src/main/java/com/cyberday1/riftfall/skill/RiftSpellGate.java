package com.cyberday1.riftfall.skill;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.container.SpellContainerSource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Spell Engine's half of the ability gate. The offer filter is the defence: it runs on every
 * container rebuild and does not use the engine's event, whose shared {@code executing} flag can
 * skip a handler in singleplayer. A grant change forces that rebuild. The {@code CASTING_ATTEMPT}
 * veto is the second wall; the melee hit the client reports is refused on its own, outside both.
 * A granted use the engine starts pays Riftfall's mana and cooldown, never the engine's cooldown.
 */
public final class RiftSpellGate {

    static final EngineShape SHAPE = new EngineShape();

    /** The engine's {@code Result}, bound field by field. */
    static final class EngineShape implements SpellOffer.Shape<SpellContainerSource.Result, SpellContainer,
            Holder<Spell>, SpellContainerSource.SourcedContainer> {
        @Override
        public SpellContainer active(SpellContainerSource.Result result) {
            return result.activeContainer();
        }

        @Override
        public List<Holder<Spell>> actives(SpellContainerSource.Result result) {
            return result.actives();
        }

        @Override
        public List<Holder<Spell>> passives(SpellContainerSource.Result result) {
            return result.passives();
        }

        @Override
        public List<Holder<Spell>> modifiers(SpellContainerSource.Result result) {
            return result.modifiers();
        }

        @Override
        public List<SpellContainerSource.SourcedContainer> sources(SpellContainerSource.Result result) {
            return result.sources();
        }

        @Override
        public @Nullable ResourceLocation id(Holder<Spell> spell) {
            return idOf(spell);
        }

        @Override
        public List<String> ids(SpellContainer container) {
            return container.spell_ids();
        }

        @Override
        public SpellContainer withIds(SpellContainer container, List<String> ids) {
            return container.copyWith(ids);
        }

        @Override
        public SpellContainer container(SpellContainerSource.SourcedContainer source) {
            return source.container();
        }

        @Override
        public SpellContainerSource.SourcedContainer withContainer(SpellContainerSource.SourcedContainer source,
                                                                   SpellContainer container) {
            return new SpellContainerSource.SourcedContainer(source.name(), source.itemStack(), container);
        }

        @Override
        public SpellContainerSource.Result rebuild(SpellContainer active, List<Holder<Spell>> actives,
                                                   List<Holder<Spell>> passives, List<Holder<Spell>> modifiers,
                                                   List<SpellContainerSource.SourcedContainer> sources) {
            return new SpellContainerSource.Result(active, actives, passives, modifiers, sources);
        }
    }

    private static final AbilityGrants.Offered OFFERED = new AbilityGrants.Offered();
    private static final AtomicLong VETOED = new AtomicLong();
    private static final AtomicLong CUT = new AtomicLong();
    private static final AtomicLong REFRESHED = new AtomicLong();
    private static final AtomicLong MELEE_REFUSED = new AtomicLong();
    private static final AtomicLong NATIVE_PAID = new AtomicLong();
    private static final AtomicLong NATIVE_REFUSED = new AtomicLong();
    private static final AtomicLong HITS_COVERED = new AtomicLong();
    private static final AtomicLong HITS_PAID = new AtomicLong();
    private static final AtomicLong HITS_REFUSED = new AtomicLong();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean warned;

    private RiftSpellGate() {
    }

    static void register() {
        RiftSpellEngine.registerClassSchools();
        SpellEvents.CASTING_ATTEMPT.PRE.register(RiftSpellGate::attempt);
        RiftPassiveSpells.register();
        CooldownReset.register();
        SummonControl.register();
        ShifterAspects.register();
        DotImpacts.register();
        StruckImpact.register();
        Rampage.register();
        RiftSpellEngine.registerTravel();
        BladeRains.registerDelivery();
    }

    /**
     * Head of {@code SpellContainerSource#update}. With container caching the engine rebuilds only
     * when a source is dirty, and a buy or an unslot dirties none, so a changed grant set marks the
     * main hand dirty (as {@code syncServerSideContainers} does) and this same update rebuilds.
     */
    public static void refresh(Object owner) {
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }
        if (OFFERED.stale(player, AbilityGrants.lookup(() -> AbilityGrants.of(player)))) {
            REFRESHED.incrementAndGet();
            SpellContainerSource.setDirty(player, SpellContainerSource.MAIN_HAND);
            SpellContainerSource.setDirty(player, RiftPassiveSpells.SOURCE);
        }
    }

    /** Called by {@code MixinSpellContainerSource} with whatever the engine is about to store. */
    public static SpellContainerSource.Result offer(Object owner, SpellContainerSource.Result result) {
        if (!(owner instanceof ServerPlayer player) || result == null) {
            return result;
        }
        AbilityGrants.Grants grants = AbilityGrants.lookup(() -> AbilityGrants.of(player));
        SpellContainerSource.Result kept;
        try {
            kept = SpellOffer.keep(result, SHAPE, ability -> grants.allows(SkillNode.Engine.SPELL_ENGINE, ability));
        } catch (RuntimeException failure) {
            warnOnce("offer", failure);
            kept = SpellContainerSource.Result.EMPTY;
        }
        OFFERED.built(player, grants);
        if (kept != result) {
            CUT.incrementAndGet();
        }
        return kept;
    }

    /**
     * Whether to drop a melee skill hit or swing the client reported. {@code Melee#performAttackAgainstTargets}
     * and {@code Melee#broadcastAttackFx} take the spell id from the packet and check no container.
     */
    public static boolean refusesMelee(ServerPlayer player, @Nullable ResourceLocation ability) {
        if (SkillCasts.eligible(player)
                && !AbilityGrants.refuses(true, () -> AbilityGrants.of(player), SkillNode.Engine.SPELL_ENGINE, ability)) {
            return false;
        }
        MELEE_REFUSED.incrementAndGet();
        return true;
    }

    /** Client-reported melee hits arrive after the paid cast's temporary rank modifier has ended. */
    public static void rankedMelee(ServerPlayer player, ResourceLocation ability, Runnable hit) {
        SkillBook book = SkillService.bookOf(player);
        SkillDef def = AbilityGrants.payer(book, SkillDefs::get, SkillNode.Engine.SPELL_ENGINE, ability);
        if (def != null) {
            SkillCasts.atRank(player, def.cast(), book.rank(def.id()), hit);
        }
    }

    /**
     * Wraps the engine's delivery in {@code SpellExecution#performSpell}: a player's cast is delivered
     * only once Riftfall's price is paid. A refusal delivers nothing, so the engine's own cost (its
     * completion) never runs either. A lookup that fails refuses; the engine's own throw is left alone.
     */
    public static boolean deliverPaid(LivingEntity caster, Holder<Spell> spell, SpellExecution.ImpactContext context,
                                      List<Entity> targets, Function<SpellExecution.ImpactContext, Boolean> deliver) {
        // A passive is no cast, so Berserk does not hold it.
        if (caster instanceof ServerPlayer player && RiftPassiveSpells.isPassive(spell)) {
            return caster.isAlive() && !caster.isSpectator()
                    && RiftPassiveSpells.deliver(player, spell, context, targets, deliver);
        }
        if (!SkillCasts.eligible(caster)) {
            return false;
        }
        if (!(caster instanceof ServerPlayer)) {
            return Boolean.TRUE.equals(deliver.apply(context));
        }
        return deliverPaid(caster, spell, (node, riders) -> Boolean.TRUE.equals(deliver.apply(
                RiftSpellEngine.nativeContext(caster, spell.value(), node, riders, context))));
    }

    /** As above for an active: a passive's trigger goes to {@link RiftPassiveSpells} instead, with no mana price. */
    public static boolean deliverPaid(LivingEntity caster, Holder<Spell> spell, BooleanSupplier deliver) {
        if (!SkillCasts.eligible(caster)) return false;
        if (!(caster instanceof ServerPlayer player)) {
            return deliver.getAsBoolean();
        }
        return deliverPaid(player, spell, (node, riders) -> deliver.getAsBoolean());
    }

    private static boolean deliverPaid(LivingEntity caster, Holder<Spell> spell,
                                       java.util.function.BiFunction<SkillNode, SkillCasts.Riders, Boolean> deliver) {
        ServerPlayer player = (ServerPlayer) caster;
        boolean[] ran = {false};
        boolean[] delivered = {false};
        SkillService.Casting paid;
        try {
            paid = SkillService.pay(player, SkillNode.Engine.SPELL_ENGINE, idOf(spell), (level, owner, node, riders) -> {
                ran[0] = true;
                Reactions.cleansing(caster, () -> delivered[0] = Boolean.TRUE.equals(deliver.apply(node, riders)));
                delivered[0] |= RiftSpellEngine.hasSelfEffect(caster, node, spell.value());
                if (!delivered[0]) return SkillCasts.Invocation.NOT_CASTABLE;
                paidMelee(player, spell);
                return SkillCasts.Invocation.CAST;
            });
        } catch (RuntimeException failure) {
            if (ran[0]) {
                throw failure;
            }
            warnOnce("price", failure);
            paid = null;
        }
        if (paid == null || paid.result() != SkillService.Cast.CAST) {
            NATIVE_REFUSED.incrementAndGet();
            return false;
        }
        NATIVE_PAID.incrementAndGet();
        return delivered[0];
    }

    /** Whether the engine may start its own cooldown: never for a server player, who paid Riftfall's. */
    public static boolean enginePrices(Object player) {
        return !(player instanceof ServerPlayer);
    }

    /** Leaves a paid melee cast's strikes for the hits the client will report. */
    static void paidMelee(LivingEntity caster, Holder<Spell> spell) {
        Spell value = spell.value();
        if (!(caster instanceof ServerPlayer player) || value.deliver == null
                || value.deliver.type != Spell.Delivery.Type.MELEE || value.deliver.melee == null) {
            return;
        }
        int hits = 0;
        for (Spell.Delivery.Melee.Attack attack : value.deliver.melee.attacks) {
            hits += 1 + Math.max(0, attack.additional_strikes);
        }
        ResourceLocation id = idOf(spell);
        if (id != null) {
            PaidHits.MELEE.paid(player.getUUID(), id, hits, player.level().getGameTime());
        }
    }

    /**
     * Whether to drop a granted melee hit nobody paid for: one a paid cast left a strike for passes,
     * otherwise the hit pays Riftfall's price itself, as a cast. A lookup that fails refuses.
     */
    public static boolean refusesUnpaidHit(ServerPlayer player, @Nullable ResourceLocation ability) {
        if (!SkillCasts.eligible(player)) return true;
        try {
            if (ability != null && PaidHits.MELEE.spend(player.getUUID(), ability, player.level().getGameTime())) {
                HITS_COVERED.incrementAndGet();
                return false;
            }
            if (ability != null && SkillService.pay(player, SkillNode.Engine.SPELL_ENGINE, ability,
                    () -> SkillCasts.Invocation.CAST).result() == SkillService.Cast.CAST) {
                HITS_PAID.incrementAndGet();
                return false;
            }
        } catch (RuntimeException failure) {
            warnOnce("hit price", failure);
        }
        HITS_REFUSED.incrementAndGet();
        return true;
    }

    /** Never throws: one throw leaves the engine's event flag set and the event dead for the JVM. */
    private static SpellCast.Attempt attempt(SpellEvents.CastingAttemptEvent.Args args) {
        try {
            ServerPlayer player = args.caster() instanceof ServerPlayer server ? server : null;
            if (!AbilityGrants.refuses(player != null, () -> AbilityGrants.of(player),
                    SkillNode.Engine.SPELL_ENGINE, args.spell() == null ? null : idOf(args.spell()))) {
                return null;
            }
        } catch (RuntimeException failure) {
            warnOnce("veto", failure);
        }
        VETOED.incrementAndGet();
        return SpellCast.Attempt.none();
    }

    private static void warnOnce(String where, RuntimeException failure) {
        if (!warned) {
            warned = true;
            LOGGER.error("Ability gate {} failed; refusing rather than letting the ability through", where, failure);
        }
    }

    private static @Nullable ResourceLocation idOf(Holder<Spell> spell) {
        return spell.unwrapKey().map(ResourceKey::location).orElse(null);
    }

    /** How many attempts the veto refused. A probe's read. */
    public static long vetoed() {
        return VETOED.get();
    }

    /** How many offers lost at least one ability. A probe's read. */
    public static long cut() {
        return CUT.get();
    }

    /** How many rebuilds a grant change forced. A probe's read. */
    public static long refreshed() {
        return REFRESHED.get();
    }

    /** How many reported melee hits and swings were dropped. A probe's read. */
    public static long meleeRefused() {
        return MELEE_REFUSED.get();
    }

    /** Engine-started casts that paid Riftfall's price, and that were refused it. Probe reads. */
    public static long nativePaid() {
        return NATIVE_PAID.get();
    }

    public static long nativeRefused() {
        return NATIVE_REFUSED.get();
    }

    /** Granted melee hits a paid cast covered, that paid for themselves, and that could not pay. Probe reads. */
    public static long hitsCovered() {
        return HITS_COVERED.get();
    }

    public static long hitsPaid() {
        return HITS_PAID.get();
    }

    public static long hitsRefused() {
        return HITS_REFUSED.get();
    }
}
