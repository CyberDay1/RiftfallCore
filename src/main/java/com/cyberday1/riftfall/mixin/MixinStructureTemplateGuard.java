package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.home.TilePlacementGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Home tiles and gallery stamps survive a block that throws on placement: see {@link TilePlacementGuard}. */
@Mixin(StructureTemplate.class)
public abstract class MixinStructureTemplateGuard {

    @WrapOperation(method = "placeInWorld", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerLevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean riftfall$guardTileBlock(ServerLevelAccessor level, BlockPos pos, BlockState state, int flags,
                                            Operation<Boolean> original) {
        if (!TilePlacementGuard.active()) return original.call(level, pos, state, flags);
        BlockPos at = pos.immutable();
        return TilePlacementGuard.write(() -> original.call(level, at, state, flags),
                () -> level.setBlock(at, Blocks.AIR.defaultBlockState(), 2 | 16),
                () -> BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " at " + at.toShortString());
    }

    @WrapOperation(method = "placeInWorld", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;updateShapeAtEdge(Lnet/minecraft/world/level/LevelAccessor;ILnet/minecraft/world/phys/shapes/DiscreteVoxelShape;III)V"))
    private void riftfall$guardTileEdges(LevelAccessor level, int flags, DiscreteVoxelShape shape, int x, int y, int z,
                                         Operation<Void> original) {
        if (!TilePlacementGuard.active()) { original.call(level, flags, shape, x, y, z); return; }
        try {
            original.call(level, flags, shape, x, y, z);
        } catch (RuntimeException e) {
            // The blocks are down; only some edge shapes stay stale, which the next neighbour update corrects.
            LogUtils.getLogger().error("Home tile edge shape update threw; the tile is kept", e);
        }
    }
}
