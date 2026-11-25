package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.gear.ApotheosisProgression;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.util.Map;

@Mixin(ServerAdvancementManager.class)
public abstract class MixinApotheosisAdvancements {
    @ModifyVariable(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"), argsOnly = true)
    private Map<ResourceLocation, JsonElement> riftfall$removeTierTree(Map<ResourceLocation, JsonElement> input) {
        return com.cyberday1.riftfall.progression.AdvancementPresentation.quietIntroductions(
                ApotheosisProgression.filterAdvancements(input));
    }
}
