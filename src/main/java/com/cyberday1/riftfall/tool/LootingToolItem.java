package com.cyberday1.riftfall.tool;

import com.cyberday1.riftfall.block.barrel.RiftBarrelBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.List;

/** Permanent, durability-free pick. Upgrades and appearances share the same persistent item stack. */
public final class LootingToolItem extends Item {
    public static final String SKIN_KEY = "riftfall_tool_skin";
    public static final String LEVEL_KEY = "riftfall_tool_level";
    public static final String UPGRADES_KEY = "riftfall_tool_upgrades";

    public LootingToolItem(Properties properties) {
        super(properties.stacksTo(1)
                .component(DataComponents.TOOL, Tiers.NETHERITE.createToolProperties(BlockTags.MINEABLE_WITH_PICKAXE))
                .component(DataComponents.CUSTOM_DATA, initialData()));
    }

    private static CustomData initialData() {
        CompoundTag data = new CompoundTag();
        data.putInt(LEVEL_KEY, 1);
        data.put(UPGRADES_KEY, new CompoundTag());
        return CustomData.of(data);
    }

    public static boolean isTool(ItemStack stack) { return stack.getItem() instanceof LootingToolItem; }

    public static ToolAppearances.Skin appearance(ItemStack stack) {
        String id = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(SKIN_KEY);
        var skin = ToolAppearances.find(id);
        return skin == null ? ToolAppearances.DEFAULT : skin;
    }

    /** Mutate one field on the SAME stack. Never reconstruct the item or reset any other component. */
    public static boolean applyAppearance(ItemStack stack, String id) {
        var skin = ToolAppearances.find(id);
        if (!isTool(stack) || !ToolAppearances.available(skin)) return false;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(SKIN_KEY, skin.item().toString()));
        return true;
    }

    @Override public boolean canPerformAction(ItemStack stack, ItemAbility action) {
        if (ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(action)) return true;
        for (var mode : ToolMode.of(stack)) if (mode.abilities.contains(action)) return true;
        return false;
    }

    /** Right-click with learnt modes: strip, scrape or unwax first; then till, or path when sneaking (path first). */
    @Override public InteractionResult useOn(UseOnContext context) {
        var modes = ToolMode.of(context.getItemInHand());
        if (modes.isEmpty()) return InteractionResult.PASS;
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        var player = context.getPlayer();
        boolean sneaking = player != null && player.isSecondaryUseActive();
        var order = new java.util.ArrayList<ItemAbility>();
        if (modes.contains(ToolMode.AXE)) order.addAll(List.of(ItemAbilities.AXE_STRIP, ItemAbilities.AXE_SCRAPE, ItemAbilities.AXE_WAX_OFF));
        if (modes.contains(ToolMode.SHOVEL) && sneaking) order.add(ItemAbilities.SHOVEL_FLATTEN);
        if (modes.contains(ToolMode.HOE)) order.add(ItemAbilities.HOE_TILL);
        if (modes.contains(ToolMode.SHOVEL) && !sneaking) order.add(ItemAbilities.SHOVEL_FLATTEN);
        if (modes.contains(ToolMode.SHOVEL)) order.add(ItemAbilities.SHOVEL_DOUSE);
        for (var ability : order) {
            if (ability == ItemAbilities.SHOVEL_FLATTEN
                    && (context.getClickedFace() == Direction.DOWN || !level.getBlockState(pos.above()).isAir())) continue;
            var changed = state.getToolModifiedState(context, ability, false);
            if (changed == null) continue;
            effects(level, pos, player, ability);
            if (!level.isClientSide) {
                level.setBlock(pos, changed, 11);
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, changed));
                if (player instanceof ServerPlayer server) CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(server, pos, context.getItemInHand());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private static void effects(Level level, BlockPos pos, Player player, ItemAbility ability) {
        if (ability == ItemAbilities.AXE_STRIP) level.playSound(player, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        else if (ability == ItemAbilities.AXE_SCRAPE) {
            level.playSound(player, pos, SoundEvents.AXE_SCRAPE, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.levelEvent(player, 3005, pos, 0);
        } else if (ability == ItemAbilities.AXE_WAX_OFF) {
            level.playSound(player, pos, SoundEvents.AXE_WAX_OFF, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.levelEvent(player, 3004, pos, 0);
        } else if (ability == ItemAbilities.SHOVEL_FLATTEN) level.playSound(player, pos, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1.0F, 1.0F);
        else if (ability == ItemAbilities.HOE_TILL) level.playSound(player, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        else if (ability == ItemAbilities.SHOVEL_DOUSE && !level.isClientSide) level.levelEvent(null, 1009, pos, 0);
    }

    @Override public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return state.getBlock() instanceof RiftBarrelBlock || super.canAttackBlock(state, level, pos, player);
    }

    @Override public float getDestroySpeed(ItemStack stack, BlockState state) {
        float base = state.getBlock() instanceof RiftBarrelBlock ? Tiers.IRON.getSpeed() : ToolMode.tool(ToolMode.of(stack)).getMiningSpeed(state);
        return (float) (base * (1 + ToolUpgrades.bonus(stack, ToolStat.MINING_SPEED) / 100));
    }

    @Override public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return state.getBlock() instanceof RiftBarrelBlock || ToolMode.tool(ToolMode.of(stack)).isCorrectForDrops(state);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable(ToolLang.PICK_LEVEL, ToolUpgrades.level(stack)).withStyle(ChatFormatting.GOLD));
        for (var stat : ToolStat.values()) {
            lines.add(Component.translatable(ToolLang.PICK_STAT, stat.displayName(), stat.bonus(ToolUpgrades.ranks(stack, stat)))
                    .withStyle(ChatFormatting.GRAY));
        }
        var modes = ToolMode.of(stack);
        if (!modes.isEmpty()) {
            var names = Component.empty();
            for (var mode : modes) {
                if (!names.getSiblings().isEmpty()) names.append(", ");
                names.append(Component.translatable(com.cyberday1.riftfall.home.npc.smith.ToolWorkLang.mode(mode)));
            }
            lines.add(Component.translatable(com.cyberday1.riftfall.home.npc.smith.ToolWorkLang.PICK_MODES, names).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable(ToolLang.PICK_BARRELS).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable(ToolLang.PICK_APPEARANCE, appearance(stack).displayName()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(ToolLang.PICK_CHANGE).withStyle(ChatFormatting.DARK_GRAY));
    }
}
