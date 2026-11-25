package com.cyberday1.riftfall.mixincompat;

import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.network.Packets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The remapped packet has two type() methods with different return types; Java cannot select one. */
@Mixin(value = Packets.SpellAnimation.class, remap = false)
public interface SpellAnimationAccess {
    @Accessor("type")
    SpellCast.Animation riftfall$animationType();
}
