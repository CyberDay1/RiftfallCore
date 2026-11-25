package com.cyberday1.riftfall.stat;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.UUID;

/**
 * Putting an allocation on a player, taking it off again, and putting it back after a respawn.
 *
 * <h2>Transient, so {@link RiftStats} stays the only record</h2>
 * A permanent modifier is written into the player's own NBT by {@code AttributeMap#save}, which
 * would make the allocation exist in two places that can disagree &mdash; and would leave a point
 * spent forever on a world whose Riftfall jar had been pulled. Transient means the modifiers are
 * gone on every relog and have to be rebuilt from storage, so {@link #reapply} is not a nicety: it
 * is what makes the saved data load-bearing. It is also what makes an orphaned id self-healing, if
 * a later jar ever drops a primary.
 *
 * <h2>Removal is by id, never by attribute</h2>
 * Respec removes exactly the ids this class wrote. Clearing an <em>attribute</em> instead would
 * take whatever else happens to be on it, and on a worn player that is real: an equipped stack's
 * modifiers live in the same {@link AttributeMap}, under the donor's ids. {@code WeaponFloor} has
 * already stripped modifiers once by policing too wide a set.
 */
public final class StatModifiers {

    /**
     * The one prefix every player-borne stat modifier is written under.
     *
     * <p>
     * ⚠ A second spelling of it lives in {@code tools/rig/checks.py} as {@code STAT_PREFIX}, which
     * is deliberate for the reason the budget check gives: the rig counts what lands under this
     * prefix, so the two halves drifting has to be a red run.
     */
    public static final String PREFIX = "stat/";

    /** The same prefix the way an id prints it, which is the spelling the rig's copy is in. */
    public static final String QUALIFIED_PREFIX = RiftfallCore.MODID + ":" + PREFIX;

    private StatModifiers() {
    }

    /** Where a primary's points are written. Deterministic, because respec removes by this. */
    public static ResourceLocation id(StatBlock.Primary primary) {
        return RiftfallCore.id(PREFIX + primary.key());
    }

    /** Whether an id is one of ours, asked the way anything outside this package should ask it. */
    public static boolean isStatModifier(ResourceLocation id) {
        return id.getNamespace().equals(RiftfallCore.MODID)
                && id.getPath().startsWith(PREFIX);
    }

    /**
     * Writes an allocation, returning how many primaries carry points.
     *
     * <p>
     * Every primary is removed before anything is added, so applying twice is applying once and a
     * re-apply after a spend cannot leave the old amount stacked underneath the new one.
     * A primary with no points is written as nothing rather than as zero &mdash; a zero modifier
     * would show on the tooltip and mean the same as absence.
     */
    public static int apply(AttributeMap attributes, StatBlock block) {
        int written = 0;
        for (StatBlock.Primary primary : StatBlock.Primary.values()) {
            AttributeInstance instance = attributes.getInstance(RiftfallAttributes.primary(primary));
            if (instance == null) {
                continue;
            }
            instance.removeModifier(id(primary));
            int points = block.points(primary);
            if (points > 0) {
                instance.addTransientModifier(new AttributeModifier(
                        id(primary), points, AttributeModifier.Operation.ADD_VALUE));
                written++;
            }
        }
        applyDerived(attributes, block);
        return written;
    }

    /**
     * The second write: what the points buy, in the pack's own channels.
     *
     * <p>
     * A row whose mod is absent is skipped rather than defaulted, which is the same answer
     * {@code getInstance} gives for a channel nothing attached &mdash; and the reason the rig
     * asks whether each row landed instead of trusting this count.
     *
     * @return how many rows were written
     */
    public static int applyDerived(AttributeMap attributes, StatBlock block) {
        clearDerived(attributes);
        int written = 0;
        for (Derivation.Row row : Derivation.rowsView()) {
            int points = block.points(row.primary());
            if (points <= 0) {
                continue;
            }
            AttributeInstance instance = instance(attributes, row);
            if (instance == null) {
                continue;
            }
            instance.addTransientModifier(new AttributeModifier(
                    row.id(), row.amount(points), row.operation()));
            written++;
        }
        return written;
    }

    /** Takes every modifier this class wrote back off, and nothing else. Returns how many went. */
    public static int clear(AttributeMap attributes) {
        int removed = 0;
        for (StatBlock.Primary primary : StatBlock.Primary.values()) {
            AttributeInstance instance = attributes.getInstance(RiftfallAttributes.primary(primary));
            if (instance != null && instance.removeModifier(id(primary))) {
                removed++;
            }
        }
        clearDerived(attributes);
        return removed;
    }

    /**
     * Removes the derived set by id, one row at a time.
     *
     * <p>
     * ⛔ Never by attribute. A derived row shares its channel with real foreign writes &mdash;
     * {@code riftfall:defence_floor/armor/<slot>} and the donor armour's own modifier both sit on
     * {@code minecraft:generic.armor}, which is where the Vitality row lands &mdash; so clearing
     * the channel would strip the player's gear. Since S4 that failure needs no synthetic fixture
     * to reach.
     */
    public static int clearDerived(AttributeMap attributes) {
        int removed = 0;
        for (Derivation.Row row : Derivation.rowsView()) {
            AttributeInstance instance = instance(attributes, row);
            if (instance != null && instance.removeModifier(row.id())) {
                removed++;
            }
        }
        return removed;
    }

    /**
     * Attack damage with Strength's multiplier divided back out: what a class school that scales on
     * another primary reads, so Intellect and Vitality skills do not also grow with Strength. Exact,
     * because the row is a total multiplier and multiplies the value once, last.
     */
    public static double withoutStrength(@org.jetbrains.annotations.Nullable AttributeInstance attack) {
        if (attack == null) {
            return 0.0D;
        }
        double value = attack.getValue();
        AttributeModifier strength = attack.getModifier(Derivation.strengthDamage().id());
        if (strength == null || strength.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                || !(strength.amount() > -1.0D)) {
            return value;
        }
        return value / (1.0D + strength.amount());
    }

    private static @org.jetbrains.annotations.Nullable AttributeInstance instance(
            AttributeMap attributes, Derivation.Row row) {
        var attribute = row.attribute();
        return attribute == null ? null : attributes.getInstance(attribute);
    }

    public static int apply(ServerPlayer player, StatBlock block) {
        return apply(player.getAttributes(), block);
    }

    public static int clear(ServerPlayer player) {
        return clear(player.getAttributes());
    }

    /**
     * Stores a class block and puts it on the player with their own points, in that order and in one call.
     *
     * <p>
     * One call because they are one fact. Storing without applying is a point the player paid for
     * and cannot feel until they relog; applying without storing is one they lose when they do.
     */
    public static void store(ServerPlayer player, StatBlock block) {
        RiftStats stats = RiftStats.of(player.server);
        stats.set(player.getUUID(), block);
        apply(player, block.combined(stats.allocFor(player.getUUID())));
    }

    /** Stores the player's own attribute points and applies them on top of the class block, in one call. */
    public static void storeAlloc(ServerPlayer player, StatBlock alloc) {
        RiftStats stats = RiftStats.of(player.server);
        stats.setAlloc(player.getUUID(), alloc);
        apply(player, stats.blockFor(player.getUUID()).combined(alloc));
    }

    /**
     * Rebuilds a player's modifiers from storage, after bringing storage in line with their class.
     * A class chosen before class blocks existed, or a table edit, lands here at the next login.
     */
    public static int reapply(ServerPlayer player) {
        return apply(player, applied(RiftStats.of(player.server), player.getUUID()));
    }

    /** What a player carries: the reconciled class block plus their own points. Reconcile leaves {@code alloc} alone. */
    static StatBlock applied(RiftStats stats, UUID player) {
        return reconciled(stats, player).combined(stats.allocFor(player));
    }

    /** The block a player should carry, written back to storage only when it differs. */
    static StatBlock reconciled(RiftStats stats, UUID player) {
        StatBlock stored = stats.blockFor(player);
        StatBlock block = ClassAlignment.reconcile(stored, stats.bookFor(player).skillClass());
        if (!block.equals(stored)) {
            stats.set(player, block);
        }
        return block;
    }

    /**
     * The two moments a player's attribute map is new.
     *
     * <p>
     * Login is the obvious one. Respawn is the one worth naming: {@code PlayerList#respawn} builds
     * a second {@code ServerPlayer} and {@code ServerPlayer#restoreFrom} calls
     * {@code assignBaseValues}, not {@code assignAllValues} &mdash; so base values carry and every
     * transient modifier is dropped.
     *
     * <p>
     * ⭐ Changing dimension is deliberately <em>not</em> here, and that is checked rather than
     * assumed: {@code ServerPlayer#changeDimension} returns {@code this}, so the player keeps the
     * map they already had. For a mod whose whole subject is walking between dimensions, a listener
     * there would fire constantly and prove nothing.
     */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(StatModifiers::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(StatModifiers::onRespawn);
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reapply(player);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reapply(player);
        }
    }
}
