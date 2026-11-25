package com.cyberday1.riftfall.home.npc.jeweler;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.home.npc.depth.WorkOrders;
import net.minecraft.resources.ResourceLocation;

/** The Jeweler's item work order: a held gem comes back one purity up. */
public final class JewelerOrders {
    public static final ResourceLocation PURITY_EDIT = RiftfallCore.id("jeweler/purity");

    private JewelerOrders() {}

    public static void register() {
        WorkOrders.registerEdit(PURITY_EDIT, held -> JewelerService.purityUp(held, JewelerGems.current()));
    }
}
