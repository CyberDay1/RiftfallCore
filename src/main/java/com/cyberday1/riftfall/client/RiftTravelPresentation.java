package com.cyberday1.riftfall.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.spell_engine.api.effect.CustomParticleStatusEffect;

/** Optional client bridge, loaded only with Spell Engine. Keep grapple evasion without magical rings. */
final class RiftTravelPresentation {
    private static boolean installed;
    private RiftTravelPresentation() { }

    static void install() {
        if (installed) return;
        var effect = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.fromNamespaceAndPath("bards_rpg", "eclipse_mantle"));
        if (effect == null) return;
        var original = CustomParticleStatusEffect.spawnerOf(effect);
        if (original == null) return;
        CustomParticleStatusEffect.register(effect, (entity, amplifier) -> {
            if (!TravelCue.quietGrapple(entity)) original.spawnParticles(entity, amplifier);
        });
        installed = true;
    }
}
