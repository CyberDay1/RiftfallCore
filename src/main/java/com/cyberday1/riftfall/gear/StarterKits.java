package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.compat.BackpackUpgrades;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gives a character its class's starter kit, once, when the class is chosen.
 *
 * <p>
 * Every worn or held piece goes through {@link RiftLootRarity#starter}, the tier-1 half of the path a
 * rift's loot takes, so a kit is a common tier-1 drop and nothing more: the donor mod's stats are
 * blanked by the floors, the worn-gear ban silences its tick, and the equipment-set ban has already
 * emptied its set bonus. A piece is only taken from its mod when the rifts own it
 * ({@link GearPool#drawable}) and it grants no ability; otherwise the entry's vanilla fallback is given,
 * at vanilla's own (lower) numbers.
 */
public final class StarterKits {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The marker a kit's gear carries, in {@code minecraft:custom_data}. Nothing reads it yet. */
    public static final String MARKER = "riftfall_starter";

    static final String PREFIX = "message." + RiftfallCore.MODID + ".starter_kit.";
    static final String GRANTED = PREFIX + "granted";
    public static final String GIVEN = PREFIX + "given";
    public static final String NONE = PREFIX + "none";

    /** Every key above with its English, which the language provider generates from. */
    public static final Map<String, String> ENGLISH = Map.of(
            GRANTED, "Your %s starter kit is equipped; anything it replaced is in your inventory.",
            GIVEN, "Gave %s the %s starter kit (%s stacks).",
            NONE, "There is no starter kit for %s.");

    private static volatile StarterKit.Loaded loaded = StarterKit.Loaded.EMPTY;

    private StarterKits() {
    }

    /** What happened when a class was chosen. */
    public enum Grant {
        GRANTED, ALREADY_GRANTED, NO_KIT
    }

    /** One entry as given: where, which id, whether that id was the fallback, and the stack placed. */
    public record Placed(StarterKit.Place place, ResourceLocation id, boolean fallback, ItemStack stack) {
    }

    public static StarterKit.Loaded loaded() {
        return loaded;
    }

    public static @Nullable StarterKit kit(String skillClass) {
        return loaded.kits().get(skillClass);
    }

    /**
     * The class screen's (and the command's) hook: gives the kit if this character has never had one.
     * The ledger is written before anything is handed over, so nothing that goes wrong mid-way can be
     * turned into a second kit.
     */
    public static Grant onClassChosen(ServerPlayer player, String skillClass) {
        StarterKitLedger ledger = StarterKitLedger.of(player.server);
        StarterKit kit = kit(skillClass);
        Grant grant = decide(ledger.grantedTo(player.getUUID()), kit);
        if (grant == Grant.ALREADY_GRANTED) {
            return grant;
        }
        if (grant == Grant.NO_KIT) {
            LOGGER.warn("Starter kit: none loaded for class {}, so {} got nothing.", skillClass,
                    player.getGameProfile().getName());
            return Grant.NO_KIT;
        }
        ledger.record(player.getUUID(), skillClass);
        give(player, kit);
        if (!(player instanceof FakePlayer)) {
            player.sendSystemMessage(Component.translatable(GRANTED,
                    Component.translatable("class." + RiftfallCore.MODID + "." + skillClass)));
        }
        return Grant.GRANTED;
    }

    /**
     * The once-only rule on its own: a character the ledger names never gets a second kit, whichever
     * class it names (so a class reset does not re-arm it), and a class with no kit gets nothing and is
     * not recorded, so a kit added later is still given on the next choice.
     */
    static Grant decide(@Nullable String alreadyGranted, @Nullable StarterKit kit) {
        if (alreadyGranted != null) {
            return Grant.ALREADY_GRANTED;
        }
        return kit == null ? Grant.NO_KIT : Grant.GRANTED;
    }

    /**
     * Hands over every entry of a kit, then the common set, regardless of the ledger. The admin command's
     * verb. A common item the class's own entries already gave (a leather fallback, say) is not given twice.
     */
    public static List<Placed> give(ServerPlayer player, StarterKit kit) {
        List<Placed> placed = new ArrayList<>();
        Set<ResourceLocation> given = new HashSet<>();
        DynamicOps<JsonElement> ops = player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (StarterKit.Entry entry : kit.entries()) {
            Placed one = resolve(player, entry, ops);
            if (one == null) {
                LOGGER.warn("Starter kit {}: neither {} nor its fallback {} could be given.", kit.skillClass(),
                        entry.item(), entry.fallback());
                continue;
            }
            hand(player, entry, one, placed);
            given.add(one.id());
        }
        for (StarterKit.Entry entry : kit.shared()) {
            Placed one = resolve(player, entry, ops);
            if (one == null) {
                LOGGER.warn("Starter kit common set: {} could not be given.", entry.item());
            } else if (!given.contains(one.id())) {
                hand(player, entry, one, placed);
            }
        }
        player.inventoryMenu.broadcastChanges();
        return placed;
    }

    /** Places one resolved entry, its upgrades installed first; an upgrade that will not go in is given loose. */
    private static void hand(ServerPlayer player, StarterKit.Entry entry, Placed one, List<Placed> placed) {
        ItemStack stack = one.stack().copy();
        for (ResourceLocation upgradeId : entry.install()) {
            ItemStack upgrade = BuiltInRegistries.ITEM.containsKey(upgradeId)
                    ? new ItemStack(BuiltInRegistries.ITEM.get(upgradeId)) : ItemStack.EMPTY;
            if (upgrade.isEmpty()) {
                LOGGER.warn("Starter kit: upgrade {} for {} is not registered.", upgradeId, one.id());
            } else if (!BackpackUpgrades.install(stack, upgrade)) {
                LOGGER.warn("Starter kit: {} could not be installed in {}, so it is given loose.", upgradeId, one.id());
                place(player, StarterKit.Place.INVENTORY, upgrade.copy());
                placed.add(new Placed(StarterKit.Place.INVENTORY, upgradeId, true, upgrade));
            }
        }
        // Recorded before placing: a merge into an existing stack empties the one handed in.
        placed.add(new Placed(one.place(), one.id(), one.fallback(), stack.copy()));
        place(player, one.place(), stack);
    }

    private static @Nullable Placed resolve(ServerPlayer player, StarterKit.Entry entry, DynamicOps<JsonElement> ops) {
        ItemStack own = build(entry, entry.item(), ops).result().orElse(ItemStack.EMPTY);
        if (!own.isEmpty() && (!entry.place().gear() || owned(player, own, entry.ownStats()))) {
            return new Placed(entry.place(), entry.item(), false, finish(player, entry, own));
        }
        if (entry.fallback() == null) {
            return null;
        }
        LOGGER.info("Starter kit: {} {}, so its fallback {} is given.", entry.item(),
                own.isEmpty() ? "is not registered" : !entry.ownStats() && !GearPool.drawable(own.getItem())
                        ? "is not the rifts' gear" : "grants an ability", entry.fallback());
        ItemStack fallback = build(entry, entry.fallback(), ops).result().orElse(ItemStack.EMPTY);
        return fallback.isEmpty() ? null : new Placed(entry.place(), entry.fallback(), true,
                finish(player, entry, fallback));
    }

    /**
     * A modded piece is only given when the rifts own it, so the floor is certain to rewrite it, and when
     * it grants no ability. {@code ownStats} waives the first half only.
     */
    private static boolean owned(ServerPlayer player, ItemStack stack, boolean ownStats) {
        return (ownStats || GearPool.drawable(stack.getItem()))
                && !AbilityDropBan.grantsAbility(stack, player.serverLevel());
    }

    private static ItemStack finish(ServerPlayer player, StarterKit.Entry entry, ItemStack stack) {
        if (!entry.place().gear()) {
            return stack;
        }
        ItemStack stamped = RiftLootRarity.starter(stack, player);
        CustomData.update(DataComponents.CUSTOM_DATA, stamped, tag -> tag.putBoolean(MARKER, true));
        return stamped;
    }

    /**
     * The stack an entry names, through {@code ItemStack.CODEC} so components are read exactly as a
     * {@code /give} reads them. An unregistered id is an error result rather than air.
     */
    static DataResult<ItemStack> build(StarterKit.Entry entry, ResourceLocation id, DynamicOps<JsonElement> ops) {
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            return DataResult.error(() -> id + " is not registered");
        }
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("count", entry.count());
        JsonObject components = entry.componentsFor(id);
        if (components != null) {
            json.add("components", components.deepCopy());
        }
        return ItemStack.CODEC.parse(ops, json);
    }

    /**
     * Puts a stack where its entry says. A worn or held place is taken even when occupied (the default
     * kit the pack hands out on first join sits there), and what was there goes to the inventory; a
     * piece that does not fit the slot it was filed under goes to the inventory instead of being forced.
     */
    static void place(ServerPlayer player, StarterKit.Place place, ItemStack stack) {
        EquipmentSlot slot = switch (place) {
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
            case MAINHAND -> EquipmentSlot.MAINHAND;
            case OFFHAND -> EquipmentSlot.OFFHAND;
            case INVENTORY -> null;
        };
        if (slot == null || (slot.isArmor() && player.getEquipmentSlotForItem(stack) != slot)) {
            player.getInventory().placeItemBackInInventory(stack);
            return;
        }
        ItemStack old = player.getItemBySlot(slot);
        player.setItemSlot(slot, stack);
        if (!old.isEmpty()) {
            player.getInventory().placeItemBackInInventory(old);
        }
    }

    /** Reads {@code data/<ns>/starter_kit/*.json} on every reload. */
    public static final class Loader extends SimpleJsonResourceReloadListener {

        public Loader() {
            super(new Gson(), StarterKit.DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            StarterKit.Loaded parsed = StarterKit.parse(files);
            parsed.refusals().forEach(refusal -> LOGGER.error("Starter kit refused {}", refusal));
            LOGGER.info("Starter kits: {} classes, {} refused.", parsed.kits().size(), parsed.refusals().size());
            loaded = parsed;
        }
    }

    /** For the tests, which parse the shipped files without a reload. */
    static void install(StarterKit.Loaded kits) {
        loaded = kits;
    }
}
