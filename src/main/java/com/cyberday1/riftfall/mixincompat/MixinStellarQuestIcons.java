package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarQuestIcon;
import dev.ftb.mods.ftblibrary.icon.Icon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** FTB still lays out and operates every widget; only our explicit theme icons use native stellar art. */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.quest.theme.property.IconProperty", remap = false)
public abstract class MixinStellarQuestIcons {
    @Inject(method = "parse(Ljava/lang/String;)Ldev/ftb/mods/ftblibrary/icon/Icon;",
            at = @At("HEAD"), cancellable = true)
    private void riftfall$stellarIcon(String value, CallbackInfoReturnable<Icon> cir) {
        if (com.cyberday1.riftfall.client.StellarQuests.supports(value))
            cir.setReturnValue(new StellarQuestIcon(value));
    }
}
