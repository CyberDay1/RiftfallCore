package com.cyberday1.riftfall.block.marker;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class NpcSpawnMarkers {
    private NpcSpawnMarkers() {}

    public static Map<NpcRole, DeferredBlock<NpcSpawnMarkerBlock>> registerAll(DeferredRegister.Blocks registry) {
        var blocks = new EnumMap<NpcRole, DeferredBlock<NpcSpawnMarkerBlock>>(NpcRole.class);
        for (NpcRole role : NpcRole.values()) {
            blocks.put(role, registry.register(role.blockId(), () -> new NpcSpawnMarkerBlock(role,
                    BlockBehaviour.Properties.of().strength(-1.0F, 3600000.0F).sound(SoundType.STONE)
                            .noLootTable().pushReaction(PushReaction.BLOCK))));
        }
        return Collections.unmodifiableMap(blocks);
    }
}
