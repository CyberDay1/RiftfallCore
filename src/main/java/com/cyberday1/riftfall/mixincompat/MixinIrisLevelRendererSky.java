package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.home.ShatteredHomeLevels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iris injects at HEAD of {@code LevelRenderer.renderSky} with a {@code ModifyVariable} that forces
 * {@code isFoggy=false}, which bypasses our {@code DimensionSpecialEffects.renderSky()} returning
 * {@code true}. The previous approach cancelled {@code renderSky} wholesale, but that also blocked
 * our own {@code DimensionSpecialEffects.renderSky} from firing — the procedural starfield never
 * rendered because NeoForge calls it INSIDE {@code renderSky}.
 *
 * <p>New approach (priority 1100 > Iris's 1000): override Iris's {@code isFoggy} variable back to
 * {@code true} for shattered_home so vanilla skips its sky path and defers to our
 * {@code DimensionSpecialEffects.renderSky()}. We do NOT cancel {@code renderSky} itself, so the
 * dimension-effects dispatch inside it still fires and paints our procedural starfield.
 *
 * <p>If Iris changes its injection shape this may need revisiting. The telltale symptom would be
 * colored rectangles (shader-pack sky textures) appearing over the starfield again.
 */
@Mixin(value = LevelRenderer.class, priority = 1100)
public abstract class MixinIrisLevelRendererSky {

    /**
     * Overrides the {@code isFoggy} local variable that Iris forces to false. For shattered_home
     * we force it true so vanilla's sky renderer defers to our DimensionSpecialEffects. This runs
     * AFTER Iris's ModifyVariable (our priority 1100 > Iris's 1000), so our value wins.
     *
     * <p>Target ordinal 0 matches the first boolean local in renderSky, which is the isFoggy flag
     * that both vanilla and Iris read to decide whether to skip custom sky rendering.
     */
    @ModifyVariable(
        method = "renderSky",
        at = @At(value = "LOAD", ordinal = 0),
        argsOnly = false,
        ordinal = 0
    )
    private boolean riftfall$overrideIsFoggyForShatteredHome(boolean isFoggy) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && ShatteredHomeLevels.isShatteredHome(level.dimension())) {
            return true; // Force vanilla to defer to our DimensionSpecialEffects.renderSky
        }
        return isFoggy;
    }
}