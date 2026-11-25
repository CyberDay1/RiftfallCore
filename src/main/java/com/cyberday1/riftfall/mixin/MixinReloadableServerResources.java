package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.gear.CrystalRecipeSwap;
import com.cyberday1.riftfall.gear.GearRecipeBan;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The first moment the recipes are loaded and the tags are true at the same time.
 *
 * <p>
 * {@link GearRecipeBan} needs both, and a datapack reload gives them in that order but not together:
 * {@code RecipeManager#apply} runs while the tags bound to the item registry are still the previous
 * pack's, so a ban run there would read a stale {@code gear_denied} and take recipes from the mods
 * that tag is there to spare. Tags are bound in {@code updateRegistryTags}, several listeners later.
 *
 * <p>
 * Injected on its return rather than on the {@code TagsUpdatedEvent} it posts, because the event
 * carries no recipe manager and the one on {@code ServerLifecycleHooks} does not exist yet on the
 * first load of a world. Here both are fields of the same object.
 *
 * <p>
 * Still ahead of anyone reading the result: the player list is told to reload, and so sends every
 * client its recipe list, after this returns.
 */
@Mixin(ReloadableServerResources.class)
public class MixinReloadableServerResources {

    @Shadow
    @Final
    private RecipeManager recipes;

    @Shadow
    @Final
    private ReloadableServerRegistries.Holder fullRegistryHolder;

    @Inject(method = "updateRegistryTags", at = @At("RETURN"))
    private void riftfall$banCraftableGear(CallbackInfo callback) {
        CrystalRecipeSwap.swap(this.recipes);
        GearRecipeBan.strip(this.recipes, this.fullRegistryHolder.get());
    }
}
