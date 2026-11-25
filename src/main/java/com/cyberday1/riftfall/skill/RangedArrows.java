package com.cyberday1.riftfall.skill;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Bows and crossbows without ammo. A bow that would use a plain arrow, or finds none, is handed a free one that
 * vanilla marks intangible, so the bow never takes the player's own. Each arrow entering the level debits Charge;
 * Riftfall then takes one plain arrow from the shooter itself. An arrow it took earns the bonus, and so does a
 * tipped, spectral or modded arrow the bow used up, which keeps its own effect.
 */
public final class RangedArrows {

    /** On an arrow whose shot used up a real arrow: the damage multiplier it lands with. */
    public static final String BONUS_KEY = "riftfall:ammo_bonus";
    /** On an arrow Charge has already seen, so a second join (a dimension change) is not a second shot. */
    static final String SEEN_KEY = "riftfall:charge_seen";

    private RangedArrows() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RangedArrows::onProjectile);
        NeoForge.EVENT_BUS.addListener(RangedArrows::onLoose);
        NeoForge.EVENT_BUS.addListener(RangedArrows::onJoin);
        NeoForge.EVENT_BUS.addListener(RangedArrows::onHurt);
    }

    // ---- the rules ----

    /** The arrow a bow is handed: vanilla reads the mark as "nothing was used up" and forbids picking it up. */
    static ItemStack freeArrow() {
        ItemStack arrow = new ItemStack(Items.ARROW);
        arrow.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
        return arrow;
    }

    /** A plain arrow, the one ammo Riftfall takes itself. Tipped, spectral and modded arrows are left to the bow. */
    static boolean plain(ItemStack stack) {
        return stack.is(Items.ARROW) && stack.getComponentsPatch().isEmpty();
    }

    /** Whether a bow looking for ammo and finding {@code found} is handed the free arrow instead. */
    static boolean supplies(ItemStack found) {
        return found.isEmpty() || plain(found);
    }

    /** How the arrow in the air was paid for. */
    enum Ammo { FREE, REAL, COPY }

    /**
     * FREE is the arrow a bow was handed (vanilla leaves the intangible mark on it): Riftfall may take a plain one.
     * REAL is a special arrow the bow used up itself. COPY is an unpaid extra, as a multishot side arrow is.
     */
    static Ammo ammo(ItemStack origin) {
        boolean intangible = origin.has(DataComponents.INTANGIBLE_PROJECTILE);
        if (origin.isEmpty() || plain(origin) || intangible && origin.is(Items.ARROW)) {
            return Ammo.FREE;
        }
        return intangible ? Ammo.COPY : Ammo.REAL;
    }

    /** Takes one plain arrow from {@code stacks}, first found. True when one was really removed. */
    static boolean take(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (plain(stack)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an arrow can be picked up after its shot. One that used an arrow up gives it back as vanilla does; one
     * that used nothing never becomes an item, whatever its bow said.
     */
    static AbstractArrow.Pickup pickup(AbstractArrow.Pickup set, boolean used) {
        if (used) {
            return set == AbstractArrow.Pickup.CREATIVE_ONLY ? AbstractArrow.Pickup.ALLOWED : set;
        }
        return set == AbstractArrow.Pickup.ALLOWED ? AbstractArrow.Pickup.CREATIVE_ONLY : set;
    }

    // ---- the hooks ----

    private static void onProjectile(LivingGetProjectileEvent event) {
        if (!(event.getEntity() instanceof Player player) || !RangedCharge.applies(player)) {
            return;
        }
        ItemStack weapon = event.getProjectileWeaponItemStack();
        if (weapon.getItem() instanceof ProjectileWeaponItem bow && supplies(event.getProjectileItemStack())
                && bow.getAllSupportedProjectiles(weapon).test(new ItemStack(Items.ARROW))) {
            event.setProjectileItemStack(freeArrow());
        }
    }

    /** A bow's release and a crossbow's shot, both sides: the client asks its synced copy, the server the pool. */
    private static void onLoose(ArrowLooseEvent event) {
        Player player = event.getEntity();
        if (!RangedCharge.applies(player)) {
            return;
        }
        if (player instanceof ServerPlayer server) {
            ChargeRules.Shot shot = ChargedWeapons.bow(event.getBow());
            if (shot != null && !RangedCharge.gate(server, shot, ManaCombat.now(server.server))) {
                event.setCanceled(true);
                ChargeSync.tell(server);
            }
        } else if (player.isLocalPlayer() && ChargeView.locked()) {
            event.setCanceled(true);
        }
    }

    /** Every arrow a player's bow puts in the level, whether or not its bow posted {@link ArrowLooseEvent}. */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()
                || !(event.getEntity() instanceof AbstractArrow arrow) || arrow instanceof ThrownTrident
                || !(arrow.getOwner() instanceof ServerPlayer player) || !RangedCharge.applies(player)
                || arrow.getPersistentData().contains(SEEN_KEY)) {
            return;
        }
        ItemStack bow = bowOf(arrow, player);
        ChargeRules.Shot shot = bow == null ? null : ChargedWeapons.bow(bow);
        if (shot == null) {
            return;
        }
        if (!RangedCharge.gate(player, shot, ManaCombat.now(player.server))) {
            event.setCanceled(true);
            ChargeSync.tell(player);
            return;
        }
        arrow.getPersistentData().putBoolean(SEEN_KEY, true);
        RangedCharge.debit(player, shot.cost());
        Ammo ammo = ammo(arrow.getPickupItemStackOrigin());
        boolean used = ammo == Ammo.REAL;
        if (ammo == Ammo.FREE) {
            used = take(player.getInventory());
            arrow.pickup = pickup(arrow.pickup, used);
            if (used) {
                // Picked up again it must be the plain arrow that was taken, not the marked one.
                arrow.getSlot(0).set(new ItemStack(Items.ARROW));
            }
        }
        if (used) {
            arrow.getPersistentData().putDouble(BONUS_KEY, ChargeRules.ammoBonus(true, false));
        }
        ChargeSync.tell(player);
    }

    /** The bow an arrow left: the one written on it, else the one its owner holds; null for anything else. */
    private static @Nullable ItemStack bowOf(AbstractArrow arrow, Player owner) {
        ItemStack weapon = arrow.getWeaponItem();
        if (weapon != null && !weapon.isEmpty()) {
            return ChargedWeapons.isBow(weapon) ? weapon : null;
        }
        if (ChargedWeapons.isBow(owner.getMainHandItem())) {
            return owner.getMainHandItem();
        }
        return ChargedWeapons.isBow(owner.getOffhandItem()) ? owner.getOffhandItem() : null;
    }

    /** Off hand first, then the carried slots, as vanilla looks for ammo. */
    private static boolean take(Inventory inventory) {
        return take(inventory.offhand) || take(inventory.items);
    }

    /** How many plain arrows {@code stacks} hold. */
    static int count(List<ItemStack> stacks) {
        int count = 0;
        for (ItemStack stack : stacks) {
            if (plain(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Takes {@code count} plain arrows from {@code player}, all or none. A skill cast's price for its bonus. */
    static boolean take(Player player, int count) {
        Inventory inventory = player.getInventory();
        if (count <= 0 || count(inventory.offhand) + count(inventory.items) < count) {
            return false;
        }
        for (int taken = 0; taken < count; taken++) {
            take(inventory);
        }
        return true;
    }

    /** Hands back arrows a cast took and then was not kept. */
    static void give(Player player, int count) {
        if (count > 0) {
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.ARROW, count));
        }
    }

    private static void onHurt(LivingIncomingDamageEvent event) {
        if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow
                && arrow.getPersistentData().contains(BONUS_KEY)) {
            event.setAmount((float) (event.getAmount() * arrow.getPersistentData().getDouble(BONUS_KEY)));
        }
    }
}
