package com.cyberday1.riftfall.data;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.worldgen.level.GalleryDimension;
import com.cyberday1.riftfall.worldgen.level.RiftDimensions;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.core.RegistrySetBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.Set;

/**
 * Datagen entry point.
 *
 * <p>
 * The processor subsystem is 180 blocks; hand-writing its ~540 client JSONs plus the lang file is
 * not a thing anyone should do, and a hand-written set would drift from the registry the first
 * time a variant is added. Everything client-side is generated from the same
 * {@code ProcessorSlot} x {@code ProcessorVariant} loop that does the registration.
 */
public final class RiftfallDataGenerators {

    /**
     * The worldgen registries this mod contributes: the template pools that make the piece library
     * addressable, and the processor lists those pools attach to every element.
     */
    private static final RegistrySetBuilder WORLDGEN = new RegistrySetBuilder()
            .add(Registries.PROCESSOR_LIST, TemplatePoolProvider::processorLists)
            .add(Registries.TEMPLATE_POOL, TemplatePoolProvider::pools)
            .add(Registries.BIOME, RiftDimensions::biomes)
            // The rift and the gallery are unrelated dimensions that happen to bootstrap into the
            // same two registries, and a RegistrySetBuilder takes one entry per registry. Composed
            // here rather than split into two builders, so the whole worldgen contribution stays
            // one declaration.
            .add(Registries.DIMENSION_TYPE, context -> {
                RiftDimensions.dimensionTypes(context);
                GalleryDimension.dimensionType(context);
            })
            .add(Registries.LEVEL_STEM, context -> {
                RiftDimensions.levelStems(context);
                GalleryDimension.levelStem(context);
            });

    private RiftfallDataGenerators() {
    }

    /**
     * Subscribed from the mod constructor rather than via {@code @EventBusSubscriber}: the
     * annotation's {@code bus} attribute is deprecated for removal on this NeoForge line, and an
     * explicit listener is one fewer thing to port.
     */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RiftfallDataGenerators::gatherData);
    }

    private static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();

        generator.addProvider(event.includeClient(),
                new ProcessorBlockStateProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(),
                new MarkerBlockStateProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(),
                new BarrelBlockStateProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(),
                new RiftfallLanguageProvider(output, "en_us"));
        generator.addProvider(event.includeClient(), new CreditsProvider(output));
        generator.addProvider(event.includeServer(),
                new RiftfallBlockTagsProvider(output, event.getLookupProvider(), existingFileHelper));
        generator.addProvider(event.includeServer(),
                new BlockFamilyTableProvider(output));
        generator.addProvider(event.includeServer(),
                new SyntheticAnchorProvider(output));
        generator.addProvider(event.includeServer(),
                new RoomIndexProvider(output));
        generator.addProvider(event.includeServer(),
                new DatapackBuiltinEntriesProvider(output, event.getLookupProvider(), WORLDGEN,
                        Set.of(RiftfallCore.MODID)));
    }
}
