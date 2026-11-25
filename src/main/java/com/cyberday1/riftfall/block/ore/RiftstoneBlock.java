package com.cyberday1.riftfall.block.ore;

import com.cyberday1.riftfall.worldgen.ore.RiftOres;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * The filler of an ore room's mass, drawn and heard as vanilla stone or deepslate ({@code host}, the theme's crystal
 * host). Mines like deepslate and drops no block; worldgen places it {@code natural=true}, the only state whose
 * loot can roll a rune, so a placed item farms nothing.
 */
public final class RiftstoneBlock extends Block {

    public static final BooleanProperty NATURAL = BooleanProperty.create("natural");
    public static final EnumProperty<RiftOres.Host> HOST = EnumProperty.create("host", RiftOres.Host.class);

    public RiftstoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NATURAL, false).setValue(HOST, RiftOres.Host.STONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NATURAL, HOST);
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return state.getValue(HOST) == RiftOres.Host.DEEPSLATE ? SoundType.DEEPSLATE : SoundType.STONE;
    }
}
