package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.ShaderPackRenderTypes;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

/** The Null Servant and its phantoms stay visible under a shader pack; with shaders off nothing changes. */
@Mixin(targets = "tamaized.voidscape.client.entity.model.ModelNullServant", remap = false)
public abstract class MixinNullServantShaderPack {

    @ModifyExpressionValue(method = "<clinit>", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/Util;memoize(Ljava/util/function/Function;)Ljava/util/function/Function;"))
    private static Function<ResourceLocation, RenderType> riftfall$visibleUnderShaderPack(Function<ResourceLocation, RenderType> voidSky) {
        return ShaderPackRenderTypes.voidSky(voidSky);
    }
}
