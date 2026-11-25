package com.cyberday1.riftfall.worldgen.level;

import java.util.concurrent.atomic.LongAdder;

/** Per-rift generation counters. Times are summed across threads, so they can exceed wall time. */
public final class RiftLoadStats {

    public enum Phase { LIBRARY, PLAN, COLUMN, FILL, ROOM_BAKE, PROP_BAKE, ROOM_WAIT, APPLY, NATIVE, FACE, SEAM, TEMPLATE }

    private final LongAdder[] nanos = new LongAdder[Phase.values().length];
    private final LongAdder[] counts = new LongAdder[Phase.values().length];

    public RiftLoadStats() {
        for (int i = 0; i < nanos.length; i++) {
            nanos[i] = new LongAdder();
            counts[i] = new LongAdder();
        }
    }

    /** Records one occurrence that began at {@code startNanos}. */
    public void add(Phase phase, long startNanos) {
        nanos[phase.ordinal()].add(System.nanoTime() - startNanos);
        counts[phase.ordinal()].increment();
    }

    private volatile long heldRooms;
    private volatile long heldBlocks;
    private volatile long peakBlocks;

    /** What the room cache holds after its latest change. */
    public void held(long rooms, long blocks) {
        heldRooms = rooms;
        heldBlocks = blocks;
        if (blocks > peakBlocks) peakBlocks = blocks;
    }

    public long count(Phase phase) {
        return counts[phase.ordinal()].sum();
    }

    public long millis(Phase phase) {
        return nanos[phase.ordinal()].sum() / 1_000_000;
    }

    public String report() {
        StringBuilder out = new StringBuilder();
        for (Phase phase : Phase.values()) {
            if (!out.isEmpty()) out.append(", ");
            out.append(phase.name().toLowerCase(java.util.Locale.ROOT)).append(' ')
                    .append(count(phase)).append('/').append(millis(phase)).append("ms");
        }
        out.append(", cache ").append(heldRooms).append(" rooms/").append(heldBlocks)
                .append(" blocks, peak ").append(peakBlocks);
        return out.toString();
    }
}
