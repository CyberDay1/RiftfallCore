package com.cyberday1.riftfall.home.npc.cartographer;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentLanding;
import com.cyberday1.riftfall.home.npc.ResidentMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/** The Cartographer's landing: one tile, the atlas, which is also where keys are attuned from Journeyman (ruling 12). */
public final class CartographerMenus {
    public static final int ATLAS = 1;

    public static final List<ResidentLanding.Entry> LANDING = List.of(
            ResidentLanding.Entry.service(ATLAS, CartographerLang.LANDING_ATLAS, CartographerLang.LANDING_ATLAS_HINT,
                    CartographerLang.LANDING_ATLAS_NOTE, () -> Items.FILLED_MAP, (p, cartographer, m) -> open(p, cartographer)));

    private CartographerMenus() {}

    static void open(ServerPlayer player, UUID cartographer) {
        CartographerMenu.send(player);
        ResidentMenus.open(player, cartographer, NpcRole.RIFT_CARTOGRAPHER, (id, inventory, p) -> new CartographerMenu(id, inventory, cartographer, 0));
    }
}
