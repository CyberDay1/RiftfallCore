package com.cyberday1.riftfall.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * A landed hit from a Riftfall skill that no Spell Engine trigger reports: an Iron's cast from the skill
 * bar. Names no engine type, so the Iron's side can report a hit without loading Spell Engine, and the
 * passive triggers listen only when Spell Engine is there.
 */
public final class SkillHits {

    private static volatile @Nullable BiConsumer<ServerPlayer, LivingEntity> listener;

    private SkillHits() {
    }

    static void listen(@Nullable BiConsumer<ServerPlayer, LivingEntity> hits) {
        listener = hits;
    }

    /** A granted skill's damage landed on {@code target}, after the hit (a killing blow leaves it at 0 HP). */
    static void landed(ServerPlayer player, LivingEntity target) {
        BiConsumer<ServerPlayer, LivingEntity> hits = listener;
        if (hits != null) {
            hits.accept(player, target);
        }
    }
}
