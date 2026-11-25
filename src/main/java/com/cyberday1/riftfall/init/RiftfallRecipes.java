package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.home.npc.conversion.ResidentConversionRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Recipe types and serializers. */
public final class RiftfallRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, RiftfallCore.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, RiftfallCore.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<ResidentConversionRecipe>> RESIDENT_CONVERSION =
            TYPES.register("resident_conversion", () -> RecipeType.simple(RiftfallCore.id("resident_conversion")));
    public static final DeferredHolder<RecipeSerializer<?>, ResidentConversionRecipe.Serializer> RESIDENT_CONVERSION_SERIALIZER =
            SERIALIZERS.register("resident_conversion", ResidentConversionRecipe.Serializer::new);

    private RiftfallRecipes() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        SERIALIZERS.register(bus);
    }
}
