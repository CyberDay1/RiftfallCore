package com.cyberday1.riftfall.home.npc;

import com.cyberday1.riftfall.block.marker.NpcRole;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuConstructor;

import java.util.UUID;

/** The one server boundary for opening and keeping resident menus. */
public final class ResidentMenus {
    private ResidentMenus() {}

    /** Ours, of this role, alive, in reach and the player may use this home. Clients always pass. */
    public static boolean canUse(Player player, UUID resident, NpcRole role) {
        if (!(player instanceof ServerPlayer server)) return true;
        var entity = entity(server, resident);
        var owned = entity == null ? null : HomeResidents.owned(entity);
        return owned != null && owned.role() == role && HomeResidents.canUse(server, entity);
    }

    private static Entity entity(ServerPlayer player, UUID resident) {
        return resident == null ? null : player.serverLevel().getEntity(resident);
    }

    /** Opens the landing for whichever role the entity holds. */
    public static void openLanding(ServerPlayer player, UUID resident) {
        var entity = entity(player, resident);
        var owned = entity == null ? null : HomeResidents.owned(entity);
        if (owned == null) return;
        open(player, resident, owned.role(), (id, inventory, opener) -> new ResidentLandingMenu(id, inventory, resident, owned.role()));
    }

    /** Titles are lang keys, so the client names the screen in its own language. */
    public static void open(ServerPlayer player, UUID resident, NpcRole role, MenuConstructor menu) {
        if (!canUse(player, resident, role)) return;
        player.openMenu(new SimpleMenuProvider(menu, title(role)));
    }

    /** Every resident screen is headed "Name, Role". */
    public static Component title(NpcRole role) { return ResidentLang.nameplate(role); }
}
