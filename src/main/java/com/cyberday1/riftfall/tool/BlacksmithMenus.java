package com.cyberday1.riftfall.tool;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentLanding;
import com.cyberday1.riftfall.home.npc.ResidentMenus;
import com.cyberday1.riftfall.init.RiftfallItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/** The Blacksmith's pages and its landing entries. */
public final class BlacksmithMenus {
    public static final int RUNESTONES = 1, APPEARANCES = 2, CLAIM = 3, TEMPER = 4, SALVAGE = 5, TOOLS = 6, BULK_SCRAP = 7;

    public static final List<ResidentLanding.Entry> LANDING = List.of(
            ResidentLanding.Entry.service(RUNESTONES, ToolLang.LANDING_RUNES, ToolLang.LANDING_RUNES_HINT,
                    ToolLang.LANDING_RUNES_NOTE, () -> RuneGrade.COMMON.item(), (p, smith, m) -> open(p, smith, RUNESTONES)),
            ResidentLanding.Entry.service(TEMPER, com.cyberday1.riftfall.home.npc.smith.SmithLang.LANDING_TEMPER,
                    com.cyberday1.riftfall.home.npc.smith.SmithLang.LANDING_TEMPER_HINT, com.cyberday1.riftfall.home.npc.smith.SmithLang.LANDING_TEMPER_NOTE,
                    () -> RiftfallItems.TEMPERING_STOCK.get(), (p, smith, m) -> open(p, smith,
                            com.cyberday1.riftfall.home.npc.ResidentRank.of(p.serverLevel(), NpcRole.BLACKSMITH)
                                    .atLeast(com.cyberday1.riftfall.home.npc.ResidentRank.JOURNEYMAN) ? TEMPER : SALVAGE)),
            ResidentLanding.Entry.service(TOOLS, com.cyberday1.riftfall.home.npc.smith.ToolWorkLang.LANDING,
                    com.cyberday1.riftfall.home.npc.smith.ToolWorkLang.LANDING_HINT, com.cyberday1.riftfall.home.npc.smith.ToolWorkLang.LANDING_NOTE,
                    () -> net.minecraft.world.item.Items.IRON_SHOVEL, (p, smith, m) -> open(p, smith, TOOLS)),
            ResidentLanding.Entry.service(APPEARANCES, ToolLang.LANDING_LOOKS, ToolLang.LANDING_LOOKS_HINT,
                    ToolLang.LANDING_LOOKS_NOTE, () -> RiftfallItems.LOOTING_PICK.get(), (p, smith, m) -> open(p, smith, APPEARANCES)),
            ResidentLanding.Entry.action(CLAIM, ToolLang.LANDING_STARTER, List.of(ToolLang.LANDING_CLAIM_FREE,
                    ToolLang.LANDING_CLAIM_CARRIED, ToolLang.LANDING_CLAIM_SLOT), BlacksmithMenus::claimState, BlacksmithMenus::claim));

    private BlacksmithMenus() {}

    public static boolean canUse(Player player, UUID smith) { return ResidentMenus.canUse(player, smith, NpcRole.BLACKSMITH); }

    public static void open(ServerPlayer player, UUID smith, int page) {
        switch (page) {
            case RUNESTONES -> ResidentMenus.open(player, smith, NpcRole.BLACKSMITH, (id, inv, p) -> new BlacksmithMenu(id, inv, smith));
            case APPEARANCES -> ResidentMenus.open(player, smith, NpcRole.BLACKSMITH, (id, inv, p) -> new BlacksmithAppearanceMenu(id, inv, smith));
            case TEMPER -> ResidentMenus.open(player, smith, NpcRole.BLACKSMITH,
                    (id, inv, p) -> new com.cyberday1.riftfall.home.npc.smith.SmithMenu(id, inv, smith, com.cyberday1.riftfall.home.npc.smith.SmithService.RETEMPER));
            case SALVAGE -> ResidentMenus.open(player, smith, NpcRole.BLACKSMITH,
                    (id, inv, p) -> new com.cyberday1.riftfall.home.npc.smith.SmithMenu(id, inv, smith, com.cyberday1.riftfall.home.npc.smith.SmithService.SALVAGE));
            case BULK_SCRAP -> ResidentMenus.open(player, smith, NpcRole.BLACKSMITH,
                    (id, inv, p) -> new com.cyberday1.riftfall.home.npc.smith.BulkScrapMenu(id, inv, smith));
            case TOOLS -> ResidentMenus.open(player, smith, NpcRole.BLACKSMITH,
                    (id, inv, p) -> new com.cyberday1.riftfall.home.npc.smith.ToolWorkMenu(id, inv, smith));
            default -> ResidentMenus.openLanding(player, smith);
        }
    }

    /** 0 free; 1 already carries a pick; 2 needs an empty main hand and cursor. */
    static int claimState(ServerPlayer player, AbstractContainerMenu menu) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++)
            if (LootingToolItem.isTool(inventory.getItem(i))) return 1;
        return player.getMainHandItem().isEmpty() && menu.getCarried().isEmpty() ? 0 : 2;
    }

    /** A fresh level-1 pick; it restores nothing, upgrades live on the lost item. */
    static void claim(ServerPlayer player, UUID smith, AbstractContainerMenu menu) {
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(RiftfallItems.LOOTING_PICK.get()));
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }
}
