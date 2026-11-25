package com.cyberday1.riftfall.data;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.processor.ProcessorBlockFamily;
import com.cyberday1.riftfall.block.processor.ProcessorBlocks;
import com.cyberday1.riftfall.block.processor.ProcessorSlot;
import com.cyberday1.riftfall.block.processor.ProcessorVariant;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Blockstate + block model + item model generation for all 180 processor placeholders.
 *
 * <p>
 * Every family shares one 16x16 texture ({@code riftfall:block/processor_block_&lt;N&gt;}) across all
 * twelve shapes. That is deliberate for a placeholder: the whole point of a material slot is that
 * it reads as <em>one</em> material regardless of shape. See
 * {@code assets/riftfall/TEXTURES.md} for the art that should replace it.
 */
public class ProcessorBlockStateProvider extends BlockStateProvider {

    /** Glass, panes and trapdoors want alpha, so their models opt into the cutout layer. */
    private static final String CUTOUT = "minecraft:cutout";

    public ProcessorBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, RiftfallCore.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        for (ProcessorBlockFamily family : ProcessorBlocks.families()) {
            family(family);
        }
    }

    private void family(ProcessorBlockFamily family) {
        ProcessorSlot slot = family.slot();
        ResourceLocation texture = modLoc("block/" + slot.familyId());

        // Base full cube. Its model doubles as the "double slab" model.
        ModelFile cube = models().cubeAll(slot.familyId(), texture);
        simpleBlockWithItem(family.block(ProcessorVariant.BASE), cube);

        slabBlock((SlabBlock) family.block(ProcessorVariant.SLAB), cube.getLocation(), texture);
        itemFromBlockModel(family, ProcessorVariant.SLAB, slot.blockId(ProcessorVariant.SLAB));

        stairsBlock((StairBlock) family.block(ProcessorVariant.STAIRS), texture);
        itemFromBlockModel(family, ProcessorVariant.STAIRS, slot.blockId(ProcessorVariant.STAIRS));

        wallBlock((WallBlock) family.block(ProcessorVariant.WALL), texture);
        itemModels().wallInventory(slot.blockId(ProcessorVariant.WALL), texture);

        fenceBlock((FenceBlock) family.block(ProcessorVariant.FENCE), texture);
        itemModels().fenceInventory(slot.blockId(ProcessorVariant.FENCE), texture);

        fenceGateBlock((FenceGateBlock) family.block(ProcessorVariant.FENCE_GATE), texture);
        itemFromBlockModel(family, ProcessorVariant.FENCE_GATE, slot.blockId(ProcessorVariant.FENCE_GATE));

        buttonBlock((ButtonBlock) family.block(ProcessorVariant.BUTTON), texture);
        itemModels().buttonInventory(slot.blockId(ProcessorVariant.BUTTON), texture);

        pressurePlateBlock((PressurePlateBlock) family.block(ProcessorVariant.PRESSURE_PLATE), texture);
        itemFromBlockModel(family, ProcessorVariant.PRESSURE_PLATE, slot.blockId(ProcessorVariant.PRESSURE_PLATE));

        // orientable=false: the placeholder texture has no "up" so the simpler template is fine,
        // and the trapdoor's facing/half/open properties are unaffected either way.
        trapdoorBlockWithRenderType((TrapDoorBlock) family.block(ProcessorVariant.TRAPDOOR), texture, false,
                CUTOUT);
        // The bottom model is the conventional inventory model for a trapdoor.
        itemModels().withExistingParent(slot.blockId(ProcessorVariant.TRAPDOOR),
                modLoc("block/" + slot.blockId(ProcessorVariant.TRAPDOOR) + "_bottom"));

        ModelFile glass = models().cubeAll(slot.blockId(ProcessorVariant.GLASS), texture)
                .renderType(CUTOUT);
        simpleBlockWithItem(family.block(ProcessorVariant.GLASS), glass);

        paneBlockWithRenderType((IronBarsBlock) family.block(ProcessorVariant.GLASS_PANE), texture, texture,
                CUTOUT);
        // Panes are held as a flat sprite in the inventory, exactly like vanilla glass panes.
        itemModels().singleTexture(slot.blockId(ProcessorVariant.GLASS_PANE),
                mcLoc("item/generated"), "layer0", texture);

        // Same texture on side and end: the pillar exists for its axis property, not its look.
        axisBlock((RotatedPillarBlock) family.block(ProcessorVariant.DIRECTIONAL_PILLAR), texture, texture);
        itemFromBlockModel(family, ProcessorVariant.DIRECTIONAL_PILLAR,
                slot.blockId(ProcessorVariant.DIRECTIONAL_PILLAR));
    }

    /**
     * Item model whose parent is the block model of the same name — the default for anything that
     * renders as its block in the hand (slab, stairs, fence gate, pressure plate, pillar).
     */
    private void itemFromBlockModel(ProcessorBlockFamily family, ProcessorVariant variant, String modelName) {
        itemModels().withExistingParent(family.slot().blockId(variant), modLoc("block/" + modelName));
    }
}
