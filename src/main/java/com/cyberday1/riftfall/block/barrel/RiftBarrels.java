package com.cyberday1.riftfall.block.barrel;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Registration and per-tier lookup for the four loot barrels. */
public final class RiftBarrels {

    private static Map<BarrelTier, DeferredBlock<RiftBarrelBlock>> blocks = Map.of();

    private RiftBarrels() {
    }

    public static Map<BarrelTier, DeferredBlock<RiftBarrelBlock>> registerAll(DeferredRegister.Blocks registry) {
        if (!blocks.isEmpty()) {
            throw new IllegalStateException("Barrel blocks have already been registered");
        }
        EnumMap<BarrelTier, DeferredBlock<RiftBarrelBlock>> registered = new EnumMap<>(BarrelTier.class);
        for (BarrelTier tier : BarrelTier.ALL) {
            registered.put(tier, registry.register(tier.blockId(),
                    () -> new RiftBarrelBlock(properties(), tier)));
        }
        blocks = Collections.unmodifiableMap(registered);
        return blocks;
    }

    /**
     * Wood, and no loot table.
     *
     * <p>
     * {@code noLootTable} is the mechanism behind "breaking one drops nothing": a barrel's contents
     * belong to whoever opened it, so there is nothing that could honestly fall out of it, and the
     * block itself is a fixture of a dungeon that will not exist tomorrow.
     */
    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5F)
                .sound(SoundType.WOOD)
                // Inset staves and an open mouth expose neighbouring block faces.
                .noOcclusion()
                .noLootTable();
    }

    public static DeferredBlock<RiftBarrelBlock> holder(BarrelTier tier) {
        DeferredBlock<RiftBarrelBlock> held = blocks.get(tier);
        if (held == null) {
            throw new IllegalStateException("Barrel blocks have not been registered yet");
        }
        return held;
    }

    public static RiftBarrelBlock block(BarrelTier tier) {
        return holder(tier).get();
    }

    /** Every barrel block, in tier order — for the block-entity type and the model swap. */
    public static Block[] all() {
        Block[] all = new Block[BarrelTier.ALL.length];
        for (int index = 0; index < all.length; index++) {
            all[index] = block(BarrelTier.ALL[index]);
        }
        return all;
    }
}
