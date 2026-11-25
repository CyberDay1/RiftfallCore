package com.cyberday1.riftfall.home.npc.quarry;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.ResidentServiceMenu;
import com.cyberday1.riftfall.home.npc.conversion.ResidentConversionRecipe;
import com.cyberday1.riftfall.home.npc.conversion.ResidentConversions;
import com.cyberday1.riftfall.home.npc.depth.ResidentDepth;
import com.cyberday1.riftfall.home.npc.depth.ResidentProgress;
import com.cyberday1.riftfall.home.npc.depth.WorkOrders;
import com.cyberday1.riftfall.init.RiftfallMenus;
import com.cyberday1.riftfall.init.RiftfallTags;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.*;

/**
 * The Quarry Specialist's page: six input slots, a shard slot and an output. The rank, path and this player's surveys
 * travel in data slots so both sides list the same recipes and swap targets; the server's quote is synced for display.
 */
public final class QuarryMenu extends ResidentServiceMenu<QuarryService.Quote> {
    private static final Layout LAYOUT = new Layout(new int[][]{{166, 34}, {184, 34}, {202, 34}, {166, 52}, {184, 52}, {202, 52},
            {240, 34}, {278, 43}}, 74, 154, 212);

    /** What the client draws: the server's quote and the player's free order slots. */
    public record Shown(String status, int shards, int balance, boolean ordered, int runs, int minutes, int freeOrders) {}

    private final DataSlot rank = DataSlot.standalone(), path = DataSlot.standalone();
    private final DataSlot surveyedLow = DataSlot.standalone(), surveyedHigh = DataSlot.standalone();
    private final DataSlot chartedLow = DataSlot.standalone(), chartedHigh = DataSlot.standalone();
    private final DataSlot[] shown = new DataSlot[7];

    public QuarryMenu(int id, Inventory inventory) { this(id, inventory, null, 0); }
    public QuarryMenu(int id, Inventory inventory, UUID quarry, int page) {
        super(RiftfallMenus.QUARRY.get(), id, inventory, quarry, NpcRole.QUARRY_SPECIALIST, LAYOUT);
        path.set(-1);
        for (var slot : new DataSlot[]{rank, path, surveyedLow, surveyedHigh, chartedLow, chartedHigh}) addDataSlot(slot);
        for (int i = 0; i < shown.length; i++) { shown[i] = DataSlot.standalone(); addDataSlot(shown[i]); }
        if (inventory.player instanceof ServerPlayer server) sync(server);
        startAt(page);
    }

    @Override protected boolean accepts(int input, ItemStack stack) { return input != QuarryService.OUTPUT && QuarryService.accepts(input, stack); }

    public ResidentRank rank() { return ResidentRank.values()[Math.clamp(rank.get(), 0, ResidentRank.values().length - 1)]; }
    public Optional<String> path() {
        var paths = ResidentPaths.of(NpcRole.QUARRY_SPECIALIST);
        int i = path.get();
        return i >= 0 && i < paths.size() ? Optional.of(paths.get(i)) : Optional.empty();
    }

    /** The recipes this page lists, the same on both sides once the rank and path have synced. */
    public List<RecipeHolder<ResidentConversionRecipe>> recipes() {
        return ResidentConversions.visible(ResidentConversions.forRole(inventory.player.level().getRecipeManager(), NpcRole.QUARRY_SPECIALIST),
                NpcRole.QUARRY_SPECIALIST, rank(), path());
    }

    public List<Item> targets() {
        var crystals = BuiltInRegistries.ITEM.getTag(RiftfallTags.THEME_CRYSTALS).map(set -> set.stream().map(h -> h.value()).toList())
                .orElse(List.of());
        return QuarryService.targets(crystals);
    }

    public boolean surveyed(int target) {
        int bits = surveyedLow.get() & 0x1FF | (surveyedHigh.get() & 0x1FF) << 9;
        return target >= 0 && target < 18 && (bits >> target & 1) == 1;
    }

    /** Whether a swap target's theme is in this player's atlas (synced). */
    public boolean charted(int target) {
        int bits = chartedLow.get() & 0x1FF | (chartedHigh.get() & 0x1FF) << 9;
        return target >= 0 && target < 18 && (bits >> target & 1) == 1;
    }

    private QuarryService.Context context() {
        ResidentConversions.Context conversion;
        Set<ResourceLocation> charted;
        if (inventory.player instanceof ServerPlayer server) {
            var here = ResidentDepth.here(server, NpcRole.QUARRY_SPECIALIST);
            conversion = here.conversion(NpcRole.QUARRY_SPECIALIST, ResidentConversions.safe(server.server.getRecipeManager()));
            charted = com.cyberday1.riftfall.home.npc.cartographer.CartographerCrystals.charted(ResidentProgress.of(server.server).player(server.getUUID()));
        } else {
            var known = new HashSet<ResourceLocation>();
            charted = new HashSet<>();
            var targets = targets();
            for (int i = 0; i < targets.size(); i++) {
                if (surveyed(i)) known.add(BuiltInRegistries.ITEM.getKey(targets.get(i)));
                if (charted(i)) charted.add(BuiltInRegistries.ITEM.getKey(targets.get(i)));
            }
            conversion = new ResidentConversions.Context(NpcRole.QUARRY_SPECIALIST, rank(), path(), Integer.MAX_VALUE, known, 1, true);
        }
        return new QuarryService.Context(conversion, recipes(), targets(), charted);
    }

    @Override protected QuarryService.Quote quote(int selection) {
        return QuarryService.quote(inputs, inventory, selection, context());
    }

    @Override protected boolean selectable(int selection) {
        if (selection == QuarryService.SURVEY) return true;
        if (selection >= 0 && selection < QuarryService.RECIPES) return selection < recipes().size();
        int t = selection - QuarryService.SWAP;
        return t >= 0 && t < targets().size() && rank().atLeast(ResidentRank.JOURNEYMAN);
    }

    @Override protected Committed commit(ServerPlayer player, QuarryService.Quote quote) {
        var ctx = context();
        boolean done;
        String message;
        switch (quote.kind()) {
            case SURVEY -> {
                var progress = ResidentProgress.of(player.server);
                done = QuarryService.survey(quote, inputs, inventory, ctx, id -> progress.player(player.getUUID()).discover(id));
                if (done) progress.setDirty();
                message = done ? QuarryLang.SURVEYED : QuarryLang.CHANGED;
                // A survey is free and repeatable for new crystals only; it earns no resident XP.
                if (done) { sync(player); player.displayClientMessage(Component.translatable(message, quote.result().getHoverName()), true); }
                return new Committed(false, done ? null : Component.translatable(message));
            }
            case SWAP -> {
                done = QuarryService.swap(quote, inputs, inventory, ctx);
                message = done ? QuarryLang.SWAPPED : QuarryLang.CHANGED;
            }
            case RECIPE -> {
                var holder = ctx.recipes().get(quote.selection());
                var here = ResidentDepth.here(player, NpcRole.QUARRY_SPECIALIST);
                var view = new ResidentConversions.Slots(inputs, 0, QuarryService.INPUTS, QuarryService.SHARDS, QuarryService.OUTPUT, inventory);
                done = ResidentConversions.commit(holder, ctx.conversion(), view, quote.recipe(), (h, consumed, consume) -> {
                    if (here.owner() == null) return false;
                    var orders = WorkOrders.of(player.server);
                    var order = h.value().workOrder().orElseThrow();
                    long online = ResidentProgress.of(player.server).player(player.getUUID()).onlineTicks();
                    return orders.startRecipe(player.getUUID(), here.owner(), NpcRole.QUARRY_SPECIALIST, here.rank(), h.id(), 1,
                            order.runs(), order.minutes(), online, consumed, () -> ResidentDepth.saveNow(player.server), consume) != null;
                });
                message = !done ? QuarryLang.CHANGED : quote.ordered() ? QuarryLang.ORDERED : QuarryLang.MADE;
                if (done) LogUtils.getLogger().info("Quarry: {} {} {}", player.getUUID(), quote.ordered() ? "ordered" : "made", holder.id());
            }
            default -> { done = false; message = QuarryLang.CHANGED; }
        }
        return new Committed(done, Component.translatable(message));
    }

    private void sync(ServerPlayer player) {
        var here = ResidentDepth.here(player, NpcRole.QUARRY_SPECIALIST);
        rank.set(here.rank().ordinal());
        path.set(ResidentPaths.index(NpcRole.QUARRY_SPECIALIST, here.path().orElse(null)));
        int bits = 0;
        var targets = targets();
        for (int i = 0; i < targets.size(); i++)
            if (here.discoveries().contains(BuiltInRegistries.ITEM.getKey(targets.get(i)))) bits |= 1 << i;
        surveyedLow.set(bits & 0x1FF);
        surveyedHigh.set(bits >> 9 & 0x1FF);
        var charted = com.cyberday1.riftfall.home.npc.cartographer.CartographerCrystals.charted(ResidentProgress.of(player.server).player(player.getUUID()));
        int chartBits = 0;
        for (int i = 0; i < targets.size(); i++) if (charted.contains(BuiltInRegistries.ITEM.getKey(targets.get(i)))) chartBits |= 1 << i;
        chartedLow.set(chartBits & 0x1FF);
        chartedHigh.set(chartBits >> 9 & 0x1FF);
    }

    @Override public void broadcastChanges() {
        if (inventory.player instanceof ServerPlayer server) {
            sync(server);
            var q = preview();
            int free = ResidentDepth.here(server, NpcRole.QUARRY_SPECIALIST).freeOrders();
            int[] values = {QuarryService.STATUSES.indexOf(q.status()), q.shards(), q.balance(), q.ordered() ? 1 : 0, q.runs(), q.minutes(), free};
            for (int i = 0; i < values.length; i++) shown[i].set(Math.clamp(values[i], Short.MIN_VALUE, Short.MAX_VALUE));
        }
        super.broadcastChanges();
    }

    public Shown shown() {
        int s = shown[0].get();
        String status = s >= 0 && s < QuarryService.STATUSES.size() ? QuarryService.STATUSES.get(s) : QuarryService.NOTHING;
        return new Shown(status, shown[1].get(), shown[2].get(), shown[3].get() == 1, shown[4].get(), shown[5].get(), shown[6].get());
    }
}
