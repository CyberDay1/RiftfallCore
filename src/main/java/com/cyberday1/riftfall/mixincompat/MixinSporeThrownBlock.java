package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.mob.SporeEncounters;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.Harbinger.Spore.Sentities.Projectile.ThrownBlockProjectile", remap = false)
public abstract class MixinSporeThrownBlock {
    @Redirect(method = {"onHitEntity", "onHitBlock", "tick"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;fall(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/entity/item/FallingBlockEntity;"))
    private FallingBlockEntity riftfall$noDepositedRubble(Level level, BlockPos pos, BlockState state) {
        // fall() removes the source block immediately, before its entity-join event.
        return SporeEncounters.protectedLevel(level)
                || com.cyberday1.riftfall.mob.SporeCombat.managedProjectile((net.minecraft.world.entity.Entity) (Object) this)
                ? null : FallingBlockEntity.fall(level, pos, state);
    }
}
