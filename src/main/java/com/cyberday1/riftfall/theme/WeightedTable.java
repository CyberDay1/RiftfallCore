package com.cyberday1.riftfall.theme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * A normalised weighted table sampled by a value in {@code [0, 1)}.
 *
 * <p>
 * The sample value is <em>not</em> a random number — the substitutor feeds it coherent 3D simplex
 * noise, so neighbouring blocks get neighbouring samples and therefore the same entry. That is what
 * makes a themed room look like geology instead of confetti. This class only cares that the value
 * is in range and monotonic in the weights.
 *
 * <p>
 * Weights are relative on the way in and normalised here (defence #1 of the fallback ladder:
 * dropping an entry whose block does not exist must not silently change the mix of the survivors —
 * a two-entry 0.5/0.5 slot that loses one entry becomes 1.0, not 0.5-plus-a-hole).
 *
 * <p>
 * Deliberately free of any Minecraft type so it is directly unit-testable.
 *
 * @param <T> the entry payload
 */
public final class WeightedTable<T> {

    /** Weights at or below this are treated as absent; guards against a table summing to zero. */
    private static final double MIN_WEIGHT = 1.0E-6D;

    private final List<T> entries;

    /** {@code cumulative[i]} is the running total of normalised weights up to and including i. */
    private final double[] cumulative;

    private WeightedTable(List<T> entries, double[] cumulative) {
        this.entries = entries;
        this.cumulative = cumulative;
    }

    /**
     * Builds a table, dropping non-positive weights and normalising what is left.
     *
     * @param entries the candidates, in declaration order
     * @param weight  relative weight of an entry; non-positive means "drop"
     * @return the table, or {@code null} if nothing survived — callers must then apply the
     *         empty-slot fallback (defence #2) rather than emitting nothing
     */
    public static <T> WeightedTable<T> of(List<T> entries, ToDoubleFunction<T> weight) {
        List<T> kept = new ArrayList<>(entries.size());
        List<Double> weights = new ArrayList<>(entries.size());
        double total = 0.0D;
        for (T entry : entries) {
            double w = weight.applyAsDouble(entry);
            if (!(w > MIN_WEIGHT) || !Double.isFinite(w)) {
                continue;
            }
            kept.add(entry);
            weights.add(w);
            total += w;
        }
        if (kept.isEmpty() || total <= MIN_WEIGHT) {
            return null;
        }

        double[] cumulative = new double[kept.size()];
        double running = 0.0D;
        for (int i = 0; i < kept.size(); i++) {
            running += weights.get(i) / total;
            cumulative[i] = running;
        }
        // Float error must never leave a sliver above the last boundary that selects nothing.
        cumulative[cumulative.length - 1] = 1.0D;
        return new WeightedTable<>(List.copyOf(kept), cumulative);
    }

    /** A table with exactly one entry at weight 1. */
    public static <T> WeightedTable<T> single(T entry) {
        return new WeightedTable<>(List.of(entry), new double[] { 1.0D });
    }

    /** Entries that survived construction, in declaration order. Never empty. */
    public List<T> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    /**
     * Normalised weight of entry {@code i}. Exposed for tests and for logging; the hot path uses
     * {@link #select(double)}.
     */
    public double normalisedWeight(int index) {
        return index == 0 ? cumulative[0] : cumulative[index] - cumulative[index - 1];
    }

    /**
     * Picks an entry.
     *
     * @param sample a value in {@code [0, 1)}; out-of-range values are clamped rather than
     *               rejected, because the caller's noise source is not contractually bounded and
     *               throwing mid-worldgen is not an improvement over picking the last entry
     */
    public T select(double sample) {
        int size = cumulative.length;
        if (size == 1) {
            return entries.getFirst();
        }
        for (int i = 0; i < size; i++) {
            if (sample < cumulative[i]) {
                return entries.get(i);
            }
        }
        return entries.get(size - 1);
    }
}
