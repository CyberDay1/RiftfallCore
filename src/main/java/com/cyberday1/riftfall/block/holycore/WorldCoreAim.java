package com.cyberday1.riftfall.block.holycore;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.resurrection.WorldResurrectionMachinePartBlock;
import com.cyberday1.riftfall.home.HomeProtection;
import com.cyberday1.riftfall.init.RiftfallItems;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * A right-click aimed at the floating orb uses the Core, never the held item: no thrown spear, no block placed
 * through it. Only a shard keeps its own use, which feeds the Core. Runs on both sides so the client predicts nothing.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class WorldCoreAim {
    private WorldCoreAim() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void useItem(PlayerInteractEvent.RightClickItem event) {
        var result = intercept(event.getLevel(), event.getEntity(), event.getItemStack());
        if (result != null) { event.setCancellationResult(result); event.setCanceled(true); }
    }

    /** A block behind the orb, or the empty hand on it. The Core's own cells already route to the Core. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (coreCell(event.getLevel().getBlockState(event.getPos()))) return;
        var result = intercept(event.getLevel(), event.getEntity(), event.getItemStack());
        if (result == null) return;
        event.setUseBlock(TriState.FALSE); event.setUseItem(TriState.FALSE);
        event.setCancellationResult(result); event.setCanceled(true);
    }

    /** Null when the click is not on an orb, or the held item feeds it. */
    static @Nullable InteractionResult intercept(Level level, Player player, ItemStack stack) {
        if (player instanceof FakePlayer || player.isSpectator() || stack.is(RiftfallItems.SHATTERED_WORLD_SHARD.get())) return null;
        var target = WorldCoreTarget.find(level, player);
        if (target.isEmpty()) return null;
        if (HomeProtection.denied(level, player)) return InteractionResult.FAIL;
        return HolyCoreBlock.inspect(level, target.get(), player);
    }

    /**
     * The empty-hand packet (CoreTouchPayload): only the aim is the client's, as in vanilla's use packet. Reach, line
     * of sight and the orb itself are found again from the server's own eye, then the visitor rules; an occupied hand
     * goes through the vanilla packets instead. True when the Core answered.
     */
    public static boolean touched(Player player, float yRot, float xRot) {
        if (player instanceof FakePlayer || player.isSpectator() || !Float.isFinite(yRot) || !Float.isFinite(xRot)
                || !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) return false;
        var level = player.level();
        var target = WorldCoreTarget.find(level, player, net.minecraft.world.phys.Vec3.directionFromRotation(
                net.minecraft.util.Mth.clamp(xRot, -90, 90), yRot));
        if (target.isEmpty() || HomeProtection.denied(level, player)) return false;
        HolyCoreBlock.inspect(level, target.get(), player);
        return true;
    }

    static boolean coreCell(BlockState state) {
        return state.getBlock() instanceof HolyCoreBlock || state.getBlock() instanceof WorldResurrectionMachinePartBlock;
    }
}
