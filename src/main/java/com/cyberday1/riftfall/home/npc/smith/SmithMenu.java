package com.cyberday1.riftfall.home.npc.smith;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentServiceMenu;
import com.cyberday1.riftfall.home.npc.depth.ResidentDepth;
import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.jeweler.JewelerGems;
import com.cyberday1.riftfall.init.RiftfallMenus;
import com.cyberday1.riftfall.progression.RiftTier;
import com.cyberday1.riftfall.progression.RiftXp;
import com.cyberday1.riftfall.stat.RiftStats;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/** The Blacksmith's re-temper and salvage page: gear, stock and shards in, two outputs for salvage. */
public final class SmithMenu extends ResidentServiceMenu<SmithService.Quote> {
    private static final Layout LAYOUT = new Layout(new int[][]{{20, 36}, {78, 36}, {118, 36}, {238, 36}, {258, 36}}, 74, 154, 212);
    private static final EnumSet<SmithService.Result> DONE = EnumSet.of(SmithService.Result.RETEMPERED,
            SmithService.Result.SALVAGED);

    /** The server's quote, as the client draws it; {@code band} -1 is unlimited. */
    public record Shown(SmithService.Result result, int tier, int steps, int band, int playerCap, int shards, int stock,
                        int material, int stockOut, int funds) {}

    /** Opens the bulk scrap page; not a selection. */
    public static final int BULK_SCRAP = 2;
    private final DataSlot[] shown = new DataSlot[10];

    public SmithMenu(int id, Inventory inventory) { this(id, inventory, null, SmithService.RETEMPER); }
    public SmithMenu(int id, Inventory inventory, UUID smith, int page) {
        super(RiftfallMenus.SMITH.get(), id, inventory, smith, NpcRole.BLACKSMITH, LAYOUT);
        for (int i = 0; i < shown.length; i++) {
            shown[i] = DataSlot.standalone();
            shown[i].set(i == 0 ? SmithService.Result.NO_ITEM.ordinal() : 0);
            addDataSlot(shown[i]);
        }
        startAt(page);
    }

    @Override protected boolean accepts(int input, ItemStack stack) { return SmithService.accepts(input, stack); }
    @Override protected int maxStack(int input) { return SmithService.maxStack(input); }

    /** The player's tier cap: their level's tier, never a key's. */
    public static int playerCap(ServerPlayer player) {
        return RiftTier.of(RiftXp.levelFor(RiftStats.of(player.server).xpFor(player.getUUID()), RiftXp.LEVEL_CAP));
    }

    private SmithService.Context context() { return context(inventory.player); }

    /** The salvage and re-temper context for this player's home Fitter; a client gets an Apprentice stand-in. */
    public static SmithService.Context context(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer server) {
            var here = ResidentDepth.here(server, NpcRole.BLACKSMITH);
            boolean prospector = ResidentDepth.path(server.server, here.owner(), NpcRole.QUARRY_SPECIALIST)
                    .filter(ResidentPaths.PROSPECTOR::equals).isPresent();
            return new SmithService.Context(here.rank(), here.path(), prospector, playerCap(server), JewelerGems.current());
        }
        return new SmithService.Context(ResidentRank.APPRENTICE, Optional.empty(), false, 1, JewelerGems.current());
    }

    @Override protected SmithService.Quote quote(int selection) {
        return SmithService.quote(inputs, inventory, selection, context());
    }

    @Override protected boolean selectable(int selection) {
        return selection == SmithService.RETEMPER || selection == SmithService.SALVAGE;
    }

    @Override public boolean clickMenuButton(net.minecraft.world.entity.player.Player player, int button) {
        if (button != BULK_SCRAP) return super.clickMenuButton(player, button);
        if (!(player instanceof ServerPlayer server) || player.containerMenu != this || !stillValid(player)) return false;
        com.cyberday1.riftfall.tool.BlacksmithMenus.open(server, resident, com.cyberday1.riftfall.tool.BlacksmithMenus.BULK_SCRAP);
        return true;
    }

    @Override protected Committed commit(ServerPlayer player, SmithService.Quote quote) {
        var result = SmithService.commit(inputs, inventory, quote, context());
        if (DONE.contains(result))
            LogUtils.getLogger().info("Blacksmith: {} {} tier {} -> {}", player.getUUID(), result, quote.tier(), quote.tier() + 1);
        return new Committed(DONE.contains(result), Component.translatable(result.key()));
    }

    @Override public void broadcastChanges() {
        if (inventory.player instanceof ServerPlayer) {
            var q = preview();
            int[] values = {q.result().ordinal(), q.tier(), q.steps(), q.band() == Integer.MAX_VALUE ? -1 : q.band(), q.playerCap(),
                    q.shards(), q.stock(), q.material().getCount(), q.stockOut(), q.funds()};
            for (int i = 0; i < values.length; i++) shown[i].set(Math.clamp(values[i], Short.MIN_VALUE, Short.MAX_VALUE));
        }
        super.broadcastChanges();
    }

    public Shown shown() {
        int r = shown[0].get();
        var result = r < 0 || r >= SmithService.Result.values().length ? SmithService.Result.NO_ITEM : SmithService.Result.values()[r];
        return new Shown(result, shown[1].get(), shown[2].get(), shown[3].get(), shown[4].get(), shown[5].get(), shown[6].get(),
                shown[7].get(), shown[8].get(), shown[9].get());
    }
}
