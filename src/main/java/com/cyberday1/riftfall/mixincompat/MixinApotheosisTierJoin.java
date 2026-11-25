package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.gear.ApotheosisTierCleanup;
import dev.shadowsoffire.apotheosis.AdventureEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AdventureEvents.class, remap = false)
public abstract class MixinApotheosisTierJoin {
    @Inject(method = "applyMissedTierAugments", at = @At("HEAD"), cancellable = true)
    private void riftfall$removeSavedTierEffects(EntityJoinLevelEvent event, CallbackInfo ci) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof LivingEntity entity) {
            ApotheosisTierCleanup.remove(level, entity);
        }
        ci.cancel();
    }
}
