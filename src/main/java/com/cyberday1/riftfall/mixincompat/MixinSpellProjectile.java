package com.cyberday1.riftfall.mixincompat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.cyberday1.riftfall.skill.LobbedThrows;
import com.cyberday1.riftfall.skill.ProjectileSurface;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.impact.SpellImpacts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Where a Riftfall projectile bursts: outside a struck face, and in place when a lob runs out of range. */
@Mixin(value = SpellProjectile.class, remap = false)
public abstract class MixinSpellProjectile {
    @Shadow private SpellExecution.ImpactContext context;

    @Shadow public abstract Holder<Spell> getSpellEntry();

    @WrapOperation(method = "onHitBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/BlockHitResult;getLocation()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 riftfall$outsideHitFace(BlockHitResult hit, Operation<Vec3> original) {
        Vec3 point = original.call(hit);
        Holder<Spell> spell = getSpellEntry();
        if (spell != null && spell.value().area_impact != null && ours(spell)) {
            SpellProjectile self = (SpellProjectile) (Object) this;
            return ProjectileSurface.outside(hit, point, self.getDeltaMovement());
        }
        return point;
    }

    /** A lob out of range bursts in place; the engine would end it silently. */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void riftfall$burstAtRange(CallbackInfo ci) {
        SpellProjectile self = (SpellProjectile) (Object) this;
        if (self.level().isClientSide || !LobbedThrows.ENABLED) return;
        Holder<Spell> spell = getSpellEntry();
        if (spell == null || !ours(spell)) return;
        Spell value = spell.value();
        Spell.ProjectileData data = self.projectileData();
        float gravity = data == null || data.motion == null ? 0.0F : data.motion.gravity;
        if (!LobbedThrows.burstsAtRange(true, gravity, self.getBehaviour() == SpellProjectile.Behaviour.FLY,
                self.moveDist, self.range)) return;
        if (self.getOwner() instanceof LivingEntity caster && !caster.isRemoved() && value.area_impact != null) {
            SpellImpacts.projectileImpact(caster, self, null, spell, context.position(self.position()));
        }
        self.kill();
        ci.cancel();
    }

    private static boolean ours(Holder<Spell> spell) {
        return spell.unwrapKey().map(k -> k.location().getNamespace().equals("riftfall")).orElse(false);
    }
}
