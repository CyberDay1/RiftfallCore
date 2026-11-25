package com.cyberday1.riftfall.home.npc.depth;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentLang;
import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.ShardWallet;
import com.cyberday1.riftfall.home.npc.conversion.ResidentConversionRecipe;
import com.cyberday1.riftfall.home.npc.depth.commission.CommissionBoard;
import com.cyberday1.riftfall.home.npc.depth.commission.Commissions;
import com.cyberday1.riftfall.network.ResidentBoardPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import com.cyberday1.riftfall.home.npc.jeweler.JewelerService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * The landing's Requests, Orders and Path tabs on the server: what they show, and the buttons behind them. Every
 * button is rechecked here; the client's copy is display only. Delivery and rerolls are for home members; a path is
 * the home owner's choice (SP1); a respec needs the retrain request, ten home runs and {@code 20 x home tier} shards.
 * Any member pays the next station level in gem-fused slate and shards.
 */
public final class ResidentBoard {
    public static final int DELIVER = 200, REROLL = 220, COLLECT = 230, CANCEL = 240, CHOOSE = 260, RESPEC = 270, STATION = 275, LAST = 279;
    public static final ResourceLocation SLATE = ResourceLocation.fromNamespaceAndPath("apotheosis", "gem_fused_slate");

    private ResidentBoard() {}

    public static boolean handles(int button) { return button >= DELIVER && button <= LAST; }

    /** This player's orders at this resident in this home, in the order they were started. */
    static List<WorkOrders.Order> orders(ServerPlayer player, UUID owner, NpcRole role) {
        return WorkOrders.of(player.server).of(player.getUUID()).stream()
                .filter(o -> o.role == role && o.home.equals(owner)).toList();
    }

    static int deliverable(ServerPlayer player, CommissionBoard.Request request) {
        int total = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) total += Commissions.accepts(request, inventory.getItem(i));
        return Math.min(total, Math.max(0, request.target - request.progress()));
    }

    public static ResidentBoardPayload snapshot(ServerPlayer player, NpcRole role) {
        var owner = ResidentDepth.homeOf(player);
        if (owner == null) return ResidentBoardPayload.EMPTY;
        var server = player.server;
        var home = HomeResidentDepth.of(server).home(owner);
        var r = home.role(role);
        boolean member = ResidentDepth.member(server, player.getUUID(), owner);
        var requests = new ArrayList<ResidentBoardPayload.Request>();
        for (var q : r.board().requests())
            requests.add(new ResidentBoardPayload.Request(q.def.toString(), q.kind.getSerializedName(), q.theme.map(Object::toString).orElse(""),
                    q.target, q.progress(), q.minRunTier, q.tierOffset, q.minutes, member ? deliverable(player, q) : 0));
        int refresh = CommissionBoard.REFRESH_RUNS - home.homeRuns() % CommissionBoard.REFRESH_RUNS;
        long online = ResidentProgress.of(server).player(player.getUUID()).onlineTicks();
        var orders = new ArrayList<ResidentBoardPayload.Order>();
        for (var o : orders(player, owner, role)) {
            int minutesDone = o.minutesDone(online);
            boolean cancellable = o.kind == WorkOrders.Kind.ITEM || o.materialised() || !o.progressed(online);
            orders.add(new ResidentBoardPayload.Order(o.id, show(player, o), o.runsDone(), o.runs, minutesDone, o.minutes,
                    o.ready(online), cancellable));
        }
        boolean isOwner = owner.equals(player.getUUID());
        String path = r.specialisation().orElse("");
        String pathState = !isOwner ? HomeResidentDepth.PathResult.NOT_OWNER.name()
                : r.rank() != ResidentRank.MASTER ? HomeResidentDepth.PathResult.NOT_MASTER.name()
                : r.specialisation().isPresent() ? HomeResidentDepth.PathResult.ALREADY_CHOSEN.name() : "";
        String respec = "";
        if (r.specialisation().isPresent()) {
            var other = ResidentPaths.of(role).stream().filter(p -> !p.equals(path)).findFirst().orElse(path);
            var answer = home.canRespec(role, other, isOwner);
            respec = answer == HomeResidentDepth.PathResult.DONE ? "" : answer.name();
        }
        int top = stationTop(player, role, r);
        var answer = home.canFundStation(role, top, member);
        int next = Math.min(3, r.stationFunded() + 1);
        var station = new ResidentBoardPayload.Station(r.stationFunded(), top, HomeResidentDepth.STATION_SLATE[next],
                HomeResidentDepth.STATION_SHARDS[next], answer == HomeResidentDepth.StationResult.DONE ? "" : answer.name());
        return new ResidentBoardPayload(role.ordinal(), requests, r.board().rerollCost(home.homeTier()), refresh, member, orders,
                path, pathState, respec, home.respecShards(), isOwner, station);
    }

    /** The highest station level this role has an overlay for, read in the level the player stands in. */
    private static int stationTop(ServerPlayer player, NpcRole role, HomeResidentDepth.Role r) {
        return StationOverlays.top(player.serverLevel(), role, r.specialisation().orElse(null));
    }

    /** Gem-fused slate in the player's inventory. */
    public static int slate(Inventory inventory) {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++)
            if (JewelerService.is(inventory.getItem(i), SLATE)) n += inventory.getItem(i).getCount();
        return n;
    }

    /** What an order makes, or the item it holds. */
    static ItemStack show(ServerPlayer player, WorkOrders.Order o) {
        if (!o.items().isEmpty()) return o.items().getFirst().copy();
        var holder = player.server.getRecipeManager().byKey(o.ref).orElse(null);
        if (holder != null && holder.value() instanceof ResidentConversionRecipe recipe)
            return recipe.result().copyWithCount(Math.min(99, recipe.result().getCount() * o.count));
        return ItemStack.EMPTY;
    }

    public static void send(ServerPlayer player, NpcRole role) {
        PacketDistributor.sendToPlayer(player, snapshot(player, role));
    }

    /** One tab button. Returns whether anything changed. */
    public static boolean handle(ServerPlayer player, NpcRole role, int button) {
        var owner = ResidentDepth.homeOf(player);
        if (owner == null) return false;
        var server = player.server;
        var depth = HomeResidentDepth.of(server);
        var home = depth.home(owner);
        var r = home.role(role);
        boolean member = ResidentDepth.member(server, player.getUUID(), owner);
        boolean changed = false;
        if (button >= DELIVER && button < DELIVER + 16) {
            int index = button - DELIVER;
            if (!member || index >= r.board().requests().size()) return false;
            var request = r.board().requests().get(index);
            var progress = ResidentProgress.of(server);
            var inventory = player.getInventory();
            int units = 0;
            for (int i = 0; i < inventory.getContainerSize() && !request.done(); i++) {
                var stack = inventory.getItem(i);
                if (Commissions.accepts(request, stack) <= 0) continue;
                units += Commissions.deliver(server, home, role, request, player.getUUID(), stack, progress);
            }
            if (units > 0) {
                inventory.setChanged();
                depth.setDirty();
                progress.setDirty();
                player.displayClientMessage(Component.translatable(BoardLang.DELIVERED, units), true);
                if (request.done()) ResidentDepth.handBack(player, progress);
                LogUtils.getLogger().info("Commission delivery: {} gave {} to {} {}", player.getUUID(), units, role.id(), request.def);
                changed = true;
            }
        } else if (button == REROLL) {
            if (!member) return false;
            int cost = r.board().rerollCost(home.homeTier());
            var payment = ShardWallet.plan(null, -1, player.getInventory(), cost);
            if (payment == null) { player.displayClientMessage(Component.translatable(BoardLang.REROLL_SHORT, cost), true); return false; }
            long open = r.board().requests().stream().filter(q -> !q.done()).count();
            var fresh = Commissions.post(server, owner, home, role, (int) open, (int) (server.overworld().getGameTime() & 0x7FFFFFFF));
            if (!payment.pay()) return false;
            r.board().reroll(fresh);
            depth.setDirty();
            player.getInventory().setChanged();
            player.displayClientMessage(Component.translatable(BoardLang.REROLLED), true);
            changed = true;
        } else if (button == COLLECT) {
            changed = ResidentDepth.collect(player, o -> o.role == role && o.home.equals(owner)) > 0;
        } else if (button >= CANCEL && button < CANCEL + 16) {
            var mine = orders(player, owner, role);
            int index = button - CANCEL;
            if (index >= mine.size()) return false;
            long online = ResidentProgress.of(server).player(player.getUUID()).onlineTicks();
            var answer = WorkOrders.of(server).cancel(player.getUUID(), mine.get(index).id, online, stack -> player.getInventory().add(stack));
            player.displayClientMessage(Component.translatable(answer == WorkOrders.Cancel.DONE ? BoardLang.CANCELLED : BoardLang.CANCEL_REFUSED), true);
            changed = answer == WorkOrders.Cancel.DONE;
        } else if (button >= CHOOSE && button < CHOOSE + 3) {
            var paths = ResidentPaths.of(role);
            int index = button - CHOOSE;
            if (index >= paths.size()) return false;
            var answer = home.choose(role, paths.get(index), owner.equals(player.getUUID()));
            if (answer == HomeResidentDepth.PathResult.DONE) {
                depth.setDirty();
                announce(player, role, paths.get(index));
                changed = true;
            } else player.displayClientMessage(Component.translatable(BoardLang.pathState(answer.name())), true);
        } else if (button == STATION) {
            var answer = home.canFundStation(role, stationTop(player, role, r), member);
            if (answer != HomeResidentDepth.StationResult.DONE) {
                player.displayClientMessage(Component.translatable(BoardLang.stationState(answer.name())), true);
                return false;
            }
            int next = r.stationFunded() + 1, slate = HomeResidentDepth.STATION_SLATE[next], shards = HomeResidentDepth.STATION_SHARDS[next];
            var inventory = player.getInventory();
            var payment = ShardWallet.plan(null, -1, inventory, shards);
            if (payment == null || slate(inventory) < slate) {
                player.displayClientMessage(Component.translatable(BoardLang.STATION_SHORT, slate, shards), true);
                return false;
            }
            if (!payment.pay()) return false;
            int left = slate;
            for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
                var stack = inventory.getItem(i);
                if (!JewelerService.is(stack, SLATE)) continue;
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
            home.fundStation(role);
            depth.setDirty();
            inventory.setChanged();
            player.displayClientMessage(Component.translatable(BoardLang.STATION_FUNDED), true);
            LogUtils.getLogger().info("Station funded: {} {} level {}", owner, role.id(), next);
            changed = true;
        } else if (button >= RESPEC && button < RESPEC + 3) {
            var paths = ResidentPaths.of(role);
            int index = button - RESPEC;
            if (index >= paths.size()) return false;
            boolean isOwner = owner.equals(player.getUUID());
            var answer = home.canRespec(role, paths.get(index), isOwner);
            if (answer != HomeResidentDepth.PathResult.DONE) {
                player.displayClientMessage(Component.translatable(BoardLang.pathState(answer.name())), true);
                return false;
            }
            var payment = ShardWallet.plan(null, -1, player.getInventory(), home.respecShards());
            if (payment == null) { player.displayClientMessage(Component.translatable(BoardLang.RESPEC_SHORT, home.respecShards()), true); return false; }
            if (!payment.pay()) return false;
            home.respec(role, paths.get(index), true);
            depth.setDirty();
            player.getInventory().setChanged();
            announce(player, role, paths.get(index));
            changed = true;
        }
        if (changed) send(player, role);
        return changed;
    }

    private static void announce(ServerPlayer player, NpcRole role, String path) {
        var line = Component.translatable(BoardLang.PATH_CHOSEN, Component.translatable(ResidentLang.name(role)),
                Component.translatable(ResidentPaths.name(role, path)));
        var owner = ResidentDepth.homeOf(player);
        for (var other : player.server.getPlayerList().getPlayers())
            if (ResidentDepth.member(player.server, other.getUUID(), owner)) other.sendSystemMessage(line);
        LogUtils.getLogger().info("Resident path: {} {} -> {}", owner, role.id(), path);
    }
}
