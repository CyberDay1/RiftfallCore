package com.cyberday1.riftfall.mob;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

/** Encounter weights use original health, before the ordinary-mob health adjustment. */
public final class RiftMobBalance {
    public static final TagKey<EntityType<?>> ELITES =
            TagKey.create(Registries.ENTITY_TYPE, RiftfallCore.id("rift_mob_elites"));

    private RiftMobBalance() {}

    @SuppressWarnings("unchecked")
    public static int weight(EntityType<?> type) {
        var attributes = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
        double health = attributes == null ? 20 : attributes.getBaseValue(Attributes.MAX_HEALTH);
        return weight(health, type.is(ELITES));
    }

    public static int weight(double originalHealth, boolean taggedElite) {
        if (originalHealth >= 100) return 1;
        if (originalHealth >= 60 || taggedElite) return 2;
        return 8;
    }

    public static boolean elite(EntityType<?> type) { return weight(type) < 8; }

    /** Keep normal enemies unchanged; retain a tougher elite without a several-hundred-HP corridor fight. */
    public static double health(double originalHealth) {
        return originalHealth <= 60 ? originalHealth : Math.min(100, 60 + (originalHealth - 60) * 0.25);
    }
}
