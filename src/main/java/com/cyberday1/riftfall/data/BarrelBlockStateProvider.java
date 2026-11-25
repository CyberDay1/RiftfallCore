package com.cyberday1.riftfall.data;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.barrel.BarrelTier;
import com.cyberday1.riftfall.block.barrel.RiftBarrelBlock;
import com.cyberday1.riftfall.block.barrel.RiftBarrels;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Blockstates and models for the four loot barrels.
 *
 * <p>
 * Four models per tier, because two boolean axes cross: the lid is open or shut, and the barrel is
 * drawn plain or emptied. The lid is a real blockstate and the emptied art is not &mdash; see
 * {@link RiftBarrelBlock} for why the {@code looted} property exists anyway &mdash; so the generated
 * file for each tier must carry 6 x 2 x 2 = 24 entries. A short one means a property went missing
 * and half the pairs the model swap looks for would not be there to find.
 *
 * <p>
 * The rotations are vanilla's barrel: {@code x} lays it on its side and {@code y} turns it, which
 * {@code directionalBlock} produces for the six-way facing property without being told.
 */
public class BarrelBlockStateProvider extends BlockStateProvider {

    public BarrelBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, RiftfallCore.MODID, existingFileHelper);
    }

    @Override
    public String getName() {
        return "Barrel Block States: " + RiftfallCore.MODID;
    }

    @Override
    protected void registerStatesAndModels() {
        for (BarrelTier tier : BarrelTier.ALL) {
            barrel(tier);
        }
    }

    private void barrel(BarrelTier tier) {
        ModelFile shut = model(tier, false, false);
        ModelFile open = model(tier, true, false);
        ModelFile shutLooted = model(tier, false, true);
        ModelFile openLooted = model(tier, true, true);

        getVariantBuilder(RiftBarrels.block(tier)).forAllStates(state -> {
            boolean lid = state.getValue(RiftBarrelBlock.OPEN);
            boolean looted = state.getValue(RiftBarrelBlock.LOOTED);
            Direction facing = state.getValue(RiftBarrelBlock.FACING);
            return ConfiguredModel.builder()
                    .modelFile(looted ? (lid ? openLooted : shutLooted) : (lid ? open : shut))
                    .rotationX(facing == Direction.DOWN ? 180 : facing.getAxis().isHorizontal() ? 90 : 0)
                    .rotationY(facing.getAxis().isVertical() ? 0 : ((int) facing.toYRot()) % 360)
                    .build();
        });

        // The item wears the shut, unlooted model: an item is nobody's barrel in particular.
        itemModels().withExistingParent(tier.blockId(), modLoc("block/" + tier.blockId()));
    }

    private ModelFile model(BarrelTier tier, boolean open, boolean looted) {
        String name = "block/" + tier.blockId() + (open ? "_open" : "") + (looted ? "_looted" : "");
        return models().cubeBottomTop(name,
                modLoc(tier.texture("side", looted)),
                modLoc(tier.texture("bottom", looted)),
                modLoc(tier.texture(open ? "top_open" : "top", looted)));
    }
}
