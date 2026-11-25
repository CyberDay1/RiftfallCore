package com.cyberday1.riftfall.client;

import com.mojang.logging.LogUtils;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * A client mesh read on first use, off the render thread, and dropped after {@code idleMillis} unused.
 * A failed read is not retried until the next resource reload.
 */
final class LazyMesh<T> {
    interface Loader<T> { T load() throws Exception; }

    private final String name;
    private final Loader<T> loader;
    private final Executor executor;
    private final LongSupplier clock;
    private final long idleMillis;
    private volatile T value;
    private volatile long lastUse;
    private CompletableFuture<?> pending;
    private int generation;
    private boolean failed;

    LazyMesh(String name, Loader<T> loader, Executor executor, LongSupplier clock, long idleMillis) {
        this.name = name; this.loader = loader; this.executor = executor; this.clock = clock; this.idleMillis = idleMillis;
    }

    /** The mesh, or null while it loads (the first call starts the read). */
    T get() {
        lastUse = clock.getAsLong();
        T loaded = value;
        if (loaded == null) request();
        return value;
    }

    /** Start the read ahead of use and keep the mesh resident. */
    void prefetch() { get(); }

    /** Blocking read, for the probes; true once loaded. */
    boolean loadNow() {
        lastUse = clock.getAsLong();
        if (value != null) return true;
        CompletableFuture<?> waiting;
        synchronized (this) { request(); waiting = pending; }
        if (waiting != null) waiting.join();
        return value != null;
    }

    /** Called every client tick: releases the mesh once nothing has used it for the idle window. */
    void tick() {
        if (value != null && clock.getAsLong() - lastUse > idleMillis) value = null;
    }

    /** The resources changed: drop the mesh and ignore any read still running. */
    synchronized void invalidate() {
        generation++; value = null; pending = null; failed = false;
    }

    boolean loaded() { return value != null; }
    synchronized boolean failed() { return failed; }

    private synchronized void request() {
        if (value != null || pending != null || failed) return;
        int expected = generation;
        var read = CompletableFuture.supplyAsync(() -> {
            try { return loader.load(); } catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        }, executor);
        // The handler is what pending holds, so a join sees the value already published.
        var done = new CompletableFuture<Void>();
        pending = done;
        read.whenComplete((result, error) -> {
            synchronized (this) {
                if (expected == generation && pending == done) {
                    pending = null;
                    if (error != null) {
                        failed = true;
                        LogUtils.getLogger().error("Could not load the {} mesh", name, error);
                    } else {
                        lastUse = clock.getAsLong();
                        value = result;
                    }
                }
            }
            done.complete(null);
        });
    }
}
