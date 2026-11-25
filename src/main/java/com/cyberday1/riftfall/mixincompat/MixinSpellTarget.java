package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.skill.CasterFallback;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.target.SpellTarget;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A placed field's caster fallback yields to the block its aim hits: see {@link CasterFallback}. */
@Mixin(value = SpellTarget.class, remap = false)
public abstract class MixinSpellTarget {

    @WrapOperation(method = "findTargets(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/Holder;Lnet/spell_engine/internals/target/SpellTarget$SearchResult;ZF)Lnet/spell_engine/internals/target/SpellTarget$SearchResult;",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/spell_engine/api/spell/Spell$Target$Aim;use_caster_as_fallback:Z"))
    private static boolean riftfall$fieldOnTheBlockAimedAt(Spell.Target.Aim aim, Operation<Boolean> original,
                                                          @Local(argsOnly = true) LivingEntity caster,
                                                          @Local(argsOnly = true) Holder<Spell> spell,
                                                          @Local(argsOnly = true) float range) {
        boolean fallback = original.call(aim);
        if (!fallback) return false;
        Spell value = spell.value();
        boolean placed = value.deliver != null && value.deliver.type == Spell.Delivery.Type.CLOUD;
        return CasterFallback.keeps(true, placed, placed && CasterFallback.hitsBlock(caster, range * caster.getScale()));
    }
}
