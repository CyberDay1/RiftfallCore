package com.cyberday1.riftfall.mob;

import com.cyberday1.riftfall.home.ShatteredHomeLevels;
import com.cyberday1.riftfall.rift.RiftLevels;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import java.util.Set;

/** Spore supplies encounter bodies and attacks; Riftfall owns their population and terrain. */
public final class SporeEncounters {
    private static final String AUTHORED = "riftfall:spore_encounter";
    private static final Set<String> ROSTER = Set.of("inf_human", "inf_villager", "spitter",
            "leaper", "brute", "stalker", "slasher");

    private SporeEncounters() {}

    public static boolean protectedLevel(LevelAccessor level) {
        return level instanceof ServerLevel server && (RiftLevels.isRiftWorld(server)
                || ShatteredHomeLevels.isShatteredHome(server.dimension()));
    }

    public static boolean restricted(Entity entity) {
        return protectedLevel(entity.level()) || entity.getPersistentData().getBoolean(AUTHORED);
    }

    /** Called before finalization: native Spore initialization can already start evolution. */
    public static void authorize(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id.getNamespace().equals("spore") && ROSTER.contains(id.getPath()))
            entity.getPersistentData().putBoolean(AUTHORED, true);
    }

    public static boolean infection(ResourceLocation id) {
        return id.getNamespace().equals("spore")
                && (id.getPath().equals("mycelium_ef") || id.getPath().equals("madness"));
    }

    /** Also clears infections restored from saves before native death conversion runs. */
    public static void clearInfection(LivingEntity entity) {
        if (!restricted(entity) || entity.getActiveEffects().isEmpty()) return;
        for (var effect : entity.getActiveEffects().stream().toList()) {
            if (infection(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value())))
                entity.removeEffect(effect.getEffect());
        }
    }

    public static void register() {
        SporeCombat.register();
        SporeContainment.register();
        NeoForge.EVENT_BUS.addListener(SporeEncounters::explicitSpawn);
        // Refuse colonies before Spore registers them in its global hive bookkeeping.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SporeEncounters::join);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SporeEncounters::effect);
    }

    private static void explicitSpawn(FinalizeSpawnEvent event) {
        if (!protectedLevel(event.getLevel())) return;
        // Developer commands/eggs may place curated forms; native reinforcements never qualify.
        switch (event.getSpawnType()) {
            case COMMAND, SPAWN_EGG, DISPENSER -> authorize(event.getEntity());
            default -> { }
        }
    }

    private static void join(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Projectile projectile) SporeCombat.prepareProjectile(projectile);
        if (!protectedLevel(event.getLevel())) return;
        if (entity instanceof LivingEntity living) clearInfection(living);
        if (!BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals("spore")) return;
        // Native addFreshEntity calls skip FinalizeSpawnEvent. Block summons, scents, corpses,
        // mounds and conversions here, while retaining ranged attacks and saved authored mobs.
        if (!(entity instanceof Projectile) && !entity.getPersistentData().getBoolean(AUTHORED))
            event.setCanceled(true);
    }

    private static void effect(MobEffectEvent.Applicable event) {
        if (restricted(event.getEntity()) && infection(BuiltInRegistries.MOB_EFFECT.getKey(
                event.getEffectInstance().getEffect().value())))
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }
}
