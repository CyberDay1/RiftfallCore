package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallConfig;
import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Dying in a rift costs two things: your own gear comes back {@value #WEAR_PERCENT}% closer to worn
 * out, and what the dungeon gave you does not come back at all.
 *
 * <p>
 * Nothing here destroys anything. A rift is deleted when its run ends, so an item this takes no claim
 * on goes with the level; what is claimed is held in {@link RiftClaims} until it is fetched. The whole
 * difficulty is running early enough that there is still an inventory to take.
 *
 * <p>
 * {@link RiftfallConfig#DEFEAT_DESTROYS_LOOT} turns the loot half off. {@link RiftLoot} tells the two
 * halves apart.
 *
 * <p>
 * The same bill is presented at login to anybody whose run wiped while they were offline: that is a
 * {@link RiftTolls} note, and it is charged here so there is one price rather than two.
 */
public final class RiftDeaths {

    private static final String HELD = "message." + RiftfallCore.MODID + ".claim_held";
    private static final String LOST = "message." + RiftfallCore.MODID + ".claim_loot_lost";
    private static final String TOLL = "message." + RiftfallCore.MODID + ".claim_run_wiped";

    /** How much of an item's total durability one death in a rift spends. */
    static final int WEAR_PERCENT = 20;

    /**
     * Whether {@link RiftCurios} may be called into. This and {@link #accessories} are the wall that
     * keeps an absent mod's classes from ever loading, so every mention of one lives behind them.
     * Answered at registration rather than in a static initialiser so that loading this class does not
     * require a mod list: the wear arithmetic is unit-tested.
     */
    private static boolean curios;

    /** The same question for {@link RiftAccessories}, and a separate one: the two mods are unrelated. */
    private static boolean accessories;

    private RiftDeaths() {
    }

    public static void register() {
        curios = ModList.get().isLoaded("curios");
        accessories = ModList.get().isLoaded("accessories");
        // LOW is load-bearing: late but not last. Late, because a mod that cancels this event is
        // keeping the player alive and a cancelled event is not delivered to later listeners, so a
        // death that is undone confiscates nothing. Not last, because Corpse (and its Curios addon)
        // snapshots the inventory at LOWEST -- running before it means it snapshots the emptied
        // inventory instead of making a second copy of somebody's gear inside a doomed level.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, RiftDeaths::confiscate);
        NeoForge.EVENT_BUS.addListener(RiftDeaths::refuseTotem);
        NeoForge.EVENT_BUS.addListener(RiftDeaths::noticeOnLogin);
    }

    /**
     * A totem of undying does nothing in a rift, for everything alive rather than only for players: a
     * totem in a boss's hand is the same argument with the sides swapped.
     *
     * <p>
     * Refused, not consumed. Vanilla shrinks the stack only after this hook returns
     * ({@code LivingEntity#checkTotemDeathProtection} tests the item, calls out, and consumes on the
     * way back), so a player who walks in holding one walks out still holding it.
     *
     * <p>
     * ⚠ Only vanilla's totem fires this event. {@code MixinLivingEntity} now refuses every
     * protection hooked into {@code checkTotemDeathProtection} first, so on the {@code hurt} path this
     * listener is a backstop.
     */
    private static void refuseTotem(LivingUseTotemEvent event) {
        if (refusesDeathProtection(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * Whether nothing may save this entity from dying. {@code MixinLivingEntity} asks it before any
     * death-protection code runs, which is how modded protection that never fires the event is reached.
     */
    public static boolean refusesDeathProtection(LivingEntity entity) {
        return RiftLevels.isRift(entity.level().dimension());
    }

    /**
     * Takes everything a player was carrying, before the rift can take it instead.
     *
     * <p>
     * Two of the guards are vanilla's own conditions read back, and must stay that way.
     * {@code ServerPlayer#die} skips {@code dropAllDeathLoot} for a spectator, so there was never a
     * drop to intercept and confiscating would invent a loss; {@code Player#dropEquipment} tests
     * {@code keepInventory} before dropping anything, so taking things anyway would be an item-eating
     * bug, and skipping means wear is not charged where death's price is turned off. {@code isRift} is
     * ours: only an instance deletes itself out from under its own floor.
     */
    private static void confiscate(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.isSpectator()
                || !RiftLevels.isRift(player.level().dimension())
                || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }

        List<ItemStack> taken = strip(player.getInventory());
        if (curios) {
            RiftCurios.strip(player, event.getSource(), taken);
        }
        if (accessories) {
            RiftAccessories.strip(player, event.getSource(), taken);
        }
        if (taken.isEmpty()) {
            return;
        }

        RiftRun run = RiftRuns.in(player.level().dimension());
        boolean known = run != null && run.hasEntered(player.getUUID());
        RiftLoot.Split split = keeping(taken,
                known ? player.level().dimension().location() : null,
                known ? run.carriedIn(player.getUUID()) : List.of());

        // Chat rather than the action bar, unlike the portal and the anchor: the death screen is up
        // over the HUD, so an action-bar line is shown to nobody.
        charge(player, split, split.lost() ? LOST : HELD);
    }

    /**
     * Which of the things a defeat took the player is getting back. With the rule off, all of it; with
     * it on, only what they carried in.
     *
     * <p>
     * ⛔⛔ {@code rift} being null is the whole of what an expiry has to get right. It means the run
     * could not be found, and then everything is kept — so an expiry that kills a player after the
     * run has left {@code RiftRuns}' map confiscates nothing and says nothing about it. That is why
     * {@code RiftRuns#end} kills before it removes.
     *
     * <p>
     * {@code rift} is null when the run cannot be found or has no record of this player, and then
     * everything is kept. That is an operator's {@code /riftfall rift open} level or somebody
     * teleported in by command, neither of which went through a door where a snapshot could have been
     * taken, and guessing harshly there would eat the gear of the person most likely to be carrying
     * something irreplaceable.
     */
    private static RiftLoot.Split keeping(List<ItemStack> taken, @Nullable ResourceLocation rift,
            List<ItemStack> kit) {
        return keeping(RiftfallConfig.DEFEAT_DESTROYS_LOOT.get(), taken,
                rift == null ? null : stack -> RiftLoot.carriedInto(stack, rift), kit);
    }

    /**
     * The same decision with the two things that need a running game handed in: whether the rule is
     * on, and how to tell a stack that came in through the door from one the dungeon gave. A null
     * {@code carriedIn} is the run that could not be found.
     */
    static RiftLoot.Split keeping(boolean destroysLoot, List<ItemStack> taken,
            @Nullable Predicate<ItemStack> carriedIn, List<ItemStack> kit) {
        if (!destroysLoot || carriedIn == null) {
            return new RiftLoot.Split(taken, false);
        }
        return RiftLoot.split(taken, kit, carriedIn);
    }

    /**
     * Charges a defeat, from the split onwards: what is kept is worn, held for collection, and the
     * player is told. Shared so that a toll charged at login is this code rather than a copy of it,
     * because two copies of a price are two prices as soon as one of them is edited.
     */
    private static void charge(ServerPlayer player, RiftLoot.Split split, String message) {
        split.kept().forEach(RiftDeaths::wear);
        if (!split.kept().isEmpty()) {
            RiftClaims.of(player.server).hold(player.getUUID(), split.kept());
        }
        player.sendSystemMessage(Component.translatable(message).withStyle(ChatFormatting.GRAY));
    }

    /**
     * Charges a player for a run that wiped while they were offline.
     *
     * <p>
     * The toll is struck off whether or not it is charged: it was owed for one run, not held until a
     * gamerule changes.
     *
     * <p>
     * The inventory only. {@link RiftAccessories#strip} is upstream's death routine and it tells other
     * mods a player died, which at a login is not true, so worn trinkets go uncharged rather than
     * having a death announced for them; asking Curios alone would split a player wearing one of each.
     */
    private static void chargeToll(ServerPlayer player) {
        RiftTolls tolls = RiftTolls.of(player.server);
        RiftTolls.Toll toll = tolls.owed(player.getUUID());
        if (toll == null) {
            return;
        }
        tolls.paid(player.getUUID());
        if (!charging(toll, player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY))) {
            return;
        }

        List<ItemStack> taken = strip(player.getInventory());
        if (!taken.isEmpty()) {
            charge(player, keeping(taken, toll.rift(), toll.kit()), TOLL);
        }
    }

    /**
     * Whether a login charges a toll.
     *
     * <p>
     * {@code keepInventory} is honoured here as it is at a death: a gamerule that says death is free
     * should mean it.
     *
     * <p>
     * ⚠ Where the player is standing is deliberately not asked, and no level is passed in so that it
     * cannot be. A toll is charged in the level they logged into, which is their own doorstep and never
     * an instance, so {@link #confiscate}'s {@code isRift} guard copied across for symmetry would turn
     * every toll into a silent no-op.
     */
    static boolean charging(@Nullable RiftTolls.Toll toll, boolean keepInventory) {
        return toll != null && !keepInventory;
    }

    /**
     * Marks what a player is wearing in whichever of the two trinket mods is installed. Here rather
     * than in {@link RiftLoot} so that the class-loading wall stays in exactly one place.
     */
    static void stampWorn(ServerPlayer player, ResourceLocation rift) {
        if (curios) {
            RiftCurios.stamp(player, rift);
        }
        if (accessories) {
            RiftAccessories.stamp(player, rift);
        }
    }

    /**
     * Empties a player's inventory into a list. Walks the container indices rather than the main
     * compartment, because {@code Inventory}'s indexing runs across all three -- the 36 carried slots,
     * the four pieces of armour, then the off hand.
     *
     * <p>
     * Curse of Vanishing is honoured rather than defeated: vanilla destroys those stacks on death
     * instead of dropping them, so preserving them would make the rift the one place in the game where
     * the curse does nothing.
     */
    private static List<ItemStack> strip(Inventory inventory) {
        List<ItemStack> taken = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.removeItemNoUpdate(slot);
            if (!stack.isEmpty()
                    && !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                taken.add(stack);
            }
        }
        return taken;
    }

    /**
     * Spends {@value #WEAR_PERCENT}% of an item's durability, and never the last point of it.
     *
     * <p>
     * Of the total rather than of what is left, so the bill is the same every time and five deaths on
     * the same axe finish it whatever order they came in. Rounded up, so a small item still costs
     * something. Clamped at {@code max - 1}, which is what makes "nothing you brought is destroyed" a
     * promise rather than an intention.
     *
     * <p>
     * Written straight onto the stack, not through {@code hurtAndBreak}: this is a stated price, not a
     * swing of a sword, so no random roll, no Unbreaking discount and no advancement.
     */
    static void wear(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return;
        }
        int max = stack.getMaxDamage();
        int bill = Math.max(1, Mth.ceil(max * (WEAR_PERCENT / 100.0F)));
        stack.setDamageValue(Math.min(stack.getDamageValue() + bill, max - 1));
    }

    /**
     * Charges a login's toll. The "still held" notice moved to {@link RiftAutoClaims}, which hands
     * the gear back a moment later or says once that it is waiting for room.
     */
    private static void noticeOnLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            chargeToll(player);
        }
    }
}
