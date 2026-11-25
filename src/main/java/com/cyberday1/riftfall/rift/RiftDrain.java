package com.cyberday1.riftfall.rift;

/**
 * When a closed rift may actually be closed: once its chunk work has drained, never sooner, and never by blocking.
 *
 * <p>
 * A rift's generation writes blocks, and {@code ServerLevel#onBlockStateChange} hands each point-of-interest update to
 * {@code server.execute} for later. Closing the level first shuts its POI storage, and the deferred update then joins a
 * read on a closed {@code IOWorker} whose future never completes: the server thread parks for good. Closing it while
 * the work is in flight is no better, because {@code ServerChunkCache#close} finishes the generation inside the close,
 * on the server thread, and queues more of the same updates as it goes.
 *
 * <p>
 * So {@link RiftLevels} lets go of every ticket and keeps the level ticking until its chunk map has no work at all
 * ({@code ChunkMap#hasWork}, the condition vanilla's own stop waits on), and for {@link #QUIET_TICKS} ticks in a row:
 * the server runs its queued tasks between ticks, so a POI update queued on the tick the last chunk unloaded runs while
 * the level is still open. There is no give-up: a rift that never drains is a level left open until the stop, which
 * is a leak, and a leak is better than a frozen server. {@link #overdue} is the warning.
 */
final class RiftDrain {

    /** Ticks in a row with no chunk work before the level is closed. Two: the tasks between them have to run. */
    static final int QUIET_TICKS = 2;

    /** A minute: past this a rift is still draining for a reason worth a line in the log. */
    static final int OVERDUE_TICKS = 1200;

    enum Step { WAIT, TEAR_DOWN }

    private int waited;
    private int quiet;
    private boolean warned;

    /** One tick's look at the level: whether its chunk map still has work. */
    Step observe(boolean chunkWork) {
        waited++;
        quiet = chunkWork ? 0 : quiet + 1;
        return quiet >= QUIET_TICKS ? Step.TEAR_DOWN : Step.WAIT;
    }

    /** True once, the first time the drain has run past {@link #OVERDUE_TICKS}. */
    boolean overdue() {
        if (warned || waited < OVERDUE_TICKS) {
            return false;
        }
        warned = true;
        return true;
    }

    int waited() {
        return waited;
    }

    /**
     * Whether a stop keeps a rift (its run brings it back) rather than taking it down with the server. A rift already
     * closing is not kept even if a run still names it: it was on its way down.
     */
    static boolean keptAtStop(boolean closing, boolean runHolds) {
        return !closing && runHolds;
    }
}
