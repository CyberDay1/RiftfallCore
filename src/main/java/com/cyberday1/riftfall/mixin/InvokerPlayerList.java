package com.cyberday1.riftfall.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Persist a return address before crossing into a disposable world. */
@Mixin(PlayerList.class)
public interface InvokerPlayerList {
    @Invoker("save") void riftfall$savePlayer(ServerPlayer player);
}
