package com.cyberday1.riftfall.loot;

import com.cyberday1.riftfall.RiftfallConfig;
import com.cyberday1.riftfall.init.RiftfallLootEntries;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@code riftfall:barrel_gear_chance}: passes with probability {@code barrelGearScale}, read live.
 *
 * <p>
 * Used in a gear entry's {@code pay_if}, which is tested after the pool picked the entry. As an entry
 * {@code conditions} it would reroll the draw into crystals and supplies instead of emptying it.
 * At 1 or above it passes and at 0 it fails, neither drawing from the stream, so scale 1 is seed for seed.
 */
public final class BarrelGearChance implements LootItemCondition {

    public static final BarrelGearChance INSTANCE = new BarrelGearChance();
    public static final MapCodec<BarrelGearChance> CODEC = MapCodec.unit(INSTANCE);

    /** The optional {@code pay_if} field a gear-type entry carries. */
    public static final MapCodec<List<LootItemCondition>> PAY_IF =
            LootItemCondition.DIRECT_CODEC.listOf().optionalFieldOf("pay_if", List.of());

    private static volatile @Nullable Double probeScale;

    private BarrelGearChance() {
    }

    /** The live {@code barrelGearScale} (its default before the server config loads), or a pinned override. */
    public static double scale() {
        Double override = probeScale;
        return override != null ? override : configured();
    }

    /** The configured value, ignoring any probe pin. */
    public static double configured() {
        return RiftfallConfig.SPEC.isLoaded() ? RiftfallConfig.BARREL_GEAR_SCALE.get()
                : RiftfallConfig.BARREL_GEAR_SCALE.getDefault();
    }

    /** Probe and test use: pins {@link #scale()}; null restores the config. */
    public static void overrideScaleForProbe(@Nullable Double scale) {
        probeScale = scale;
    }

    /** The current pin, so a probe that sets its own can put it back. */
    public static @Nullable Double pinned() {
        return probeScale;
    }

    /** Whether a picked entry pays out: every {@code pay_if} condition, in order. */
    public static boolean pays(List<LootItemCondition> payIf, LootContext context) {
        for (LootItemCondition condition : payIf) {
            if (!condition.test(context)) {
                return false;
            }
        }
        return true;
    }

    /** Validates a {@code pay_if} list the way vanilla validates an entry's {@code conditions}. */
    public static void validate(List<LootItemCondition> payIf, ValidationContext context) {
        for (int i = 0; i < payIf.size(); i++) {
            payIf.get(i).validate(context.forChild(".pay_if[" + i + "]"));
        }
    }

    @Override
    public LootItemConditionType getType() {
        return RiftfallLootEntries.BARREL_GEAR_CHANCE.get();
    }

    @Override
    public boolean test(LootContext context) {
        double scale = scale();
        if (scale >= 1.0D) {
            return true;
        }
        if (!(scale > 0.0D)) {
            return false;
        }
        return context.getRandom().nextDouble() < scale;
    }
}
