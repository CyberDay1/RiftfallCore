package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.skill.RiftIronsGate;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every player Iron's cast starts at {@code attemptInitiateCast} (book, imbued weapon, scroll,
 * {@code /cast}) and lands at {@code castSpell}, whichever path started it. An ungranted spell is
 * refused at both; a granted one lands only once Riftfall's price is paid.
 */
@Mixin(value = AbstractSpell.class, remap = false)
public abstract class MixinIronsSpell {

    @Inject(method = "attemptInitiateCast", at = @At("HEAD"), cancellable = true)
    private void riftfall$gateStart(ItemStack stack, int spellLevel, Level level, Player player, CastSource source,
                                    boolean triggerCooldown, String slot, CallbackInfoReturnable<Boolean> cir) {
        if (RiftIronsGate.refusesStart(player, ((AbstractSpell) (Object) this).getSpellId())) {
            cir.setReturnValue(false);
        }
    }

    @WrapMethod(method = "castSpell")
    private void riftfall$gateCast(Level world, int spellLevel, ServerPlayer player, CastSource source,
                                   boolean triggerCooldown, Operation<Void> original) {
        RiftIronsGate.castPaid(player, ((AbstractSpell) (Object) this).getSpellId(),
                () -> original.call(world, spellLevel, player, source, triggerCooldown));
    }
}
