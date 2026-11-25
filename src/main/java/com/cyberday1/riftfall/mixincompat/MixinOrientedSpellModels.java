package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.OrientedSpellModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.spell_engine.entity.SpellModelEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spell Engine draws a model effect in world axes and ignores the yaw it spawned with (the caster's). Riftfall's
 * slash arcs opt in by path and turn to it, so local -Z follows the swing. Client-only.
 */
@Mixin(targets = "net.spell_engine.client.render.SpellModelEffectRenderer", remap = false)
public abstract class MixinOrientedSpellModels {
    @Inject(method = "render(Lnet/spell_engine/entity/SpellModelEffect;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/spell_engine/client/render/ModelEffectOperations;renderEffect(Lnet/spell_engine/api/spell/fx/ModelEffect;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/entity/ItemRenderer;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private void riftfall$faceSwing(SpellModelEffect entity, float yaw, float partial, PoseStack pose,
                                    MultiBufferSource buffers, int light, CallbackInfo ci) {
        var effect = entity.getModelEffect();
        if (effect != null && OrientedSpellModels.oriented(effect.model_id)) {
            pose.mulPose(Axis.YP.rotationDegrees(OrientedSpellModels.turn(entity.getYRot())));
        }
    }
}
