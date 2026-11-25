package com.cyberday1.riftfall.skill;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Iron's Spells' half of the ability gate, on the shared {@link AbilityGrants} allowlist. A player's
 * Iron's cast is refused unless Riftfall granted the spell, where one starts
 * ({@code AbstractSpell#attemptInitiateCast}, and {@code SpellCastUtil#castForPlayer} for the affix
 * procs that skip it) and where it lands ({@code AbstractSpell#castSpell}), which a granted cast
 * reaches only by paying Riftfall's price. Takes Iron's spell id as its string, so names no Iron's type.
 *
 * <p>Riftfall's own cast of an Iron's node ({@link RiftIronsCast}) is priced before it starts, so the
 * cast hook still judges it but does not charge it again. A granted cast never pays Iron's own mana
 * or cooldown, whichever route started it.
 */
public final class RiftIronsGate {

    static final SkillNode.Engine ENGINE = SkillNode.Engine.IRONS_SPELLBOOKS;

    private static final AtomicLong STARTS_REFUSED = new AtomicLong();
    private static final AtomicLong AFFIX_REFUSED = new AtomicLong();
    private static final AtomicLong CASTS_REFUSED = new AtomicLong();
    private static final AtomicLong CASTS_PAID = new AtomicLong();
    private static final AtomicLong CASTS_UNPAID = new AtomicLong();
    private static final AtomicLong EXEMPT_PASSED = new AtomicLong();
    private static final AtomicLong CASTS_OWN = new AtomicLong();
    private static final AtomicLong MANA_WAIVED = new AtomicLong();
    private static final AtomicLong COOLDOWNS_WAIVED = new AtomicLong();
    private static final ThreadLocal<Own> OWN = new ThreadLocal<>();
    private static final Predicate<Object> NOBODY = caster -> false;
    private static volatile Predicate<Object> probeExempt = NOBODY;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean warned;

    private RiftIronsGate() {
    }

    /** Head of {@code AbstractSpell#attemptInitiateCast}: books, imbued weapons, scrolls, {@code /cast}. */
    public static boolean refusesStart(Object caster, String spellId) {
        if (!refuses(caster, spellId)) {
            return false;
        }
        STARTS_REFUSED.incrementAndGet();
        return true;
    }

    /** Head of irons_apothic's {@code SpellCastUtil#castForPlayer}: an affix proc, which never attempts. */
    public static boolean refusesAffix(Object caster, String spellId) {
        if (!refuses(caster, spellId)) {
            return false;
        }
        AFFIX_REFUSED.incrementAndGet();
        return true;
    }

    /** Riftfall's own cast of one spell, already priced. {@code ran} is set by the cast hook. */
    static final class Own {
        final Object caster;
        final String spellId;
        boolean ran;

        Own(Object caster, String spellId) {
            this.caster = caster;
            this.spellId = spellId;
        }
    }

    /**
     * Wraps {@code AbstractSpell#castSpell}: runs {@code cast} only for a granted spell whose owning node
     * pays Riftfall's mana and cooldown. Answers whether it ran. A non-player is not judged.
     */
    public static boolean castPaid(Object caster, String spellId, Runnable cast) {
        if (!(caster instanceof ServerPlayer player)) {
            cast.run();
            return true;
        }
        if (!SkillCasts.eligible(player)) return false;
        if (exempt(caster)) {
            cast.run();
            return true;
        }
        Own own = ownCast(caster, spellId);
        boolean ran = castPaid(() -> AbilityGrants.of(player),
                (ability, use) -> SkillService.pay(player, ENGINE, ability,
                        (level, owner, node, riders) -> RiftIronsCast.atPower(player, node, use)),
                spellId, cast, own != null);
        if (own != null) {
            own.ran = ran;
        }
        return ran;
    }

    static boolean castPaid(Supplier<AbilityGrants.Grants> grants,
                            BiFunction<ResourceLocation, Supplier<SkillCasts.Invocation>, SkillService.Casting> pay,
                            String spellId, Runnable cast) {
        return castPaid(grants, pay, spellId, cast, false);
    }

    /** {@code prepaid}: Riftfall's own cast, priced by {@code SkillCasts}. Still judged, never charged twice. */
    static boolean castPaid(Supplier<AbilityGrants.Grants> grants,
                            BiFunction<ResourceLocation, Supplier<SkillCasts.Invocation>, SkillService.Casting> pay,
                            String spellId, Runnable cast, boolean prepaid) {
        ResourceLocation ability = idOf(spellId);
        if (AbilityGrants.refuses(true, grants, ENGINE, ability)) {
            CASTS_REFUSED.incrementAndGet();
            return false;
        }
        if (prepaid) {
            CASTS_OWN.incrementAndGet();
            cast.run();
            return true;
        }
        boolean[] ran = {false};
        SkillService.Casting paid;
        try {
            paid = pay.apply(ability, () -> {
                ran[0] = true;
                cast.run();
                return SkillCasts.Invocation.CAST;
            });
        } catch (RuntimeException failure) {
            if (ran[0]) {
                throw failure;
            }
            warnOnce(failure);
            paid = null;
        }
        if (paid == null || paid.result() != SkillService.Cast.CAST) {
            CASTS_UNPAID.incrementAndGet();
            return false;
        }
        CASTS_PAID.incrementAndGet();
        return ran[0];
    }

    /**
     * Runs {@code cast} as Riftfall's own, already-priced cast of {@code spellId} by {@code caster}.
     * Answers whether the cast hook let it through; only that one spell by that one caster is covered.
     */
    public static boolean castOwn(Object caster, String spellId, Runnable cast) {
        Own was = OWN.get();
        Own own = new Own(caster, spellId);
        OWN.set(own);
        try {
            cast.run();
        } finally {
            if (was == null) {
                OWN.remove();
            } else {
                OWN.set(was);
            }
        }
        return own.ran;
    }

    /** The open own-cast scope if it is this caster's cast of this spell, else null. */
    static @Nullable Own ownCast(Object caster, @Nullable String spellId) {
        Own own = OWN.get();
        return own != null && own.caster == caster && own.spellId.equals(spellId) ? own : null;
    }

    /** {@code SpellOnCastEvent}: a granted cast pays no Iron's mana. */
    public static boolean waivesMana(Object caster, String spellId) {
        if (!waives(caster, spellId)) {
            return false;
        }
        MANA_WAIVED.incrementAndGet();
        return true;
    }

    /** {@code SpellCooldownAddedEvent.Pre}: a granted spell starts no Iron's cooldown; the node's timer is the one. */
    public static boolean waivesCooldown(Object caster, String spellId) {
        if (!waives(caster, spellId)) {
            return false;
        }
        COOLDOWNS_WAIVED.incrementAndGet();
        return true;
    }

    private static boolean waives(Object caster, String spellId) {
        ServerPlayer player = caster instanceof ServerPlayer server ? server : null;
        return waives(player != null, () -> AbilityGrants.of(player), spellId);
    }

    /** Only a server player's granted spell; a probe-exempt caster has no grant, so pays Iron's price. */
    static boolean waives(boolean serverPlayer, Supplier<AbilityGrants.Grants> grants, @Nullable String spellId) {
        return serverPlayer && !AbilityGrants.refuses(true, grants, ENGINE, idOf(spellId));
    }

    private static boolean refuses(Object caster, String spellId) {
        if (exempt(caster)) {
            return false;
        }
        ServerPlayer player = caster instanceof ServerPlayer server ? server : null;
        return refuses(player != null, () -> AbilityGrants.of(player), spellId);
    }

    /** Only a server player is judged; an id that will not parse, or a lookup that throws, refuses. */
    static boolean refuses(boolean serverPlayer, Supplier<AbilityGrants.Grants> grants, @Nullable String spellId) {
        return AbilityGrants.refuses(serverPlayer, grants, ENGINE, idOf(spellId));
    }

    static @Nullable ResourceLocation idOf(@Nullable String spellId) {
        return spellId == null ? null : ResourceLocation.tryParse(spellId);
    }

    private static boolean exempt(Object caster) {
        if (!probeExempt.test(caster)) {
            return false;
        }
        EXEMPT_PASSED.incrementAndGet();
        return true;
    }

    /**
     * Probe use: casters {@code exempt} accepts pass all three hooks unjudged and unpriced, so the rig's
     * control arm can show what the gate would have stopped. Null puts it back to nobody. No command reaches it.
     */
    public static void exemptForProbe(Predicate<Object> exempt) {
        probeExempt = exempt == null ? NOBODY : exempt;
    }

    private static void warnOnce(RuntimeException failure) {
        if (!warned) {
            warned = true;
            LOGGER.error("Iron's ability gate price failed; refusing rather than letting the cast through", failure);
        }
    }

    /** Casts refused at the start, affix procs refused, and casts refused where they land. Probe reads. */
    public static long startsRefused() {
        return STARTS_REFUSED.get();
    }

    public static long affixRefused() {
        return AFFIX_REFUSED.get();
    }

    public static long castsRefused() {
        return CASTS_REFUSED.get();
    }

    /** Granted casts that paid Riftfall's price, and that could not. Probe reads. */
    public static long castsPaid() {
        return CASTS_PAID.get();
    }

    public static long castsUnpaid() {
        return CASTS_UNPAID.get();
    }

    /** Riftfall's own casts the cast hook judged and let through unpriced. Probe reads. */
    public static long castsOwn() {
        return CASTS_OWN.get();
    }

    /** Granted casts whose Iron's mana, and whose Iron's cooldown, were waived. Probe reads. */
    public static long manaWaived() {
        return MANA_WAIVED.get();
    }

    public static long cooldownsWaived() {
        return COOLDOWNS_WAIVED.get();
    }

    /** Hook passes the probe exemption granted. Zero outside a probe. */
    public static long exemptPassed() {
        return EXEMPT_PASSED.get();
    }
}
