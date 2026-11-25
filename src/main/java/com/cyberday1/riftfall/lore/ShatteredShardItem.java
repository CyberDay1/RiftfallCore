package com.cyberday1.riftfall.lore;

import com.cyberday1.riftfall.init.RiftfallDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The Shattered World Shard. A shard with a {@link ShardOrigin} is named for its core, with the item's own name
 * beneath, so it is told from a Rift Shard on sight; one without shows nothing. The name is read from the origin,
 * never stored, so stacking is unchanged.
 */
public final class ShatteredShardItem extends Item {
    public static final String ORIGIN = "lore.riftfall.shard.origin";
    public static final String ORIGIN_ALONE = "lore.riftfall.shard.origin_alone";
    public static final String GUARDED = "lore.riftfall.shard.guarded";
    public static final String WORLD = "lore.riftfall.shard.world";

    public ShatteredShardItem(Properties properties) { super(properties); }

    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        var target=com.cyberday1.riftfall.block.holycore.WorldCoreTarget.find(level,player);
        if(target.isEmpty())return net.minecraft.world.InteractionResultHolder.pass(stack);
        if(com.cyberday1.riftfall.home.HomeProtection.denied(level,player))
            return net.minecraft.world.InteractionResultHolder.fail(stack);
        var result=com.cyberday1.riftfall.block.holycore.HolyCoreBlock.offer(level,target.get(),player,stack);
        return result==net.minecraft.world.ItemInteractionResult.FAIL
                ?net.minecraft.world.InteractionResultHolder.fail(stack)
                :net.minecraft.world.InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }

    @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        var player=context.getPlayer();
        if(player==null)return net.minecraft.world.InteractionResult.PASS;
        return use(context.getLevel(),player,context.getHand()).getResult();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var origin = stack.get(RiftfallDataComponents.SHARD_ORIGIN.get());
        if (origin != null) tooltip.addAll(lines(origin, getDescription()));
    }

    @Override
    public Component getName(ItemStack stack) {
        var origin = stack.get(RiftfallDataComponents.SHARD_ORIGIN.get());
        return origin == null || origin.core().isEmpty() ? super.getName(stack) : Component.literal(origin.coreTitle());
    }

    /** Under the core's name: "Shattered World Shard", "Of the Kept Hollow" then, dimmed, "Guarded by ...". */
    public static List<Component> lines(ShardOrigin origin, Component kind) {
        var lines = new java.util.ArrayList<Component>();
        lines.add(kind.copy().withStyle(ChatFormatting.GRAY));
        if (!origin.coreHoldsWorld()) lines.add(Component.translatable(WORLD, origin.world()).withStyle(ChatFormatting.GRAY));
        // The theme's epithet; a theme without one shows the boss's real name.
        var guard = DefenderNames.of(origin, false)
                .or(() -> BuiltInRegistries.ENTITY_TYPE.getOptional(origin.boss()).map(type -> type.getDescription()));
        guard.ifPresent(name -> lines.add(Component.translatable(GUARDED, name).withStyle(ChatFormatting.DARK_GRAY)));
        return lines;
    }
}
