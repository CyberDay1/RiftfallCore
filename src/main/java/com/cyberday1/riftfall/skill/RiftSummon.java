package com.cyberday1.riftfall.skill;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.spell_engine.entity.SummonedEntity;

/** Engine ownership, targeting, movement, expiry and commands; no vanilla hostile-mob goals or loot. */
public final class RiftSummon extends SummonedEntity {
    public RiftSummon(EntityType<? extends SummonedEntity> type, Level level) {
        super(type, level);
        String kind = kind();
        if (kind.equals("skeleton")) setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        if (kind.equals("lich")) setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BLAZE_ROD));
        if (kind.equals("demon_knight") || kind.equals("guardian_spirit")) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0);
            if (!getItemBySlot(slot).isEmpty()) getItemBySlot(slot).set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                    net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        }
        setCanPickUpLoot(false);
    }
    public String kind() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath().replace("summon_", "");
    }
    @Override public boolean shouldDropLoot() { return false; }
    @Override public boolean shouldDropExperience() { return false; }
    @Override public void onAttackAnimated(int duration, int variant) {
        super.onAttackAnimated(duration, variant);
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide || tickCount % 6 != 0) return;
        var particle = switch (kind()) {
            case "storm_elemental" -> net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK;
            case "fire_elemental", "imp" -> net.minecraft.core.particles.ParticleTypes.SMALL_FLAME;
            case "sprite", "spirit_healer" -> net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER;
            case "wraith", "ancestral_spirit", "guardian_spirit" -> net.minecraft.core.particles.ParticleTypes.SOUL;
            case "plague_servant" -> net.minecraft.core.particles.ParticleTypes.SPORE_BLOSSOM_AIR;
            default -> null;
        };
        if (particle != null) level().addParticle(particle, getRandomX(.65), getRandomY(), getRandomZ(.65), 0, .025, 0);
    }
}
