package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.SkillProjectileRenderer;
import com.cyberday1.riftfall.client.SkillProjectileMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.spell_engine.entity.SpellProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client-only, guarded by Spell Engine availability. Other projectiles retain their own renderer. */
@Mixin(targets = "net.spell_engine.client.render.SpellProjectileRenderer", remap = false)
public abstract class MixinSkillProjectileRenderer {
    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void riftfall$energyProjectile(Entity entity, float yaw, float partial, PoseStack pose,
                                    MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!(entity instanceof SpellProjectile projectile) || projectile.getSpellEntry() == null) return;
        var models = projectile.renderModels();
        // Do not discard held-item layers, composite effects, or third-party projectiles.
        if (models == null || models.models == null || models.models.size() != 1) return;
        var model = models.models.getFirst();
        if (model.use_held_item || model.fx == null) return;
        String spell = projectile.getSpellEntry().unwrapKey().map(key -> key.location().toString()).orElse("");
        var style = SkillProjectileMesh.style(spell, model.fx.model_id);
        if (style == null) return;
        SkillProjectileRenderer.render(style, entity, projectile.previousVelocity,
                projectile.getScaleMultiplier() * model.fx.scale, partial, pose, buffers);
        ci.cancel();
    }
}
