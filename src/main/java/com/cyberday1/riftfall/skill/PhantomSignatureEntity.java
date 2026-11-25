package com.cyberday1.riftfall.skill;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * A Phantom signature ultimate's sight ({@link PhantomSignaturePlan}): the server holds only its kind, radius and
 * lifetime; the client draws it. Never saved, never hit, ends on its own.
 */
public class PhantomSignatureEntity extends Entity {

    private static final EntityDataAccessor<Integer> KIND =
            SynchedEntityData.defineId(PhantomSignatureEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(PhantomSignatureEntity.class, EntityDataSerializers.FLOAT);
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(.62F, .06F, .16F), 1.4F);
    private static final DustParticleOptions VOID = new DustParticleOptions(new Vector3f(.82F, .23F, 1.0F), 1.1F);

    public PhantomSignatureEntity(EntityType<? extends PhantomSignatureEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    void arm(PhantomSignaturePlan.Kind kind, float radius) {
        entityData.set(KIND, kind.ordinal());
        entityData.set(RADIUS, radius);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, 0);
        builder.define(RADIUS, 6.0F);
    }

    public PhantomSignaturePlan.Kind kind() {
        PhantomSignaturePlan.Kind[] kinds = PhantomSignaturePlan.Kind.values();
        return kinds[Math.floorMod(entityData.get(KIND), kinds.length)];
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount >= kind().lifetime) {
            if (!level().isClientSide()) discard();
            return;
        }
        if (level().isClientSide()) drift();
    }

    /** A few motes on the client: blood falling from the moon, void dust drawn into the collapse. */
    private void drift() {
        float r = radius();
        if (kind() == PhantomSignaturePlan.Kind.BLOOD_MOON) {
            if (tickCount < PhantomSignaturePlan.MOON_RISE_TICKS) return;
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
                level().addParticle(BLOOD, getX() + Math.cos(a) * d, getY() + 4.5 + random.nextDouble() * 2,
                        getZ() + Math.sin(a) * d, 0, -.4, 0);
            }
        } else if (tickCount >= PhantomSignaturePlan.TEAR_TICKS && tickCount < PhantomSignaturePlan.COLLAPSE_END) {
            for (int i = 0; i < 4; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = r * (.6 + random.nextDouble() * .4);
                double x = Math.cos(a) * d, z = Math.sin(a) * d, y = 1 + random.nextDouble() * 2;
                level().addParticle(VOID, getX() + x, getY() + y, getZ() + z, -x * .12, (1.2 - y) * .12, -z * .12);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        double r = radius() + 3.0;
        return getBoundingBox().inflate(r, 1.0, r).expandTowards(0.0, PhantomSignaturePlan.MOON_HIGH + 4.0, 0.0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0 * 128.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
