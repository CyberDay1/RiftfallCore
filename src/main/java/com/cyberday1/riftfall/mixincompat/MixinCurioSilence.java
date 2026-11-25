package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.gear.CurioSilence;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.common.capability.ItemizedCurioCapability;

/**
 * Curios wraps every {@code ICurioItem} in this bridge, so a silenced stack loses its worn behaviour here: tick,
 * own attributes (the CurioAttributeModifierEvent still adds affixes), and the worn perks. Equip, unequip, drop
 * rules and sync are left alone.
 */
@Mixin(value = ItemizedCurioCapability.class, remap = false)
public abstract class MixinCurioSilence {

    @Shadow @Final private ItemStack stack;

    @Inject(method = "curioTick", at = @At("HEAD"), cancellable = true)
    private void riftfall$silenceTick(SlotContext slotContext, CallbackInfo ci) {
        if (CurioSilence.silenced(stack)) {
            CurioSilence.refusedTick();
            ci.cancel();
        }
    }

    @Inject(method = "getAttributeModifiers", at = @At("HEAD"), cancellable = true)
    private void riftfall$silenceModifiers(SlotContext slotContext, ResourceLocation id,
            CallbackInfoReturnable<Multimap<Holder<Attribute>, AttributeModifier>> cir) {
        if (CurioSilence.silenced(stack)) {
            CurioSilence.withheldModifiers();
            cir.setReturnValue(LinkedHashMultimap.create());
        }
    }

    @Inject(method = "makesPiglinsNeutral", at = @At("HEAD"), cancellable = true)
    private void riftfall$silencePiglins(SlotContext slotContext, CallbackInfoReturnable<Boolean> cir) {
        if (CurioSilence.silenced(stack)) cir.setReturnValue(false);
    }

    @Inject(method = "isEnderMask", at = @At("HEAD"), cancellable = true)
    private void riftfall$silenceEnderMask(SlotContext slotContext, EnderMan enderMan,
            CallbackInfoReturnable<Boolean> cir) {
        if (CurioSilence.silenced(stack)) cir.setReturnValue(false);
    }

    @Inject(method = "canWalkOnPowderedSnow", at = @At("HEAD"), cancellable = true)
    private void riftfall$silenceSnow(SlotContext slotContext, CallbackInfoReturnable<Boolean> cir) {
        if (CurioSilence.silenced(stack)) cir.setReturnValue(false);
    }

    @Inject(method = "getFortuneLevel", at = @At("HEAD"), cancellable = true)
    private void riftfall$silenceFortune(SlotContext slotContext, LootContext lootContext,
            CallbackInfoReturnable<Integer> cir) {
        if (CurioSilence.silenced(stack)) cir.setReturnValue(0);
    }

    @Inject(method = "getLootingLevel", at = @At("HEAD"), cancellable = true)
    private void riftfall$silenceLooting(SlotContext slotContext, LootContext lootContext,
            CallbackInfoReturnable<Integer> cir) {
        if (CurioSilence.silenced(stack)) cir.setReturnValue(0);
    }
}
