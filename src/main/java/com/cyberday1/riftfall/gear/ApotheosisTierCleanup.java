package com.cyberday1.riftfall.gear;

import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import dev.shadowsoffire.apotheosis.tiers.augments.TierAugmentRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Only reached by the optional Apotheosis mixin; removes saved tier effects by their exact IDs. */
public final class ApotheosisTierCleanup {
    private ApotheosisTierCleanup() { }

    public static void remove(ServerLevel level, LivingEntity entity) {
        float max = entity.getMaxHealth(), health = entity.getHealth();
        for (var augment : TierAugmentRegistry.INSTANCE.getValues()) augment.remove(level, entity);
        entity.removeData(Apoth.Attachments.TIER_AUGMENTS_APPLIED);
        if (entity instanceof Player) entity.setData(Apoth.Attachments.WORLD_TIER, WorldTier.HAVEN);
        if (max > 0 && entity.getMaxHealth() != max) entity.setHealth(health / max * entity.getMaxHealth());
    }
}
