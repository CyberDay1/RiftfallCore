package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.client.PackKeyDefaults;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyMappingLookup;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Both root constructors: changes new-install and Reset defaults, never loaded user preferences. */
@Mixin(KeyMapping.class)
public abstract class MixinPackKeyDefaults {
    @Shadow @Final private String name;
    @Shadow @Final @Mutable private InputConstants.Key defaultKey;
    @Shadow private InputConstants.Key key;
    @Shadow private KeyModifier keyModifierDefault;
    @Shadow private KeyModifier keyModifier;
    @Shadow @Final private static KeyMappingLookup MAP;

    @Inject(method = {
            "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILjava/lang/String;)V",
            "<init>(Ljava/lang/String;Lnet/neoforged/neoforge/client/settings/IKeyConflictContext;Lnet/neoforged/neoforge/client/settings/KeyModifier;Lcom/mojang/blaze3d/platform/InputConstants$Key;Ljava/lang/String;)V"
    }, at = @At("RETURN"))
    private void riftfall$packDefaults(CallbackInfo ci) {
        String binding = PackKeyDefaults.binding(name);
        if (binding == null) return;
        String[] parts = binding.split(":", 2);
        var self = (KeyMapping) (Object) this;
        // Avoid virtual calls on modded KeyMapping subclasses before their constructors finish.
        MAP.remove(self);
        key = InputConstants.getKey(parts[0]);
        defaultKey = key;
        keyModifier = parts.length == 2 ? KeyModifier.valueOf(parts[1]) : KeyModifier.NONE;
        keyModifierDefault = keyModifier;
        MAP.put(key, self);
    }
}
