package com.cyberday1.riftfall.mixincompat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.cost.Ammo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * A player's cast needs and takes none of the engine's item cost (a rune, an arrow): Riftfall's mana
 * and cooldown are the one price. Both sides, because {@code SpellCasting#attempt} refuses a cast
 * without the item on the client before the server ever sees it.
 */
@Mixin(value = Ammo.class, remap = false)
public abstract class MixinAmmo {

    @Inject(method = "ammoForSpell", at = @At("HEAD"), cancellable = true)
    private static void riftfall$noItemCost(Player player, Spell spell, ItemStack casterStack,
                                            CallbackInfoReturnable<Ammo.Result> cir) {
        Spell.Cost.Item item = spell.cost.item;
        Ammo.Searched named = item != null && item.id != null && !item.id.isEmpty() ? Ammo.Searched.from(item.id) : null;
        cir.setReturnValue(new Ammo.Result(true, named, 0, List.of()));
    }
}
