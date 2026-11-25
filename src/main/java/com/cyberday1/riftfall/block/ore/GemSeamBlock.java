package com.cyberday1.riftfall.block.ore;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Authoring marker in a gem-vein template. The theme's {@code riftfall:gem_vein} decorator turns each
 * one into a gem ore, an amethyst cluster or a light when the prop is placed; none survives into a rift.
 */
public final class GemSeamBlock extends Block {

    /** What a seam becomes. */
    public enum Role implements StringRepresentable {
        /** A solid cell on the pocket wall: a gem ore, or the stone beside it. */
        GEM("gem"),
        /** An air cell on the pocket floor: an amethyst cluster, or air. */
        CLUSTER("cluster"),
        /** The theme's light block. */
        LIGHT("light");

        private final String name;

        Role(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Role> ROLE = EnumProperty.create("role", Role.class);

    public GemSeamBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ROLE, Role.GEM));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROLE);
    }

    public static Role role(BlockState state) {
        return state.getValue(ROLE);
    }
}
