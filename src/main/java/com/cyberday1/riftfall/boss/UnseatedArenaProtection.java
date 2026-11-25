package com.cyberday1.riftfall.boss;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** The court's geometry is part of the encounter, including its jumpable, level pulse lanes. */
final class UnseatedArenaProtection {
    static void register(){
        NeoForge.EVENT_BUS.addListener(UnseatedArenaProtection::breaking);
        NeoForge.EVENT_BUS.addListener(UnseatedArenaProtection::placing);
        NeoForge.EVENT_BUS.addListener(UnseatedArenaProtection::explosion);
        NeoForge.EVENT_BUS.addListener(UnseatedArenaProtection::griefing);
        NeoForge.EVENT_BUS.addListener(UnseatedArenaProtection::demolition);
        NeoForge.EVENT_BUS.addListener(UnseatedArenaProtection::bucketBlock);
        NeoForge.EVENT_BUS.addListener(UnseatedArenaProtection::bucketAir);
    }
    private static boolean inside(LevelAccessor level){return level instanceof Level l && UnseatedArena.isArena(l.dimension());}
    private static void breaking(BlockEvent.BreakEvent e){if(inside(e.getLevel()))e.setCanceled(true);}
    private static void placing(BlockEvent.EntityPlaceEvent e){if(inside(e.getLevel()))e.setCanceled(true);}
    private static void explosion(ExplosionEvent.Detonate e){if(inside(e.getLevel()))e.getAffectedBlocks().clear();}
    private static void griefing(EntityMobGriefingEvent e){if(inside(e.getEntity().level()))e.setCanGrief(false);}
    private static void demolition(LivingDestroyBlockEvent e){if(inside(e.getEntity().level()))e.setCanceled(true);}
    private static void bucketBlock(PlayerInteractEvent.RightClickBlock e){if(inside(e.getLevel()) && e.getItemStack().getItem() instanceof BucketItem)e.setCanceled(true);}
    private static void bucketAir(PlayerInteractEvent.RightClickItem e){if(inside(e.getLevel()) && e.getItemStack().getItem() instanceof BucketItem)e.setCanceled(true);}
}
