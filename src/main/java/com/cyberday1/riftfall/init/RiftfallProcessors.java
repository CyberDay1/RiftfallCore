package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.worldgen.processor.BarrelConversionProcessor;
import com.cyberday1.riftfall.worldgen.processor.ClipProcessor;
import com.cyberday1.riftfall.worldgen.processor.PoiAnchorProcessor;
import com.cyberday1.riftfall.worldgen.processor.RoomKindProcessor;
import com.cyberday1.riftfall.worldgen.processor.ThemeSubstitutionProcessor;
import com.cyberday1.riftfall.worldgen.processor.TrialSpawnerProcessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Structure processor types for Riftfall Core. */
public final class RiftfallProcessors {

    public static final DeferredRegister<StructureProcessorType<?>> PROCESSORS =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, RiftfallCore.MODID);

    public static final DeferredHolder<StructureProcessorType<?>,
            StructureProcessorType<com.cyberday1.riftfall.home.WildernessVariation>> WILDERNESS =
            PROCESSORS.register("wilderness_variation", () -> () -> com.cyberday1.riftfall.home.WildernessVariation.CODEC);

    /**
     * {@code riftfall:theme} — the placeholder substitutor. Added to a structure's processor list:
     *
     * <pre>
     * { "processor_type": "riftfall:theme", "theme": "riftfall:cave" }
     * </pre>
     *
     * with {@code theme} optional; omitted means {@code riftfall:default}.
     */
    public static final DeferredHolder<StructureProcessorType<?>,
            StructureProcessorType<ThemeSubstitutionProcessor>> THEME =
            PROCESSORS.register("theme", () -> () -> ThemeSubstitutionProcessor.CODEC);

    /**
     * {@code riftfall:barrel} — the chest-marker converter. Registered because a processor has to
     * have a type; it is never named in a datapack, because only the chunk generator knows the seed
     * and the per-rift rules one has to be built with.
     */
    public static final DeferredHolder<StructureProcessorType<?>,
            StructureProcessorType<BarrelConversionProcessor>> BARREL =
            PROCESSORS.register("barrel", () -> () -> BarrelConversionProcessor.CODEC);

    /**
     * {@code riftfall:poi_anchor} — the anchor-block remover. Registered for the same reason the
     * barrel one is: a processor must have a type, not because anything names it in a datapack.
     */
    public static final DeferredHolder<StructureProcessorType<?>,
            StructureProcessorType<PoiAnchorProcessor>> POI_ANCHOR =
            PROCESSORS.register("poi_anchor", () -> () -> PoiAnchorProcessor.CODEC);

    /**
     * {@code riftfall:trial_spawner} — the authored-spawner converter. Registered on the same terms
     * as the other two: a type is required, nothing names it in a datapack.
     */
    public static final DeferredHolder<StructureProcessorType<?>,
            StructureProcessorType<TrialSpawnerProcessor>> TRIAL_SPAWNER =
            PROCESSORS.register("trial_spawner", () -> () -> TrialSpawnerProcessor.CODEC);

    /**
     * {@code riftfall:room_kind} — the label a placement carries so the theme's decorators can tell
     * which room they are decorating. It changes no blocks. Registered because a processor must have
     * a type, and because a datapack placing its own structure can then say which kind it counts as
     * with {@code {"processor_type": "riftfall:room_kind", "kind": "chaos"}}.
     */
    public static final DeferredHolder<StructureProcessorType<?>,
            StructureProcessorType<RoomKindProcessor>> ROOM_KIND =
            PROCESSORS.register("room_kind", () -> () -> RoomKindProcessor.CODEC);

    public static final DeferredHolder<StructureProcessorType<?>,
            StructureProcessorType<ClipProcessor>> CLIP =
            PROCESSORS.register("clip", () -> () -> ClipProcessor.CODEC);

    /** Carries a placement's tier and prop boxes to the decorators; changes no blocks. */
    public static final DeferredHolder<StructureProcessorType<?>,
            StructureProcessorType<com.cyberday1.riftfall.worldgen.processor.RoomFactsProcessor>> ROOM_FACTS =
            PROCESSORS.register("room_facts", () -> () -> com.cyberday1.riftfall.worldgen.processor.RoomFactsProcessor.CODEC);

    private RiftfallProcessors() {
    }

    public static void register(IEventBus modEventBus) {
        PROCESSORS.register(modEventBus);
    }
}
