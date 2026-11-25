package com.cyberday1.riftfall.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Entity render types a shader pack can draw: Iris masks colour and depth off for any core shader it does not know. */
public final class ShaderPackRenderTypes {

    private ShaderPackRenderTypes() {
    }

    /** {@code custom} with shaders off, {@code plain} while a pack is on; asked on every call, never cached. */
    public static <K, V> Function<K, V> unlessShaderPack(Function<K, V> custom, Function<K, V> plain, BooleanSupplier packActive) {
        return key -> packActive.getAsBoolean() ? plain.apply(key) : custom.apply(key);
    }

    /** A Voidscape void-sky body, drawn as a solid starfield while a pack is on (Iris's own end-portal fallback). */
    public static Function<ResourceLocation, RenderType> voidSky(Function<ResourceLocation, RenderType> custom) {
        return unlessShaderPack(custom, RenderType::entitySolid, ShatteredSkyRenderer::shaderPackActive);
    }
}
