package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.skill.RiftSpellGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.cost.SpellCost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The engine's own cost after a player's cast, dropped: that cast already paid Riftfall's mana and
 * cooldown ({@code MixinSpellExecution}). Cooldown, food exhaustion and durability here; the item
 * cost in {@code MixinAmmo}. {@code cost.effect_id} removal still runs: no granted ability declares one.
 */
@Mixin(value = SpellCost.class, remap = false)
public abstract class MixinSpellCost {

    @WrapOperation(method = "consume", at = @At(value = "INVOKE",
            target = "Lnet/spell_engine/internals/cost/SpellCooldowns;imposeCooldown(Lnet/minecraft/world/entity/player/Player;Lnet/spell_engine/internals/container/SpellContainerSource$SourcedContainer;Lnet/minecraft/core/Holder;F)V"))
    private static void riftfall$noEngineCooldown(Player player, SpellContainerSource.SourcedContainer source,
                                                  Holder<Spell> spell, float progress, Operation<Void> original) {
        if (RiftSpellGate.enginePrices(player)) {
            original.call(player, source, spell, progress);
        }
    }

    @WrapOperation(method = "consume", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private static void riftfall$noEngineExhaustion(Player player, float amount, Operation<Void> original) {
        if (RiftSpellGate.enginePrices(player)) {
            original.call(player, amount);
        }
    }

    @WrapOperation(method = "consume", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private static void riftfall$noEngineDurability(ItemStack stack, int amount, LivingEntity holder,
                                                    EquipmentSlot slot, Operation<Void> original) {
        if (RiftSpellGate.enginePrices(holder)) {
            original.call(stack, amount, holder, slot);
        }
    }
}
