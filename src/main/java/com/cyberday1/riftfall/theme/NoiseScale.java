package com.cyberday1.riftfall.theme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.List;

/**
 * How fast the substitution noise varies per axis. Written as {@code [x, y, z]}.
 *
 * <p>
 * Smaller numbers mean larger blotches. The default 0.075 puts a patch boundary roughly every
 * dozen blocks, which is about the size of one wall segment — small enough to read as variation,
 * large enough that a single wall is mostly one material. Anything above ~0.3 degenerates into the
 * per-block confetti that coherent noise exists to avoid.
 *
 * @param x per-block step along X fed to the simplex sampler
 * @param y per-block step along Y
 * @param z per-block step along Z
 */
public record NoiseScale(double x, double y, double z) {

    public static final NoiseScale DEFAULT = new NoiseScale(0.075D, 0.075D, 0.075D);

    public static final Codec<NoiseScale> CODEC = Codec.DOUBLE.listOf().comapFlatMap(
            values -> values.size() == 3
                    ? DataResult.success(new NoiseScale(values.get(0), values.get(1), values.get(2)))
                    : DataResult.error(() -> "noise_scale must be exactly 3 numbers [x, y, z], got " + values.size()),
            scale -> List.of(scale.x(), scale.y(), scale.z()));
}
