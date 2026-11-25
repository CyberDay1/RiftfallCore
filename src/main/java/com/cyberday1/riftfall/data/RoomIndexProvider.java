package com.cyberday1.riftfall.data;

import com.cyberday1.riftfall.worldgen.layout.RoomIndex;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/** Writes the size and ring sockets of every pooled room, which is all a rift's plan reads of one. */
public final class RoomIndexProvider implements DataProvider {

    private final PackOutput output;

    public RoomIndexProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Path file = output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(RoomIndex.FILE.getNamespace()).resolve(RoomIndex.FILE.getPath());
        return DataProvider.saveStable(cache, RoomIndex.write(index()), file);
    }

    @Override
    public String getName() {
        return "Riftfall room index";
    }

    /** Package-private so the drift gate re-runs the real thing against the checked-in file. */
    static Map<ResourceLocation, RoomIndex.Entry> index() {
        Path root = TemplatePoolProvider.structureRoot();
        Map<ResourceLocation, RoomIndex.Entry> rooms = new TreeMap<>();
        for (String room : TemplatePoolProvider.pooledRooms()) {
            ResourceLocation id = ResourceLocation.parse(room);
            rooms.put(id, RoomIndex.Entry.of(read(root.resolve(id.getPath() + ".nbt"))));
        }
        return rooms;
    }

    /** Reads a room the way the game's template manager does. */
    private static StructureTemplate read(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            int version = NbtUtils.getDataVersion(tag, 500);
            if (version < SharedConstants.getCurrentVersion().getDataVersion().getVersion()) {
                tag = DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), tag, version);
            }
            StructureTemplate template = new StructureTemplate();
            template.load(BuiltInRegistries.BLOCK.asLookup(), tag);
            return template;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }
}
