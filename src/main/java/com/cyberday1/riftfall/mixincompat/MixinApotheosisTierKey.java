package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.gear.ApotheosisProgression;
import dev.shadowsoffire.apotheosis.client.AdventureModuleClient;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = AdventureModuleClient.class, remap = false)
public abstract class MixinApotheosisTierKey {
    @Redirect(method = "keys", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/event/RegisterKeyMappingsEvent;register(Lnet/minecraft/client/KeyMapping;)V"))
    private static void riftfall$omitWorldTierKey(RegisterKeyMappingsEvent event, KeyMapping key) {
        if (!ApotheosisProgression.TIER_KEY.equals(key.getName())) event.register(key);
    }
}
