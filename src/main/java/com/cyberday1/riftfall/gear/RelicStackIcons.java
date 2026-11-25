package com.cyberday1.riftfall.gear;

import java.util.ArrayList;
import java.util.List;

import com.cyberday1.riftfall.gear.RelicSignatures.Signature;
import com.cyberday1.riftfall.network.RelicStacksPayload;

/**
 * Which relic icons the HUD row shows, and what each carries: the stacks (Rage, Soul, Momentum), then the
 * cooldowns (Spore Burst, Ground Pound), then Berserk. Pure, so the client draws it, JUnit tests it and the rig
 * reads what a client would show.
 */
public final class RelicStackIcons {

    /** The row's order, left to right (or top down). */
    public static final List<Signature> ORDER = List.of(Signature.BERSERKER_RAGE, Signature.SOUL_STACKS,
            Signature.MOMENTUM, Signature.SPORE_BURST, Signature.GROUND_POUND, Signature.BERSERK);

    /** Berserk with no end ({@code /effect ... infinite}): the icon, no sweep, no number. */
    public static final double FOREVER = -1.0D;

    /** The most seconds drawn as a number; a third glyph would run past the icon's slot. */
    static final int MAX_SECONDS = 99;

    /**
     * One icon: the relic it draws, the number over it (stacks, or whole seconds left; 0 draws none), and the
     * share of its wait still to run, which the vanilla cooldown sweep covers (0 for a stack icon).
     */
    public record Icon(Signature signature, int count, float sweep) {
        public static Icon stack(Signature signature, int count) {
            return new Icon(signature, count, 0.0F);
        }
    }

    private RelicStackIcons() {
    }

    /**
     * The icons at game time {@code now} (partial ticks in): a stack of 1 or more, a cooldown still running,
     * Berserk while its effect has time left ({@code berserkLeft} in ticks, 0 none, {@link #FOREVER} no end).
     */
    public static List<Icon> icons(RelicStacksPayload p, double now, double berserkLeft) {
        List<Icon> out = new ArrayList<>(ORDER.size());
        if (p.rage() >= 1) out.add(Icon.stack(Signature.BERSERKER_RAGE, p.rage()));
        if (p.souls() >= 1) out.add(Icon.stack(Signature.SOUL_STACKS, p.souls()));
        if (p.momentum() >= 1) out.add(Icon.stack(Signature.MOMENTUM, p.momentum()));
        timed(out, Signature.SPORE_BURST, p.sporeReady() == 0L ? 0.0D : p.sporeReady() - now,
                RelicSignatureProcs.SPORE_COOLDOWN_TICKS);
        timed(out, Signature.GROUND_POUND, p.poundReady() == 0L ? 0.0D : p.poundReady() - now,
                RelicSignatureProcs.POUND_COOLDOWN_TICKS);
        if (berserkLeft == FOREVER) out.add(new Icon(Signature.BERSERK, 0, 0.0F));
        else timed(out, Signature.BERSERK, berserkLeft, RelicSignatureCosts.BERSERK_TICKS);
        return out;
    }

    private static void timed(List<Icon> out, Signature signature, double left, int total) {
        if (left > 0.0D) out.add(new Icon(signature, seconds(left), sweep(left, total)));
    }

    /** Share of a {@code total}-tick wait still to run, 0 to 1. */
    public static float sweep(double left, int total) {
        if (total <= 0 || !(left > 0.0D)) return 0.0F;
        return (float) Math.min(1.0D, left / total);
    }

    /** Whole seconds left, rounded up so the last second reads 1; 0 (no number) past {@link #MAX_SECONDS}. */
    public static int seconds(double left) {
        if (!(left > 0.0D)) return 0;
        int s = (int) Math.ceil(left / 20.0D - 1e-9);
        return s > MAX_SECONDS ? 0 : Math.max(1, s);
    }
}
