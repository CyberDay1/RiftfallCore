package com.cyberday1.riftfall.home.npc.depth;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.HomeAccess;
import com.cyberday1.riftfall.home.npc.ResidentLang;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.conversion.ResidentConversionRecipe;
import com.cyberday1.riftfall.home.npc.conversion.ResidentConversions;
import com.cyberday1.riftfall.home.npc.depth.commission.Commissions;
import com.cyberday1.riftfall.rift.RiftRunReport;
import com.cyberday1.riftfall.rift.RiftSettledEvent;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/** The depth layers on a live server: settle, service XP, the landing panel, online time, rewards and orders. */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class ResidentDepth {
    private ResidentDepth() {}

    /** A guest uses the host's resident but never advances it. */
    public static boolean member(MinecraftServer server, UUID player, UUID owner) {
        return owner != null && owner.equals(HomeAccess.homeOwner(server, player));
    }

    /** The home this player is standing in, or null (also for a player not attached to a server). */
    public static UUID homeOf(ServerPlayer player) {
        if (player.server == null || player.serverLevel() == null) return null;
        return HomeAccess.owner(player.serverLevel().dimension().location());
    }

    /**
     * What a service needs to know about where the player stands: the home, the resident's rank and path, the home
     * tier, this player's surveys and free order slots. Outside a home: Apprentice, no path, no orders.
     */
    public record Here(UUID owner, ResidentRank rank, Optional<String> path, int homeTier, Set<net.minecraft.resources.ResourceLocation> discoveries,
                       int freeOrders) {
        public com.cyberday1.riftfall.home.npc.conversion.ResidentConversions.Context conversion(NpcRole role, boolean safe) {
            return new com.cyberday1.riftfall.home.npc.conversion.ResidentConversions.Context(role, rank, path, homeTier, discoveries, freeOrders, safe);
        }
    }

    public static Here here(ServerPlayer player, NpcRole role) {
        var owner = homeOf(player);
        var discoveries = player.server == null ? Set.<net.minecraft.resources.ResourceLocation>of()
                : ResidentProgress.of(player.server).player(player.getUUID()).discoveries();
        if (owner == null) return new Here(null, ResidentRank.APPRENTICE, Optional.empty(), 1, discoveries, 0);
        var home = HomeResidentDepth.of(player.server).peek(owner).orElse(null);
        var rank = home == null ? ResidentRank.APPRENTICE : HomeResidentDepth.of(player.server).rank(owner, role);
        var path = home == null || !home.roles().containsKey(role) ? Optional.<String>empty() : home.roles().get(role).specialisation();
        int free = WorkOrders.of(player.server).free(player.getUUID(), owner, role, rank);
        return new Here(owner, rank, path, home == null ? 1 : home.homeTier(), discoveries, free);
    }

    /** The path a home has chosen for a role, if any. */
    public static Optional<String> path(MinecraftServer server, UUID owner, NpcRole role) {
        if (owner == null) return Optional.empty();
        return HomeResidentDepth.of(server).peek(owner).map(h -> h.roles().get(role)).flatMap(r -> r == null ? Optional.empty() : r.specialisation());
    }

    /** Persists the overworld records before a service consumes anything for a work order. */
    public static void saveNow(MinecraftServer server) { server.overworld().getDataStorage().save(); }

    /** One completed service transaction: 1 XP, at most 5 per resident per home run, members only. */
    public static void service(ServerPlayer player, NpcRole role) {
        var owner = homeOf(player);
        if (owner == null || !member(player.server, player.getUUID(), owner)) return;
        var depth = HomeResidentDepth.of(player.server);
        var award = depth.home(owner).award(role, HomeResidentDepth.Source.SERVICE, 1);
        if (award.granted() > 0) depth.setDirty();
        if (award.promoted()) announce(player.server, owner, role, award.after());
    }

    /** A registered resident is unlocked for milestones and boards. */
    public static void unlock(MinecraftServer server, UUID owner, NpcRole role) {
        if (owner == null) return;
        var depth = HomeResidentDepth.of(server);
        if (depth.home(owner).unlock(role)) depth.setDirty();
    }

    static void announce(MinecraftServer server, UUID owner, NpcRole role, ResidentRank rank) {
        var line = Component.translatable(DepthLang.RANK_UP, Component.translatable(ResidentLang.name(role)),
                Component.translatable(DepthLang.rank(rank)));
        for (var player : server.getPlayerList().getPlayers())
            if (member(server, player.getUUID(), owner)) player.sendSystemMessage(line);
    }

    @SubscribeEvent
    public static void settled(RiftSettledEvent event) {
        var server = event.server();
        var depth = HomeResidentDepth.of(server);
        var progress = ResidentProgress.of(server);
        var byHome = new LinkedHashMap<UUID, List<RiftRunReport>>();
        for (var report : event.reports()) byHome.computeIfAbsent(report.home(), h -> new ArrayList<>()).add(report);
        var tierBefore = new HashMap<UUID, Integer>();
        var newBest = new HashMap<UUID, Boolean>();
        for (var entry : byHome.entrySet()) {
            var owner = entry.getKey();
            var members = entry.getValue();
            var home = depth.home(owner);
            var first = members.getFirst();
            var settled = home.settle(first.tier(), first.cleared(), first.theme());
            tierBefore.put(owner, settled.tierBefore());
            newBest.put(owner, settled.newBest());
            if (settled.counted()) {
                for (var role : NpcRole.values())
                    if (home.role(role).unlocked() && home.role(role).board().generation() < 0) Commissions.refresh(server, owner, home, role);
                for (var advanced : Commissions.advance(home, members, settled.tierBefore())) {
                    if (advanced.completed()) {
                        var award = Commissions.complete(server, home, advanced.role(), advanced.request(), progress);
                        if (award.promoted()) announce(server, owner, advanced.role(), award.after());
                    }
                    var toast = Commissions.toast(advanced);
                    for (var report : members) {
                        var player = server.getPlayerList().getPlayer(report.player());
                        if (player != null) player.displayClientMessage(toast, true);
                    }
                }
                for (var role : NpcRole.values()) Commissions.refresh(server, owner, home, role);
            }
            for (var role : settled.promoted()) announce(server, owner, role, home.role(role).rank());
        }
        depth.setDirty();
        var orders = WorkOrders.of(server);
        for (var report : event.reports()) {
            var boss = report.bosses().isEmpty() ? Optional.<net.minecraft.resources.ResourceLocation>empty() : Optional.of(report.bosses().getFirst());
            progress.player(report.player()).settled(report.tier(), report.cleared(),
                    newBest.getOrDefault(report.home(), false), boss, report.died());
            progress.player(report.player()).chart(report.theme(), report.tier(), report.cleared(), report.bosses());
            orders.settled(report.player(), home -> report.tier()
                    >= tierBefore.getOrDefault(home, depth.home(home).homeTier()) - 2);
            var player = server.getPlayerList().getPlayer(report.player());
            if (player != null) handBack(player, progress);
        }
        progress.setDirty();
    }

    /** Request rewards into the inventory; what does not fit waits. */
    public static void handBack(ServerPlayer player, ResidentProgress progress) {
        if (progress.player(player.getUUID()).pending().isEmpty()) return;
        progress.handBack(player.getUUID(), stack -> player.getInventory().add(stack));
        player.sendSystemMessage(Component.translatable(progress.player(player.getUUID()).pending().isEmpty()
                ? DepthLang.REWARDS : DepthLang.REWARDS_WAITING));
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) handBack(player, ResidentProgress.of(player.server));
    }

    /**
     * Online time for the work-order fallback clock: counted only while the player is on the server and an order of
     * theirs still reads it, so an idle record is not rewritten at every autosave.
     */
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % 20 != 0 || server.getPlayerList().getPlayerCount() == 0) return;
        var progress = ResidentProgress.of(server);
        var orders = WorkOrders.of(server);
        boolean counted = false;
        for (var player : server.getPlayerList().getPlayers()) {
            var mine = progress.player(player.getUUID());
            if (!orders.clocking(player.getUUID(), mine.onlineTicks())) continue;
            mine.online(20);
            counted = true;
        }
        if (counted) progress.setDirty();
    }

    /**
     * What the landing shows: rank, XP, the home's best clear, this visit's line, and whether this resident has
     * recognised their note for this player (their lines swap). Mutates dialogue state.
     */
    public record Panel(int rank, int xp, int best, int trigger, int variant, int arg, boolean known) {}

    public static Panel panel(ServerPlayer player, NpcRole role) {
        var owner = homeOf(player);
        if (owner == null) return null;
        com.cyberday1.riftfall.story.StoryBridge.residentMet(player, role);
        var server = player.server;
        var depth = HomeResidentDepth.of(server);
        var home = depth.home(owner);
        home.unlock(role);
        Commissions.refresh(server, owner, home, role);
        depth.setDirty();
        var r = home.role(role);
        var progress = ResidentProgress.of(server);
        var mine = progress.player(player.getUUID());
        int homeRank = com.cyberday1.riftfall.home.ShatteredHomeProgress.of(player.serverLevel()).unlockedTiles().size();
        var line = ResidentDialogue.pick(player.getUUID(), mine, role, r.rank().ordinal(),
                r.completed(), id -> BuiltInRegistries.ENTITY_TYPE.getId(BuiltInRegistries.ENTITY_TYPE.get(id)), homeRank);
        progress.setDirty();
        return new Panel(r.rank().ordinal(), r.xp(), home.bestCleared(), line.trigger().ordinal(), line.variant(), line.arg(),
                com.cyberday1.riftfall.lore.ResidentStory.known(role, mine.seen(role).noted()));
    }

    /** A ready recipe order's output, or null when its recipe no longer makes a permitted result. */
    static List<ItemStack> output(MinecraftServer server, WorkOrders.Order order) {
        var holder = server.getRecipeManager().byKey(order.ref).orElse(null);
        if (holder == null || !(holder.value() instanceof ResidentConversionRecipe recipe)
                || !ResidentConversions.permittedResult(recipe.result())) return null;
        var out = new ArrayList<ItemStack>();
        long total = (long) recipe.result().getCount() * order.count;
        while (total > 0) {
            var stack = recipe.result().copy();
            int n = (int) Math.min(total, stack.getMaxStackSize());
            stack.setCount(n);
            out.add(stack);
            total -= n;
        }
        return out;
    }

    public static int collect(ServerPlayer player) { return collect(player, o -> true); }

    public static int collect(ServerPlayer player, java.util.function.Predicate<WorkOrders.Order> which) {
        var server = player.server;
        var orders = WorkOrders.of(server);
        long online = ResidentProgress.of(server).player(player.getUUID()).onlineTicks();
        int done = orders.collect(player.getUUID(), online, which, o -> output(server, o), stack -> player.getInventory().add(stack));
        long running = orders.of(player.getUUID()).stream().filter(o -> !o.ready(online)).count();
        player.sendSystemMessage(Component.translatable(DepthLang.ORDERS_COLLECTED, done, running));
        if (orders.of(player.getUUID()).stream().anyMatch(WorkOrders.Order::materialised))
            player.sendSystemMessage(Component.translatable(DepthLang.ORDERS_LEFTOVER));
        LogUtils.getLogger().info("Work orders: {} collected {}", player.getUUID(), done);
        return done;
    }

    /** {@code /riftfall orders list}: every order of this player, in any home, in the order started. Returns the count. */
    public static int list(ServerPlayer player) {
        var server = player.server;
        var mine = WorkOrders.of(server).of(player.getUUID());
        if (mine.isEmpty()) {
            player.sendSystemMessage(Component.translatable(DepthLang.ORDERS_NONE));
            return 0;
        }
        long online = ResidentProgress.of(server).player(player.getUUID()).onlineTicks();
        long ready = mine.stream().filter(o -> o.ready(online)).count();
        player.sendSystemMessage(Component.translatable(DepthLang.ORDERS_SUMMARY, ready, mine.size() - ready));
        for (var o : mine) player.sendSystemMessage(line(o, itemName(ResidentBoard.show(player, o)), online));
        return mine.size();
    }

    /** One order's line: who has it, what it is, and its two clocks or that it is ready. */
    static Component line(WorkOrders.Order o, Component what, long online) {
        var who = Component.translatable(ResidentLang.name(o.role));
        return o.ready(online) ? Component.translatable(DepthLang.ORDERS_LINE_READY, who, what)
                : Component.translatable(DepthLang.ORDERS_LINE, who, what, o.runsDone(), o.runs,
                        Math.min(o.minutesDone(online), o.minutes), o.minutes);
    }

    /** "Rare Runestone x4", as the Orders tab names it. */
    private static Component itemName(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return Component.literal("?");
        return stack.getCount() > 1 ? stack.getHoverName().copy().append(" x" + stack.getCount()) : stack.getHoverName();
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("riftfall").then(Commands.literal("orders")
                .executes(ctx -> list(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("collect").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    var owner = homeOf(player);
                    if (owner == null || !owner.equals(HomeAccess.homeOwner(player.server, player.getUUID()))) {
                        player.sendSystemMessage(Component.translatable(DepthLang.ORDERS_NOT_HOME));
                        return 0;
                    }
                    return Math.max(1, collect(player));
                }))));
    }
}
