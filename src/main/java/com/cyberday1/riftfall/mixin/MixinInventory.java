package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.gear.WornGearBan;
import com.cyberday1.riftfall.gear.AcquisitionBan;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops modded armour doing anything on its own while it is worn.
 *
 * <h2>Why here and not on {@code ItemStack}</h2>
 * {@code ItemStack#inventoryTick} takes the slot as a parameter, which would be the obvious place to
 * ask "is this an armour slot?". But that parameter only means what it says when the caller is this
 * loop: {@code Inventory#tick} is the only thing in vanilla and NeoForge that calls it, and mods with
 * their own containers call it too, numbering their slots however they like. A backpack ticking its
 * thirty-seventh internal slot would be read as a chestplate. Redirecting the one call site instead
 * means the slot is the player's own global index by construction.
 *
 * <h2>Which slots are armour</h2>
 * NeoForge patched this loop to pass the global index rather than the per-compartment one, and
 * {@code compartments} is {@code items}(36), {@code armor}(4), {@code offhand}(1) in that order, so
 * armour is 36..39. Read out of the 21.1.224 sources rather than remembered.
 */
@Mixin(Inventory.class)
public abstract class MixinInventory {
    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void riftfall$rejectDisabledGrant(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (AcquisitionBan.blocked(stack)) cir.setReturnValue(false);
    }

    @ModifyVariable(method = "setItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack riftfall$rejectDisabledTransfer(ItemStack stack) {
        return AcquisitionBan.allowed(stack);
    }

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;inventoryTick"
                            + "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V"))
    private void riftfall$silenceWornGear(ItemStack stack, Level level, Entity entity,
                                          int slot, boolean selected) {
        if (WornGearBan.silenced(stack, slot)) {
            return;
        }
        stack.inventoryTick(level, entity, slot, selected);
    }
}
