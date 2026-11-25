package com.cyberday1.riftfall.home.npc.jeweler;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.ResidentServiceMenu;
import com.cyberday1.riftfall.home.npc.depth.ResidentDepth;
import com.cyberday1.riftfall.home.npc.depth.ResidentProgress;
import com.cyberday1.riftfall.home.npc.depth.WorkOrders;
import com.cyberday1.riftfall.init.RiftfallMenus;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/** The Jeweler's one page: gear, gem, grit, two crystal slots, shards and a chisel/faceted slot in; the server's quote is synced. */
public final class JewelerMenu extends ResidentServiceMenu<JewelerService.Quote> {
    private static final Layout LAYOUT = new Layout(
            new int[][]{{20, 36}, {58, 36}, {118, 36}, {158, 36}, {178, 36}, {238, 36}, {84, 36}}, 74, 172, 230);
    private static final EnumSet<JewelerService.Result> DONE = EnumSet.of(JewelerService.Result.SET_DONE,
            JewelerService.Result.UNSET_DONE, JewelerService.Result.DISSOLVE_DONE, JewelerService.Result.REFINE_DONE,
            JewelerService.Result.SOCKET_DONE, JewelerService.Result.ORDER_STARTED);

    /** What the client draws: the server's quote, since costs are server data. */
    public record Shown(JewelerService.Result result, int purity, int shards, int grit, int theme, int rare,
                        int faceted, int yield, int funds, int sockets, int cap, int orderRuns, int orderMinutes, int rank,
                        int material) {}

    private final DataSlot[] shown = new DataSlot[15];

    public JewelerMenu(int id, Inventory inventory) { this(id, inventory, null, JewelerService.APPRAISE); }
    public JewelerMenu(int id, Inventory inventory, UUID jeweler, int page) {
        super(RiftfallMenus.JEWELER.get(), id, inventory, jeweler, NpcRole.JEWELER, LAYOUT);
        for (int i = 0; i < shown.length; i++) {
            shown[i] = DataSlot.standalone();
            shown[i].set(i == 0 ? -1 : 0);
            addDataSlot(shown[i]);
        }
        startAt(page);
    }

    @Override protected boolean accepts(int input, ItemStack stack) { return JewelerService.accepts(input, stack); }
    @Override protected int maxStack(int input) { return JewelerService.maxStack(input); }

    private JewelerService.Context context() {
        if (inventory.player instanceof ServerPlayer server) {
            var here = ResidentDepth.here(server, NpcRole.JEWELER);
            return new JewelerService.Context(here.rank(), here.path(), here.owner() != null, here.freeOrders());
        }
        return new JewelerService.Context(ResidentRank.APPRENTICE, Optional.empty(), false, 0);
    }

    @Override protected JewelerService.Quote quote(int selection) {
        return JewelerService.quote(inputs, inventory, selection, context(), JewelerCosts.current(), JewelerGems.current());
    }

    @Override protected boolean selectable(int selection) {
        return selection >= JewelerService.APPRAISE && selection <= JewelerService.REFINE_ORDER
                || selection >= JewelerService.UNSET && selection < JewelerService.UNSET + JewelerService.MAX_SOCKETS;
    }

    @Override protected Committed commit(ServerPlayer player, JewelerService.Quote quote) {
        var here = ResidentDepth.here(player, NpcRole.JEWELER);
        var result = JewelerService.commit(inputs, inventory, quote, context(), JewelerCosts.current(), JewelerGems.current(),
                (gem, runs, minutes, consume) -> {
                    if (here.owner() == null) return false;
                    long online = ResidentProgress.of(player.server).player(player.getUUID()).onlineTicks();
                    return WorkOrders.of(player.server).startItem(player.getUUID(), here.owner(), NpcRole.JEWELER, here.rank(),
                            JewelerOrders.PURITY_EDIT, gem, runs, minutes, online, () -> ResidentDepth.saveNow(player.server), consume) != null;
                });
        return new Committed(DONE.contains(result), Component.translatable(result.key()));
    }

    @Override public void broadcastChanges() {
        if (inventory.player instanceof ServerPlayer) {
            var q = preview();
            int[] values = {q.result().ordinal(), q.purity(), q.shards(), q.grit(), q.theme(), q.rare(), q.faceted(), q.yield(), q.funds(),
                    q.sockets(), q.cap(), q.orderRuns(), q.orderMinutes(), q.rank().ordinal(), q.chisels()};
            for (int i = 0; i < values.length; i++) shown[i].set(Math.clamp(values[i], Short.MIN_VALUE, Short.MAX_VALUE));
        }
        super.broadcastChanges();
    }

    public Shown shown() {
        int r = shown[0].get();
        var result = r < 0 || r >= JewelerService.Result.values().length ? JewelerService.Result.APPRAISE : JewelerService.Result.values()[r];
        return new Shown(result, shown[1].get(), shown[2].get(), shown[3].get(), shown[4].get(), shown[5].get(),
                shown[6].get(), shown[7].get(), shown[8].get(), shown[9].get(), shown[10].get(), shown[11].get(), shown[12].get(),
                shown[13].get(), shown[14].get());
    }
}
