package com.cyberday1.riftfall.home.npc.depth.commission;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentLang;
import com.cyberday1.riftfall.home.npc.conversion.ResidentConversions;
import com.cyberday1.riftfall.home.npc.depth.DepthLang;
import com.cyberday1.riftfall.home.npc.depth.HomeResidentDepth;
import com.cyberday1.riftfall.home.npc.depth.ResidentProgress;
import com.cyberday1.riftfall.rift.RiftRunReport;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** The request boards on a live server: posting, advancing from settled runs, delivery and completion. */
public final class Commissions {
    private Commissions() {}

    public record Advanced(NpcRole role, CommissionBoard.Request request, boolean completed) {}

    /** Posts a fresh board when one is due. Seeded by home, role and refresh, so a reload posts the same board. */
    public static boolean refresh(MinecraftServer server, UUID owner, HomeResidentDepth.Home home, NpcRole role) {
        var r = home.role(role);
        if (!r.unlocked() || !r.board().due(home.homeRuns())) return false;
        r.board().refresh(home.homeRuns(), post(server, owner, home, role, r.rank().boardSlots() + bonusSlots(home, role), 0));
        return true;
    }

    /** An Expert Cartographer adds one slot to the Quartermaster's board. */
    public static int bonusSlots(HomeResidentDepth.Home home, NpcRole role) {
        if (role != NpcRole.QUARTERMASTER) return 0;
        var cartographer = home.roles().get(NpcRole.RIFT_CARTOGRAPHER);
        return cartographer != null && cartographer.rank().atLeast(com.cyberday1.riftfall.home.npc.ResidentRank.EXPERT) ? 1 : 0;
    }

    /** Bounty Scout: boss requests pay half again as much resident XP, at every resident of the home. */
    public static int xp(HomeResidentDepth.Home home, CommissionBoard.Request request) {
        int xp = request.xp * HomeResidentDepth.band(home.homeTier());
        var cartographer = home.roles().get(NpcRole.RIFT_CARTOGRAPHER);
        boolean scout = cartographer != null && cartographer.specialisation()
                .filter(com.cyberday1.riftfall.home.npc.ResidentPaths.BOUNTY_SCOUT::equals).isPresent();
        return scout && request.kind == CommissionDef.Kind.BOSS ? xp * 3 / 2 : xp;
    }

    public static List<CommissionBoard.Request> post(MinecraftServer server, UUID owner, HomeResidentDepth.Home home,
                                                     NpcRole role, int slots, int salt) {
        var r = home.role(role);
        var random = new Random(Objects.hash(owner, role.id(), home.homeRuns() / CommissionBoard.REFRESH_RUNS, salt));
        boolean retrainOpen = r.rank() == com.cyberday1.riftfall.home.npc.ResidentRank.MASTER && r.specialisation().isPresent();
        return CommissionPool.generate(CommissionPool.of(role), home.homeTier(), slots, retrainOpen, random,
                CommissionPool.live(server.registryAccess(), home.clearedLastCycle(home.homeRuns() / CommissionBoard.REFRESH_RUNS)));
    }

    /**
     * Advances at most {@link CommissionBoard#MAX_PER_RUN} open requests across the home's boards, in role then board
     * order, by the best member's facts. Every member in the run is a contributor. Pure apart from the reports' tags.
     */
    public static List<Advanced> advance(HomeResidentDepth.Home home, List<RiftRunReport> members, int homeTierAtRun) {
        var ids = members.stream().map(RiftRunReport::player).toList();
        var out = new ArrayList<Advanced>();
        for (var role : NpcRole.values()) {
            var r = home.roles().get(role);
            if (r == null || !r.unlocked()) continue;
            for (var request : r.board().requests()) {
                if (out.size() >= CommissionBoard.MAX_PER_RUN) return out;
                int delta = 0;
                for (var report : members) delta = Math.max(delta, request.delta(report, homeTierAtRun));
                if (delta <= 0) continue;
                boolean completed = request.advance(delta, ids);
                request.counted(members.getFirst().theme());
                out.add(new Advanced(role, request, completed));
            }
        }
        return out;
    }

    /** XP once to the home, rewards to every contributor (held until they can take them). */
    public static HomeResidentDepth.Award complete(MinecraftServer server, HomeResidentDepth.Home home, NpcRole role,
                                                   CommissionBoard.Request request, ResidentProgress progress) {
        var award = home.award(role, HomeResidentDepth.Source.COMMISSION, xp(home, request));
        home.completed(role, request.retrain);
        var rewards = new ArrayList<ItemStack>();
        CommissionPool.find(request.def).ifPresent(entry -> {
            for (var stack : entry.def().rewards()) {
                if (ResidentConversions.permittedResult(stack)) rewards.add(stack.copy());
                else LogUtils.getLogger().error("Commission {} reward {} refused by the result rule", request.def, stack);
            }
        });
        for (var player : request.contributors()) if (!rewards.isEmpty()) progress.reward(player, rewards);
        return award;
    }

    /** Units a delivery request would take from {@code stack}; 0 if it is the wrong item or not a delivery. */
    public static int accepts(CommissionBoard.Request request, ItemStack stack) {
        if (request.kind != CommissionDef.Kind.DELIVERY || request.done() || request.tag.isEmpty() || stack.isEmpty()) return 0;
        if (!stack.is(TagKey.create(Registries.ITEM, request.tag.get())) || !stack.is(CommissionPool.DELIVERABLE)) return 0;
        return Math.min(stack.getCount(), request.target - request.progress());
    }

    /**
     * A home member hands in rift material: consumed from {@code stack}, progress up, XP 1 per unit times the
     * home tier band under the per-run delivery cap. Returns units taken.
     */
    public static int deliver(MinecraftServer server, HomeResidentDepth.Home home, NpcRole role,
                              CommissionBoard.Request request, UUID player, ItemStack stack, ResidentProgress progress) {
        int units = accepts(request, stack);
        if (units <= 0) return 0;
        stack.shrink(units);
        boolean completed = request.advance(units, List.of(player));
        home.award(role, HomeResidentDepth.Source.DELIVERY, units * HomeResidentDepth.band(home.homeTier()));
        if (completed) complete(server, home, role, request, progress);
        return units;
    }

    public static Component toast(Advanced advanced) {
        var name = Component.translatable(ResidentLang.name(advanced.role()));
        return advanced.completed() ? Component.translatable(DepthLang.REQUEST_DONE, name)
                : Component.translatable(DepthLang.REQUEST_PROGRESS, name, advanced.request().progress(), advanced.request().target);
    }
}
