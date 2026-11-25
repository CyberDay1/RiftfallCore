package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.block.barrel.RiftBarrelBlock;
import com.cyberday1.riftfall.block.barrel.RiftBarrelBlockEntity;
import com.cyberday1.riftfall.block.barrel.RiftBarrels;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Makes a rift barrel look emptied to the player who emptied it, and untouched to everybody else.
 *
 * <p>
 * Both halves of every pair are ordinary blockstate variants, baked by the ordinary loader with the
 * rotation their facing calls for. That is the whole reason {@link RiftBarrelBlock#LOOTED} exists as
 * a property: a side-loaded model is baked {@code X0_Y0} and nothing else, so a barrel facing east
 * would have had an upright looted twin. Once the pairs are baked, the plain one in the map is
 * swapped for a wrapper that holds both and asks the block entity which to draw.
 *
 * <p>
 * The decision is per quad-fetch rather than per chunk-section, so two players standing in the same
 * room see two different barrels: each client's own {@code ModelData} carries its own answer.
 */
final class RiftBarrelModels {

    private RiftBarrelModels() {
    }

    static void pairUp(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (Block block : RiftBarrels.all()) {
            for (BlockState plainState : block.getStateDefinition().getPossibleStates()) {
                if (plainState.getValue(RiftBarrelBlock.LOOTED)) {
                    continue;
                }
                ModelResourceLocation plainKey = BlockModelShaper.stateToModelLocation(plainState);
                BakedModel plain = models.get(plainKey);
                BakedModel looted = models.get(BlockModelShaper.stateToModelLocation(
                        plainState.setValue(RiftBarrelBlock.LOOTED, Boolean.TRUE)));
                if (plain != null && looted != null) {
                    models.put(plainKey, new PerPlayerBarrel(plain, looted));
                }
            }
        }
    }

    private static final class PerPlayerBarrel extends BakedModelWrapper<BakedModel> {

        private final BakedModel looted;

        private PerPlayerBarrel(BakedModel plain, BakedModel looted) {
            super(plain);
            this.looted = looted;
        }

        private BakedModel pick(ModelData data) {
            return Boolean.TRUE.equals(data.get(RiftBarrelBlockEntity.LOOTED)) ? looted : originalModel;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                        ModelData data, @Nullable RenderType renderType) {
            return pick(data).getQuads(state, side, random, data, renderType);
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            return pick(data).getParticleIcon(data);
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return pick(data).getRenderTypes(state, random, data);
        }
    }
}
