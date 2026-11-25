package com.cyberday1.riftfall.home.npc.smith;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.ResidentServiceMenu;
import com.cyberday1.riftfall.home.npc.depth.ResidentDepth;
import com.cyberday1.riftfall.init.RiftfallMenus;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.UUID;

/** The Blacksmith's tool upgrade page: tool, rarity material, rare crystal and shards in; Fortune or a pick mode. */
public final class ToolWorkMenu extends ResidentServiceMenu<ToolWorkService.Quote> {
    private static final Layout LAYOUT = new Layout(new int[][]{{20, 36}, {78, 36}, {118, 36}, {158, 36}}, 74, 154, 212);

    /** The server's quote, as the client draws it. */
    public record Shown(ToolWorkService.Result result, int level, int target, int shards, int rarity, int material,
                        int crystals, int faceted, int funds) {}

    private final DataSlot[] shown = new DataSlot[9];

    public ToolWorkMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public ToolWorkMenu(int id, Inventory inventory, UUID smith) {
        super(RiftfallMenus.SMITH_TOOLS.get(), id, inventory, smith, NpcRole.BLACKSMITH, LAYOUT);
        for (int i = 0; i < shown.length; i++) {
            shown[i] = DataSlot.standalone();
            shown[i].set(i == 0 ? ToolWorkService.Result.NO_ITEM.ordinal() : 0);
            addDataSlot(shown[i]);
        }
        startAt(ToolWorkService.FORTUNE);
    }

    @Override protected boolean accepts(int input, ItemStack stack) { return ToolWorkService.accepts(input, stack); }
    @Override protected int maxStack(int input) { return ToolWorkService.maxStack(input); }

    public static Holder<Enchantment> fortune(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.FORTUNE).orElse(null);
    }

    private ToolWorkService.Context context() {
        if (inventory.player instanceof ServerPlayer server) {
            var here = ResidentDepth.here(server, NpcRole.BLACKSMITH);
            boolean toolwright = here.path().filter(ResidentPaths.TOOLWRIGHT::equals).isPresent();
            return new ToolWorkService.Context(here.rank(), toolwright, fortune(server.server));
        }
        return new ToolWorkService.Context(ResidentRank.APPRENTICE, false, null);
    }

    @Override protected ToolWorkService.Quote quote(int selection) {
        return ToolWorkService.quote(inputs, inventory, selection, context());
    }

    @Override protected boolean selectable(int selection) { return ToolWorkService.selectable(selection); }

    @Override protected Committed commit(ServerPlayer player, ToolWorkService.Quote quote) {
        var result = ToolWorkService.commit(inputs, inventory, quote, context());
        boolean done = result == ToolWorkService.Result.RAISED || result == ToolWorkService.Result.LEARNT;
        if (done) LogUtils.getLogger().info("Blacksmith tools: {} {} selection {} fortune {} -> {}", player.getUUID(), result,
                quote.selection(), quote.level(), quote.target());
        return new Committed(done, Component.translatable(result.key()));
    }

    @Override public void broadcastChanges() {
        if (inventory.player instanceof ServerPlayer) {
            var q = preview();
            int[] values = {q.result().ordinal(), q.level(), q.target(), q.shards(), q.rarity(), q.material(), q.crystals(), q.faceted(), q.funds()};
            for (int i = 0; i < values.length; i++) shown[i].set(Math.clamp(values[i], Short.MIN_VALUE, Short.MAX_VALUE));
        }
        super.broadcastChanges();
    }

    public Shown shown() {
        int r = shown[0].get();
        var results = ToolWorkService.Result.values();
        var result = r < 0 || r >= results.length ? ToolWorkService.Result.NO_ITEM : results[r];
        return new Shown(result, shown[1].get(), shown[2].get(), shown[3].get(), shown[4].get(), shown[5].get(),
                shown[6].get(), shown[7].get(), shown[8].get());
    }
}
