package com.cyberday1.riftfall.home.npc.cartographer;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.boss.RiftBosses;
import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.ResidentServiceMenu;
import com.cyberday1.riftfall.home.npc.depth.ResidentDepth;
import com.cyberday1.riftfall.home.npc.depth.ResidentProgress;
import com.cyberday1.riftfall.init.RiftfallMenus;
import com.cyberday1.riftfall.init.RiftfallRegistries;
import com.cyberday1.riftfall.network.AtlasPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * The Cartographer's page: a key, a crystal and a shard slot beside the atlas list. The list itself travels in an
 * {@link AtlasPayload} (rift themes are a server registry); rank and path ride data slots; the quote is the server's.
 */
public final class CartographerMenu extends ResidentServiceMenu<CartographerService.Quote> {
    private static final Layout LAYOUT = new Layout(new int[][]{{184, 40}, {222, 40}, {260, 40}}, 74, 154, 212);
    private static final int SYNC_TICKS = 40;

    /** What the client draws: the server's quote. */
    public record Shown(CartographerService.Status status, int tier, int shards, int balance, int crystals) {}

    private final DataSlot rank = DataSlot.standalone(), path = DataSlot.standalone();
    private final DataSlot[] shown = new DataSlot[5];
    private int ticks;

    public CartographerMenu(int id, Inventory inventory) { this(id, inventory, null, 0); }
    public CartographerMenu(int id, Inventory inventory, UUID cartographer, int page) {
        super(RiftfallMenus.CARTOGRAPHER.get(), id, inventory, cartographer, NpcRole.RIFT_CARTOGRAPHER, LAYOUT);
        path.set(-1);
        addDataSlot(rank);
        addDataSlot(path);
        for (int i = 0; i < shown.length; i++) { shown[i] = DataSlot.standalone(); addDataSlot(shown[i]); }
        startAt(page);
    }

    @Override protected boolean accepts(int input, ItemStack stack) { return CartographerService.accepts(input, stack); }
    @Override protected int maxStack(int input) { return CartographerService.maxStack(input); }

    public ResidentRank rank() { return ResidentRank.values()[Math.clamp(rank.get(), 0, ResidentRank.values().length - 1)]; }
    public Optional<String> path() {
        var paths = ResidentPaths.of(NpcRole.RIFT_CARTOGRAPHER);
        int i = path.get();
        return i >= 0 && i < paths.size() ? Optional.of(paths.get(i)) : Optional.empty();
    }

    /** Registered themes in id order, capped to the page's selections. */
    public static List<ResourceLocation> themes(MinecraftServer server) {
        return server.registryAccess().registry(RiftfallRegistries.RIFT_THEMES)
                .map(r -> r.registryKeySet().stream().map(k -> k.location()).sorted(Comparator.comparing(ResourceLocation::toString))
                        .limit(CartographerService.MAX_THEMES).toList())
                .orElse(List.of());
    }

    /** Bosses this player's atlas recorded, by theme then boss id, capped to the page's selections. */
    public static List<CartographerService.BossEntry> bosses(ResidentProgress.Player player) {
        var out = new ArrayList<CartographerService.BossEntry>();
        player.atlas().forEach((theme, chart) -> chart.bosses().keySet().forEach(boss -> out.add(new CartographerService.BossEntry(theme, boss))));
        return out.stream().limit(CartographerService.MAX_BOSSES).toList();
    }

    public static boolean onRoster(ResourceLocation boss, int tier) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(boss).map(type -> type.is(RiftBosses.rosterForTier(tier))).orElse(false);
    }

    /** The live context for a player on the server. */
    public static CartographerService.Context context(ServerPlayer player) {
        var here = ResidentDepth.here(player, NpcRole.RIFT_CARTOGRAPHER);
        var atlas = ResidentProgress.of(player.server).player(player.getUUID());
        return new CartographerService.Context(here.rank(), here.path(), themes(player.server), bosses(atlas), atlas::visited,
                CartographerCrystals::of, CartographerMenu::onRoster);
    }

    /** The client's copy, from the last atlas the server sent; display only. */
    private CartographerService.Context clientContext() {
        var atlas = com.cyberday1.riftfall.client.AtlasClient.latest();
        var themes = atlas.themes().stream().map(t -> ResourceLocation.tryParse(t.id())).filter(Objects::nonNull).toList();
        var visited = new HashSet<ResourceLocation>();
        for (var t : atlas.themes()) if (t.visits() > 0) visited.add(ResourceLocation.tryParse(t.id()));
        var bosses = atlas.bosses().stream().map(b -> new CartographerService.BossEntry(ResourceLocation.parse(b.theme()), ResourceLocation.parse(b.boss()))).toList();
        return new CartographerService.Context(rank(), path(), themes, bosses, visited::contains, CartographerCrystals::of, (b, t) -> true);
    }

    private CartographerService.Context context() {
        return inventory.player instanceof ServerPlayer server ? context(server) : clientContext();
    }

    @Override protected CartographerService.Quote quote(int selection) {
        return CartographerService.quote(inputs, inventory, selection, context());
    }

    @Override protected boolean selectable(int selection) {
        var ctx = context();
        if (selection >= 0 && selection < Math.min(CartographerService.MAX_THEMES, ctx.themes().size())) return true;
        int boss = selection - CartographerService.BOSS;
        return boss >= 0 && boss < Math.min(CartographerService.MAX_BOSSES, ctx.bosses().size()) && rank().atLeast(ResidentRank.EXPERT);
    }

    @Override protected Committed commit(ServerPlayer player, CartographerService.Quote quote) {
        boolean done = CartographerService.commit(quote, inputs, inventory, context(player));
        if (done) {
            LogUtils.getLogger().info("Cartographer: {} attuned a tier {} key to {}", player.getUUID(), quote.tier(), quote.after());
            send(player);
        }
        return new Committed(done, Component.translatable(done ? CartographerLang.ATTUNED : CartographerLang.CHANGED));
    }

    /** The atlas list for this player. */
    public static AtlasPayload snapshot(ServerPlayer player) {
        var atlas = ResidentProgress.of(player.server).player(player.getUUID());
        var themes = new ArrayList<AtlasPayload.Theme>();
        for (var id : themes(player.server)) {
            var chart = atlas.atlas().get(id);
            int kills = chart == null ? 0 : chart.bosses().values().stream().mapToInt(Integer::intValue).sum();
            themes.add(new AtlasPayload.Theme(id.toString(), chart == null ? 0 : chart.visits(), chart == null ? 0 : chart.clears(),
                    chart == null ? 0 : chart.best(), kills, CartographerCrystals.of(id).isPresent()));
        }
        var bosses = new ArrayList<AtlasPayload.Boss>();
        for (var entry : bosses(atlas))
            bosses.add(new AtlasPayload.Boss(entry.theme().toString(), entry.boss().toString(),
                    atlas.atlas().get(entry.theme()).bosses().getOrDefault(entry.boss(), 0)));
        return new AtlasPayload(themes, bosses);
    }

    static void send(ServerPlayer player) { PacketDistributor.sendToPlayer(player, snapshot(player)); }

    @Override public void broadcastChanges() {
        if (inventory.player instanceof ServerPlayer server) {
            if (ticks++ % SYNC_TICKS == 0) send(server);
            var here = ResidentDepth.here(server, NpcRole.RIFT_CARTOGRAPHER);
            rank.set(here.rank().ordinal());
            path.set(ResidentPaths.index(NpcRole.RIFT_CARTOGRAPHER, here.path().orElse(null)));
            var q = preview();
            int[] values = {q.status().ordinal(), q.tier(), q.shards(), q.balance(), q.crystals()};
            for (int i = 0; i < values.length; i++) shown[i].set(Math.clamp(values[i], Short.MIN_VALUE, Short.MAX_VALUE));
        }
        super.broadcastChanges();
    }

    public Shown shown() {
        int s = shown[0].get();
        var statuses = CartographerService.Status.values();
        var status = s >= 0 && s < statuses.length ? statuses[s] : CartographerService.Status.NOTHING;
        return new Shown(status, shown[1].get(), shown[2].get(), shown[3].get(), shown[4].get());
    }
}
