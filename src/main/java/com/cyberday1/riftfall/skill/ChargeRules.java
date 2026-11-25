package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.gear.WeaponFloor;

/**
 * Charge: what a bow's arrow or a gun's bullet costs now that neither needs ammo. One pool a player, counted in
 * melee-seconds (the damage a sword of the weapon's tier deals in a second), debited per projectile whatever weapon
 * fired it. The bank and the refill are the player's {@code riftfall:max_charge} and {@code riftfall:charge_regen};
 * the two bases here are their defaults.
 */
public final class ChargeRules {

    /** k at 0 points: melee-seconds refilled a second in combat. The default of {@code riftfall:charge_regen}. */
    public static final double BASE_REFILL = 0.5;
    /** S at 0 points: the pool's ceiling and, negated, its overdraw floor. The default of {@code riftfall:max_charge}. */
    public static final double BASE_BANK = 10.0;
    /** A pool that ran dry fires again once it holds this many seconds of the held weapon's fire. */
    public static final double UNLOCK_SECONDS = 1.0;
    /** A bullet that used up a real round. */
    public static final double GUN_AMMO_BONUS = 1.20;
    /** An arrow that used up a real arrow, from a bow or a crossbow. */
    public static final double ARROW_AMMO_BONUS = 1.10;
    /** Arrows a Ranger's cast uses up for the arrow bonus, bow held. */
    public static final int SKILL_ARROWS = 1;
    /** Rounds of the held gun's calibre a Gunner's cast uses up for the gun bonus. */
    public static final int SKILL_ROUNDS = 3;

    /** One shot of a weapon: what it costs the pool, and how many it fires a second flat out. */
    public record Shot(double cost, double perSecond) {

        /** What a dry pool of {@code bank} must hold before this weapon fires again. */
        public double unlock(double bank) {
            return ChargeRules.unlock(cost, perSecond, bank);
        }
    }

    private ChargeRules() {
    }

    /** A weapon dealing {@code commonDamage} a shot at common rarity, {@code perSecond} times a second. */
    public static Shot shot(double commonDamage, int tier, double perSecond) {
        return new Shot(cost(commonDamage, tier), Math.max(0.0, perSecond));
    }

    /** M(T): a tier's sword, in damage a second. Tier 0 (unstamped) reads as tier 1. */
    public static double meleeDps(int tier) {
        return WeaponFloor.meleeDamageAt(Math.max(1, tier), WeaponFloor.Melee.SWORD) * WeaponFloor.Melee.SWORD.speed();
    }

    /** A projectile's price: its damage at common rarity over its tier's melee damage a second. */
    public static double cost(double commonDamage, int tier) {
        return commonDamage > 0.0 ? commonDamage / meleeDps(tier) : 0.0;
    }

    /** The refill a second from a {@code riftfall:charge_regen} of {@code regen}: the mana combat window's split. */
    public static double refillPerSecond(double regen, boolean inCombat) {
        return Math.max(0.0, regen) * ManaRules.regenMultiplier(inCombat);
    }

    /** What the pool must hold for a shot of {@code cost} to fire: the cost, or the whole bank if it costs more. */
    public static double need(double cost, double bank) {
        return Math.min(Math.max(0.0, cost), bank);
    }

    /** What a dry pool must hold before a weapon firing {@code perSecond} shots of {@code cost} fires again. */
    public static double unlock(double cost, double perSecond, double bank) {
        return Math.min(bank, Math.max(0.0, cost) * Math.max(1.0, perSecond * UNLOCK_SECONDS));
    }

    /** The pool after a projectile of {@code cost}: never refused here, and never below a bank overdrawn. */
    public static double debit(double current, double cost, double bank) {
        return Math.max(-bank, current - Math.max(0.0, cost));
    }

    /**
     * Whether a pool is shut after this look. An open one shuts when it cannot pay one shot; a shut one opens
     * again only at the weapon's unlock, so automatic fire does not stutter on every refilled round.
     */
    public static boolean locked(boolean wasLocked, double current, Shot shot, double bank) {
        double need = need(shot.cost(), bank);
        if (need <= 0.0) {
            // A shot that costs nothing is never refused, even on an overdrawn pool.
            return false;
        }
        return wasLocked ? current < Math.max(need, shot.unlock(bank)) : current < need;
    }

    /** A projectile's damage multiplier: the bonus only when a real round or arrow was used up for it. */
    public static double ammoBonus(boolean consumed, boolean gun) {
        return !consumed ? 1.0 : gun ? GUN_AMMO_BONUS : ARROW_AMMO_BONUS;
    }
}
