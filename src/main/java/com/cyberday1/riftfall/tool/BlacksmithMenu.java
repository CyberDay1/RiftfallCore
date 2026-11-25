package com.cyberday1.riftfall.tool;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentServiceMenu;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.init.RiftfallMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** The runestone page: pick, rune and Rift Shards in; one stat chosen; the pick comes back upgraded in place. */
public final class BlacksmithMenu extends ResidentServiceMenu<ToolUpgrades.Quote> {
    private static final Layout LAYOUT = new Layout(new int[][]{{30, 30}, {130, 30}, {230, 30}}, 74, 154, 212);

    public BlacksmithMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public BlacksmithMenu(int id, Inventory inventory, UUID smith) {
        super(RiftfallMenus.BLACKSMITH.get(), id, inventory, smith, NpcRole.BLACKSMITH, LAYOUT);
    }

    @Override protected boolean accepts(int slot, ItemStack stack) {
        return switch (slot) {
            case 0 -> LootingToolItem.isTool(stack);
            case 1 -> RuneGrade.of(stack) != null;
            case 2 -> stack.is(RiftfallItems.RIFT_SHARD.get());
            default -> false;
        };
    }
    @Override protected int maxStack(int input) { return input == 0 ? 1 : super.maxStack(input); }

    public ToolStat selected() { return ToolStat.values()[Math.clamp(selection(), 0, ToolStat.values().length - 1)]; }

    private final net.minecraft.world.inventory.DataSlot toolwright = net.minecraft.world.inventory.DataSlot.standalone();
    { addDataSlot(toolwright); }

    /** The home's Blacksmith took the Toolwright path; synced so the client lists the same effects. */
    public boolean toolwright() {
        if (inventory.player instanceof ServerPlayer server) {
            boolean is = com.cyberday1.riftfall.home.npc.depth.ResidentDepth.here(server, NpcRole.BLACKSMITH).path()
                    .filter(com.cyberday1.riftfall.home.npc.ResidentPaths.TOOLWRIGHT::equals).isPresent();
            toolwright.set(is ? 1 : 0);
            return is;
        }
        return toolwright.get() == 1;
    }

    @Override protected ToolUpgrades.Quote quote(int selection) {
        return ToolUpgrades.quote(inputs, inventory, ToolStat.values()[Math.clamp(selection, 0, ToolStat.values().length - 1)], toolwright());
    }
    @Override protected boolean selectable(int selection) {
        if (selection >= ToolStat.values().length) return false;
        return ToolUpgrades.supports(RuneGrade.of(inputs.getItem(1)), ToolStat.values()[selection], toolwright());
    }
    @Override protected Committed commit(ServerPlayer player, ToolUpgrades.Quote quote) {
        var result = ToolUpgrades.install(inputs, inventory, quote, toolwright());
        return new Committed(result == ToolUpgrades.Result.INSTALLED, result.text());
    }
}
