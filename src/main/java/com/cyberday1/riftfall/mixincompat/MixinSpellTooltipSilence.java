package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.gear.CurioSilence;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Spell Engine's item tooltip: a silenced relic describes no spell. Its descriptions are literal text built
 * from the spell, so no key filter could find them; the one entry point is cut instead. Client only.
 */
@Pseudo
@Mixin(targets = "net.spell_engine.client.gui.SpellTooltip", remap = false)
public abstract class MixinSpellTooltipSilence {

    @Inject(method = "addSpellLines(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/TooltipFlag;Ljava/util/List;)V",
            at = @At("HEAD"), cancellable = true)
    private static void riftfall$silent(ItemStack stack, TooltipFlag flag, List<Component> lines, CallbackInfo ci) {
        if (CurioSilence.silenced(stack)) ci.cancel();
    }
}
