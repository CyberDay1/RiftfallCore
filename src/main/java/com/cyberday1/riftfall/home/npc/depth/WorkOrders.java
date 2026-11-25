package com.cyberday1.riftfall.home.npc.depth;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.gear.GearPool;
import com.cyberday1.riftfall.gear.GunGrade;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.init.RiftfallDataComponents;
import com.cyberday1.riftfall.tool.LootingToolItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Jobs that finish while the player runs rifts. The clock is the owner's settled runs at or above
 * {@code home tier - 2}, or minutes of the owner's online time, whichever comes first (WO1). Nothing ticks: runs are
 * counted at settle, online time by {@link ResidentProgress}. Recipe orders keep only what was consumed, for a
 * cancel before any progress; item orders hold the one real stack. Per player, on the overworld. No order ever holds
 * run gear (RULED 2026-11-02): see {@link #runGear}.
 */
public final class WorkOrders extends SavedData {
    private static final String FILE = RiftfallCore.MODID + "_work_orders";
    public static final long TICKS_PER_MINUTE = 1200;

    public enum Kind { RECIPE, ITEM }

    public static final class Order {
        public final UUID id, player, home;
        public final NpcRole role;
        public final Kind kind;
        /** RECIPE: the conversion recipe; ITEM: the registered edit applied on collect. */
        public final ResourceLocation ref;
        public final int count, runs, minutes;
        public final long startOnline;
        private int runsDone;
        private boolean materialised;
        /** ITEM: the held stack. After a collect that did not fit: what is still owed. */
        private final List<ItemStack> items = new ArrayList<>();
        /** RECIPE: what was consumed, returned by a cancel before progress; cleared at the first progress. */
        private final List<ItemStack> refund = new ArrayList<>();

        Order(UUID id, UUID player, UUID home, NpcRole role, Kind kind, ResourceLocation ref, int count, int runs,
              int minutes, long startOnline) {
            this.id = id; this.player = player; this.home = home; this.role = role; this.kind = kind; this.ref = ref;
            this.count = count; this.runs = runs; this.minutes = minutes; this.startOnline = startOnline;
        }

        public int runsDone() { return runsDone; }
        public boolean materialised() { return materialised; }
        public List<ItemStack> items() { return Collections.unmodifiableList(items); }
        public List<ItemStack> refund() { return Collections.unmodifiableList(refund); }

        /** Done by runs or by the owner's online minutes. An order with neither clock is ready at once. */
        public boolean ready(long onlineNow) {
            if (materialised) return true;
            boolean byRuns = runs > 0 && runsDone >= runs;
            boolean byTime = minutes > 0 && onlineNow - startOnline >= minutes * TICKS_PER_MINUTE;
            return byRuns || byTime || runs <= 0 && minutes <= 0;
        }

        /** Whole minutes of the owner's online time since the order started. */
        public int minutesDone(long onlineNow) {
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0, onlineNow - startOnline) / TICKS_PER_MINUTE);
        }

        /** Any progress at all: a counted run, or a minute of online time. */
        public boolean progressed(long onlineNow) {
            return runsDone > 0 || onlineNow - startOnline >= TICKS_PER_MINUTE || materialised;
        }
    }

    /** Edits for item orders, registered by the service that owns them. An unknown edit returns the item as it was. */
    private static final Map<ResourceLocation, UnaryOperator<ItemStack>> EDITS = new HashMap<>();
    public static void registerEdit(ResourceLocation id, UnaryOperator<ItemStack> edit) { EDITS.put(id, edit); }
    public static void unregisterEdit(ResourceLocation id) { EDITS.remove(id); }

    private final List<Order> orders = new ArrayList<>();

    public WorkOrders() {}

    /**
     * What a player needs to run a rift, so never held by an order: the Looting Pick, rift-tiered gear, guns, anything
     * with durability, and whatever the gear pool files as armour, weapon, tool or curio.
     */
    public static boolean runGear(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return LootingToolItem.isTool(stack) || stack.getOrDefault(RiftfallDataComponents.RIFT_TIER.get(), 0) > 0
                || GunGrade.isGun(stack) || stack.isDamageableItem() || GearPool.classify(stack.getItem()) != null;
    }

    public static WorkOrders of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WorkOrders::new, WorkOrders::load), FILE);
    }

    public List<Order> of(UUID player) { return orders.stream().filter(o -> o.player.equals(player)).toList(); }
    public List<Order> all() { return Collections.unmodifiableList(orders); }

    /**
     * Whether any of this player's orders still reads their online clock. Ready and progressed only ever turn true,
     * so once both have, more online time changes nothing a player can see, and a new order starts from the clock.
     */
    public boolean clocking(UUID player, long onlineNow) {
        for (var o : orders) if (o.player.equals(player) && (!o.ready(onlineNow) || !o.progressed(onlineNow))) return true;
        return false;
    }
    /** Probe cleanup only. */
    public void forget(UUID player) { if (orders.removeIf(o -> o.player.equals(player))) setDirty(); }

    /** Open slots for this player at this resident in this home: 1/1/2/3 by rank. */
    public int free(UUID player, UUID home, NpcRole role, ResidentRank rank) {
        long used = orders.stream().filter(o -> o.player.equals(player) && o.home.equals(home) && o.role == role).count();
        return (int) Math.max(0, rank.orderSlots() - used);
    }

    /**
     * Records a recipe order then runs {@code consume} in the same tick; a consume that throws removes the record.
     * {@code save} persists the record first (the caller passes the storage's save). Null when no slot is free or an
     * input is run gear.
     */
    public Order startRecipe(UUID player, UUID home, NpcRole role, ResidentRank rank, ResourceLocation recipe, int count,
                             int runs, int minutes, long onlineNow, List<ItemStack> consumed, Runnable save, Runnable consume) {
        if (free(player, home, role, rank) <= 0 || count <= 0 || consumed.stream().anyMatch(WorkOrders::runGear)) return null;
        var order = new Order(UUID.randomUUID(), player, home, role, Kind.RECIPE, recipe, count, runs, minutes, onlineNow);
        for (var stack : consumed) if (!stack.isEmpty()) order.refund.add(stack.copy());
        return record(order, save, consume);
    }

    /** Records an item order holding {@code stack} (taken by {@code consume}). Null when no slot is free or it is run gear. */
    public Order startItem(UUID player, UUID home, NpcRole role, ResidentRank rank, ResourceLocation edit, ItemStack stack,
                           int runs, int minutes, long onlineNow, Runnable save, Runnable consume) {
        if (free(player, home, role, rank) <= 0 || stack.isEmpty() || stack.getCount() != 1 || runGear(stack)) return null;
        var order = new Order(UUID.randomUUID(), player, home, role, Kind.ITEM, edit, 1, runs, minutes, onlineNow);
        order.items.add(stack.copy());
        return record(order, save, consume);
    }

    private Order record(Order order, Runnable save, Runnable consume) {
        orders.add(order);
        setDirty();
        save.run();
        try {
            consume.run();
        } catch (RuntimeException failed) {
            orders.remove(order);
            setDirty();
            throw failed;
        }
        return order;
    }

    /** One of this player's runs settled; {@code counts} says whether it is at or above an order's home tier - 2. */
    public void settled(UUID player, Predicate<UUID> counts) {
        for (var order : orders) {
            if (!order.player.equals(player) || order.materialised || !counts.test(order.home)) continue;
            order.runsDone++;
            order.refund.clear();
            setDirty();
        }
    }

    /**
     * Hands every ready order of {@code player} matching {@code which} into {@code into} (Inventory#add semantics).
     * Outputs are made on collect ({@code recipeOutput} null: not now); what does not fit stays on the order.
     * Returns orders fully collected.
     */
    public int collect(UUID player, long onlineNow, Predicate<Order> which, Function<Order, List<ItemStack>> recipeOutput,
                       Predicate<ItemStack> into) {
        int done = 0;
        for (var it = orders.iterator(); it.hasNext(); ) {
            var order = it.next();
            if (!order.player.equals(player) || !which.test(order) || !order.ready(onlineNow)) continue;
            if (!order.materialised) {
                var made = new ArrayList<ItemStack>();
                if (order.kind == Kind.RECIPE) {
                    var output = recipeOutput.apply(order);
                    if (output == null) continue; // The recipe is gone: keep the order rather than lose it.
                    made.addAll(output);
                } else for (var held : order.items) made.add(EDITS.getOrDefault(order.ref, UnaryOperator.identity()).apply(held.copy()));
                order.items.clear();
                order.items.addAll(made);
                order.refund.clear();
                order.materialised = true;
            }
            var left = new ArrayList<ItemStack>();
            for (var stack : order.items) if (!into.test(stack) && !stack.isEmpty()) left.add(stack);
            order.items.clear();
            order.items.addAll(left);
            setDirty();
            if (left.isEmpty()) { it.remove(); done++; }
        }
        return done;
    }

    public enum Cancel { DONE, NOT_FOUND, IN_PROGRESS }

    /** Recipe orders return what was consumed only before any progress; item orders return the item unchanged. */
    public Cancel cancel(UUID player, UUID order, long onlineNow, Predicate<ItemStack> into) {
        for (var it = orders.iterator(); it.hasNext(); ) {
            var o = it.next();
            if (!o.id.equals(order) || !o.player.equals(player)) continue;
            if (o.kind == Kind.RECIPE && !o.materialised && o.progressed(onlineNow)) return Cancel.IN_PROGRESS;
            var back = o.kind == Kind.RECIPE && !o.materialised ? o.refund : o.items;
            var left = new ArrayList<ItemStack>();
            for (var stack : back) if (!into.test(stack) && !stack.isEmpty()) left.add(stack);
            if (left.isEmpty()) it.remove();
            else { o.items.clear(); o.items.addAll(left); o.refund.clear(); o.materialised = true; }
            setDirty();
            return Cancel.DONE;
        }
        return Cancel.NOT_FOUND;
    }

    public static WorkOrders load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new WorkOrders();
        var list = tag.getList("orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var e = list.getCompound(i);
            NpcRole role = null;
            for (var r : NpcRole.values()) if (r.id().equals(e.getString("role"))) role = r;
            var ref = ResourceLocation.tryParse(e.getString("ref"));
            Kind kind = "item".equals(e.getString("kind")) ? Kind.ITEM : Kind.RECIPE;
            if (role == null || ref == null || !e.hasUUID("id") || !e.hasUUID("player") || !e.hasUUID("home")) continue;
            var order = new Order(e.getUUID("id"), e.getUUID("player"), e.getUUID("home"), role, kind, ref,
                    e.getInt("count"), e.getInt("runs"), e.getInt("minutes"), e.getLong("startOnline"));
            order.runsDone = e.getInt("runsDone");
            order.materialised = e.getBoolean("materialised");
            for (var item : e.getList("items", Tag.TAG_COMPOUND)) ItemStack.parse(registries, (CompoundTag) item).ifPresent(order.items::add);
            for (var item : e.getList("refund", Tag.TAG_COMPOUND)) ItemStack.parse(registries, (CompoundTag) item).ifPresent(order.refund::add);
            // An item order whose stack did not parse has nothing left to hand back.
            if (kind == Kind.ITEM && order.items.isEmpty()) continue;
            data.orders.add(order);
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var o : orders) {
            var e = new CompoundTag();
            e.putUUID("id", o.id); e.putUUID("player", o.player); e.putUUID("home", o.home);
            e.putString("role", o.role.id());
            e.putString("kind", o.kind == Kind.ITEM ? "item" : "recipe");
            e.putString("ref", o.ref.toString());
            e.putInt("count", o.count); e.putInt("runs", o.runs); e.putInt("minutes", o.minutes);
            e.putLong("startOnline", o.startOnline);
            e.putInt("runsDone", o.runsDone);
            e.putBoolean("materialised", o.materialised);
            var items = new ListTag();
            for (var s : o.items) if (!s.isEmpty()) items.add(s.save(registries));
            e.put("items", items);
            var refund = new ListTag();
            for (var s : o.refund) if (!s.isEmpty()) refund.add(s.save(registries));
            e.put("refund", refund);
            list.add(e);
        }
        tag.put("orders", list);
        return tag;
    }
}
