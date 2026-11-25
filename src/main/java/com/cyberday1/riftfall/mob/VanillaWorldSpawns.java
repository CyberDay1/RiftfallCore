package com.cyberday1.riftfall.mob;

import com.cyberday1.riftfall.RiftfallCore;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * The overworld, the Nether and the End keep vanilla's monsters: modded hostiles are refused there,
 * rifts and homes keep their rosters.
 *
 * <p>Four layers, cheapest first: the biome modifier takes the entries out of those dimensions' biome
 * lists, the placement check refuses one before it takes a cap slot, finalization cancels one that got
 * that far, and the join check catches mod code that finalizes without the event. Rule:
 * {@link VanillaWorldSpawnRule}. {@code -Driftfall.vanilla_world_spawns=off} turns every layer off (the rig's
 * "before" census).
 */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class VanillaWorldSpawns {
    public static final String PROPERTY = "riftfall.vanilla_world_spawns";
    public static final boolean ENFORCED = VanillaWorldSpawnRule.enforced(System.getProperty(PROPERTY));
    public static final Set<ResourceKey<Level>> DIMENSIONS = Set.of(Level.OVERWORLD, Level.NETHER, Level.END);

    public static final TagKey<EntityType<?>> ALLOWED =
            TagKey.create(Registries.ENTITY_TYPE, RiftfallCore.id("vanilla_world_allowed"));
    public static final TagKey<EntityType<?>> SUPPRESSED =
            TagKey.create(Registries.ENTITY_TYPE, RiftfallCore.id("vanilla_world_suppressed"));

    /** Enderman Overhaul takes vanilla endermen out of every biome in this tag; with its own gone, vanilla goes back. */
    private static final String ENDERMAN_REPLACER = "endermanoverhaul";
    private static final TagKey<Biome> ENDERMAN_REPLACED =
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(ENDERMAN_REPLACER, "all_spawns"));

    /** Types seen as {@code Enemy} at finalization, so the placement check can refuse them next time. */
    private static final Set<EntityType<?>> ENEMIES = ConcurrentHashMap.newKeySet();
    /** Riftfall's own placements (rift spawners, boss pads): never refused. Worldgen threads read it too. */
    private static final Set<Entity> AUTHORED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Map<String, LongAdder> REFUSALS = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> STRIPPED = new ConcurrentHashMap<>();
    private static final Set<String> RESTORED = ConcurrentHashMap.newKeySet();

    private VanillaWorldSpawns() {
    }

    /** Marks a mob Riftfall is placing itself; call before finalization. */
    public static void authorize(Entity entity) {
        AUTHORED.add(entity);
    }

    @Nullable
    private static ResourceKey<Level> dimension(LevelAccessor level) {
        return level instanceof ServerLevelAccessor server ? server.getLevel().dimension() : null;
    }

    /** The overworld, the Nether or the End; worldgen regions answer for the level they generate. */
    public static boolean vanillaWorld(LevelAccessor level) {
        var dimension = dimension(level);
        return dimension != null && DIMENSIONS.contains(dimension);
    }

    public static boolean hostile(EntityType<?> type, @Nullable Entity entity) {
        String namespace = BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace();
        boolean enemy = entity instanceof Enemy || ENEMIES.contains(type);
        return VanillaWorldSpawnRule.hostile(namespace, type.getCategory() == MobCategory.MONSTER, enemy,
                type.is(ALLOWED), type.is(SUPPRESSED));
    }

    static boolean refuses(LevelAccessor level, EntityType<?> type, @Nullable Entity entity, @Nullable MobSpawnType spawnType) {
        if (!ENFORCED || !VanillaWorldSpawnRule.refusedType(spawnType) || !vanillaWorld(level)) return false;
        if (entity != null && AUTHORED.contains(entity)) return false;
        return hostile(type, entity);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void placement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (refuses(event.getLevel(), event.getEntityType(), null, event.getSpawnType())) {
            count("placement", event.getLevel(), event.getSpawnType(), event.getEntityType());
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void finalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        if (mob instanceof Enemy) ENEMIES.add(mob.getType());
        if (!refuses(event.getLevel(), mob.getType(), mob, event.getSpawnType())) return;
        count("finalize", event.getLevel(), event.getSpawnType(), mob.getType());
        // Cancelling alone skips initialization and still adds the mob; the flag is what refuses it.
        if (!mob.isAddedToLevel()) mob.setSpawnCancelled(true);
        else mob.discard();
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void join(EntityJoinLevelEvent event) {
        // A finalizeSpawn called directly skips the event but still records its type on the mob; worldgen
        // chunk entities join here too, not as loaded from disk, with the type saved in their NBT.
        if (event.loadedFromDisk() || !(event.getEntity() instanceof Mob mob)) return;
        if (refuses(event.getLevel(), mob.getType(), mob, mob.getSpawnType())) {
            count("join", event.getLevel(), mob.getSpawnType(), mob.getType());
            event.setCanceled(true);
        }
    }

    private static void count(String layer, LevelAccessor level, @Nullable MobSpawnType spawnType, EntityType<?> type) {
        var dimension = dimension(level);
        String key = layer + "|" + (dimension == null ? "?" : dimension.location().getPath()) + "|" + spawnType
                + "|" + BuiltInRegistries.ENTITY_TYPE.getKey(type);
        REFUSALS.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    /** Refusals since boot, keyed {@code layer|dimension|spawnType|entity}. */
    public static Map<String, Long> refusals() {
        var out = new TreeMap<String, Long>();
        REFUSALS.forEach((key, count) -> out.put(key, count.sum()));
        return out;
    }

    /** Entries the biome modifier took out, by biome. */
    public static Map<String, List<String>> stripped() {
        return new TreeMap<>(STRIPPED);
    }

    /** Biomes the modifier gave vanilla endermen back. */
    public static Set<String> restored() {
        return Set.copyOf(RESTORED);
    }

    /**
     * {@code riftfall:strip_modded_hostiles}, one file per dimension's biome tag: runs last, so it sees
     * every other mod's additions.
     */
    public record BiomeStrip(HolderSet<Biome> biomes) implements BiomeModifier {
        public static final MapCodec<BiomeStrip> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Biome.LIST_CODEC.fieldOf("biomes").forGetter(BiomeStrip::biomes)).apply(instance, BiomeStrip::new));

        @Override
        public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
            if (phase != Phase.AFTER_EVERYTHING || !ENFORCED || !biomes.contains(biome)) return;
            var spawns = builder.getMobSpawnSettings();
            var removed = new ArrayList<String>();
            boolean replacedEnderman = false;
            for (MobCategory category : MobCategory.values()) {
                for (var it = spawns.getSpawner(category).iterator(); it.hasNext(); ) {
                    EntityType<?> type = it.next().type;
                    if (!hostile(type, null)) continue;
                    it.remove();
                    spawns.removeSpawnCost(type);
                    ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                    removed.add(id.toString());
                    replacedEnderman |= id.getNamespace().equals(ENDERMAN_REPLACER);
                }
            }
            String key = biome.unwrapKey().map(k -> k.location().toString()).orElse("?");
            var monsters = spawns.getSpawner(MobCategory.MONSTER);
            if ((replacedEnderman || biome.is(ENDERMAN_REPLACED))
                    && monsters.stream().noneMatch(d -> d.type == EntityType.ENDERMAN)) {
                // The biome's own entry before any modifier (Nether endermen are weight 1, 4-4, with a
                // spawn cost in soul sand valley and warped forest); an overworld biome with none gets
                // vanilla's overworld entry, as on 11-08. Nowhere else gains endermen it never had.
                var original = biome.value().modifiableBiomeInfo().getOriginalBiomeInfo().mobSpawnSettings();
                var own = original.getMobs(MobCategory.MONSTER).unwrap().stream()
                        .filter(d -> d.type == EntityType.ENDERMAN).findFirst();
                boolean vanillaMonsters = monsters.stream().anyMatch(d ->
                        BuiltInRegistries.ENTITY_TYPE.getKey(d.type).getNamespace().equals("minecraft"));
                if (own.isPresent()) {
                    monsters.add(own.get());
                    var cost = original.getMobSpawnCost(EntityType.ENDERMAN);
                    if (cost != null) spawns.addMobCharge(EntityType.ENDERMAN, cost.charge(), cost.energyBudget());
                    RESTORED.add(key);
                } else if (vanillaMonsters && biome.is(BiomeTags.IS_OVERWORLD)) {
                    monsters.add(new MobSpawnSettings.SpawnerData(EntityType.ENDERMAN, 10, 1, 4));
                    RESTORED.add(key);
                }
            }
            if (!removed.isEmpty()) STRIPPED.put(key, List.copyOf(removed));
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
                    RiftfallCore.id("strip_modded_hostiles"), () -> BiomeStrip.CODEC);
        }
    }
}
