package com.cyberday1.riftfall.skill;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A placed field with {@code use_caster_as_fallback} (Sacred Ground, ruled 2026-11-16) lands on the block its aim hits,
 * as every ground field has since 10-31, and on its caster only when the aim finds no entity and no block.
 * {@code MixinSpellTarget} reads it at Spell Engine's fallback.
 */
public final class CasterFallback {

    private CasterFallback() {
    }

    /** Spell Engine's fallback, kept for a placed field only when its aim reached no block. */
    public static boolean keeps(boolean fallback, boolean placedField, boolean hitsBlock) {
        return fallback && !(placedField && hitsBlock);
    }

    /** The engine's own aim ray ({@code TargetHelper.locationFromRayCast}): from the eye, {@code range} along the view. */
    public static boolean hitsBlock(LivingEntity caster, double range) {
        Vec3 start = caster.getEyePosition();
        Vec3 end = start.add(caster.getViewVector(1.0F).normalize().scale(range));
        return caster.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                caster)).getType() == HitResult.Type.BLOCK;
    }
}
