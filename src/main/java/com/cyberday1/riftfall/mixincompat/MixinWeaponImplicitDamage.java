package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.skill.SkillDamage;
import com.cyberday1.riftfall.stat.SingleCrit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Simply Swords' weapon implicits leave a skill's hit at its own number (see {@link SkillDamage}), and the ones that
 * roll a second crit are never registered ({@link SingleCrit#CLOSED_IMPLICITS}).
 */
@Pseudo
@Mixin(targets = "net.sweenus.simplyswords.api.WeaponImplicitRegistry", remap = false)
public abstract class MixinWeaponImplicitDamage {

    @Shadow @Final private static Map<ResourceLocation, ?> DEFINITIONS_BY_ID;
    @Shadow @Final private static Map<ResourceLocation, ?> DEFINITIONS_BY_TYPE;

    @Inject(method = "modifyDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At("HEAD"), cancellable = true)
    private static void riftfall$skillHitsKeepTheirNumber(LivingEntity target, DamageSource source, float amount,
                                                          CallbackInfoReturnable<Float> cir) {
        if (SkillDamage.active()) cir.setReturnValue(amount);
    }

    @Inject(method = "registerBuiltins()V", at = @At("TAIL"))
    private static void riftfall$oneCritRoll(CallbackInfo ci) {
        SingleCrit.dropImplicits(DEFINITIONS_BY_ID, DEFINITIONS_BY_TYPE);
    }
}
