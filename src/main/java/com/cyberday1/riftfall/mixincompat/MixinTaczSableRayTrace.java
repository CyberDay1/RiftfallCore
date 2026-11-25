package com.cyberday1.riftfall.mixincompat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tacz.guns.util.block.BlockRayTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps a bullet's block trace on TaCZ's own walk while tacz-tweaks is loaded, so bullets hit mobs again.
 * TaCZ 1.2.0-hotfix sends the trace through Sable's {@code level.clip} when Sable is installed, which skips
 * {@code performRayTrace}. tacz-tweaks' raytracer finds entity hits inside {@code performRayTrace} and cancels the
 * rest of {@code onBulletTick} once the trace returns, so with the Sable branch no hit is ever checked. Only applied
 * with both mods (the compat plugin); a TaCZ without the branch has nothing to match ({@code require = 0}).
 * {@code -Driftfall.tacz_sable_trace=on} keeps TaCZ's branch, the probe's before arm.
 */
@Mixin(value = BlockRayTrace.class, remap = false)
public abstract class MixinTaczSableRayTrace {

    private static final boolean KEEP_SABLE = "on".equals(System.getProperty("riftfall.tacz_sable_trace"));

    @ModifyExpressionValue(method = "rayTraceBlocks", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/tacz/guns/compat/sable/SableCompat;isEnabled()Z"))
    private static boolean riftfall$traceThroughTaczTweaks(boolean sable) {
        return KEEP_SABLE && sable;
    }
}
