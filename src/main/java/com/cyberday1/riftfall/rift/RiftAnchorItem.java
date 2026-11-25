package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;

/**
 * The rift anchor: the way out of a dungeon that is not a walk back to the entrance.
 *
 * <p>
 * It is immediate, because that is what it is for. A dungeon has two other exits and both of them
 * are somewhere &mdash; the entrance you came in by, and whatever a boss drops &mdash; so the thing
 * a party is missing is a way out from wherever they happen to be standing when it goes wrong. A
 * channel or a cast time would put that back: it would make the anchor a plan rather than a
 * decision, and the moment it is reached for is exactly the moment there is no time to make plans.
 *
 * <p>
 * It goes to the same doorstep the entrance does, and it is spent doing it. Leaving is leaving: the
 * roster keeps the player off this run whichever way they left it, so an anchor buys an escape from
 * a dungeon and never a second look at one.
 */
public class RiftAnchorItem extends Item {

    private static final String OUTSIDE = "message." + RiftfallCore.MODID + ".anchor_outside_rift";
    private static final String NO_WAY_BACK = "message." + RiftfallCore.MODID + ".anchor_no_way_back";

    public RiftAnchorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server)) {
            // A client knows which dimension it is standing in and nothing at all about the run, so
            // it answers the half of the question it can and leaves the rest, including what to say
            // about it, to the server. An arm that swings on a use the server then declines is a far
            // smaller lie than one that does not swing on a use that works.
            return RiftLevels.isRift(level.dimension())
                    ? InteractionResultHolder.sidedSuccess(stack, true)
                    : InteractionResultHolder.fail(stack);
        }
        if (!RiftLevels.isRift(level.dimension())) {
            notice(server, OUTSIDE);
            return InteractionResultHolder.fail(stack);
        }
        DimensionTransition home = RiftRuns.leaving(server);
        if (home == null) {
            notice(server, NO_WAY_BACK);
            return InteractionResultHolder.fail(stack);
        }

        // The cooldown is the one thing the portal gets for free and this does not: handlePortal sets
        // it on itself before it teleports. The doorstep on file is where the player stood while the
        // entrance was working on them, which is inside the frame they lit, so without this they land
        // in a live portal with nothing between them and a second trip but the roster refusing it out
        // loud, once a second, for as long as they stand there.
        server.setPortalCooldown();
        if (server.changeDimension(home) == null) {
            // Something else refused the trip. Not consumed: they are still in the dungeon.
            return InteractionResultHolder.fail(stack);
        }
        stack.consume(1, server);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /** Says why nothing happened, on the action bar and in grey, exactly as a refused portal does. */
    private static void notice(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
    }
}
