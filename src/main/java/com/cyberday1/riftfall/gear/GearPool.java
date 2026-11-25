package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.init.RiftfallTags;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every piece of modded gear the game has loaded, in up to thirteen buckets.
 *
 * <h2>Built from the registry, never from a list</h2>
 * The pack is 87 mods of armour and weapons that Riftfall uses as a model library, and it changes.
 * A shipped list would be wrong the first time a mod is added and would have to be re-audited every
 * time one is dropped, so membership is decided by what an item <em>is</em> rather than by what it
 * is called.
 *
 * <h2>Balanced per slot, not per item</h2>
 * A draw picks a non-empty bucket first and an item inside it second. A flat draw over everything
 * would be armour more than half the time purely because more armour exists, and a barrel would
 * hand a player four helmets.
 *
 * <p>
 * Vanilla is skipped whole: its gear is craftable, and this exists to be the only route to gear that
 * is not.
 */
public final class GearPool {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The thirteen buckets a draw picks between. */
    public enum Slot {
        HEAD, CHEST, LEGS, FEET, SWORD, OTHER_MELEE, SHIELD, BOW, CROSSBOW, TRIDENT, MACE, TOOL, CURIO
    }

    /**
     * What an unclassed item has to swing for to count as a weapon rather than a tool. Vanilla
     * pickaxes add 1.0 and shovels 1.5; every vanilla axe adds at least 4.0.
     */
    private static final double MELEE_DAMAGE = 3.0D;

    private static final String VANILLA = "minecraft";

    /**
     * The two trinket mods the pack runs, named as tag namespaces.
     *
     * <p>
     * A ring is not {@code Equipable} and extends nothing in particular, so the only thing that says
     * it is worn is the slot tag its mod files it under. Both are read because the pack has both, and
     * reading the tag rather than the mod's API means neither has to be a compile dependency.
     */
    private static final String CURIOS = "curios";
    private static final String ACCESSORIES = "accessories";

    /**
     * What makes an item a tool rather than a weapon, asked functionally for the reason the shield
     * test is. {@code AXE_DIG} is deliberately absent: an axe is a weapon that also digs, and the
     * pack's battleaxes would otherwise land in the toolbox.
     */
    private static final List<ItemAbility> DIGS =
            List.of(ItemAbilities.PICKAXE_DIG, ItemAbilities.SHOVEL_DIG,
                    ItemAbilities.HOE_DIG, ItemAbilities.SHEARS_DIG);

    /** Below this a bucket is too thin to be drawn against the others. See {@link #fold}. */
    static final int MIN_BUCKET = 8;

    /** The four buckets a player wears. Never folded, so these are exactly the armour. */
    private static final List<Slot> ARMOUR = List.of(Slot.HEAD, Slot.CHEST, Slot.LEGS, Slot.FEET);

    /** The seven buckets a player swings or raises. Unlike {@link #ARMOUR}, these do fold. */
    private static final List<Slot> WEAPONS =
            List.of(Slot.SWORD, Slot.OTHER_MELEE, Slot.SHIELD, Slot.BOW, Slot.CROSSBOW,
                    Slot.TRIDENT, Slot.MACE);

    /** The buckets that have somewhere to go, in the order they are offered it. */
    private static final List<Slot> FOLDABLE =
            List.of(Slot.MACE, Slot.TRIDENT, Slot.CROSSBOW, Slot.BOW, Slot.SHIELD, Slot.SWORD);

    /** Built on first draw, dropped on every tag reload. */
    private static volatile @Nullable Pool pool;

    /** What {@link #classify} has answered since the last tag bind. Null until tags are bound, so nothing outlives a bind. */
    private static volatile @Nullable Map<Item, Optional<Slot>> classified;

    /** What the last {@link #fold} tipped into {@link Slot#OTHER_MELEE}, against how many it held. */
    private static volatile Map<Slot, Integer> folded = Map.of();

    private GearPool() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(GearPool::invalidate);
        // Server configs load just before this; answers given while the world's resources were built predate them.
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> classified = new ConcurrentHashMap<>());
    }

    /**
     * Drops the pool when tags reload, because {@link RiftfallTags#GEAR_DENIED} is half the filter
     * and a datapack may have just changed it.
     */
    static void invalidate(@Nullable TagsUpdatedEvent event) {
        pool = null;
        // Replaced, not cleared: an answer still being worked out against the old tags lands in the old map.
        classified = new ConcurrentHashMap<>();
    }

    /** One piece of gear, or an empty stack if nothing loaded qualifies. */
    public static ItemStack draw(RandomSource random) {
        Slot bucket = drawBucket(random);
        return bucket == null ? ItemStack.EMPTY : drawFrom(bucket, random);
    }

    /**
     * The first half of {@link #draw}: which bucket. A group (armour, weapons, accessories, tools) by
     * {@link GearMix}'s weights, then a bucket uniformly inside it (2026-10-11). Split out so a
     * weapon roll can be recognised before its item is chosen ({@link ClassLoot}).
     */
    public static @Nullable Slot drawBucket(RandomSource random) {
        Pool current = pool();
        Map<GearMix.Group, List<Slot>> groups = current.groups();
        int total = GearMix.total(groups.keySet());
        if (total <= 0) {
            return null;
        }
        GearMix.Group group = GearMix.pick(groups.keySet(), random.nextInt(total));
        List<Slot> slots = groups.get(group);
        return slots.get(random.nextInt(slots.size()));
    }

    /**
     * The second half of {@link #draw}: one item from {@code bucket}, or empty if the bucket is not drawn. Unique
     * weapons are never drawn here; an accessory picks its kind by {@link GearMix#kindWeight} first.
     */
    public static ItemStack drawFrom(Slot bucket, RandomSource random) {
        Pool current = pool();
        if (bucket == Slot.CURIO && !current.curioKinds().isEmpty()) {
            Map<String, List<Item>> kinds = current.curioKinds();
            int total = GearMix.kindTotal(kinds.keySet());
            if (total > 0) {
                List<Item> items = kinds.get(GearMix.pickKind(kinds.keySet(), random.nextInt(total)));
                return new ItemStack(items.get(random.nextInt(items.size())));
            }
        }
        List<Item> items = current.drawable().get(bucket);
        return items == null || items.isEmpty() ? ItemStack.EMPTY : new ItemStack(items.get(random.nextInt(items.size())));
    }

    /** The accessory kind an item is filed under: {@code relic} for a relic, else from its slot tags. */
    public static String curioKind(Item item) {
        return curioKind(BuiltInRegistries.ITEM.wrapAsHolder(item)::is, slotTagPaths(item));
    }

    /** Split from the item so a test can hand it a truth table. */
    static String curioKind(java.util.function.Predicate<TagKey<Item>> tagged, java.util.Collection<String> slotPaths) {
        return TrinketSlots.isRelic(tagged) ? GearMix.RELIC : GearMix.kind(slotPaths);
    }

    static List<String> slotTagPaths(Item item) {
        return BuiltInRegistries.ITEM.wrapAsHolder(item).tags()
                .filter(GearPool::trinketSlot)
                .map(tag -> tag.location().getPath())
                .sorted()
                .toList();
    }

    /** Whether a drawn bucket is one of the held ones, after the fold as well as before it. */
    public static boolean isWeapon(Slot bucket) {
        return WEAPONS.contains(bucket);
    }

    /** Every weapon the rifts drop, against its bucket before the fold. The class families are cut from this. */
    public static Map<Item, Slot> weapons() {
        return Collections.unmodifiableMap(pool().weapons());
    }

    /** The buckets as built, for the report and for tests. */
    public static Map<Slot, List<Item>> bySlot() {
        return pool().bySlot();
    }

    /**
     * Whether a rift is where this item comes from, answered cheaply enough to ask every tick.
     *
     * <p>
     * {@link #owned} is the same question and is not cheap: it runs the item's own code. This reads
     * the set the pool already built, so the cost is one identity hash. The two cannot disagree,
     * because the set is exactly what {@link #build} kept.
     */
    public static boolean drawable(Item item) {
        return pool().items().contains(item);
    }

    /**
     * The armour slot a rift would drop this in, or null if the rifts do not dress a player in it.
     *
     * <p>
     * Read back off the pool rather than asked of the item a second time, so the slot a piece is
     * given stats for is by construction the slot the barrel filed it under. That the answer is
     * trustworthy at all rests on {@link #fold} leaving armour alone: a folded weapon is filed under
     * a bucket it does not belong to, and the same lookup over {@link Slot#SWORD} would be a lie.
     */
    public static @Nullable Slot armourSlot(Item item) {
        return pool().armour().get(item);
    }

    /**
     * The weapon bucket a rift would drop this in, or null if the rifts do not arm a player with it.
     *
     * <p>
     * The counterpart to {@link #armourSlot} and built differently on purpose. That one reads a map
     * inverted after {@link #fold} ran, which is safe only because armour is never folded; this one
     * reads a map inverted <em>before</em> it, because weapons are. A mace in this pack shares a
     * drawing bucket with every other melee weapon and is still a mace in the hand, and it is the
     * second of those two facts that decides what {@link WeaponFloor} writes on it.
     */
    public static @Nullable Slot weaponSlot(Item item) {
        return pool().weapons().get(item);
    }

    /**
     * Which buckets the last fold tipped, and how many items each held.
     *
     * <p>
     * The decisions were only ever logged, and a log line cannot be read back. Once the fold has
     * run, a folded bucket and a bucket that classed nothing are the same empty bucket, and telling
     * those two apart is the whole of what a census of a pack's weapon classes is for.
     *
     * <p>
     * Describes whatever {@link #bySlot()} last returned, so ask for that first.
     */
    public static Map<Slot, Integer> folded() {
        return folded;
    }

    /**
     * The bucket the rifts would drop this in, or null if the rifts do not own it at all.
     *
     * <p>
     * One predicate, asked from both ends. {@link #build} asks it to decide what a barrel may hand
     * out and {@link GearRecipeBan} asks it to decide what a bench may not make, so the two cannot
     * drift apart: if a rift can drop it you cannot craft it, and if {@link RiftfallTags#GEAR_DENIED}
     * or {@link RiftfallTags#NOT_GEAR} names it, its own mod keeps it whole. Those two tags are the
     * only exceptions there are, and they are two because they mean different things: the first is
     * gear the rifts decline, the second was never gear. {@link RiftfallTags#RIFT_ONLY} is also kept out, the other
     * way: the rifts own it outright but draw it from {@code rift_arms}, not this pool, and the recipe ban takes its
     * recipes on its own test.
     *
     * <p>
     * An item that throws while being asked is left to its mod. Classifying runs the item's own code,
     * and {@link GearRecipeBan} asks this while the world's resources are being built, which is before
     * any server config is loaded &mdash; Aether's ambrosium shard reads one in
     * {@code getUseAnimation}, and is named in {@link RiftfallTags#NOT_GEAR} so it is never asked. The
     * alternative to catching that is one mod stopping every world from loading, so the throw is logged
     * and the item stands.
     */
    public static @Nullable Slot owned(Holder<Item> holder) {
        Item item = holder.value();
        if (VANILLA.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace()) || excluded(holder::is)) {
            return null;
        }
        try {
            Slot slot = classify(item);
            // Being wearable is not enough: decorative heads, terminals, batteries and unsupported
            // slot types must stay with their own mods, including their crafting recipes.
            if (slot != null && (slot == Slot.CURIO || ARMOUR.contains(slot))
                    && !RiftLootRarity.supportsAffixes(new ItemStack(item))) {
                return null;
            }
            return slot;
        } catch (Throwable unanswerable) {
            LOGGER.warn("Gear pool: {} threw when asked what it is, so its own mod keeps it.",
                    BuiltInRegistries.ITEM.getKey(item), unanswerable);
            return null;
        }
    }

    /**
     * The stat kind of worn jewellery or gloves the rifts cannot affix, or null. Recipe ban only: never drawn.
     * Catches Accessories, Trinkets and Aether-style slot tags, which {@link #owned} skips (no Curios category).
     */
    public static @Nullable String unaffixedAccessory(Holder<Item> holder) {
        Item item = holder.value();
        if (VANILLA.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace()) || excluded(holder::is)) {
            return null;
        }
        try {
            return unaffixedAccessory(classify(item), holder.tags().map(TagKey::location).toList());
        } catch (Throwable unanswerable) {
            LOGGER.warn("Gear pool: {} threw when asked what it is, so its own mod keeps it.",
                    BuiltInRegistries.ITEM.getKey(item), unanswerable);
            return null;
        }
    }

    /** Split from the item for a truth-table test. Weapons, armour and tools answer to {@link #owned}. */
    static @Nullable String unaffixedAccessory(@Nullable Slot classified, java.util.Collection<net.minecraft.resources.ResourceLocation> tags) {
        return classified == null || classified == Slot.CURIO ? WornAccessory.kind(tags) : null;
    }

    /** Still rift-owned (recipe-banned, stripped outside rifts), just never a barrel draw. */
    public static boolean barrelDrawn(Item item) {
        return !BuiltInRegistries.ITEM.wrapAsHolder(item).is(RiftfallTags.NOT_BARREL_DRAWN);
    }

    /** The tags that keep an item out of both rules. */
    static boolean excluded(java.util.function.Predicate<TagKey<Item>> tagged) {
        return tagged.test(RiftfallTags.ACQUISITION_BANNED) || tagged.test(RiftfallTags.GEAR_DENIED)
                || tagged.test(RiftfallTags.NOT_GEAR) || tagged.test(RiftfallTags.RIFT_ONLY);
    }

    /**
     * Which bucket an item belongs in, or null for everything that is not gear.
     *
     * <p>
     * Order is load-bearing. Armour answers first because a worn slot is the least ambiguous thing
     * an item can say about itself; the shield test is next because a shield is equippable too and
     * would otherwise fall through to the weapon checks. Tools and trinkets answer after every
     * weapon class, because both are things a weapon can also be.
     *
     * <p>
     * Remembered per item between tag binds. A throw is not remembered, so the item is asked again.
     */
    public static @Nullable Slot classify(Item item) {
        Map<Item, Optional<Slot>> known = classified;
        if (known == null) {
            return classifyFresh(item);
        }
        Optional<Slot> slot = known.get(item);
        if (slot == null) {
            // Not computeIfAbsent: this runs the item's own code, which must not run under the map's lock.
            slot = Optional.ofNullable(classifyFresh(item));
            known.put(item, slot);
        }
        return slot.orElse(null);
    }

    private static @Nullable Slot classifyFresh(Item item) {
        ItemStack stack = new ItemStack(item);
        if (item instanceof Equipable equipable) {
            Slot worn = worn(equipable.getEquipmentSlot());
            if (worn != null) {
                return worn;
            }
        }
        // Explicit magic/throwing implements can advertise BLOCK merely as a charging animation.
        if (stack.is(RiftfallTags.MELEE_WEAPONS)) {
            return Slot.OTHER_MELEE;
        }
        // Asked functionally rather than by class: many modded shields do not extend ShieldItem, and
        // the ability is what NeoForge itself gates blocking on.
        if (stack.canPerformAction(ItemAbilities.SHIELD_BLOCK) || item.getUseAnimation(stack) == UseAnim.BLOCK) {
            return Slot.SHIELD;
        }
        if (item instanceof BowItem) {
            return Slot.BOW;
        }
        if (item instanceof CrossbowItem) {
            return Slot.CROSSBOW;
        }
        if (item instanceof TridentItem) {
            return Slot.TRIDENT;
        }
        if (item instanceof MaceItem) {
            return Slot.MACE;
        }
        if (item instanceof SwordItem) {
            return Slot.SWORD;
        }
        if (item instanceof AxeItem) {
            return Slot.OTHER_MELEE;
        }
        if (item instanceof DiggerItem || digs(stack)) {
            return Slot.TOOL;
        }
        if (trinket(item)) {
            return Slot.CURIO;
        }
        // Everything else that hits hard: spears, hammers, claws and the rest of the custom classes.
        return meleeDamage(item, stack) >= MELEE_DAMAGE ? Slot.OTHER_MELEE : null;
    }

    private static boolean digs(ItemStack stack) {
        for (ItemAbility ability : DIGS) {
            if (stack.canPerformAction(ability)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether any mod files this item under a trinket slot.
     *
     * <p>
     * Asked last of the classes above it because both mods let a pack put a real weapon in a trinket
     * slot &mdash; Curios ships a sword slot &mdash; and a sword that can also be worn on a belt is
     * still a sword to a player opening a barrel.
     */
    private static boolean trinket(Item item) {
        return BuiltInRegistries.ITEM.wrapAsHolder(item).tags().anyMatch(GearPool::trinketSlot);
    }

    /**
     * Whether a tag is one of the two trinket mods' slot lists, decided on the namespace alone
     * because the slot names are the pack's to invent and neither mod publishes a closed set.
     */
    static boolean trinketSlot(TagKey<Item> tag) {
        String namespace = tag.location().getNamespace();
        return CURIOS.equals(namespace) || ACCESSORIES.equals(namespace);
    }

    private static @Nullable Slot worn(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> Slot.HEAD;
            case CHEST -> Slot.CHEST;
            case LEGS -> Slot.LEGS;
            case FEET -> Slot.FEET;
            default -> null;
        };
    }

    /**
     * The mainhand attack damage an item grants by itself.
     *
     * <p>
     * Read off the component rather than through {@code ItemStack#getAttributeModifiers}, which
     * fires {@code ItemAttributeModifiersEvent}: this runs once per registered item and has no
     * business waking every other mod's listener to ask a question about a bare stack.
     */
    private static double meleeDamage(Item item, ItemStack stack) {
        ItemAttributeModifiers modifiers =
                item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (modifiers.modifiers().isEmpty()) {
            modifiers = item.getDefaultAttributeModifiers(stack);
        }
        double damage = 0.0D;
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (Attributes.ATTACK_DAMAGE.equals(entry.attribute())
                    && entry.slot().test(EquipmentSlot.MAINHAND)
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                damage += entry.modifier().amount();
            }
        }
        return damage;
    }

    private static Pool pool() {
        Pool current = pool;
        if (current != null) {
            return current;
        }
        synchronized (GearPool.class) {
            if (pool == null) {
                pool = build();
            }
            return pool;
        }
    }

    private static Pool build() {
        Map<Slot, List<Item>> bySlot = new EnumMap<>(Slot.class);
        for (Holder.Reference<Item> holder : BuiltInRegistries.ITEM.holders().toList()) {
            Slot slot = owned(holder);
            if (slot != null) {
                bySlot.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(holder.value());
            }
        }
        // Inverted BEFORE the fold, which is the whole reason it is a second map and not a reuse of
        // armourSlot's. The fold tips thin weapon buckets into OTHER_MELEE, so after it a mace is
        // filed under a bucket it does not belong to and the lookup would answer OTHER_MELEE for an
        // item the player is holding a mace of. Armour is never folded, so that map can be built
        // from the folded result and this one cannot.
        Map<Item, Slot> weapons = new IdentityHashMap<>();
        for (Slot slot : WEAPONS) {
            bySlot.getOrDefault(slot, List.of()).forEach(item -> weapons.put(item, slot));
        }
        fold(bySlot);
        Set<Item> items = Collections.newSetFromMap(new IdentityHashMap<>());
        bySlot.values().forEach(items::addAll);
        Map<Item, Slot> armour = new IdentityHashMap<>();
        for (Slot slot : ARMOUR) {
            bySlot.getOrDefault(slot, List.of()).forEach(item -> armour.put(item, slot));
        }
        // What the barrels draw: the same buckets less the unique weapons, grouped; accessories by kind.
        Map<Slot, List<Item>> drawable = new EnumMap<>(Slot.class);
        bySlot.forEach((slot, list) -> {
            List<Item> kept = list.stream().filter(UniqueWeapons::barrelDrawable)
                    .filter(BarrelTools::allowed).filter(GearPool::barrelDrawn).toList();
            if (!kept.isEmpty()) {
                drawable.put(slot, kept);
            }
        });
        Map<GearMix.Group, List<Slot>> groups = new EnumMap<>(GearMix.Group.class);
        drawable.keySet().forEach(slot -> groups.computeIfAbsent(GearMix.group(slot), g -> new ArrayList<>()).add(slot));
        Map<String, List<Item>> curioKinds = new java.util.TreeMap<>();
        for (Item item : drawable.getOrDefault(Slot.CURIO, List.of())) {
            curioKinds.computeIfAbsent(curioKind(item), k -> new ArrayList<>()).add(item);
        }
        Pool built = new Pool(bySlot, List.copyOf(bySlot.keySet()), items, armour, weapons, drawable, groups,
                curioKinds);
        report(built);
        return built;
    }

    /**
     * Tips buckets too thin to stand on their own into {@link Slot#OTHER_MELEE}.
     *
     * <p>
     * A balanced draw picks a bucket as often as any other however small it is, so a bucket holding
     * one item is that item a ninth of the time. The classes are the reason it happens: almost every
     * modded spear, halberd and warhammer extends {@code SwordItem}, and only a couple of items in
     * the pack extend {@code TridentItem} or {@code MaceItem} at all, so those two buckets can be a
     * fifth of all the gear a player ever sees and three items wide. Folded, the items stay drawable
     * at the rarity their numbers earn.
     *
     * <p>
     * Conditional on the count and never on the slot, so a pack that does ship forty maces keeps the
     * bucket. Armour, tools and trinkets are left out: none of them is ever thin in this pack, and
     * there is nothing to fold one into that would not be a lie about what the player is holding.
     */
    static void fold(Map<Slot, List<Item>> bySlot) {
        Map<Slot, Integer> tipped = new EnumMap<>(Slot.class);
        for (Slot slot : FOLDABLE) {
            List<Item> thin = bySlot.get(slot);
            if (thin == null || thin.size() >= MIN_BUCKET) {
                continue;
            }
            bySlot.remove(slot);
            bySlot.computeIfAbsent(Slot.OTHER_MELEE, ignored -> new ArrayList<>()).addAll(thin);
            tipped.put(slot, thin.size());
            LOGGER.info("Gear pool: {} holds only {} item(s), folded into OTHER_MELEE.", slot, thin.size());
        }
        // Unmodifiable rather than Map.copyOf, which would drop the EnumMap's slot order.
        folded = Collections.unmodifiableMap(tipped);
    }

    /**
     * Says out loud what the pool ended up holding, for the reason {@code reportThemes} does: a
     * bucket that came out empty or near-empty is invisible in play, and a balanced draw picks it
     * as often as any other, so one under-filled bucket becomes one item handed out over and over.
     */
    private static void report(Pool built) {
        StringBuilder counts = new StringBuilder();
        int total = 0;
        for (Slot slot : Slot.values()) {
            int size = built.bySlot().getOrDefault(slot, List.of()).size();
            total += size;
            counts.append(counts.isEmpty() ? "" : ", ").append(slot).append(' ').append(size);
        }
        if (total == 0) {
            LOGGER.warn("Gear pool is empty: no modded gear loaded, so barrels will give no gear at all.");
            return;
        }
        LOGGER.info("Gear pool: {} items across {} slot(s) — {}", total, built.filled().size(), counts);
    }

    /**
     * @param bySlot every bucket that got at least one item
     * @param filled the same keys as a list, so the draw can index one at random
     * @param items  the same items again as a set, for callers asking about one item per tick
     * @param armour  just the four worn buckets, inverted, for {@link #armourSlot}
     * @param weapons the seven held buckets, inverted, taken before the fold, for
     *                {@link #weaponSlot}
     */
    private record Pool(Map<Slot, List<Item>> bySlot, List<Slot> filled, Set<Item> items,
                        Map<Item, Slot> armour, Map<Item, Slot> weapons, Map<Slot, List<Item>> drawable,
                        Map<GearMix.Group, List<Slot>> groups, Map<String, List<Item>> curioKinds) {
    }
}
