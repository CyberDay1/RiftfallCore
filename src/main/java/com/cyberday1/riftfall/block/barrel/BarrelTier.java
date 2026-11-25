package com.cyberday1.riftfall.block.barrel;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The four grades of loot barrel, worst to best.
 *
 * <p>
 * The order is the declaration order and is load-bearing: it is the order the weighted pool walks
 * and the order the take-up report prints in, so a reader of either can see the ramp.
 *
 * <p>
 * A tier is a fact about one barrel and never about the rift it is standing in. Nothing here knows
 * the key's tier, and nothing that draws one is allowed to ask &mdash; the same rule
 * {@code MobScaling} and {@code BossScaling} keep from the other direction.
 */
public enum BarrelTier {

    SHABBY("shabby", "Shabby Rift Barrel"),
    WOOD("wood", "Rift Barrel"),
    IRON("iron", "Banded Rift Barrel"),
    GOLD("gold", "Gilded Rift Barrel");

    /** Cached, because {@code values()} copies and the placement loop runs per marker. */
    public static final BarrelTier[] ALL = values();

    private final String suffix;
    private final String displayName;
    private final String blockId;
    private final ResourceKey<LootTable> lootTable;
    private final ResourceKey<LootTable> shardTable;

    BarrelTier(String suffix, String displayName) {
        this.suffix = suffix;
        this.displayName = displayName;
        this.blockId = "rift_barrel_" + suffix;
        this.lootTable = ResourceKey.create(Registries.LOOT_TABLE, RiftfallCore.id("chests/" + blockId));
        this.shardTable = ResourceKey.create(Registries.LOOT_TABLE, RiftfallCore.id("shards/barrel_" + suffix));
    }

    public String blockId() {
        return blockId;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * The one table a share is drawn from. Gear and the rift's crystal are weighted entries inside
     * it &mdash; {@code riftfall:gear} and {@code riftfall:theme_crystal} &mdash; so the whole ramp
     * from shabby to gilded is four numbers per file and none of them live in Java.
     */
    public ResourceKey<LootTable> lootTable() {
        return lootTable;
    }

    /**
     * Rolled once per share, beside the draws, so shards come per barrel and not per draw. Not under {@code chests/}:
     * Apotheosis' {@code affix_loot} matches that path and would add gear to every shard roll.
     */
    public ResourceKey<LootTable> shardTable() {
        return shardTable;
    }

    /** {@code block/rift_barrel_gold_top_open_looted} and friends, for the model provider. */
    public String texture(String face, boolean looted) {
        return "block/rift_barrel_" + suffix + "_" + face + (looted ? "_looted" : "");
    }
}
