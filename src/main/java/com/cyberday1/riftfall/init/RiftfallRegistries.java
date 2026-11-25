package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.theme.RiftTheme;
import com.cyberday1.riftfall.worldgen.decorator.AttachmentDecorator;
import com.cyberday1.riftfall.worldgen.decorator.RiftDecorator;
import com.cyberday1.riftfall.worldgen.decorator.RoomLightDecorator;
import com.cyberday1.riftfall.worldgen.decorator.TrapPlateDecorator;
import com.cyberday1.riftfall.worldgen.decorator.VineDecorator;
import com.cyberday1.riftfall.home.ShatteredHomeChunkGenerator;
import com.cyberday1.riftfall.worldgen.level.RiftChunkGenerator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The two registries the theme system introduces.
 *
 * <h2>Themes are a datapack registry, not a reload listener</h2>
 * Both would work on 21.1.224, and the reload-listener route is marginally simpler, but the
 * datapack registry buys three things that matter here:
 * <ul>
 * <li>NeoForge patches {@code RegistryDataLoader.loadElementFromResource} to decode every entry
 * through {@code ConditionalOps}, so {@code "neoforge:conditions"} works with no code from us. A
 * cross-mod theme can guard itself with {@code mod_loaded} and simply not load when the mod is
 * absent — which is the only correct behaviour, because its blocks would otherwise resolve to air.
 * A reload listener would have to reimplement condition handling.</li>
 * <li>Themes become addressable by {@link ResourceKey}, so a structure processor, a dungeon
 * generator or a command can name one without inventing a parallel id scheme.</li>
 * <li>Directory layout falls out for free: NeoForge's {@code Registries.elementsDirPath} patch
 * prefixes non-vanilla namespaces, so key {@code riftfall:rift_theme} reads exactly
 * {@code data/<ns>/riftfall/rift_theme/<name>.json} as specified.</li>
 * </ul>
 * The registry is <strong>unsynced</strong> (no network codec): themes are consumed during
 * worldgen on the server only, and requiring clients to carry them would make every theme datapack
 * a client-side dependency for no benefit.
 */
public final class RiftfallRegistries {

    /** Datapack registry key. Drives {@code data/<ns>/riftfall/rift_theme/*.json}. */
    public static final ResourceKey<Registry<RiftTheme>> RIFT_THEMES =
            ResourceKey.createRegistryKey(RiftfallCore.id("rift_theme"));

    /**
     * The last line of defence. This theme must always exist and must reference vanilla blocks
     * only, because every other failure path in the system ends here.
     */
    public static final ResourceKey<RiftTheme> DEFAULT_THEME =
            ResourceKey.create(RIFT_THEMES, RiftfallCore.id("default"));

    private static final ResourceKey<Registry<MapCodec<? extends RiftDecorator>>> DECORATOR_TYPE_KEY =
            ResourceKey.createRegistryKey(RiftfallCore.id("decorator_type"));

    private static final DeferredRegister<MapCodec<? extends RiftDecorator>> DECORATOR_TYPE_REGISTER =
            DeferredRegister.create(DECORATOR_TYPE_KEY, RiftfallCore.MODID);

    /**
     * Static registry of decorator codecs, so any mod can add a decorator type in Java and any
     * datapack can then configure it from a theme file.
     */
    public static final Registry<MapCodec<? extends RiftDecorator>> DECORATOR_TYPES =
            DECORATOR_TYPE_REGISTER.makeRegistry(builder -> {
            });

    public static final DeferredHolder<MapCodec<? extends RiftDecorator>, MapCodec<AttachmentDecorator>> ATTACHMENT =
            DECORATOR_TYPE_REGISTER.register("attachment", () -> AttachmentDecorator.CODEC);

    public static final DeferredHolder<MapCodec<? extends RiftDecorator>, MapCodec<VineDecorator>> VINE =
            DECORATOR_TYPE_REGISTER.register("vine", () -> VineDecorator.CODEC);

    public static final DeferredHolder<MapCodec<? extends RiftDecorator>, MapCodec<TrapPlateDecorator>> TRAP_PLATE =
            DECORATOR_TYPE_REGISTER.register("trap_plate", () -> TrapPlateDecorator.CODEC);

    public static final DeferredHolder<MapCodec<? extends RiftDecorator>, MapCodec<RoomLightDecorator>> ROOM_LIGHT =
            DECORATOR_TYPE_REGISTER.register("room_light", () -> RoomLightDecorator.CODEC);

    public static final DeferredHolder<MapCodec<? extends RiftDecorator>, MapCodec<com.cyberday1.riftfall.worldgen.decorator.GemVeinDecorator>> GEM_VEIN =
            DECORATOR_TYPE_REGISTER.register("gem_vein", () -> com.cyberday1.riftfall.worldgen.decorator.GemVeinDecorator.CODEC);

    public static final DeferredHolder<MapCodec<? extends RiftDecorator>, MapCodec<com.cyberday1.riftfall.worldgen.decorator.OreChunkDecorator>> ORE_CHUNK =
            DECORATOR_TYPE_REGISTER.register("ore_chunk", () -> com.cyberday1.riftfall.worldgen.decorator.OreChunkDecorator.CODEC);

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATOR_REGISTER =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, RiftfallCore.MODID);

    /** Names {@code riftfall:rift} in a level stem's {@code generator.type}. */
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<RiftChunkGenerator>> RIFT_GENERATOR =
            CHUNK_GENERATOR_REGISTER.register("rift", () -> RiftChunkGenerator.CODEC);

    /** Names {@code riftfall:shattered_home} in a level stem's {@code generator.type}. */
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<ShatteredHomeChunkGenerator>> SHATTERED_HOME_GENERATOR =
            CHUNK_GENERATOR_REGISTER.register("shattered_home", () -> ShatteredHomeChunkGenerator.CODEC);

    private RiftfallRegistries() {
    }

    public static void register(IEventBus modEventBus) {
        DECORATOR_TYPE_REGISTER.register(modEventBus);
        CHUNK_GENERATOR_REGISTER.register(modEventBus);
        modEventBus.addListener(RiftfallRegistries::registerDataPackRegistries);
    }

    private static void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(RIFT_THEMES, RiftTheme.CODEC);
    }
}
