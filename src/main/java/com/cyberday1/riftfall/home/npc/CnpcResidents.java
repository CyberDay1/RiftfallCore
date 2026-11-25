package com.cyberday1.riftfall.home.npc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.api.NpcAPI;

/** The only class that links CNPC types. Called only when the optional mod is loaded. */
final class CnpcResidents {
    private CnpcResidents() {}

    static boolean spawn(ServerLevel level, HomeResidentData.Resident resident) {
        var api = NpcAPI.Instance();
        var npc = api.createNPC(level); // Creates without finalizing natural spawning or adding to the world.
        if (npc == null) return false;
        Mob entity = (Mob) npc.getMCEntity();
        entity.setUUID(resident.entityId());
        entity.getPersistentData().putString(HomeResidents.ROLE_TAG, resident.role().id());
        var appearance = ResidentAppearance.forRole(resident.role());
        // Lang keys: CNPC renders both through Component.translatable; MixinCnpcResidentName adds the person.
        npc.getDisplay().setName(ResidentLang.name(resident.role()));
        npc.getDisplay().setTitle(ResidentLang.title(resident.role()));
        npc.getDisplay().setSkinTexture(appearance.texture());
        npc.getDisplay().setSize(5);
        npc.getAi().setMovingType(0);
        npc.getAi().setReturnsHome(true);
        npc.getStats().setAggroRange(0);
        npc.getStats().setRespawnType(3); // CNPC tickDeath delegates to vanilla; Core owns replacement.
        npc.getInventory().setExp(0, 0);
        npc.setHome(resident.anchor().getX(), resident.anchor().getY(), resident.anchor().getZ());
        npc.getInventory().setRightHand(api.getIItemStack(new ItemStack(appearance.heldItem())));
        entity.setDropChance(EquipmentSlot.MAINHAND, 0);
        entity.moveTo(resident.anchor().getX() + 0.5, resident.anchor().getY(), resident.anchor().getZ() + 0.5,
                resident.facing().toYRot(), 0);
        entity.setYHeadRot(resident.facing().toYRot());
        keepAtStation(entity, resident);
        if (!level.noCollision(entity, entity.getBoundingBox())) return false;
        return level.addFreshEntity(entity);
    }

    static void keepAtStation(Entity entity, HomeResidentData.Resident resident) {
        if (entity instanceof noppes.npcs.entity.EntityNPCInterface npc) {
            // Residents spawned before the names carry the English role as a literal; setName syncs only a change.
            npc.display.setName(ResidentLang.name(resident.role()));
            npc.display.setTitle(ResidentLang.title(resident.role()));
        }
        entity.setInvulnerable(true);
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired(); mob.setNoAi(true); mob.setTarget(null); mob.getNavigation().stop();
            if (mob.isLeashed()) mob.dropLeash(true, false);
        }
        var feet = Vec3.atBottomCenterOf(resident.anchor());
        if (entity.position().distanceToSqr(feet) > 0.0001) entity.teleportTo(feet.x, feet.y, feet.z);
    }
}
