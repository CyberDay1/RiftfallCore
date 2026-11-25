package com.cyberday1.riftfall.shield;

import com.cyberday1.riftfall.init.RiftfallAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Riftfall's shield pool on a living entity: grants, reads, and spending it against a hit.
 *
 * <p>{@code MixinCommonHooks} spends it at the return of {@code CommonHooks#onLivingDamagePre}, after
 * every {@code LivingDamageEvent.Pre} listener, so resistance shrinks the hit first and nothing the
 * pack adds to it gets past the shield. The drain is lazy: every touch first drains the pool to now.
 * Every write goes through {@link #store}, which is what tells the client.
 */
public final class RiftShields {

    private RiftShields() {
    }

    /** Adds {@code amount} points to the pool and answers the pool after. A Shield node's cast grants through here. */
    public static double grant(LivingEntity entity, double amount) {
        if (!(amount > 0.0) || Double.isInfinite(amount)) {
            return total(entity);
        }
        ShieldPool after = current(entity).plus(amount);
        store(entity, after);
        return after.amount();
    }

    /** The shield points standing now, after the drain. */
    public static double total(LivingEntity entity) {
        return entity.hasData(RiftfallAttachments.SHIELDS) ? current(entity).amount() : 0.0;
    }

    /** Spends the pool against {@code damage} and answers the part that gets through. */
    public static float absorb(LivingEntity entity, float damage) {
        if (!(damage > 0.0F) || !entity.hasData(RiftfallAttachments.SHIELDS)) {
            return damage;
        }
        ShieldPool.Absorbed hit = current(entity).absorb(damage);
        store(entity, hit.pool());
        return hit.remainder();
    }

    /** The pool as last written, not drained: what the client is sent and extrapolates from. */
    public static ShieldPool stored(LivingEntity entity) {
        return entity.hasData(RiftfallAttachments.SHIELDS) ? entity.getData(RiftfallAttachments.SHIELDS) : ShieldPool.EMPTY;
    }

    private static void store(LivingEntity entity, ShieldPool pool) {
        entity.setData(RiftfallAttachments.SHIELDS, pool);
        ShieldSync.changed(entity, pool);
    }

    private static ShieldPool current(LivingEntity entity) {
        return entity.getData(RiftfallAttachments.SHIELDS).drainedTo(now(entity));
    }

    /** The overworld's game time: saved with the world and shared by every dimension, rifts included. */
    public static long now(LivingEntity entity) {
        return entity.level() instanceof ServerLevel level
                ? level.getServer().overworld().getGameTime()
                : entity.level().getGameTime();
    }
}
