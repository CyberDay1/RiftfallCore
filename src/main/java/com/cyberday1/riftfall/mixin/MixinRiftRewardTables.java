package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.gear.RiftRewardLoot;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve the datapack-authored pools before chest injectors append their own rolls at load time. */
@Mixin(value = EventHooks.class, remap = false)
public abstract class MixinRiftRewardTables {
    @Inject(method = "loadLootTable(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/level/storage/loot/LootTable;)Lnet/minecraft/world/level/storage/loot/LootTable;",
            at = @At("HEAD"), cancellable = true)
    private static void riftfall$authoredPools(HolderLookup.Provider registries, ResourceLocation name,
            LootTable table, CallbackInfoReturnable<LootTable> callback) {
        if (table != LootTable.EMPTY && RiftRewardLoot.protects(name)) callback.setReturnValue(table);
    }
}
