package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.rift.PartyCombat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.internals.target.EntityRelation;
import net.spell_engine.internals.target.EntityRelations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityRelations.class, remap = false)
public abstract class MixinSpellParties {
    @Inject(method = "getRelation", at = @At("HEAD"), cancellable = true)
    private static void riftfall$party(LivingEntity attacker, Entity target, CallbackInfoReturnable<EntityRelation> cir) {
        if (PartyCombat.allies(attacker, target)) cir.setReturnValue(EntityRelation.ALLY);
    }
}
