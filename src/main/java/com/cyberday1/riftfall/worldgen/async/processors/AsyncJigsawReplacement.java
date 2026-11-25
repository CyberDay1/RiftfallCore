package com.cyberday1.riftfall.worldgen.async.processors;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/** Jigsaw replacement needs the authored NBT, before section processing drops it. */
public final class AsyncJigsawReplacement {
    private AsyncJigsawReplacement() {}
    public static StructureBlockInfo replace(StructureBlockInfo info) {
        if (!info.state().is(Blocks.JIGSAW) || info.nbt() == null) return info;
        try {
            var state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(),
                    info.nbt().getString("final_state"), true).blockState();
            return state.is(Blocks.STRUCTURE_VOID) ? null : new StructureBlockInfo(info.pos(), state, null);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalArgumentException("Invalid jigsaw final_state at " + info.pos(), e);
        }
    }
}
