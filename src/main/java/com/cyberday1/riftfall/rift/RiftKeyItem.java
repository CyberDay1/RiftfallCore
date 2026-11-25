package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallDataComponents;
import com.cyberday1.riftfall.init.RiftfallTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * The rift key: what a copper frame is lit with, and the only thing that says how hard the dungeon
 * on the other side will be.
 *
 * <p>
 * The tier is a data component rather than one item per difficulty. The forge is going to let a
 * player pick the number, and a difficulty ladder with no agreed top to it cannot be enumerated as
 * items in the first place.
 *
 * <p>
 * A key with no tier is not a broken key, it is a tier-1 key. Anything that hands out an item can
 * produce a stack with no components on it — {@code /give}, a loot table, the creative tab — and so
 * can a saved key whose tier failed to load, because the component's codec rejects a non-positive
 * number by dropping it rather than by refusing the item. A key that stopped working in either case
 * would be a trap laid across all of those paths, so {@link #tier(ItemStack)} defaults instead.
 */
public class RiftKeyItem extends Item {

    /** What a key with no tier component is worth. */
    public static final int DEFAULT_TIER = 1;

    private static final String TIER_KEY = "item." + RiftfallCore.MODID + ".rift_key.tier";
    public static final String ATTUNED_KEY = "item." + RiftfallCore.MODID + ".rift_key.attuned";
    public static final String ATTUNED_TWO_KEY = "item." + RiftfallCore.MODID + ".rift_key.attuned_two";
    public static final String BOSS_KEY = "item." + RiftfallCore.MODID + ".rift_key.boss";
    private static final String NO_FRAME = "message." + RiftfallCore.MODID + ".key_no_frame";
    private static final String ALREADY_LIT = "message." + RiftfallCore.MODID + ".key_already_lit";

    public RiftKeyItem(Properties properties) {
        super(properties);
    }

    /** The difficulty this key was forged at, defaulting to {@link #DEFAULT_TIER}. */
    public static int tier(ItemStack stack) {
        return stack.getOrDefault(RiftfallDataComponents.RIFT_TIER.get(), DEFAULT_TIER);
    }

    /**
     * Lights a copper frame, and is spent doing it.
     *
     * <p>
     * The clicked block is not checked for being frame first: the scan starts one block off the face
     * that was clicked, which is the opening whether the player clicked the sill from above or a jamb
     * from inside, and anything else fails the scan on its own. Only {@link #refuse} looks at the
     * clicked block, and only to decide whether a refusal is worth saying out loud.
     *
     * <p>
     * {@code Axis.X} is only where {@link RiftPortalShape#find} starts looking; it tries the other
     * axis after. The scan runs on the client too, so the arm swings when the frame really was one
     * and not otherwise.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Optional<RiftPortalShape> found =
                RiftPortalShape.find(level, context.getClickedPos().relative(context.getClickedFace()),
                        Direction.Axis.X);
        Optional<RiftPortalShape> frame = found.filter(RiftPortalShape::isEmptyFrame);
        if (frame.isEmpty()) {
            if (level instanceof ServerLevel) {
                refuse(context, found.isPresent());
            }
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            int tier = tier(context.getItemInHand());
            RiftAttunement attunement = RiftAttunement.of(context.getItemInHand());
            long seed = server.getRandom().nextLong();
            // Remembered before the frame is lit, because lighting is what makes the portal enterable
            // and a player already standing in the opening is inside it on the very next tick.
            RiftPortals.of(server).remember(frame.get().bottomLeft(), tier, seed, attunement);
            frame.get().light(tier);
            // Built after, for the same reason: the walk back to the frame is the time this buys, and
            // a player who never left has simply bought none of it.
            RiftWarmup.open(server, frame.get().bottomLeft(), tier, seed, attunement);
            // Over the whole opening rather than at the block that was clicked: the frame caught all at
            // once, and a corner of it flaring is a smaller event than the one that happened. The pitch
            // is the tier, which is the only thing about this rift a bystander can hear.
            RiftPortalBlock.flare(server, frame.get(), RiftPortalBlock.LIGHT_PITCH
                    - RiftPortalBlock.LIGHT_PER_RUNG * RiftPortalBlock.intensityFor(tier));
            context.getItemInHand().consume(1, context.getPlayer());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Says why a frame was refused, when there is reason to think a frame was meant.
     *
     * <p>
     * {@code valid} is what separates the two: {@link RiftPortalShape#find} only hands back a shape it
     * already judged valid, so a shape that survived the scan and then failed
     * {@link RiftPortalShape#isEmptyFrame} is a real frame with panes still in it.
     *
     * <p>
     * The other case cannot tell a copper ring with a gap in it from a ring that was never built, and
     * does not try. What it does instead is stay quiet unless the clicked block was frame material:
     * a key that spoke on every right-click would nag on dirt, and the scan starts one block off the
     * clicked face precisely so that clicking the frame is the gesture.
     */
    private static void refuse(UseOnContext context, boolean valid) {
        if (valid) {
            RiftPortalBlock.notice(context.getPlayer(), ALREADY_LIT);
        } else if (context.getLevel().getBlockState(context.getClickedPos()).is(RiftfallTags.PORTAL_FRAME)) {
            RiftPortalBlock.notice(context.getPlayer(), NO_FRAME);
        }
    }

    /**
     * The tier, always — including on a key that never had the component set.
     *
     * <p>
     * Not left to the vanilla component tooltip: that only draws components it has been taught to
     * render, and an undecorated key would show nothing at all rather than "Tier 1".
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(TIER_KEY, tier(stack)).withStyle(ChatFormatting.GRAY));
        RiftAttunement attunement = RiftAttunement.of(stack);
        if (!attunement.themes().isEmpty()) {
            Component first = themeName(attunement.themes().getFirst());
            tooltip.add((attunement.themes().size() == 1 ? Component.translatable(ATTUNED_KEY, first)
                    : Component.translatable(ATTUNED_TWO_KEY, first, themeName(attunement.themes().get(1))))
                    .withStyle(ChatFormatting.AQUA));
        }
        attunement.boss().flatMap(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE::getOptional)
                .ifPresent(type -> tooltip.add(Component.translatable(BOSS_KEY, type.getDescription()).withStyle(ChatFormatting.AQUA)));
    }

    private static Component themeName(net.minecraft.resources.ResourceLocation theme) {
        return Component.translatable("theme." + theme.getNamespace() + "." + theme.getPath());
    }
}
