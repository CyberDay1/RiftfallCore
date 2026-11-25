package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.skill.Reactions;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iron's dodge ({@code irons_lib:dodge_chance}, the live one) cancels the incoming hit inside
 * {@code AttributeEventsHandler#handleDamageAttributes}. Right after that cancel, Riftfall reports the dodge, so a
 * passive can react to it (Spell Engine's EVASION trigger). Named by string: irons_lib is not a compile dependency.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironslib.attribute.AttributeEventsHandler", remap = false)
public abstract class MixinIronsDodge {

    @Inject(method = "handleDamageAttributes", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent;setCanceled(Z)V",
            shift = At.Shift.AFTER))
    private static void riftfall$dodged(LivingIncomingDamageEvent event, CallbackInfo ci) {
        Reactions.dodged(event.getEntity(), event.getSource(), event.getAmount());
    }
}
