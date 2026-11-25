package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.compat.WalkersShapes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * A player wearing a Woodwalkers model keeps the player's own box and eyes: Walkers answers these three getters from
 * the mob, and the model is visual only. Wrapped whole, so Walkers' own injections sit inside.
 */
@Mixin(Entity.class)
public abstract class MixinWalkersBody {

    @Shadow
    private EntityDimensions dimensions;

    @Shadow
    private float eyeHeight;

    @WrapMethod(method = "getBbWidth")
    private float riftfall$playerWidth(Operation<Float> original) {
        return (Object) this instanceof Player p && WalkersShapes.shaped(p) ? dimensions.width() : original.call();
    }

    @WrapMethod(method = "getBbHeight")
    private float riftfall$playerHeight(Operation<Float> original) {
        return (Object) this instanceof Player p && WalkersShapes.shaped(p) ? dimensions.height() : original.call();
    }

    @WrapMethod(method = "getEyeHeight()F")
    private float riftfall$playerEyes(Operation<Float> original) {
        return (Object) this instanceof Player p && WalkersShapes.shaped(p) ? eyeHeight : original.call();
    }
}
