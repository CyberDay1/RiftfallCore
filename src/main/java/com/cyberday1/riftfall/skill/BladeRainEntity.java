package com.cyberday1.riftfall.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.impact.SpellImpacts;
import net.spell_engine.internals.target.EntityRelations;
import net.spell_engine.internals.target.SpellTarget;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.UUID;

/**
 * One Blade Rain storm (its plan is {@link BladeRainPlan}). The server holds the cast's spell and context and, as each
 * blade's landing tick passes, runs the spell's own impacts on every enemy within the blade's reach: Spell Engine's
 * damage path, so spell power, crits, the school, resistances, relations and every damage listener apply per blade.
 *
 * <p>Nothing here is saved ({@code noSave}): a storm ends with its chunk, its level (a torn-down rift), or the first
 * tick its caster is gone, dead, or in another level. Both sides compute the same blade grounds from synced radius and
 * yaw; the client draws the blades and plays the telegraph and the stabs itself, so no particle crosses the network.
 */
public class BladeRainEntity extends Entity {

    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(BladeRainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> YAW =
            SynchedEntityData.defineId(BladeRainEntity.class, EntityDataSerializers.FLOAT);
    private static final TagKey<EntityType<?>> BOSSES =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "bosses"));
    /** How far below the aimed point a blade looks for ground, and above it for a step. */
    private static final int GROUND_BELOW = 6;
    private static final int GROUND_ABOVE = 1;
    /** Blocks above its landing point a blade starts its fall from, open sky permitting. */
    private static final float FALL_HEIGHT = 18.0F;

    private @Nullable UUID caster;
    private @Nullable Holder<Spell> spell;
    private @Nullable SpellExecution.ImpactContext context;
    private final BitSet landed = new BitSet(BladeRainPlan.COUNT);
    private Vec3[] grounds;
    private float[] heights;

    public BladeRainEntity(EntityType<? extends BladeRainEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** The server's storm: who cast it, the spell whose impacts each blade delivers, and the cast's context. */
    void arm(LivingEntity caster, Holder<Spell> spell, SpellExecution.ImpactContext context, float radius) {
        this.caster = caster.getUUID();
        this.spell = spell;
        this.context = context.target(SpellTarget.FocusMode.AREA);
        entityData.set(RADIUS, radius);
        entityData.set(YAW, caster.getYRot());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 4.0F);
        builder.define(YAW, 0.0F);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public float layoutYaw() {
        return entityData.get(YAW);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        grounds = null;
        heights = null;
    }

    /** Where blade {@code i} stabs: its offset from the centre, dropped onto the ground below it. */
    public Vec3 ground(int i) {
        if (grounds == null) {
            Vec3[] out = new Vec3[BladeRainPlan.COUNT];
            for (int b = 0; b < out.length; b++) {
                double[] at = BladeRainPlan.offset(b, radius(), layoutYaw());
                double x = getX() + at[0];
                double z = getZ() + at[1];
                out[b] = new Vec3(x, groundY(level(), x, z, getY()), z);
            }
            grounds = out;
        }
        return grounds[i];
    }

    /** Blocks blade {@code i} falls: up to {@link #FALL_HEIGHT}, but under a roof it starts just below it. */
    public float fallHeight(int i) {
        if (heights == null) {
            float[] out = new float[BladeRainPlan.COUNT];
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int b = 0; b < out.length; b++) {
                Vec3 at = ground(b);
                out[b] = FALL_HEIGHT;
                for (int h = 1; h <= (int) FALL_HEIGHT; h++) {
                    pos.set(Mth.floor(at.x), Mth.floor(at.y) + h, Mth.floor(at.z));
                    if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                        out[b] = Math.max(2.5F, h - 1.0F);
                        break;
                    }
                }
            }
            heights = out;
        }
        return heights[i];
    }

    /** The top of the first standing surface at {@code (x, z)}, from a step above {@code fromY} down; else fromY. */
    static double groundY(Level level, double x, double z, double fromY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = Mth.floor(fromY) + GROUND_ABOVE;
        for (int y = top; y >= Mth.floor(fromY) - GROUND_BELOW; y--) {
            pos.set(Mth.floor(x), y, Mth.floor(z));
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            BlockPos above = pos.above();
            if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) continue;
            return y + shape.max(Direction.Axis.Y);
        }
        return fromY;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            serverTick(server);
        } else {
            clientTick();
        }
    }

    private void serverTick(ServerLevel level) {
        LivingEntity owner = owner(level);
        if (owner == null || tickCount >= BladeRainPlan.LIFETIME_TICKS) {
            discard();
            return;
        }
        for (int i = 0; i < BladeRainPlan.COUNT; i++) {
            if (!landed.get(i) && BladeRainPlan.landingTick(i) <= tickCount) {
                landed.set(i);
                land(level, owner, i);
            }
        }
    }

    /** The caster, while it can still own the storm: alive, loaded, and in this level. */
    private @Nullable LivingEntity owner(ServerLevel level) {
        if (caster == null || spell == null || context == null) return null;
        Entity found = level.getEntity(caster);
        if (!(found instanceof LivingEntity living) || !living.isAlive() || living.isRemoved() || living.level() != level) {
            return null;
        }
        return living;
    }

    /** Blade {@code i} stabs: every enemy within its reach, and inside the storm, takes the spell's impacts once. */
    private void land(ServerLevel level, LivingEntity owner, int i) {
        Vec3 at = ground(i);
        double reach = BladeRainPlan.IMPACT_RADIUS;
        AABB box = new AABB(at.x - reach, at.y - 1.5, at.z - reach, at.x + reach, at.y + 2.5, at.z + reach);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != owner && e.isAlive() && !e.isSpectator())) {
            if (!reaches(at, target) || !insideStorm(target) || !target.isAttackable()) continue;
            if (!EntityRelations.actionAllowed(SpellTarget.FocusMode.AREA, SpellTarget.Intent.HARMFUL, owner, target)) {
                continue;
            }
            SpellExecution.ImpactContext hit = context.position(target.position().add(0, target.getBbHeight() * 0.5, 0));
            if (SpellImpacts.performImpacts(level, owner, target, target, spell, spell.value().impacts, hit, false, null)) {
                knockUp(target);
            }
        }
    }

    private static boolean reaches(Vec3 at, LivingEntity target) {
        double dx = target.getX() - at.x;
        double dz = target.getZ() - at.z;
        return dx * dx + dz * dz <= BladeRainPlan.IMPACT_RADIUS * BladeRainPlan.IMPACT_RADIUS;
    }

    /** The storm's footprint is the spell's area, as the single area hit had it: the radius plus the target's half-width. */
    private boolean insideStorm(LivingEntity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double edge = radius() + target.getBbWidth() * 0.5;
        return dx * dx + dz * dz <= edge * edge;
    }

    private static void knockUp(LivingEntity target) {
        if (!target.isAlive() || target.getType().is(BOSSES)) return;
        double resist = Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        if (resist >= 1.0) return;
        target.setDeltaMovement(target.getDeltaMovement().add(0.0, BladeRainPlan.KNOCKUP * (1.0 - resist), 0.0));
        target.hurtMarked = true;
    }

    /** The telegraph ring before the first blade, then each blade's stab as it lands. Client-side, locally. */
    private void clientTick() {
        Level level = level();
        float radius = radius();
        if (tickCount <= BladeRainPlan.TELEGRAPH_TICKS && tickCount % 2 == 0) {
            float progress = tickCount / (float) BladeRainPlan.TELEGRAPH_TICKS;
            int points = Math.max(16, (int) (radius * 6));
            for (int p = 0; p < points; p++) {
                double a = p * Math.PI * 2.0 / points;
                level.addParticle(ParticleTypes.SMOKE, getX() + Math.cos(a) * radius, getY() + 0.05,
                        getZ() + Math.sin(a) * radius, 0.0, 0.0, 0.0);
            }
            double inner = radius * (1.0 - progress * 0.85);
            int marks = Math.max(8, (int) (inner * 5));
            for (int p = 0; p < marks; p++) {
                double a = p * Math.PI * 2.0 / marks + progress;
                level.addParticle(ParticleTypes.CRIT, getX() + Math.cos(a) * inner, getY() + 0.1,
                        getZ() + Math.sin(a) * inner, 0.0, 0.0, 0.0);
            }
        }
        for (int i = 0; i < BladeRainPlan.COUNT; i++) {
            if (BladeRainPlan.landingTick(i) != tickCount) continue;
            Vec3 at = ground(i);
            for (int p = 0; p < 8; p++) {
                level.addParticle(ParticleTypes.CRIT, at.x, at.y + 0.1, at.z,
                        (random.nextDouble() - 0.5) * 0.5, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.5);
            }
            level.addParticle(ParticleTypes.SWEEP_ATTACK, at.x, at.y + 0.3, at.z, 0.0, 0.0, 0.0);
            level.playLocalSound(at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                    0.35F, 0.9F + random.nextFloat() * 0.3F, false);
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

    /** The blades fill the radius and fall from high above it; cull on that, not on the tiny centre box. */
    @Override
    public AABB getBoundingBoxForCulling() {
        double r = radius() + 1.0;
        return getBoundingBox().inflate(r, 1.0, r).expandTowards(0.0, 20.0, 0.0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0 * 96.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // Never saved (noSave); a storm read back somehow has no cast to deliver and ends on its first tick.
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    /** Blades landed so far. Probe read. */
    public int landedCount() {
        return landed.cardinality();
    }
}
