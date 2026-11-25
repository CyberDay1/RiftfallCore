package com.cyberday1.riftfall.gear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import org.jetbrains.annotations.Nullable;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallAttributes;

/**
 * The offence a rift's weapon swings with, in place of the numbers its own mod gave it.
 *
 * <h2>The same act of blanking as {@link DefenceFloor}, and the same trap</h2>
 * Writing {@code DataComponents.ATTRIBUTE_MODIFIERS} <em>is</em> the blanking: the stack's own
 * accessor falls back to the item's defaults only when what it read is empty. So there is no strip
 * step, and an empty floor would silently hand the weapon back at its mod's numbers. Every row here
 * therefore writes at least one attribute that is certain to exist, and {@link #stamp} refuses to
 * write a component it could not fill rather than blanking a weapon down to nothing.
 *
 * <h2>Archetype is read off the weapon, not guessed at</h2>
 * The design names five melee classes by attack speed and says nothing about how to tell them apart,
 * because it did not have to: 1436 of the pack's 1444 melee weapons already declare a speed of their
 * own, so the weapon's author has answered the question and this only has to round the answer to the
 * nearest row. The eight that declare nothing take the sword row, which is the middle of the table
 * and the least wrong thing to be wrong about.
 *
 * <h2>⚠ Reach is kept, and it is the only thing that is</h2>
 * Blanking is the point everywhere else, but reach is weapon identity rather than weapon power. The
 * pack has 130 weapons carrying a real reach modifier &mdash; cataclysm's spears at +2.00, its athame
 * at -1.00, block_factorys_bosses' large_sword at +1.50 against its own dagger at -0.50 &mdash; and a
 * spear that lost its reach is strictly worse than the one its mod shipped, with nothing gained. So
 * reach and range carry through and everything else goes, which keeps the rule the armour floor set:
 * the rifts own the ladder, and only the ladder.
 *
 * <p>
 * ⚠ 1328 further weapons carry a reach entry of {@code 0.00}, which is an entry that changes nothing.
 * Those carry through as harmlessly as they sat there.
 *
 * <h2>The component is not the last word, so {@link #register} makes one</h2>
 * Nine of the pack's weapons beat the component outright, because {@code ItemAttributeModifierEvent}
 * fires <em>after</em> it is read: two justdirethings axes clear the list and seven weapons across
 * aether, deep_aether and legendary_monsters add their own damage back on top. Data cannot reach any
 * of them. {@link #enforce} answers on the same hook at {@code LOWEST}, on the pattern
 * {@code EnchantmentBan} already set, and restores the floor after everyone else has spoken.
 */
public final class WeaponFloor {

    /**
     * The player's base attack damage, which is <em>not</em> the attribute's default.
     *
     * <p>
     * A modifier amount is a delta from the base the holder already has, so writing the design's
     * numbers means subtracting that base. {@code Attributes.ATTACK_DAMAGE} declares a default of
     * 2.0 and {@code Player.createAttributes} then overrides it to 1.0, so the attribute's own
     * default is the wrong number to subtract and is off by a whole point of damage. Attack speed
     * has no such override: the player takes the attribute's 4.0 as it stands.
     */
    static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0D;

    static final double PLAYER_BASE_ATTACK_SPEED = 4.0D;

    /**
     * How close two archetype gaps have to be to count as a tie. Wide enough to cover the rounding
     * in a one-decimal literal and far narrower than the 0.1 that separates the closest two rows.
     */
    private static final double TIE = 1.0E-6D;

    /**
     * The pack's ranged stats, named as ids and never imported, on the same terms as
     * {@code RiftfallAttributes.PACK_TENACITY}: the mod that provides them is one the pack happens to
     * have rather than one Riftfall depends on.
     */
    private static final ResourceLocation RANGED_DAMAGE =
            ResourceLocation.fromNamespaceAndPath("ranged_weapon", "damage");

    private static final ResourceLocation RANGED_PULL_TIME =
            ResourceLocation.fromNamespaceAndPath("ranged_weapon", "pull_time");

    /**
     * What every id the floor writes begins with, and the one place that string is built.
     *
     * <p>
     * Derived rather than spelled out, and the probe reads it from here rather than keeping its own
     * copy. A literal on one side of a comparison and a built id on the other is exactly how the
     * first census run came back a false pass: it matched {@code minecraft:attack_damage} against
     * ids that are still spelled {@code minecraft:generic.attack_damage} in 1.21.1, found nothing,
     * and reported the nothing as a result.
     */
    public static final String FLOOR_PREFIX = RiftfallCore.id("weapon_floor/").toString();

    private static final ResourceLocation DAMAGE_ID = RiftfallCore.id("weapon_floor/attack_damage");
    private static final ResourceLocation SPEED_ID = RiftfallCore.id("weapon_floor/attack_speed");
    private static final ResourceLocation RANGED_DAMAGE_ID = RiftfallCore.id("weapon_floor/ranged_damage");
    private static final ResourceLocation PULL_TIME_ID = RiftfallCore.id("weapon_floor/pull_time");
    private static final ResourceLocation GUARD_ID = RiftfallCore.id("weapon_floor/guard");

    /**
     * Every item the enforcer has had to correct, by id.
     *
     * <p>
     * Instrumentation, and the positive control the check needs: {@code leaked == 0} is a headline
     * that reads identically whether the enforcer won or never ran, and this is the arm that tells
     * those apart. Add-only and bounded by the number of distinct items in the pack that fight back
     * &mdash; nine, here &mdash; because nothing reaches it until a correction has actually been made.
     * It is also, in its own right, the list a {@code gear_denied} decision would be written from.
     */
    private static final Set<String> CORRECTED = ConcurrentHashMap.newKeySet();

    private WeaponFloor() {
    }

    /**
     * Registers the floor as the last word on its own attributes.
     *
     * <p>
     * {@code LOWEST} fires last, which is the whole point: at the default {@code NORMAL} this would
     * sit in registration order among the very mods it exists to answer, and whichever of them
     * happened to load later would win silently. {@code EnchantmentBan} registers the same way for
     * the same reason. ⚠ It is very-likely-wins rather than guaranteed &mdash; a mod that also picks
     * {@code LOWEST} falls back to registration order &mdash; so the probe measures the outcome
     * rather than assuming it.
     */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,
                (ItemAttributeModifierEvent event) -> enforce(event));
    }

    /**
     * Puts the stamped component back after every other listener has had its turn.
     *
     * <p>
     * Only the attributes the floor actually wrote are policed, and they are read off the component
     * rather than named here, so reach carries through untouched: its entries keep the donor's ids,
     * so they are never in the policed set and a mod adding more reach is left alone. That is the
     * design's carve-out holding at runtime and not a second copy of it.
     */
    static void enforce(ItemAttributeModifierEvent event) {
        if (policed(event.getItemStack().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY)).isEmpty()) return;
        enforce(event, RiftLootRarity.socketModifierIds(event.getItemStack()));
    }

    /** Installed-gem IDs are supplied separately so the protection can be tested without Apotheosis. */
    static void enforce(ItemAttributeModifierEvent event, Set<ResourceLocation> gemModifiers) {
        ItemAttributeModifiers stamped = event.getItemStack()
                .getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        List<Holder<Attribute>> policed = policed(stamped);
        if (policed.isEmpty()) {
            return;
        }
        // Asked before anything is touched because touching is what costs. The event builds a
        // LinkedList and a HashMap the first time it is written to, and this hook runs on every
        // read of every stack -- tooltips, equips, every swing. The overwhelming majority of
        // stamped weapons are never contested, and they get two scans of a list of three and a
        // return.
        if (!contested(event.getModifiers(), stamped, policed, gemModifiers)) {
            return;
        }
        event.removeIf(entry -> policed.contains(entry.attribute())
                && !isFloorId(entry.modifier().id())
                // The rifts' own affix rolls ride on top of the floor, not against it.
                && !AffixTiers.isAffixModifier(entry.modifier().id())
                && !gemModifiers.contains(entry.modifier().id()));
        for (ItemAttributeModifiers.Entry entry : stamped.modifiers()) {
            // Refuses silently when the entry is already there, so this restores what was cleared
            // without disturbing what was not.
            event.addModifier(entry.attribute(), entry.modifier(), entry.slot());
        }
        CORRECTED.add(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString());
    }

    /** The attributes this stack's floor claims: those carried by an entry the floor wrote. */
    private static List<Holder<Attribute>> policed(ItemAttributeModifiers stamped) {
        List<Holder<Attribute>> attributes = new ArrayList<>(4);
        for (ItemAttributeModifiers.Entry entry : stamped.modifiers()) {
            if (isFloorId(entry.modifier().id()) && !attributes.contains(entry.attribute())) {
                attributes.add(entry.attribute());
            }
        }
        return attributes;
    }

    /**
     * Whether anyone has answered over the floor on this stack: someone else's modifier on an
     * attribute the floor claims, or one of the floor's own entries missing from the answer.
     */
    private static boolean contested(List<ItemAttributeModifiers.Entry> current,
            ItemAttributeModifiers stamped, List<Holder<Attribute>> policed, Set<ResourceLocation> gemModifiers) {
        for (ItemAttributeModifiers.Entry entry : current) {
            if (policed.contains(entry.attribute()) && !isFloorId(entry.modifier().id())
                    && !AffixTiers.isAffixModifier(entry.modifier().id())
                    && !gemModifiers.contains(entry.modifier().id())) {
                return true;
            }
        }
        for (ItemAttributeModifiers.Entry entry : stamped.modifiers()) {
            if (!present(current, entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean present(List<ItemAttributeModifiers.Entry> current,
            ItemAttributeModifiers.Entry wanted) {
        for (ItemAttributeModifiers.Entry entry : current) {
            if (entry.attribute().equals(wanted.attribute())
                    && entry.modifier().id().equals(wanted.modifier().id())) {
                return true;
            }
        }
        return false;
    }

    static boolean isFloorId(ResourceLocation id) {
        return id.toString().startsWith(FLOOR_PREFIX);
    }

    /** Which items the enforcer has had to correct so far, by id. Read by the probe. */
    public static Set<String> corrections() {
        return Collections.unmodifiableSet(CORRECTED);
    }

    /** Drops the record so a probe sweep measures its own run and not the boot before it. */
    public static void forgetCorrections() {
        CORRECTED.clear();
    }

    /**
     * The five melee classes the design names, as the speed each one swings at.
     *
     * <p>
     * ⚠ Not tier-scaled, and that is the design being deliberate rather than an omission: speed is
     * what tells a greatsword from a dagger, so scaling it with tier would collapse every archetype
     * into the same weapon by the time a player got there.
     */
    public enum Melee {
        MACE(0.8D), GREATSWORD(0.9D), AXE(1.1D), SWORD(1.6D), DAGGER(2.4D);

        final double speed;

        Melee(double speed) {
            this.speed = speed;
        }

        /** The speed this row swings at, for anything outside the package that has to name a row. */
        public double speed() {
            return speed;
        }
    }

    /** The three ranged classes, as the pull time each one draws at. */
    public enum Ranged {
        SHORTBOW(0.8D), LONGBOW(1.3D), CROSSBOW(1.6D);

        final double pullTime;

        Ranged(double pullTime) {
            this.pullTime = pullTime;
        }

        /**
         * Seconds to a full draw, which is the unit the mod that owns the stat uses.
         *
         * <p>
         * {@code ranged_weapon:pull_time} defaults to 1.0 and is multiplied by 20 ticks at the one
         * place it is read, so it is seconds per shot &mdash; the reciprocal of what
         * {@code minecraft:generic.attack_speed} is. Exposed because that makes the two families'
         * damage-per-second directly comparable and something a test can assert.
         */
        public double pullTime() {
            return pullTime;
        }
    }

    /**
     * Melee damage, scaled so the slow rows hit harder: the sword row is the anchor at
     * {@code 7.5 + 0.85T} and every other row pays for its speed, which holds damage-per-second
     * level across the table instead of letting it ride the speed column. A mace that swung at half
     * a sword's rate for a sword's damage would just be a worse sword.
     *
     * <p>
     * The anchor's own level is a ruling too: a tier-1 sword at 8.35 sits a shade over the
     * netherite sword's 8, the same margin the armour floor takes over diamond, so the worst rift
     * weapon is still a reason to walk in.
     */
    public static double meleeDamageAt(int tier, Melee archetype) {
        return (7.5D + 0.85D * tier) * Melee.SWORD.speed / archetype.speed;
    }

    /** The same trade for ranged, anchored on the longbow: a slower draw lands a heavier arrow. */
    public static double rangedDamageAt(int tier, Ranged archetype) {
        return (4.5D + 0.60D * tier) * archetype.pullTime / Ranged.LONGBOW.pullTime;
    }

    static double guardAt(int tier) {
        return 0.10D + 0.004D * tier;
    }

    /**
     * Which melee class a weapon already swings like.
     *
     * <p>
     * Nearest row by speed, and ties go to the faster of the two. The tie is not hypothetical: the
     * pack's second-heaviest cluster is 351 weapons at exactly 1.00, which sits the same distance
     * from the axe row as from the greatsword row, and 56 more sit at exactly 2.00 between sword and
     * dagger. Faster wins because vanilla's own axes sit at 0.8&ndash;1.0, so a 1.00 weapon is an axe
     * by the only precedent there is.
     */
    static Melee melee(@Nullable Double effectiveSpeed) {
        if (effectiveSpeed == null) {
            return Melee.SWORD;
        }
        Melee nearest = Melee.MACE;
        double best = Double.MAX_VALUE;
        for (Melee candidate : Melee.values()) {
            double gap = Math.abs(candidate.speed - effectiveSpeed);
            // The rows are declared slowest first, so accepting an equal gap is what tips a tie to
            // the faster row. The tolerance is why it works: the ties this rule exists for are ties
            // in decimal and not in binary. 1.1 - 1.0 comes out fractionally LARGER than 1.0 - 0.9,
            // so a bare <= gives the axe row a worse gap than greatsword and the faster row never
            // wins the one tie that 351 of the pack's weapons sit on.
            if (gap <= best + TIE) {
                best = gap;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /** Which ranged class a weapon draws like. A crossbow is one by its bucket, not by its numbers. */
    static Ranged ranged(GearPool.Slot slot, @Nullable Double effectivePullTime) {
        if (slot == GearPool.Slot.CROSSBOW) {
            return Ranged.CROSSBOW;
        }
        if (effectivePullTime == null) {
            return Ranged.SHORTBOW;
        }
        return Math.abs(Ranged.LONGBOW.pullTime - effectivePullTime)
                < Math.abs(Ranged.SHORTBOW.pullTime - effectivePullTime)
                ? Ranged.LONGBOW : Ranged.SHORTBOW;
    }

    /**
     * Gives one stack this rift's offence, or hands it back untouched.
     *
     * <p>
     * Untouched covers everything that is not a weapon the rifts own, and also every weapon whose
     * row came out empty because the mod providing its stats is absent. The second case fails
     * visibly rather than silently: leaving a bow at its own mod's numbers is a floor that did not
     * apply, while writing it an empty component would be a bow with no stats at all.
     */
    public static ItemStack stamp(ItemStack stack, int riftTier) {
        if (stack.isEmpty()) {
            return stack;
        }
        GearPool.Slot slot = GearPool.weaponSlot(stack.getItem());
        if (slot == null) {
            return stack;
        }
        ItemAttributeModifiers donor = donorModifiers(stack);
        ItemAttributeModifiers.Builder floor = ItemAttributeModifiers.builder();
        boolean wrote = switch (slot) {
            case SWORD, OTHER_MELEE, TRIDENT, MACE -> writeMelee(floor, donor, riftTier);
            case BOW, CROSSBOW -> writeRanged(floor, donor, slot, riftTier);
            case SHIELD -> writeShield(floor, riftTier);
            default -> false;
        };
        if (!wrote) {
            return stack;
        }
        carryReach(floor, donor);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, floor.build());
        return stack;
    }

    private static boolean writeMelee(ItemAttributeModifiers.Builder floor,
            ItemAttributeModifiers donor, int riftTier) {
        Melee archetype = melee(effective(donor, Attributes.ATTACK_SPEED, PLAYER_BASE_ATTACK_SPEED));
        floor.add(Attributes.ATTACK_DAMAGE,
                add(DAMAGE_ID, meleeDamageAt(riftTier, archetype) - PLAYER_BASE_ATTACK_DAMAGE),
                EquipmentSlotGroup.MAINHAND);
        floor.add(Attributes.ATTACK_SPEED,
                add(SPEED_ID, archetype.speed - PLAYER_BASE_ATTACK_SPEED),
                EquipmentSlotGroup.MAINHAND);
        return true;
    }

    /**
     * Both ranged rows come from one mod, so either both are writable or neither is. Returning false
     * when it is absent is what leaves the bow whole instead of blanking it.
     */
    private static boolean writeRanged(ItemAttributeModifiers.Builder floor,
            ItemAttributeModifiers donor, GearPool.Slot slot, int riftTier) {
        Holder<Attribute> damage = attribute(RANGED_DAMAGE);
        Holder<Attribute> pullTime = attribute(RANGED_PULL_TIME);
        if (damage == null || pullTime == null) {
            return false;
        }
        double pullBase = pullTime.value().getDefaultValue();
        Ranged archetype = ranged(slot, effective(donor, pullTime, pullBase));
        floor.add(damage,
                add(RANGED_DAMAGE_ID, rangedDamageAt(riftTier, archetype) - damage.value().getDefaultValue()),
                EquipmentSlotGroup.MAINHAND);
        floor.add(pullTime, add(PULL_TIME_ID, archetype.pullTime - pullBase),
                EquipmentSlotGroup.MAINHAND);
        return true;
    }

    /**
     * A shield's row is Riftfall's own attribute, so unlike the ranged rows it cannot go missing.
     * It applies in either hand because the pack lets a shield be raised from either.
     */
    private static boolean writeShield(ItemAttributeModifiers.Builder floor, int riftTier) {
        floor.add(RiftfallAttributes.GUARD, add(GUARD_ID, guardAt(riftTier)), EquipmentSlotGroup.HAND);
        return true;
    }

    /**
     * Copies the donor's reach and range entries onto the floor, unchanged.
     *
     * <p>
     * Matched by name rather than against a list of ids, because every mod spells its own reach stat
     * and the pack has both of vanilla's plus whatever a mod invents next.
     */
    private static void carryReach(ItemAttributeModifiers.Builder floor,
            ItemAttributeModifiers donor) {
        for (ItemAttributeModifiers.Entry entry : donor.modifiers()) {
            if (isReach(entry.attribute())) {
                floor.add(entry.attribute(), entry.modifier(), entry.slot());
            }
        }
    }

    static boolean isReach(Holder<Attribute> attribute) {
        ResourceLocation key = attribute.unwrapKey().map(resource -> resource.location()).orElse(null);
        if (key == null) {
            return false;
        }
        String path = key.getPath();
        return path.contains("range") || path.contains("reach");
    }

    /**
     * What one attribute is worth on this stack today, or null if it does not carry one.
     *
     * <p>
     * Only {@code ADD_VALUE} is summed, for the reason the census gives: the multiplied operations
     * are proportions of a base this is not reading, so folding them in would invent a number.
     */
    static @Nullable Double effective(ItemAttributeModifiers donor, Holder<Attribute> attribute,
            double base) {
        boolean found = false;
        double total = base;
        for (ItemAttributeModifiers.Entry entry : donor.modifiers()) {
            if (entry.attribute().equals(attribute)
                    && entry.slot().test(EquipmentSlot.MAINHAND)
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                total += entry.modifier().amount();
                found = true;
            }
        }
        return found ? total : null;
    }

    /**
     * The stats the weapon has before this touches it.
     *
     * <p>
     * An empty component means "ask the mod" rather than "no stats", so the fallback is not an
     * optimisation and leaving it out would read every modded weapon as unarmed.
     */
    static ItemAttributeModifiers donorModifiers(ItemStack stack) {
        ItemAttributeModifiers modifiers =
                stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (modifiers.modifiers().isEmpty()) {
            modifiers = stack.getItem().getDefaultAttributeModifiers(stack);
        }
        return modifiers;
    }

    private static AttributeModifier add(ResourceLocation id, double amount) {
        return new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE);
    }

    private static @Nullable Holder<Attribute> attribute(ResourceLocation id) {
        return BuiltInRegistries.ATTRIBUTE.getHolder(id).orElse(null);
    }
}
