package com.cyberday1.riftfall.worldgen.async;

import com.cyberday1.riftfall.worldgen.layout.RiftPlan;
import com.cyberday1.riftfall.worldgen.level.RiftLoadStats;
import com.cyberday1.riftfall.worldgen.space.TripleMirror;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/** Bounded pure-work executor. Saturated or closed schedulers return null for ordered sync fallback. */
public final class BakeScheduler implements AutoCloseable {
    /** Template blocks held at once; about 60 bytes each. */
    static final long CACHE_BLOCKS = 3_000_000;

    private final ThreadPoolExecutor executor;
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private final RoomRasterizer rasterizer = new RoomRasterizer();
    private final long cacheBlocks;
    /** Whole rooms still owed to some column, oldest first. */
    private final LinkedHashMap<RiftPlan.PlacedRoom, Whole> rooms = new LinkedHashMap<>();
    private long cachedBlocks;

    /** One bake of a room, and which of its columns have asked for it. */
    private record Whole(StructureTemplate template, BakedRoomContext inputs,
                         CompletableFuture<RoomRasterizer.WholeRoom> bake, Set<Long> served,
                         int columns, long blocks) {
        boolean answers(StructureTemplate asked, BakedRoomContext ctx) {
            return template == asked && inputs.riftSeed() == ctx.riftSeed()
                    && inputs.worldSeed() == ctx.worldSeed() && inputs.roomKind() == ctx.roomKind()
                    && inputs.resolvedTheme() == ctx.resolvedTheme()
                    && inputs.convertSpawners() == ctx.convertSpawners()
                    && inputs.barrelRules().equals(ctx.barrelRules());
        }
    }

    public BakeScheduler() {
        this(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1)), 64);
    }

    BakeScheduler(int threads, int capacity) {
        this(threads, capacity, CACHE_BLOCKS);
    }

    BakeScheduler(int threads, int capacity, long cacheBlocks) {
        this.cacheBlocks = cacheBlocks;
        AtomicInteger counter = new AtomicInteger();
        executor = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity), runnable -> {
                    Thread thread = new Thread(runnable, "riftfall-bake-" + counter.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
    }

    public CompletableFuture<BakedRoom> submit(RiftPlan.PlacedRoom room, StructureTemplate template,
                                               BakedRoomContext ctx) {
        return submit(room, template, ctx, null);
    }

    public CompletableFuture<BakedRoom> submitProp(TripleMirror mirror, BlockPos anchor, BoundingBox bounds,
                                                   StructureTemplate template, BakedRoomContext ctx) {
        return submit(() -> rasterizer.rasterizeProp(mirror, anchor, bounds, template, ctx));
    }

    /** One bake per room, shared by its columns and dropped once the last of them has asked. */
    public CompletableFuture<BakedRoom> submit(RiftPlan.PlacedRoom room, StructureTemplate template,
                                               BakedRoomContext ctx, @Nullable RiftLoadStats stats) {
        BoundingBox clip = ctx.clip();
        CompletableFuture<RoomRasterizer.WholeRoom> bake;
        synchronized (rooms) {
            Whole whole = rooms.get(room);
            if (whole != null && !whole.answers(template, ctx)) {
                drop(room);
                whole = null;
            }
            if (whole == null) {
                CompletableFuture<RoomRasterizer.WholeRoom> started = start(() -> {
                    long start = System.nanoTime();
                    RoomRasterizer.WholeRoom baked = rasterizer.bakeWhole(room.mirror(), room.anchor(),
                            room.bounds(), template, ctx);
                    if (stats != null) stats.add(RiftLoadStats.Phase.ROOM_BAKE, start);
                    return baked;
                });
                if (started == null) return null;
                BoundingBox bounds = room.bounds();
                whole = new Whole(template, ctx, started, new HashSet<>(),
                        ((bounds.maxX() >> 4) - (bounds.minX() >> 4) + 1)
                                * ((bounds.maxZ() >> 4) - (bounds.minZ() >> 4) + 1),
                        (long) bounds.getXSpan() * bounds.getYSpan() * bounds.getZSpan());
                rooms.put(room, whole);
                cachedBlocks += whole.blocks();
                var eldest = rooms.entrySet().iterator();
                while (cachedBlocks > cacheBlocks && rooms.size() > 1) {
                    cachedBlocks -= eldest.next().getValue().blocks();
                    eldest.remove();
                }
            }
            bake = whole.bake();
            boolean oneColumn = clip.minX() >> 4 == clip.maxX() >> 4 && clip.minZ() >> 4 == clip.maxZ() >> 4;
            if (oneColumn && clip.intersects(room.bounds())) {
                whole.served().add(ChunkPos.asLong(clip.minX() >> 4, clip.minZ() >> 4));
                if (whole.served().size() >= whole.columns()) drop(room);
            }
            if (stats != null) stats.held(rooms.size(), cachedBlocks);
        }
        return bake.thenApply(baked -> rasterizer.slice(baked, template, room.mirror(), room.anchor(),
                clip, ctx.ignoreEntities()));
    }

    private void drop(RiftPlan.PlacedRoom room) {
        Whole gone = rooms.remove(room);
        if (gone != null) cachedBlocks -= gone.blocks();
    }

    /** Rooms whose whole bake is still held. */
    int cachedRooms() {
        synchronized (rooms) { return rooms.size(); }
    }

    public CompletableFuture<BakedRoom> submitProp(TripleMirror mirror, BlockPos anchor, BoundingBox bounds,
                                                   StructureTemplate template, BakedRoomContext ctx,
                                                   RiftLoadStats stats) {
        return submit(() -> {
            long start = System.nanoTime();
            BakedRoom baked = rasterizer.rasterizeProp(mirror, anchor, bounds, template, ctx);
            stats.add(RiftLoadStats.Phase.PROP_BAKE, start);
            return baked;
        });
    }

    CompletableFuture<BakedRoom> submit(Supplier<BakedRoom> work) {
        return start(work);
    }

    private <T> CompletableFuture<T> start(Supplier<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        pending.add(future);
        future.whenComplete((result, error) -> pending.remove(future));
        try {
            executor.execute(() -> {
                if (future.isDone()) return;
                try { future.complete(work.get()); }
                catch (Throwable error) { future.completeExceptionally(error); }
            });
            return future;
        } catch (RejectedExecutionException error) {
            future.cancel(false);
            return null;
        }
    }

    /** Only a failed bake is retried; a partially committed placement must never be replayed. */
    public static void finish(CompletableFuture<BakedRoom> future, Consumer<BakedRoom> commit,
                              Runnable fallback, Consumer<RuntimeException> report) {
        if (future == null) { fallback.run(); return; }
        BakedRoom baked;
        try { baked = future.join(); }
        catch (CompletionException | CancellationException error) {
            report.accept(error);
            fallback.run();
            return;
        }
        commit.accept(baked);
    }

    /** Called after chunk generation drains. Also releases all waiters if shutdown interrupts a bake. */
    @Override
    public void close() {
        executor.shutdownNow();
        pending.forEach(future -> future.cancel(false));
        synchronized (rooms) {
            rooms.clear();
            cachedBlocks = 0;
        }
    }
}
