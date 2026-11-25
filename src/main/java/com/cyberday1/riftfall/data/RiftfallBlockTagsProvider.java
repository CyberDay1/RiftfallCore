package com.cyberday1.riftfall.data;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.barrel.RiftBarrels;
import com.cyberday1.riftfall.init.RiftfallBlocks;
import com.cyberday1.riftfall.init.RiftfallTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

/**
 * Block tags for Riftfall Core.
 *
 * <h2>What counts as copper</h2>
 * The ask was "all types of copper blocks", and the line is drawn at <strong>full
 * cubes</strong>: five shapes &mdash; block, cut, chiselled, grate, bulb &mdash; across four
 * oxidation stages, waxed and unwaxed, which is exactly 40 blocks. Slabs, stairs, doors and
 * trapdoors are copper too and are deliberately out, because a frame is a wall and those leave holes
 * in it; a portal that lit through a half-open trapdoor would be a shape the detector had no honest
 * answer for.
 *
 * <p>
 * Also out: {@code copper_ore}, {@code deepslate_copper_ore} and {@code raw_copper_block}. The ores
 * are stone with copper in them rather than copper, and are placed by worldgen in quantity, which
 * would let a rift be lit against a cave wall. {@code raw_copper_block} is the one judgement call —
 * it is a full cube of copper by any reading, but it is unrefined, and a portal frame reads as
 * something built rather than something dug up.
 *
 * <p>
 * The 40 are written out one by one rather than assembled from name fragments. The vanilla naming is
 * not regular — the plain block is {@code copper_block} while the weathered one is
 * {@code weathered_copper}, with no {@code _block} — so a generated set would silently miss members,
 * and a tag that is quietly short is a portal that quietly will not light.
 */
public class RiftfallBlockTagsProvider extends BlockTagsProvider {

    public RiftfallBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                                     ExistingFileHelper existingFileHelper) {
        super(output, lookup, RiftfallCore.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookup) {
        tag(RiftfallTags.PORTAL_FRAME).add(
                // Copper block
                Blocks.COPPER_BLOCK, Blocks.EXPOSED_COPPER, Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER,
                Blocks.WAXED_COPPER_BLOCK, Blocks.WAXED_EXPOSED_COPPER, Blocks.WAXED_WEATHERED_COPPER,
                Blocks.WAXED_OXIDIZED_COPPER,
                // Cut copper
                Blocks.CUT_COPPER, Blocks.EXPOSED_CUT_COPPER, Blocks.WEATHERED_CUT_COPPER,
                Blocks.OXIDIZED_CUT_COPPER,
                Blocks.WAXED_CUT_COPPER, Blocks.WAXED_EXPOSED_CUT_COPPER, Blocks.WAXED_WEATHERED_CUT_COPPER,
                Blocks.WAXED_OXIDIZED_CUT_COPPER,
                // Chiseled copper
                Blocks.CHISELED_COPPER, Blocks.EXPOSED_CHISELED_COPPER, Blocks.WEATHERED_CHISELED_COPPER,
                Blocks.OXIDIZED_CHISELED_COPPER,
                Blocks.WAXED_CHISELED_COPPER, Blocks.WAXED_EXPOSED_CHISELED_COPPER,
                Blocks.WAXED_WEATHERED_CHISELED_COPPER, Blocks.WAXED_OXIDIZED_CHISELED_COPPER,
                // Copper grate
                Blocks.COPPER_GRATE, Blocks.EXPOSED_COPPER_GRATE, Blocks.WEATHERED_COPPER_GRATE,
                Blocks.OXIDIZED_COPPER_GRATE,
                Blocks.WAXED_COPPER_GRATE, Blocks.WAXED_EXPOSED_COPPER_GRATE,
                Blocks.WAXED_WEATHERED_COPPER_GRATE, Blocks.WAXED_OXIDIZED_COPPER_GRATE,
                // Copper bulb
                Blocks.COPPER_BULB, Blocks.EXPOSED_COPPER_BULB, Blocks.WEATHERED_COPPER_BULB,
                Blocks.OXIDIZED_COPPER_BULB,
                Blocks.WAXED_COPPER_BULB, Blocks.WAXED_EXPOSED_COPPER_BULB,
                Blocks.WAXED_WEATHERED_COPPER_BULB, Blocks.WAXED_OXIDIZED_COPPER_BULB);

        // Barrels are wood and break like wood. They drop nothing either way; the tag is here so the
        // break is quick and the tool takes the durability a player expects it to.
        tag(BlockTags.MINEABLE_WITH_AXE).add(RiftBarrels.all());

        // Ore-room filler mines like deepslate: any pickaxe.
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(RiftfallBlocks.RIFTSTONE.get());
        // Glacial's crystal ore mines like Crystalcraft's: pickaxe, iron or better.
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(RiftfallBlocks.ICE_OPAL_ORE.get());
        tag(BlockTags.NEEDS_IRON_TOOL).add(RiftfallBlocks.ICE_OPAL_ORE.get());
        tag(net.neoforged.neoforge.common.Tags.Blocks.ORES).add(RiftfallBlocks.ICE_OPAL_ORE.get());
        // The stone- and deepslate-hosted crystal ores mine the same way.
        for (var ore : RiftfallBlocks.CRYSTAL_HOST_ORES.values()) {
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ore.get());
            tag(BlockTags.NEEDS_IRON_TOOL).add(ore.get());
            tag(net.neoforged.neoforge.common.Tags.Blocks.ORES).add(ore.get());
        }
    }

    /** Keeps the generator's provider names unique the way the two blockstate providers already have to. */
    @Override
    public String getName() {
        return "Block Tags: " + RiftfallCore.MODID;
    }
}
