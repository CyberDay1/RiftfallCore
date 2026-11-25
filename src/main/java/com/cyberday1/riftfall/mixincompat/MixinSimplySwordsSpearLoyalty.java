package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.compat.SpearLoyalty;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A thrown Simply Swords spear returns as if it had Loyalty III. The return reads the plain {@code hasLoyalty} field,
 * which the item's use sets once on the server and nothing saves or syncs, so it is set again every tick on both
 * sides: a spear reloaded from disk still returns and the client predicts the flight. A spear falling past the
 * bottom of the world turns back instead of being lost.
 */
@Pseudo
@Mixin(targets = "net.sweenus.simplyswords.entity.ThrownSpearEntity", remap = false)
public abstract class MixinSimplySwordsSpearLoyalty extends AbstractArrow {
    @Shadow public int hasLoyalty;
    @Shadow public boolean nonReturning;
    @Shadow @Final private static EntityDataAccessor<Byte> LOYALTY;

    @Shadow public abstract ItemStack getPickupItemStackOrigin();
    @Shadow protected abstract void beginReturn();

    protected MixinSimplySwordsSpearLoyalty(EntityType<? extends AbstractArrow> type, Level level) { super(type, level); }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void riftfall$returnsLikeLoyaltyThree(CallbackInfo ci) {
        // Simply Swords loses its synced stack on a reload (it reads a key it never writes); the arrow's own is kept.
        if (nonReturning || !(SpearLoyalty.applies(getPickupItemStackOrigin()) || SpearLoyalty.applies(getPickupItem()))) return;
        hasLoyalty = SpearLoyalty.level(hasLoyalty);
        if (level().isClientSide) return;
        // The synced copy decides despawning; Simply Swords writes it before the loyalty is known.
        if (entityData.get(LOYALTY) < hasLoyalty) entityData.set(LOYALTY, (byte) hasLoyalty);
        if (getY() < level().getMinBuildHeight() && getOwner() != null) beginReturn();
    }
}
