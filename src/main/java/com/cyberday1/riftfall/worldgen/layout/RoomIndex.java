package com.cyberday1.riftfall.worldgen.layout;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.worldgen.space.RoomSocket;
import com.cyberday1.riftfall.worldgen.space.TemplateJigsaws;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

/** What planning needs of each pooled room, so a plan can be built without reading room blocks. */
public final class RoomIndex {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Written by datagen beside the room files. */
    public static final ResourceLocation FILE = RiftfallCore.id("room_index.json");

    public static final RoomIndex EMPTY = new RoomIndex(Map.of());

    public record Entry(Vec3i size, List<RoomSocket> sockets) {

        public static Entry of(StructureTemplate template) {
            return new Entry(template.getSize(), TemplateJigsaws.socketsOf(TemplateJigsaws.ringJigsaws(
                    TemplateJigsaws.parse(template), RiftfallCore.MODID)));
        }
    }

    private final Map<ResourceLocation, Entry> entries;

    private RoomIndex(Map<ResourceLocation, Entry> entries) {
        this.entries = Map.copyOf(entries);
    }

    public static RoomIndex of(Map<ResourceLocation, Entry> entries) {
        return new RoomIndex(entries);
    }

    @Nullable
    public Entry get(ResourceLocation room) {
        return entries.get(room);
    }

    public int size() {
        return entries.size();
    }

    /**
     * The entries that still describe the room file in use: one from the pack the index came from,
     * with no copy saved over it in the world.
     */
    public static RoomIndex load(ResourceManager resources, Predicate<ResourceLocation> savedInWorld) {
        Optional<Resource> file = resources.getResource(FILE);
        if (file.isEmpty()) {
            return EMPTY;
        }
        Map<ResourceLocation, Entry> read;
        try (Reader reader = file.get().openAsReader()) {
            read = read(GsonHelper.parse(reader));
        } catch (Exception error) {
            LOGGER.warn("Riftfall rooms: the room index is unreadable, reading every room instead", error);
            return EMPTY;
        }
        String pack = file.get().sourcePackId();
        Map<ResourceLocation, Entry> current = new HashMap<>();
        for (var entry : read.entrySet()) {
            ResourceLocation id = entry.getKey();
            Optional<Resource> room = resources.getResource(id.withPath(path -> "structure/" + path + ".nbt"));
            if (room.isPresent() && room.get().sourcePackId().equals(pack) && !savedInWorld.test(id)) {
                current.put(id, entry.getValue());
            }
        }
        return new RoomIndex(current);
    }

    public static JsonObject write(Map<ResourceLocation, Entry> rooms) {
        JsonObject json = new JsonObject();
        for (var room : new TreeMap<>(rooms).entrySet()) {
            JsonObject entry = new JsonObject();
            entry.add("size", triple(room.getValue().size()));
            JsonArray sockets = new JsonArray();
            for (RoomSocket socket : room.getValue().sockets()) {
                JsonObject one = new JsonObject();
                one.add("at", triple(socket.position()));
                one.addProperty("face", socket.direction().getSerializedName());
                one.addProperty("elevation", socket.elevation());
                sockets.add(one);
            }
            entry.add("sockets", sockets);
            json.add(room.getKey().toString(), entry);
        }
        return json;
    }

    public static Map<ResourceLocation, Entry> read(JsonObject json) {
        Map<ResourceLocation, Entry> rooms = new HashMap<>();
        for (var room : json.entrySet()) {
            JsonObject entry = room.getValue().getAsJsonObject();
            List<RoomSocket> sockets = new ArrayList<>();
            for (JsonElement raw : entry.getAsJsonArray("sockets")) {
                JsonObject one = raw.getAsJsonObject();
                Direction face = Direction.byName(one.get("face").getAsString());
                if (face == null) {
                    throw new IllegalArgumentException("unknown face in " + room.getKey());
                }
                sockets.add(new RoomSocket(triple(one.getAsJsonArray("at")), face,
                        one.get("elevation").getAsInt()));
            }
            rooms.put(ResourceLocation.parse(room.getKey()),
                    new Entry(triple(entry.getAsJsonArray("size")), List.copyOf(sockets)));
        }
        return rooms;
    }

    private static JsonArray triple(Vec3i vector) {
        JsonArray array = new JsonArray();
        array.add(vector.getX());
        array.add(vector.getY());
        array.add(vector.getZ());
        return array;
    }

    private static Vec3i triple(JsonArray array) {
        return new Vec3i(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }
}
