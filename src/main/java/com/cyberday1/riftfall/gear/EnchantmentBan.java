package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;

/**
 * Enchantment is an allowlist, and this is the half of it that cannot be walked around.
 *
 * <p>
 * The pack carries 116 enchantments &mdash; 42 from Minecraft and 74 from seventeen mods &mdash; and
 * Riftfall's own rolled gear stats are meant to be the power system. A second, uncontrolled one
 * running beside it is the thing being removed here. Three survive, because they change what the game
 * gives you rather than how hard you hit: Fortune, Looting and Unbreaking. Infinity left the list on
 * 2026-11-09: a bow that still carries it reads as without it.
 *
 * <h2>One list, in {@link #PERMITTED}</h2>
 * The three are named once: {@code riftfall:offered} (Looting, Unbreaking) plus Fortune in
 * {@code riftfall:permitted}. The five vanilla tags this mod overrides are each {@code "replace": true}
 * over {@code "#riftfall:offered"}: Fortune works but is never generated, because it is Blacksmith-only
 * ({@link FortuneGrant}). Adding a Riftfall enchantment later is still an edit to one file.
 *
 * <h2>Why the tags are not enough on their own</h2>
 * The tags close every route the game itself uses to hand out an enchantment: the enchanting table
 * reads {@code in_enchanting_table} and offers nothing outside it, and {@code on_random_loot},
 * {@code tradeable}, {@code on_mob_spawn_equipment} and {@code on_traded_equipment} cover chests,
 * villagers, armed mobs and trade stock. Between them they stop a banned enchantment ever being
 * <em>generated</em>, which is the important half and is worth having on its own: an enchantment the
 * table refuses to offer is one the player never pays for and never sees fail.
 *
 * <p>
 * They do not stop one being <em>written</em>. A loot table with an explicit {@code options:} list
 * on {@code enchant_randomly} names its enchantments directly and never consults a tag; so does any
 * mod calling {@code ItemStack#enchant}, and so does a creative-mode book. With seventeen mods
 * contributing enchantments, that is not a hypothetical.
 *
 * <p>
 * So the tags are the front door and this is the floor. {@link GetEnchantmentLevelEvent} fires from
 * {@code IItemStackExtension#getEnchantmentLevel} and {@code getAllEnchantments()}, which is every
 * query the game makes of an enchantment's level for gameplay purposes, whatever put it on the stack
 * and whichever mod owns it. Removing the entry there makes the enchantment read as absent.
 *
 * <p>
 * This is the same shape as the item-stat override, and deliberately: both work by intercepting the
 * question rather than by chasing every place an answer could have been stored.
 *
 * <h2>The tooltip is the known gap</h2>
 * The event's own documentation is explicit that it "is not fired for interactions with NBT, which
 * means these changes will not reflect in the item tooltip". An item that somehow acquired Sharpness
 * would still say Sharpness while dealing no extra damage. Closing that means physically clearing
 * the component off the stack, which wants to happen where Riftfall already handles one &mdash; the
 * loot roll, the run claim &mdash; rather than in a per-tick sweep of every inventory on the server.
 * It is left open because the tags make the case rare rather than routine.
 *
 * <h2>Failure direction</h2>
 * If {@code riftfall:permitted} fails to load, every holder answers no and every enchantment is
 * stripped. That is the wrong behaviour but the right direction: a ban whose list went missing
 * should ban, not permit, and losing Unbreaking is a visible bug where silently restoring 113
 * enchantments would not be.
 */
public final class EnchantmentBan {

    /**
     * The enchantments that still work. Everything not in here reads as level 0.
     *
     * <p>
     * A tag rather than a set of {@code ResourceKey}s so that the datapack owns the list outright.
     * The five vanilla overrides point at this same tag, so there is one place to edit and no way
     * for the table to offer something the event then strips.
     */
    public static final TagKey<Enchantment> PERMITTED =
            TagKey.create(Registries.ENCHANTMENT, RiftfallCore.id("permitted"));

    private EnchantmentBan() {
    }

    /**
     * Registered at {@link EventPriority#LOWEST} so that the strip is the last word.
     *
     * <p>
     * Other mods write to this event as well as read it: Apotheosis adds the levels its gems and
     * affixes grant, and it does so at {@code HIGH}, which is why the ban currently holds over a
     * socketed gem &mdash; Apotheosis writes, then this removes. That ordering is worth having on
     * purpose rather than by luck. At the default {@code NORMAL} the ban would sit in registration
     * order among its peers, and any mod that added levels below it would land after the strip and
     * pass through in silence, which is the one failure here that would not announce itself.
     */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EnchantmentBan::strip);
    }

    /**
     * Drops every enchantment outside {@link #PERMITTED} from the answer.
     *
     * <p>
     * The event carries a mutable copy, so this changes what the caller is told and not what is on
     * the stack. That is what keeps it cheap enough to run on a hook this hot, and it is also why
     * the tooltip is unaffected.
     */
    private static void strip(GetEnchantmentLevelEvent event) {
        event.getEnchantments().removeIf(enchantment -> !enchantment.is(PERMITTED));
    }
}
