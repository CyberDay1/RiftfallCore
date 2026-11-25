package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.compat.TaczGuns;
import com.cyberday1.riftfall.gear.WeaponFloor;
import com.cyberday1.riftfall.init.RiftfallEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.fml.ModList;

import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The Gunner's TaCZ guns: the rules, as plain numbers. {@link TaczGuns} is the only class that names a TaCZ type and
 * is reached only behind {@link #loaded()}, so Riftfall loads without TaCZ and the Gunner casts as before.
 *
 * <ul>
 *   <li>A held gun is a weapon whose attack damage is one trigger pull (bullet damage x pellets), so a Gunner skill
 *   cast with it scales on it as a sword's does.</li>
 *   <li>Ranged damage bonuses raise a bullet by the share they add to a tier-1 longbow; damage buffs (Strength)
 *   raise it by the share they add to the gun-as-weapon's attack damage. The Strength stat is a total multiplier
 *   (2026-11-08), so it cancels out of that share: it raises a gun's skills, never its bullets.</li>
 *   <li>Draw speed and Hastened speed up a reload; Hastened raises fire rate; Keen tightens spread.</li>
 *   <li>Tactical Reload and Quick Reload refill the held magazine, for free: a player's gun needs no ammo.</li>
 *   <li>A magazine never comes back as ammo items: its rounds are free, so unloading or changing it returns
 *   nothing. Crafted rounds are only ever taken from the inventory, one a round fired, for
 *   {@link ChargeRules#GUN_AMMO_BONUS}.</li>
 * </ul>
 */
public final class GunnerGuns {

    /** The transient attack damage a held gun adds for the length of one Gunner cast. */
    public static final ResourceLocation CAST_GUN_ID = RiftfallCore.id("gunner_gun");
    /** A gun's attack damage is a delta from the player's base 1.0, as {@link WeaponFloor} writes a sword's. */
    static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0;
    /** Ranged damage bonuses are read as a share of the tier-1 longbow's arrow (5.1). */
    public static final double RANGED_REFERENCE = WeaponFloor.rangedDamageAt(1, WeaponFloor.Ranged.LONGBOW);
    /** Hastened's fire-rate and reload share a level: its ability haste read as a percent. */
    public static final double HASTE_SHARE_PER_LEVEL = RiftfallEffects.HASTE_PER_LEVEL / 100.0;
    /** Keen's spread cut a level (assumed default: Keen II, Focus Aim's, leaves 60% of the spread). */
    public static final double SPREAD_CUT_PER_LEVEL = 0.20;
    /** TaCZ clamps a gun's rpm to this. */
    public static final int MAX_RPM = 1200;
    /** The fire-rate bonus ceiling, {@code riftfall:fire_rate}'s own: +100%. */
    public static final double MAX_FIRE_RATE = 1.0;
    /** A reload never runs slower than its own clock, whatever a negative bonus says. */
    static final double MIN_RATE = 1.0;
    /** Ticks are 50 ms; TaCZ's reload clock is wall time. */
    public static final double TICK_MILLIS = 50.0;

    public static final String TACTICAL_RELOAD = "gunner_tactical_reload";
    public static final String QUICK_RELOAD = "gunner_quick_reload";
    /** Quick Reload's own area (its spell's range). */
    static final double QUICK_RELOAD_RADIUS = 5.0;
    private static final Set<String> RELOADS = Set.of(TACTICAL_RELOAD, QUICK_RELOAD);

    private static Boolean loaded;

    private GunnerGuns() {
    }

    /** Whether TaCZ is installed. */
    public static boolean loaded() {
        if (loaded == null) {
            ModList mods = ModList.get();
            loaded = mods != null && mods.isLoaded("tacz");
        }
        return loaded;
    }

    /** Wires the TaCZ listeners; nothing without TaCZ. */
    public static void register() {
        if (loaded()) {
            TaczGuns.register();
        }
    }

    // ---- the numbers ----

    /** The attack damage modifier a gun firing {@code shotDamage} a pull is worth, as a weapon. */
    public static double castBonus(double shotDamage) {
        return Math.max(0.0, shotDamage - PLAYER_BASE_ATTACK_DAMAGE);
    }

    /** A bullet's multiplier: ranged damage bonus over the longbow reference, times the buff ratio. */
    public static double damageMultiplier(double rangedBonus, double buffRatio) {
        return Math.max(0.0, 1.0 + rangedBonus / RANGED_REFERENCE) * Math.max(0.0, buffRatio);
    }

    /** How fast a reload runs against TaCZ's clock: 1 plus draw speed bonus plus Hastened's share. */
    public static double reloadRate(double drawSpeedBonus, int hastenedLevels) {
        return Math.max(MIN_RATE, 1.0 + drawSpeedBonus + HASTE_SHARE_PER_LEVEL * Math.max(0, hastenedLevels));
    }

    /** A gun's rpm under {@code hastenedLevels} of Hastened. */
    public static int rpm(int base, int hastenedLevels) {
        return rpm(base, hastenedLevels, 1.0);
    }

    /** A gun's rpm under {@code hastenedLevels} of Hastened, times its grade's fire-rate factor. */
    public static int rpm(int base, int hastenedLevels, double gradeFactor) {
        return rpm(base, hastenedLevels, gradeFactor, 0.0);
    }

    /** As above, times one plus the shooter's fire-rate bonus (Fast Hands); never past TaCZ's 1200. */
    public static int rpm(int base, int hastenedLevels, double gradeFactor, double fireRate) {
        double scaled = base * (1.0 + HASTE_SHARE_PER_LEVEL * Math.max(0, hastenedLevels)) * Math.max(0.0, gradeFactor)
                * cycleRate(fireRate);
        return (int) Math.max(1, Math.min(MAX_RPM, Math.round(scaled)));
    }

    /** How fast a bolt-action or pump gun cycles against TaCZ's clock: one plus the capped fire-rate bonus. */
    public static double cycleRate(double fireRate) {
        return 1.0 + Math.clamp(fireRate, 0.0, MAX_FIRE_RATE);
    }

    /** A spread under {@code keenLevels} of Keen. */
    public static float spread(float base, int keenLevels) {
        return spread(base, keenLevels, 1.0);
    }

    /** A spread under {@code keenLevels} of Keen, times its grade's spread factor. */
    public static float spread(float base, int keenLevels, double gradeFactor) {
        double keen = Math.max(0.0, 1.0 - SPREAD_CUT_PER_LEVEL * Math.max(0, keenLevels));
        return (float) (base * keen * Math.max(0.0, gradeFactor));
    }

    /** Milliseconds a reload's start moves back in one tick at {@code rate}. */
    public static double reloadShiftMillis(double rate) {
        return TICK_MILLIS * Math.max(0.0, rate - 1.0);
    }

    /**
     * An attribute's value with the modifiers {@code skip} matches left out, by vanilla's order: base plus the adds,
     * times one plus the base multipliers, times each total multiplier.
     */
    public static double valueWithout(double base, Collection<AttributeModifier> modifiers,
                                      Predicate<ResourceLocation> skip) {
        double add = base;
        double multBase = 0.0;
        double multTotal = 1.0;
        for (AttributeModifier m : modifiers) {
            if (skip.test(m.id())) {
                continue;
            }
            switch (m.operation()) {
                case ADD_VALUE -> add += m.amount();
                case ADD_MULTIPLIED_BASE -> multBase += m.amount();
                case ADD_MULTIPLIED_TOTAL -> multTotal *= 1.0 + m.amount();
            }
        }
        return (add) * (1.0 + multBase) * multTotal;
    }

    /** The share buffs add: value with everything over value without {@code skip}'s modifiers; 1 when undefined. */
    public static double buffRatio(double base, Collection<AttributeModifier> modifiers,
                                   Predicate<ResourceLocation> skip) {
        double with = valueWithout(base, modifiers, id -> false);
        double without = valueWithout(base, modifiers, skip);
        return without > 0.0 && with > 0.0 ? with / without : 1.0;
    }

    // ---- the hooks Riftfall's own code calls ----

    /** Whether {@code node} is one of the two reload skills, which use no ammo up. */
    static boolean reloads(SkillNode node) {
        return RELOADS.contains(node.id());
    }

    /** Whether {@code node} is a Gunner castable. */
    static boolean gunnerNode(SkillNode node) {
        SkillDef def = SkillDefs.get(node.id());
        return def != null && "gunner".equals(def.skillClass());
    }

    /**
     * Runs {@code body} with {@code caster}'s held gun counted as a weapon of its shot damage when {@code node} is a
     * Gunner castable and its power is attack damage; otherwise just runs it.
     */
    static <T> T withGun(LivingEntity caster, SkillNode node, Supplier<T> body) {
        if (!loaded() || !Attributes.ATTACK_DAMAGE.is(node.powerAttribute()) || !gunnerNode(node)) {
            return body.get();
        }
        double shot = TaczGuns.shotDamage(caster);
        AttributeInstance attack = caster.getAttribute(Attributes.ATTACK_DAMAGE);
        if (!(shot > 0.0) || attack == null || attack.getModifier(CAST_GUN_ID) != null) {
            return body.get();
        }
        attack.addTransientModifier(new AttributeModifier(CAST_GUN_ID, castBonus(shot),
                AttributeModifier.Operation.ADD_VALUE));
        try {
            return body.get();
        } finally {
            attack.removeModifier(CAST_GUN_ID);
        }
    }

    /** After a kept cast: Tactical Reload refills the caster's gun, Quick Reload also the allies its area reaches. */
    static void landed(ServerLevel level, LivingEntity caster, SkillNode node) {
        if (!loaded() || !RELOADS.contains(node.id())) {
            return;
        }
        TaczGuns.refill(caster);
        if (QUICK_RELOAD.equals(node.id())) {
            for (ServerPlayer ally : SkillCasts.allies(level, caster, QUICK_RELOAD_RADIUS)) {
                TaczGuns.refill(ally);
            }
        }
    }
}
