package com.cyberday1.riftfall.worldgen.async.processors;

import com.cyberday1.riftfall.block.processor.ProcessorBlocks;
import com.cyberday1.riftfall.theme.NoiseScale;
import com.cyberday1.riftfall.theme.ResolvedTheme;
import com.cyberday1.riftfall.worldgen.async.BakedRoomContext;
import com.cyberday1.riftfall.worldgen.async.SectionProcessor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;


/**
 * Async rewrite of {@code ThemeSubstitutionProcessor} as a pure function over flat arrays.
 *
 * <p>
 * Replaces placeholder blocks with themed materials using coherent simplex noise, copying
 * blockstate properties without iterator allocation. Decorators are intentionally excluded;
 * they run in a separate post-pass.
 */
public final class AsyncThemeSubstitution implements SectionProcessor {

    @Override
    public void process(BlockState[] blocks, net.minecraft.core.SectionPos sectionPos, BakedRoomContext ctx) {
        ResolvedTheme theme = ctx.resolvedTheme();
        NoiseScale scale = theme.noiseScale();
        long noiseSeed = ctx.worldSeed() + theme.seedAdjustment();
        SimplexNoise noise = new SimplexNoise(RandomSource.create(noiseSeed));

        int baseX = sectionPos.minBlockX();
        int baseZ = sectionPos.minBlockZ();
        int baseY = sectionPos.minBlockY();

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = (x & 15) | ((z & 15) << 4) | ((y & 15) << 8);
                    BlockState input = blocks[index];
                    if (input == null) continue;
                    ProcessorBlocks.Placeholder placeholder = ProcessorBlocks.identify(input.getBlock());
                    if (placeholder == null) {
                        continue;
                    }

                    double sample = theme.size(placeholder.slot()) == 1
                            ? 0.0D
                            : sampleNoise(noise, scale, baseX + x, baseY + y, baseZ + z);

                    BlockState output = theme.state(placeholder.slot(), placeholder.variant(), sample);
                    blocks[index] = copyProperties(input, output);
                }
            }
        }
        // RoomRasterizer runs decorators over the complete processed template before clipping.
    }

    private static double sampleNoise(SimplexNoise noise, NoiseScale scale, int x, int y, int z) {
        double value = noise.getValue(x * scale.x(), y * scale.y(), z * scale.z());
        return Math.min(Math.abs(value), 0.999999D);
    }

    /**
     * Copies every property the input carries onto the output where the output understands it.
     * Uses indexed access via getProperties() iteration — unavoidable since Property is generic,
     * but avoids any wrapper or stream allocation.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copyProperties(BlockState from, BlockState to) {
        BlockState result = to;
        for (Property<?> property : from.getProperties()) {
            if (result.hasProperty(property)) {
                result = result.setValue((Property) property, from.getValue(property));
            }
        }
        return result;
    }
}