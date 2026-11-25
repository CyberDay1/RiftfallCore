package com.cyberday1.riftfall.worldgen.barrel;

import com.cyberday1.riftfall.RiftfallConfig;
import com.cyberday1.riftfall.block.barrel.BarrelTier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * How generous one rift is, decided once when it is made and then only read.
 *
 * <p>
 * Two independent rolls happen at every chest marker: whether there is a barrel there at all, and
 * which of the four it is. They are separate on purpose &mdash; a key modifier that makes a rift
 * richer and one that makes it better are different promises, and a single table would have made
 * them the same number.
 *
 * <h2>Why this is a snapshot and not a config read</h2>
 * The placement code never touches {@link RiftfallConfig}. It reads one of these, taken at the
 * moment the rift was created, which buys two things. An operator editing the file mid-session
 * cannot make the second half of a dungeon richer than the first, and, when key modifiers land,
 * "this key opens rifts with twice the barrels" is a value that gets bent here rather than a branch
 * threaded through worldgen.
 *
 * <h2>What is deliberately absent</h2>
 * The rift's tier. A tier-9 key buys a bigger dungeon with harder things in it, and this is neither;
 * a barrel's grade is drawn from the same pool at every tier, the way {@code MobScaling} and
 * {@code BossScaling} take a tier and this does not.
 */
public record BarrelRules(double chance, int shabby, int wood, int iron, int gold) {

    /** No barrels. What a rift gets when the pool is empty or the gate is shut. */
    public static final BarrelRules NONE = new BarrelRules(0.0D, 0, 0, 0, 0);

    /**
     * Keeps the barrel rolls off the stream of numbers the layout and the palettes are drawn from,
     * so changing the gate cannot change which rooms a seed builds.
     */
    private static final long BARREL_SALT = 0x4B0BB1E5EDL;

    public static BarrelRules fromConfig() {
        return new BarrelRules(
                RiftfallConfig.BARREL_SPAWN_CHANCE.get(),
                RiftfallConfig.BARREL_WEIGHT_SHABBY.get(),
                RiftfallConfig.BARREL_WEIGHT_WOOD.get(),
                RiftfallConfig.BARREL_WEIGHT_IRON.get(),
                RiftfallConfig.BARREL_WEIGHT_GOLD.get());
    }

    public int weight(BarrelTier tier) {
        return switch (tier) {
            case SHABBY -> shabby;
            case WOOD -> wood;
            case IRON -> iron;
            case GOLD -> gold;
        };
    }

    public int totalWeight() {
        return shabby + wood + iron + gold;
    }

    /**
     * What stands at one marker, or null for nothing.
     *
     * <p>
     * Seeded from the rift and the block position rather than from a running random, so the answer
     * does not depend on how many markers were asked about first. Chunks are generated out of order
     * and on several threads, and a rift regenerated after a restart has to come out the same.
     */
    public @Nullable BarrelTier resolve(long riftSeed, BlockPos pos) {
        if (chance <= 0.0D) {
            return null;
        }
        RandomSource random = randomAt(riftSeed, pos);
        return random.nextDouble() < chance ? draw(random) : null;
    }

    /** The weighted pool, walked in tier order. Null when every weight is zero. */
    public @Nullable BarrelTier draw(RandomSource random) {
        int total = totalWeight();
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (BarrelTier tier : BarrelTier.ALL) {
            roll -= weight(tier);
            if (roll < 0) {
                return tier;
            }
        }
        // Unreachable while the weights sum to total; returning the last is cheaper than asserting.
        return BarrelTier.ALL[BarrelTier.ALL.length - 1];
    }

    public static RandomSource randomAt(long riftSeed, BlockPos pos) {
        return RandomSource.create(riftSeed ^ BARREL_SALT
                ^ pos.getX() * 341873128712L
                ^ pos.getY() * 132897987541L
                ^ pos.getZ() * 6364136223846793005L);
    }
}
