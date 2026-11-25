package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.gear.CurioSilence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Spell Engine's Curios source: a silenced curio offers no spell container, granted or not. The ability gate
 * would already drop an ungranted spell; this keeps a relic from becoming a cast source for a granted one.
 */
@Pseudo
@Mixin(targets = "net.spell_engine.neoforge.compat.curios.CuriosCompat", remap = false)
public abstract class MixinCurioSilenceSpells {

    @Inject(method = "getEquippedStacks(Lnet/minecraft/world/entity/player/Player;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private static void riftfall$dropSilenced(Player player, CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> stacks = cir.getReturnValue();
        if (stacks == null || stacks.stream().noneMatch(CurioSilence::silenced)) return;
        List<ItemStack> kept = new ArrayList<>(stacks);
        kept.removeIf(CurioSilence::silenced);
        cir.setReturnValue(kept);
    }
}
