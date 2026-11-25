package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.BossBarExtent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Enhanced Boss Bars draws the bars it has art for in its own stack, taller than vanilla's at low GUI scales, and
 * no vanilla event sees it: once a frame's stack is laid out, its reach goes to {@link BossBarExtent}.
 */
@Pseudo
@Mixin(targets = "com.nakuring.enhanced_boss_bars.render.context.BossBarStackLayout", remap = false)
public abstract class MixinEnhancedBossBarsStack {
    @Shadow
    @Final
    private int screen_height;

    @Shadow
    @Final
    private double effective_scale;

    @Shadow
    private int current_bar_y;

    @Shadow
    public abstract boolean isDirectionDown();

    @Shadow
    private int calculateStackHeight(List<?> bars) {
        throw new AssertionError();
    }

    @Inject(method = "prepare", at = @At("TAIL"), require = 0)
    private void riftfall$reach(List<?> bars, CallbackInfo ci) {
        if (bars.isEmpty()) return;
        int scaled = (int) Math.ceil(calculateStackHeight(bars) * effective_scale);
        BossBarExtent.enhanced(current_bar_y, scaled, isDirectionDown(), screen_height);
    }
}
