package com.cyberday1.riftfall.block.barrel;

import com.cyberday1.riftfall.RiftfallConfig;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * How many draws one barrel gives one player.
 *
 * <h2>Why the count lives here and not in the table</h2>
 * A pool's {@code rolls} is the same number for every barrel that reads the table, so it cannot
 * answer {@code riftfall:scavenging}, which is a fact about the player standing at the barrel.
 * Riftfall therefore decides the count and calls the table once per draw, which leaves the four tier
 * tables as nothing but weighted item lists.
 *
 * <p>
 * Three bands of independent candidate slots decide the base count: three certain, three at a half,
 * two at a quarter. So a barrel runs 3&ndash;8 draws with an expectation of 5 before any gear is
 * involved. ⭐ Cut from 12 + 8 + 4 (about 17) on 2026-10-11 after a playtest:
 * two or three barrels filled a whole inventory.
 *
 * <p>
 * ⭐ {@code barrelLootScale} (2026-11-17, default 0.75) multiplies that count alongside scavenging, fraction as a
 * chance: the one knob for how much a rift's barrels pay. The shard and gem roll is per share and not scaled.
 *
 * <h2>⭐ Quantity multiplies that count, it does not add to it</h2>
 * {@code riftfall:scavenging} scales the base count by {@code 1 + quantity}, which makes the stat
 * mean the plain thing its name says: <b>100% is twice the loot</b>, 40% is four tenths again. An
 * earlier design gave it a fourth band of its own worth a quarter-slot per ten percent, and the
 * arithmetic of that was indefensible &mdash; a player at 100% got 19.5 draws against the then
 * baseline of 17, so a stat presented as doubling your haul moved it by a seventh.
 *
 * <p>
 * A multiplier also scales with the bands rather than against them: raise {@link #BASE_SLOTS} or a
 * band chance later and quantity keeps its meaning instead of quietly becoming weaker.
 *
 * <p>
 * The fractional part is paid as a chance rather than floored, so there is no dead zone at the
 * bottom. A common {@code riftfall:scavenging} roll is 0.05, and flooring would turn a single roll
 * into nothing a player could ever see.
 *
 * <p>
 * The container is the only ceiling. {@code scavenging} is registered without a meaningful cap, so
 * {@link #roll} clamps: it never returns more draws than there are empty slots, and stacking the stat
 * past a barrel's capacity buys nothing rather than losing something.
 *
 * <p>
 * ⭐ In a real barrel the clamp no longer fires. The designed ceiling on scavenging is 200%, so the
 * most any player can ask for is {@code 8 x 3.0 = 24}, and a barrel holds 81. That is why the
 * container grew rather than why the clamp went: the clamp is the guard on a number somebody will
 * raise later, and the failure on the other side of it is silent.
 */
public final class BarrelDraws {

    /** Certain. The floor under every barrel, whoever opens it. */
    static final int BASE_SLOTS = 3;

    static final int SECOND_BAND_SLOTS = 3;
    static final double SECOND_BAND_CHANCE = 0.5;

    static final int THIRD_BAND_SLOTS = 2;
    static final double THIRD_BAND_CHANCE = 0.25;

    private static volatile @Nullable Double probeScale;

    private BarrelDraws() {
    }

    /** The live {@code barrelLootScale} (its default before the server config loads), or a pinned override. */
    public static double scale() {
        Double override = probeScale;
        if (override != null) {
            return override;
        }
        return RiftfallConfig.SPEC.isLoaded() ? RiftfallConfig.BARREL_LOOT_SCALE.get()
                : RiftfallConfig.BARREL_LOOT_SCALE.getDefault();
    }

    /** Probe and test use: pins {@link #scale()}, say for a before arm; null restores the config. */
    public static void overrideScaleForProbe(@Nullable Double scale) {
        probeScale = scale;
    }

    /** {@link #roll(RandomSource, double, double, int)} at the designed count, scale 1. */
    public static int roll(RandomSource random, double quantity, int capacity) {
        return roll(random, quantity, 1.0D, capacity);
    }

    /**
     * The number of draws to take, never past what will fit. At scale 1 it never falls below the base band; at the
     * default 0.75 a share can take two.
     *
     * <p>
     * The capacity clamp is not dead code waiting on a bigger container. {@code scavenging}'s
     * ceiling is a number somebody will raise, and the failure on the other side of it is silent:
     * draws with nowhere to go are a reward the player is never told about.
     */
    public static int roll(RandomSource random, double quantity, double scale, int capacity) {
        int limit = Math.max(0, capacity);
        int draws = Math.min(BASE_SLOTS, limit);

        draws += band(random, SECOND_BAND_SLOTS, SECOND_BAND_CHANCE, limit - draws);
        draws += band(random, THIRD_BAND_SLOTS, THIRD_BAND_CHANCE, limit - draws);
        return scaled(random, draws, sanitized(scale) * (1.0D + sanitized(quantity)), limit);
    }

    /**
     * The base count times {@code factor} (the loot scale times {@code 1 + quantity}), the fraction paid as a chance.
     *
     * <p>
     * Clamping before the cast is what makes the arithmetic safe rather than merely tidy: quantity
     * arrives from an attribute registered to 1024 and is a {@code double}, so an infinity would
     * otherwise cast to {@code MAX_VALUE}, take the fractional branch, and wrap negative. Held to the
     * capacity first, every value on the way to the cast is a real slot count.
     */
    private static int scaled(RandomSource random, int draws, double factor, int limit) {
        double wanted = Math.min(limit, draws * factor);
        int whole = (int) wanted;
        return random.nextDouble() < wanted - whole ? Math.min(limit, whole + 1) : whole;
    }

    /**
     * How many of {@code slots} independent candidates come up.
     *
     * <p>
     * Stops at {@code room}, which keeps an absurd quantity from running a loop of two billion for
     * an answer the clamp would have thrown away. Exact rather than an approximation: the slots it
     * skips could only have raised a count that is already at the capacity.
     */
    private static int band(RandomSource random, int slots, double chance, int room) {
        int won = 0;
        for (int slot = 0; slot < slots && won < room; slot++) {
            if (random.nextDouble() < chance) {
                won++;
            }
        }
        return won;
    }

    /** NaN reads as none, the same guard {@code CombatStats} keeps: an unattached attribute is NaN. Also the scale's guard. */
    private static double sanitized(double quantity) {
        return Double.isNaN(quantity) || quantity <= 0.0D ? 0.0D : quantity;
    }
}
