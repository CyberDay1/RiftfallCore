package com.cyberday1.riftfall.mixin;

import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * The two collections a loaded recipe lives in, which the recipe manager keeps to itself.
 *
 * <p>
 * {@code RecipeManager} offers no way to remove a recipe: the maps are private, they are rebuilt
 * whole in {@code apply}, and every reader goes through them. So a ban has to replace them, and both
 * at once &mdash; {@code byType} answers the crafting grid and {@code byName} answers commands, the
 * recipe book and every mod that looks a recipe up by id, and a recipe left in one but not the other
 * is a recipe that is half gone.
 *
 * <p>
 * The setters take the immutable types the manager builds, so what it holds afterwards is the same
 * shape as what it built. An accessor rather than reflection, for the reason
 * {@link AccessorMinecraftServer} gives: a rename becomes a startup failure naming the field.
 */
@Mixin(RecipeManager.class)
public interface AccessorRecipeManager {

    @Accessor("byType")
    Multimap<RecipeType<?>, RecipeHolder<?>> getByType();

    @Accessor("byType")
    void setByType(Multimap<RecipeType<?>, RecipeHolder<?>> byType);

    @Accessor("byName")
    Map<ResourceLocation, RecipeHolder<?>> getByName();

    @Accessor("byName")
    void setByName(Map<ResourceLocation, RecipeHolder<?>> byName);
}
