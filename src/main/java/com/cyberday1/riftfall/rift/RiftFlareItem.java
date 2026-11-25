package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.boss.RiftBosses;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The rift flare: the taunt, as a thing you can hold.
 *
 * <p>
 * Every boss within {@link #RANGE} turns on whoever burned it and stays on them for
 * {@code TargetRotation#TAUNT_TICKS}. Without one a boss draws its next target at random after every
 * swing; this is the one way a party chooses who takes the hits.
 *
 * <p>
 * A flare rather than a horn because the dungeons are post-apocalyptic rather than medieval: you
 * light it, the room looks at you, and it is gone. Consumed on use and on a short cooldown, so
 * holding aggro is a resource a party spends rather than a button somebody leans on.
 */
public class RiftFlareItem extends Item {

    /**
     * How far a flare is seen, in blocks.
     *
     * <p>
     * A room rather than a dungeon. Wide enough to cover an arena and everything a party has pulled
     * into it, short enough that a boss two rooms away goes on fighting whoever it was fighting.
     */
    public static final double RANGE = 24.0;

    /** Ticks before another can be burned. Long enough that a stack is not one long taunt. */
    private static final int COOLDOWN_TICKS = 160;

    private static final String OUTSIDE = "message." + RiftfallCore.MODID + ".flare_outside_rift";
    private static final String NOBODY = "message." + RiftfallCore.MODID + ".flare_nobody_looking";

    public RiftFlareItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server) || !(level instanceof ServerLevel serverLevel)) {
            // The client knows which dimension it is standing in and nothing about the register, so
            // it swings on the half it can answer. The anchor makes the same trade for the same
            // reason: an arm that swings on a use the server then declines reads far better than one
            // that stays still on a use that worked.
            return RiftLevels.isRift(level.dimension())
                    ? InteractionResultHolder.sidedSuccess(stack, true)
                    : InteractionResultHolder.fail(stack);
        }
        if (!RiftLevels.isRift(level.dimension())) {
            notice(server, OUTSIDE);
            return InteractionResultHolder.fail(stack);
        }
        int heard = RiftBosses.taunt(serverLevel, server, RANGE);
        if (heard == 0) {
            // Not consumed. Nothing was listening, so nothing happened, and spending the flare on
            // that would be the item punishing a player for a mistake it could have just declined.
            notice(server, NOBODY);
            return InteractionResultHolder.fail(stack);
        }
        burn(serverLevel, server);
        server.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        stack.consume(1, server);
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /**
     * What everybody in the room sees and hears. Server-side particles and sound, no packet of ours,
     * so it reads the same on a vanilla client -- {@code ThreatRing}'s argument, and a taunt nobody
     * else can see is a trade the rest of the party cannot read.
     */
    private static void burn(ServerLevel level, ServerPlayer player) {
        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(),
                40, 0.4, 0.6, 0.4, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS,
                1.0F, 0.8F);
    }

    /** Says why nothing happened, on the action bar and in grey, exactly as a refused anchor does. */
    private static void notice(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
    }
}
