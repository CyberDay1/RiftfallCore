package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.barrel.RiftBarrelBlockEntity;
import com.cyberday1.riftfall.block.barrel.RiftBarrels;
import com.cyberday1.riftfall.block.breaker.WorldBreakerBlockEntity;
import com.cyberday1.riftfall.block.forge.KeyForgeBlockEntity;
import com.cyberday1.riftfall.block.marker.MarkerKind;
import com.cyberday1.riftfall.block.marker.RiftMobSpawnerBlockEntity;
import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import com.cyberday1.riftfall.block.holycore.HolyCoreBlockEntity;
import com.cyberday1.riftfall.block.resurrection.WorldResurrectionMachineBlockEntity;
import com.cyberday1.riftfall.rift.RiftTearBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entity registry for Riftfall Core. */
public final class RiftfallBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RiftfallCore.MODID);

    /**
     * The key forge's held shards.
     *
     * <p>
     * {@code Builder.of(...).build(null)} is the shape every vanilla type uses: the null is the
     * datafixer type, which only Mojang's own blocks have.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KeyForgeBlockEntity>> KEY_FORGE =
            BLOCK_ENTITIES.register("key_forge", () -> BlockEntityType.Builder
                    .of(KeyForgeBlockEntity::new, RiftfallBlocks.KEY_FORGE.get())
                    .build(null));

    /**
     * The proximity check behind every mob in a rift.
     *
     * <p>
     * The block is asked of {@link RiftfallMarkers} rather than of a field, because the markers are
     * registered through a map keyed by {@link MarkerKind} rather than as named holders. This runs
     * during the block-entity registry event, which is after the block one, so the marker is there.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftMobSpawnerBlockEntity>>
            RIFT_MOB_SPAWNER = BLOCK_ENTITIES.register("rift_mob_spawner", () -> BlockEntityType.Builder
                    .of(RiftMobSpawnerBlockEntity::new, RiftfallMarkers.block(MarkerKind.RIFT_MOB_SPAWNER))
                    .build(null));

    /**
     * The per-player contents of every loot barrel. One type across all four tiers: the grade is
     * which block is standing there, and the block entity behind them is the same in every respect.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftBarrelBlockEntity>>
            RIFT_BARREL = BLOCK_ENTITIES.register("rift_barrel", () -> BlockEntityType.Builder
                    .of(RiftBarrelBlockEntity::new, RiftBarrels.all())
                    .build(null));

    /**
     * The bottom block of every tear, and the only reason a tear has block entities at all: it is
     * what a {@code BlockEntityRenderer} attaches to, and the renderer is what lets the rip turn to
     * face whoever is looking at it.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftTearBlockEntity>>
            RIFT_TEAR = BLOCK_ENTITIES.register("rift_tear", () -> BlockEntityType.Builder
                    .of(RiftTearBlockEntity::new, RiftfallBlocks.RIFT_TEAR.get())
                    .build(null));

    /** Only while a key is in a World Breaker and until its shards are home: see {@link WorldBreakerBlockEntity}. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WorldBreakerBlockEntity>>
            WORLD_BREAKER = BLOCK_ENTITIES.register("world_breaker", () -> BlockEntityType.Builder
                    .of(WorldBreakerBlockEntity::new, RiftfallBlocks.WORLD_BREAKER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WorldResurrectionMachineBlockEntity>> WORLD_RESURRECTION_MACHINE =
            BLOCK_ENTITIES.register("world_resurrection_machine", () -> BlockEntityType.Builder
                    .of(WorldResurrectionMachineBlockEntity::new, RiftfallBlocks.WORLD_RESURRECTION_MACHINE.get())
                    .build(null));

    /** The world core at island center: tracks relic count and derives a 5-stage visual phase. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HolyCoreBlockEntity>> HOLY_CORE =
            BLOCK_ENTITIES.register("holy_core", () -> BlockEntityType.Builder
                    .of(HolyCoreBlockEntity::new, RiftfallBlocks.HOLY_CORE.get())
                    .build(null));

    private RiftfallBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
