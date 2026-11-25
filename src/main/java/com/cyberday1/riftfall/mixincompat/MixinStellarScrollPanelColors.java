package com.cyberday1.riftfall.mixincompat;

import net.neoforged.neoforge.client.gui.widget.ScrollPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Setters only; the Sophisticated scroll panel is the one caller. */
@Mixin(value = ScrollPanel.class, remap = false)
public interface MixinStellarScrollPanelColors {
    @Mutable @Accessor("barBgColor") void riftfall$track(int color);
    @Mutable @Accessor("barColor") void riftfall$shade(int color);
    @Mutable @Accessor("barBorderColor") void riftfall$face(int color);
}
