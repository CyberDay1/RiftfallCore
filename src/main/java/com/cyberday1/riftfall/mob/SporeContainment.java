package com.cyberday1.riftfall.mob;

import com.cyberday1.riftfall.rift.RiftLevels;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Outside rifts Spore infects nothing; rifts keep {@link SporeEncounters} as they are.
 *
 * <p>Its two infection effects are refused, one restored from a save is cleared at join and before
 * Spore's death conversion, and a body only conversion or spread makes is refused at join
 * ({@link SporeContainmentRule}). Block spread, death growths and fished infected are mixins.
 * {@code -Driftfall.spore_containment=off} turns all of it off.
 */
public final class SporeContainment {
    public static final String PROPERTY = "riftfall.spore_containment";
    public static final boolean ENFORCED = !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "on"));

    private static final Map<String, LongAdder> REFUSALS = new ConcurrentHashMap<>();

    private SporeContainment() {
    }

    /** Any server level that is not a rift: the overworld, the Nether, the End, homes, other mods' dimensions. */
    public static boolean contained(@Nullable LevelAccessor level) {
        return ENFORCED && level instanceof ServerLevel server && !RiftLevels.isRiftWorld(server);
    }

    public static boolean contained(@Nullable Entity entity) {
        return entity != null && contained(entity.level());
    }

    public static void register() {
        // Before Spore registers a proto or protector in its saved data.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SporeContainment::join);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SporeContainment::effect);
    }

    /** Removes mycelium and madness; Spore's death conversion only reads mycelium. */
    public static void clear(LivingEntity entity) {
        if (!contained(entity) || entity.getActiveEffects().isEmpty()) return;
        for (var effect : entity.getActiveEffects().stream().toList()) {
            if (SporeEncounters.infection(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()))) {
                entity.removeEffect(effect.getEffect());
                count("cleared", BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()));
            }
        }
    }

    private static void effect(MobEffectEvent.Applicable event) {
        if (!contained(event.getEntity())) return;
        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(event.getEffectInstance().getEffect().value());
        if (SporeEncounters.infection(id)) {
            count("effect", id);
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    private static void join(EntityJoinLevelEvent event) {
        if (!contained(event.getLevel())) return;
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living) clear(living);
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (SporeContainmentRule.refusesJoin(id.getNamespace(), id.getPath(), entity instanceof Projectile,
                event.loadedFromDisk(), entity instanceof Mob mob ? mob.getSpawnType() : null)) {
            count("join", id);
            event.setCanceled(true);
        }
    }

    /** Counts a refusal from a mixin, keyed like the event layers. */
    public static void count(String layer, @Nullable ResourceLocation id) {
        REFUSALS.computeIfAbsent(id == null ? layer : layer + "|" + id, k -> new LongAdder()).increment();
    }

    /** Refusals since boot, keyed {@code layer|id} or just {@code layer} for block spread. */
    public static Map<String, Long> refusals() {
        var out = new TreeMap<String, Long>();
        REFUSALS.forEach((key, count) -> out.put(key, count.sum()));
        return out;
    }
}
