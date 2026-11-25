package com.cyberday1.riftfall.home;

import com.cyberday1.riftfall.RiftfallCore;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Refills a forge display frame whose authored item left the pack. An unknown item id loads as an empty frame, so a
 * frame is matched by its tile position and facing, filled only while empty, and only once per home.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class HomeForgeDisplays {
    public record Frame(BlockPos position, Direction facing, int rotation, ResourceLocation from, ResourceLocation to) {
        String key() { return "forge_display_v1@" + position.getX() + "," + position.getY() + "," + position.getZ(); }
    }

    static final String TILE = "forge_100x100";
    public static final List<Frame> FRAMES = load();
    private static final Map<ServerLevel, List<ItemFrame>> PENDING = new WeakHashMap<>();

    private HomeForgeDisplays() { }

    private static List<Frame> load() {
        try (var stream = HomeForgeDisplays.class.getResourceAsStream("/data/riftfall/home_repairs/forge_displays_v1.json")) {
            if (stream == null) throw new IllegalStateException("Missing forge display repair");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("revision").getAsInt() != 1 || !root.get("tile").getAsString().equals(TILE))
                throw new IllegalStateException("Unsupported forge display repair");
            var frames = new ArrayList<Frame>();
            for (var value : root.getAsJsonArray("frames")) {
                var row = value.getAsJsonObject(); var a = row.getAsJsonArray("position");
                var pos = new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
                var facing = Direction.byName(row.get("facing").getAsString());
                int rotation = row.get("rotation").getAsInt();
                if (facing == null || rotation < 0 || rotation > 7 || pos.getX() < 0 || pos.getX() >= 100 || pos.getZ() < 0 || pos.getZ() >= 100)
                    throw new IllegalStateException("Invalid forge display " + pos);
                frames.add(new Frame(pos, facing, rotation, ResourceLocation.parse(row.get("from").getAsString()),
                        ResourceLocation.parse(row.get("to").getAsString())));
            }
            if (new HashSet<>(frames.stream().map(Frame::position).toList()).size() != frames.size())
                throw new IllegalStateException("Duplicate forge display");
            return List.copyOf(frames);
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot load forge display repair", e); }
    }

    /** True when it filled the frame. Never touches a frame that holds anything, or one already filled once. */
    public static boolean fill(ServerLevel level, ItemFrame frame, ShatteredHomeProgress progress) {
        if (frame.getType() != EntityType.ITEM_FRAME || !frame.getItem().isEmpty()
                || !ShatteredHomeLevels.isShatteredHome(level.dimension()) || !ShatteredHomeLayout.supports(progress.layoutVersion())) return false;
        var tile = ShatteredHomeLayout.tiles(progress.layoutVersion()).stream().filter(t -> t.id().equals(TILE)).findFirst();
        if (tile.isEmpty() || !progress.unlockedTiles().contains(tile.get().position())) return false;
        var local = frame.getPos().subtract(tile.get().position().blockOrigin());
        for (var entry : FRAMES) {
            if (!entry.position().equals(local) || frame.getDirection() != entry.facing() || progress.forgeDisplayRepaired(entry.key())) continue;
            var item = BuiltInRegistries.ITEM.getOptional(entry.to());
            if (item.isEmpty()) return false;
            frame.setItem(new ItemStack(item.get()));
            frame.setRotation(entry.rotation());
            progress.markForgeDisplayRepaired(entry.key());
            return true;
        }
        return false;
    }

    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        // Filled on the next tick: the join fires mid entity-section load, too early to update neighbours.
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ItemFrame frame)
                || !frame.getItem().isEmpty() || !ShatteredHomeLevels.isShatteredHome(level.dimension())) return;
        synchronized (PENDING) { PENDING.computeIfAbsent(level, k -> new ArrayList<>()).add(frame); }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        List<Map.Entry<ServerLevel, List<ItemFrame>>> batch;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return;
            batch = new ArrayList<>();
            for (var entry : PENDING.entrySet()) batch.add(Map.entry(entry.getKey(), List.copyOf(entry.getValue())));
            PENDING.clear();
        }
        for (var entry : batch) {
            var progress = ShatteredHomeProgress.of(entry.getKey());
            for (var frame : entry.getValue()) if (!frame.isRemoved()) fill(entry.getKey(), frame, progress);
        }
    }
}
