package com.cyberday1.riftfall.home.npc;

import com.cyberday1.riftfall.block.marker.NpcRole;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Bundled CNPC humanoid skins; no borrowed hostile-mob behavior or external downloads. */
public record ResidentAppearance(String title, String texture, Item heldItem) {
    private static ResidentAppearance look(String title, String skin, Item item) {
        return new ResidentAppearance(title, "customnpcs:textures/entity/" + skin + ".png", item);
    }

    public static ResidentAppearance forRole(NpcRole role) {
        return switch (role) {
            case BLACKSMITH -> look("Tools & Weapons", "dwarfmale/blacksmith", Items.IRON_AXE);
            case QUARRY_SPECIALIST -> look("Materials & Refining", "dwarfmale/miner", Items.IRON_PICKAXE);
            case RIFT_CARTOGRAPHER -> look("Atlas & Attunement", "humanmale/steampunksteve", Items.SPYGLASS);
            case RELIC_SCHOLAR -> look("Relics & Research", "elfmale/dark_elf_librarian", Items.BOOK);
            case JEWELER -> look("Gems & Sockets", "dwarffemale/ignir", Items.AMETHYST_SHARD);
            case APOTHECARIST -> look("Remedies & Draughts", "humanfemale/robesbrownstephanie", Items.GLASS_BOTTLE);
            case QUARTERMASTER -> look("Supplies & Requests", "humanmale/tradersteve", Items.BUNDLE);
            case COMBAT_MENTOR -> look("Training & Mastery", "humanmale/guardsteve", Items.WOODEN_SWORD);
            case DRUID -> look("Rift Flora", "elffemale/woodelffemale", Items.FLOWERING_AZALEA);
            case COOK -> look("Meals & Provisions", "humanfemale/chefstephanie", Items.BREAD);
            case RIFT_STABILIZER -> look("Rift Modifiers", "elfmale/blood_elf_mage", Items.ECHO_SHARD);
            case RESTORATION_KEEPER -> look("Unique Restoration", "humanmale/robeswhitesteve", Items.BRUSH);
        };
    }
}
