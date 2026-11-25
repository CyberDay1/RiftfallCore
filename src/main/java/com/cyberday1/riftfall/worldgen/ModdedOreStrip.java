package com.cyberday1.riftfall.worldgen;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallTags;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.ReplaceBlockConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * No modded ore generates: any placed feature that would put a modded ore block in the world is taken out of every
 * biome, after every other modifier has added its own. Modded dimensions are left alone unless the file says so.
 * {@code -Driftfall.modded_ore_strip=off} turns it off (the rig's "before" sample).
 */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class ModdedOreStrip {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String PROPERTY = "riftfall.modded_ore_strip";
    public static final boolean ENFORCED = enforced(System.getProperty(PROPERTY));

    private static final String VANILLA = "minecraft";

    /** Modded-dimension biomes a dimension's biome source does not list up front (Deep Aether's, in the Aether). */
    private static final TagKey<Biome> EXEMPT = TagKey.create(Registries.BIOME, RiftfallCore.id("ore_strip_exempt"));

    /** Per feature holder: the modded ore ids it places (empty for most). Biomes share holders, so each is read once. */
    private static final Map<Holder<PlacedFeature>, List<String>> JUDGED = Collections.synchronizedMap(new IdentityHashMap<>());
    /** Stripped feature id to the ore ids that condemned it. */
    private static final Map<String, List<String>> STRIPPED = new ConcurrentHashMap<>();
    /** Stripped feature id to how many biomes it came out of. */
    private static final Map<String, AtomicInteger> BIOMES = new ConcurrentHashMap<>();
    private static final AtomicInteger UNREADABLE = new AtomicInteger();
    /** Feature types that could not be read at all, so they were kept. */
    private static final Set<String> UNREAD = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger SKIPPED_BIOMES = new AtomicInteger();

    private static volatile MinecraftServer exemptFor;
    private static volatile Set<ResourceKey<Biome>> exempt = Set.of();

    private ModdedOreStrip() {
    }

    static boolean enforced(String property) {
        return property == null || !property.trim().equalsIgnoreCase("off");
    }

    /** The rule on one block id: vanilla never, a tagged block always, otherwise an {@code _ore} name. */
    static boolean moddedOre(ResourceLocation block, boolean tagged) {
        if (VANILLA.equals(block.getNamespace())) {
            return false;
        }
        String path = block.getPath();
        return tagged || path.endsWith("_ore") || path.contains("_ore_");
    }

    /** The rule on a block id string as the world has it: tag and name. */
    public static boolean moddedOre(String block) {
        ResourceLocation id = ResourceLocation.tryParse(block);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return false;
        }
        return moddedOre(id, BuiltInRegistries.BLOCK.get(id).builtInRegistryHolder().is(RiftfallTags.MODDED_ORES));
    }

    /** Every block-state {@code Name} in an encoded feature, which is every block it can place or target. */
    static void blockNames(JsonElement json, Set<String> into) {
        if (json == null) {
            return;
        }
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(element -> blockNames(element, into));
        } else if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            JsonElement name = object.get("Name");
            if (name != null && name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()) {
                into.add(name.getAsString());
            }
            object.entrySet().forEach(entry -> blockNames(entry.getValue(), into));
        }
    }

    /** Block ids the feature can place, read off its codec, so any mod's feature type answers the same way. */
    static Set<String> placedBlocks(PlacedFeature feature) {
        Set<String> blocks = new TreeSet<>();
        // Plain JSON inlines nested features; it fails on registry-bound fields, so each part is then read alone.
        var whole = ConfiguredFeature.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, feature.feature().value()).result();
        if (whole.isPresent()) {
            blockNames(whole.get(), blocks);
            return blocks;
        }
        DynamicOps<JsonElement> ops = ops();
        feature.getFeatures().forEach(configured -> {
            var one = ConfiguredFeature.DIRECT_CODEC.encodeStart(ops, configured).result();
            if (one.isPresent()) {
                blockNames(one.get(), blocks);
            } else if (configured.config() instanceof OreConfiguration ore) {
                ore.targetStates.forEach(target -> blocks.add(id(target.state.getBlock())));
            } else if (configured.config() instanceof ReplaceBlockConfiguration replace) {
                replace.targetStates.forEach(target -> blocks.add(id(target.state.getBlock())));
            } else {
                UNREADABLE.incrementAndGet();
                UNREAD.add(BuiltInRegistries.FEATURE.getKey(configured.feature()) + "");
            }
        });
        return blocks;
    }

    private static DynamicOps<JsonElement> ops() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? JsonOps.INSTANCE : server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    }

    /** The modded ores among what a feature places. */
    static List<String> ores(Holder<PlacedFeature> feature) {
        List<String> known = JUDGED.get(feature);
        if (known != null) {
            return known;
        }
        List<String> judged;
        try {
            judged = judge(feature.value(), ModdedOreStrip::moddedOre);
        } catch (RuntimeException unreadable) {
            UNREADABLE.incrementAndGet();
            LOGGER.warn("Modded ore strip: could not read {}, so it stays.", name(feature), unreadable);
            judged = List.of();
        }
        JUDGED.put(feature, judged);
        return judged;
    }

    static List<String> judge(PlacedFeature feature, Predicate<String> ore) {
        return placedBlocks(feature).stream().filter(ore).toList();
    }

    /** Takes the condemned features out of one step's list, recording each. */
    static void strip(List<Holder<PlacedFeature>> features, Function<Holder<PlacedFeature>, List<String>> ores) {
        features.removeIf(feature -> {
            List<String> found = ores.apply(feature);
            if (found.isEmpty()) {
                return false;
            }
            String id = name(feature);
            STRIPPED.putIfAbsent(id, found);
            BIOMES.computeIfAbsent(id, key -> new AtomicInteger()).incrementAndGet();
            return true;
        });
    }

    private static String id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    private static String name(Holder<PlacedFeature> feature) {
        return feature.unwrapKey().map(key -> key.location().toString()).orElse("an inline feature");
    }

    /** Biomes only a modded dimension generates; a vanilla-namespace biome is never one. */
    private static Set<ResourceKey<Biome>> exempt() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return Set.of();
        }
        if (exemptFor == server) {
            return exempt;
        }
        Set<ResourceKey<Biome>> found = new HashSet<>();
        var stems = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        stems.entrySet().forEach(entry -> {
            if (VANILLA.equals(entry.getKey().location().getNamespace())) {
                return;
            }
            try {
                for (Holder<Biome> biome : entry.getValue().generator().getBiomeSource().possibleBiomes()) {
                    biome.unwrapKey().filter(key -> !VANILLA.equals(key.location().getNamespace())).ifPresent(found::add);
                }
            } catch (RuntimeException unreadable) {
                LOGGER.warn("Modded ore strip: could not list the biomes of {}.", entry.getKey().location(), unreadable);
            }
        });
        exempt = Set.copyOf(found);
        exemptFor = server;
        return exempt;
    }

    /** What the strip did, for the probe. */
    public static JsonObject report() {
        JsonObject report = new JsonObject();
        report.addProperty("enforced", ENFORCED);
        report.addProperty("judged", JUDGED.size());
        report.addProperty("unreadable", UNREADABLE.get());
        JsonArray unread = new JsonArray();
        new TreeSet<>(UNREAD).forEach(unread::add);
        report.add("unreadTypes", unread);
        report.addProperty("skippedBiomes", SKIPPED_BIOMES.get());
        report.addProperty("exemptBiomes", exempt.size());
        JsonObject features = new JsonObject();
        new TreeMap<>(STRIPPED).forEach((feature, ores) -> {
            JsonObject row = new JsonObject();
            JsonArray ids = new JsonArray();
            ores.forEach(ids::add);
            row.add("ores", ids);
            row.addProperty("biomes", BIOMES.getOrDefault(feature, new AtomicInteger()).get());
            features.add(feature, row);
        });
        report.add("features", features);
        return report;
    }

    @SubscribeEvent
    public static void summarise(ServerAboutToStartEvent event) {
        LOGGER.info("Modded ore strip: {} feature(s) removed from vanilla-dimension biomes ({} enforced): {}",
                STRIPPED.size(), ENFORCED ? "is" : "NOT", new TreeSet<>(STRIPPED.keySet()));
    }

    /**
     * {@code riftfall:strip_modded_ores}. {@code modded_dimensions} (default false) also strips the biomes only a
     * modded dimension generates.
     */
    public record Strip(HolderSet<Biome> biomes, boolean moddedDimensions) implements BiomeModifier {
        public static final MapCodec<Strip> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Biome.LIST_CODEC.fieldOf("biomes").forGetter(Strip::biomes),
                Codec.BOOL.optionalFieldOf("modded_dimensions", false).forGetter(Strip::moddedDimensions))
                .apply(instance, Strip::new));

        @Override
        public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
            if (phase != Phase.AFTER_EVERYTHING || !ENFORCED || !biomes.contains(biome)) {
                return;
            }
            if (!moddedDimensions && (biome.is(EXEMPT) || biome.unwrapKey().map(exempt()::contains).orElse(false))) {
                SKIPPED_BIOMES.incrementAndGet();
                return;
            }
            var settings = builder.getGenerationSettings();
            for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
                strip(settings.getFeatures(step), ModdedOreStrip::ores);
            }
        }

        @Override
        public MapCodec<? extends BiomeModifier> codec() {
            return CODEC;
        }
    }

    @EventBusSubscriber(modid = RiftfallCore.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void register(RegisterEvent event) {
            event.register(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS,
                    RiftfallCore.id("strip_modded_ores"), () -> Strip.CODEC);
        }
    }
}
