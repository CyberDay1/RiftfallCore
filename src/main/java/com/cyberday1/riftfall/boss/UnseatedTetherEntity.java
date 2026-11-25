package com.cyberday1.riftfall.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * An ASCENT tether (F7): stationary, no AI, never attacks. While any stands the god takes half damage. Each has 6% of
 * the god's bar and takes hits through the same court rule and the same damage-taken multiplier, so its share of the
 * god's effective health is 6% at every tier and party size. It saves its god's UUID, and the god saves its tethers'
 * (so a chunk reload keeps the pair); a tether its god no longer holds removes itself.
 */
public final class UnseatedTetherEntity extends Mob {
    public static final double HEALTH=UnseatedScaling.MAX_HEALTH*UnseatedScaling.TETHER_HEALTH;
    /** How often a tether asks whether its god still holds it. */
    public static final int ORPHAN_CHECK=20;
    // The god's damage-taken multiplier when the tether rose; saved, like the god's tier and headcount.
    private float damageTaken=1;
    private UUID boss;

    public UnseatedTetherEntity(EntityType<? extends UnseatedTetherEntity> type,Level level){
        super(type,level);setNoGravity(true);setNoAi(true);setPersistenceRequired();xpReward=0;
    }
    public static AttributeSupplier.Builder attributes(){
        // The god's base armour, so the tier's flat armour lands on both alike.
        return createMobAttributes().add(Attributes.MAX_HEALTH,HEALTH).add(Attributes.ARMOR,12).add(Attributes.KNOCKBACK_RESISTANCE,1);
    }
    /** Scaled once when it rises, from the god it belongs to. */
    void rise(UUID boss,int tier,float damageTaken){this.boss=boss;this.damageTaken=damageTaken;BossScaling.scale(this,tier);}
    public float damageTaken(){return damageTaken;}
    /** The god this tether belongs to; null for one that never rose (summoned by hand). */
    public UUID boss(){return boss;}
    /**
     * Whether nothing holds this tether any more: no god, a god that has let it go (a wipe or the binding dropped it
     * while its chunk was unloaded), or a god that is gone for good. A god merely unloaded still holds it.
     */
    boolean orphaned(){
        if(!(level() instanceof ServerLevel server))return false;
        if(boss==null)return true;
        if(server.getEntity(boss) instanceof UnseatedEntity god)return !god.isAlive() || !god.holdsTether(getUUID());
        return UnseatedArena.bossGone(server.dimension(),boss);
    }
    @Override public void tick(){
        super.tick();
        if(!level().isClientSide && tickCount%ORPHAN_CHECK==0 && orphaned())discard();
    }
    @Override public void travel(Vec3 travel){setDeltaMovement(Vec3.ZERO);}
    @Override public boolean isPushable(){return false;}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean shouldDropLoot(){return false;}
    @Override public boolean shouldDropExperience(){return false;}
    @Override public boolean hurt(DamageSource source,float amount){
        if(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return super.hurt(source,amount);
        if(!UnseatedArena.isArena(level().dimension()))return false;
        // The god's rule: a hit counts from inside the court, or from a pet whose owner is.
        var attacker=source.getEntity();
        if(!(attacker instanceof net.minecraft.world.entity.LivingEntity))return false;
        if(!UnseatedEntity.inCourt(attacker)
                && !(attacker instanceof OwnableEntity pet && pet.getOwner()!=null && UnseatedEntity.inCourt(pet.getOwner())))return false;
        return super.hurt(source,amount);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);tag.putFloat("UnseatedDamageTaken",damageTaken);
        if(boss!=null)tag.putUUID("UnseatedBoss",boss);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);damageTaken=tag.contains("UnseatedDamageTaken")?tag.getFloat("UnseatedDamageTaken"):1;
        boss=tag.hasUUID("UnseatedBoss")?tag.getUUID("UnseatedBoss"):null;
    }
}
