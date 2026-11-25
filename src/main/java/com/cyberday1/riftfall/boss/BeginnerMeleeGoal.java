package com.cyberday1.riftfall.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.phys.Vec3;

/** Entry bosses telegraph, plant their feet, and strike the direction they committed to. */
public final class BeginnerMeleeGoal extends MeleeAttackGoal {
    static final double REACH = 2.6;
    static final double CONE_COSINE = .4;
    private final BeginnerAttackCycle cycle = new BeginnerAttackCycle();
    private Vec3 committedDirection = Vec3.ZERO;
    private Vec3 committedPoint = Vec3.ZERO;

    public BeginnerMeleeGoal(PathfinderMob mob) { super(mob, 1.15, true); }

    public boolean windingUp() { return cycle.windingUp(); }

    @Override public void tick() {
        if (!cycle.planted(mob.level().getGameTime())) { super.tick(); return; }
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(committedPoint.x, committedPoint.y, committedPoint.z, 30, 30);
        checkAndPerformAttack(mob.getTarget());
    }

    @Override protected void checkAndPerformAttack(LivingEntity target) {
        boolean inRange = target != null && target.isAlive() && mob.distanceToSqr(target) <= REACH*REACH
                && mob.getSensing().hasLineOfSight(target);
        Vec3 toward = target == null ? Vec3.ZERO : target.position().subtract(mob.position()).multiply(1,0,1).normalize();
        var action = cycle.update(mob.level().getGameTime(), target == null ? null : target.getUUID(), inRange,
                inRange && toward.dot(committedDirection) >= CONE_COSINE);
        switch (action) {
            case WARN -> {
                committedDirection = toward; committedPoint = target.getEyePosition();
                mob.getNavigation().stop();
                mob.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, .65F, 1.5F);
                if (mob.level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        mob.getX(), mob.getEyeY()+.25, mob.getZ(), 3, .3, .1, .3, 0);
            }
            case HIT -> { mob.swing(InteractionHand.MAIN_HAND); mob.doHurtTarget(target); }
            case MISS -> mob.swing(InteractionHand.MAIN_HAND);
            case NONE -> { }
        }
    }

    @Override public void stop() {
        cycle.cancel(mob.level().getGameTime());
        super.stop();
    }
}
