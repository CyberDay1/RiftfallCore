package com.cyberday1.riftfall.home;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A third-party block that throws from its own {@code onPlace} aborts the whole template: dustydecorations 2.3.2's
 * fishing lures roll an out-of-range texture one placement in five, which left home tiles half placed. While a home
 * tile (or a gallery stamp) places, each block write that throws is cleared and retried, and skipped with a log line
 * if it keeps throwing. Rift generation needs none: it writes proto chunks, which never run onPlace.
 */
public final class TilePlacementGuard {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Writes per block before it is skipped; at one failure in five that leaves 1 in 625. */
    static final int ATTEMPTS = 4;
    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();

    private TilePlacementGuard() {}

    private static final class Scope {
        final ResourceLocation template;
        int retried, skipped;
        Scope(ResourceLocation template) { this.template = template; }
    }

    /** Blocks retried and skipped while {@code work} placed {@code template}. */
    public record Outcome<T>(T result, int retried, int skipped) {}

    public static <T> Outcome<T> placing(ResourceLocation template, Supplier<T> work) {
        Scope before = SCOPE.get();
        Scope scope = new Scope(template);
        SCOPE.set(scope);
        try {
            return new Outcome<>(work.get(), scope.retried, scope.skipped);
        } finally {
            if (before == null) SCOPE.remove(); else SCOPE.set(before);
        }
    }

    public static boolean active() { return SCOPE.get() != null; }

    /**
     * One block write. Outside a tile placement it is the plain write. Inside, a write that throws is undone
     * ({@code clear}: the state already sits in the chunk, half finished) and tried again.
     */
    public static boolean write(BooleanSupplier place, Runnable clear, Supplier<String> what) {
        Scope scope = SCOPE.get();
        if (scope == null) return place.getAsBoolean();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                boolean placed = place.getAsBoolean();
                if (attempt > 1) scope.retried++;
                return placed;
            } catch (RuntimeException e) {
                last = e;
                LOGGER.warn("Template {}: {} threw while placing (attempt {} of {}): {}", scope.template, what.get(),
                        attempt, ATTEMPTS, e.toString());
                try {
                    clear.run();
                } catch (RuntimeException ignored) {
                    // The clear goes through the same block's onRemove; the next attempt overwrites whatever it left.
                }
            }
        }
        scope.skipped++;
        LOGGER.error("Template {}: skipped {}, it threw on every placement", scope.template, what.get(), last);
        return false;
    }
}
