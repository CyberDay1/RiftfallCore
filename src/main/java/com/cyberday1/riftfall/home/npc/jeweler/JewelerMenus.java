package com.cyberday1.riftfall.home.npc.jeweler;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentLanding;
import com.cyberday1.riftfall.home.npc.ResidentMenus;
import com.cyberday1.riftfall.init.RiftfallItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/** The Jeweler's landing entries; all three open the same page on a different action. */
public final class JewelerMenus {
    public static final int APPRAISE = 1, SOCKETS = 2, REFINE = 3;

    public static final List<ResidentLanding.Entry> LANDING = List.of(
            ResidentLanding.Entry.service(SOCKETS, JewelerLang.LANDING_SOCKETS, JewelerLang.LANDING_SOCKETS_HINT,
                    JewelerLang.LANDING_SOCKETS_NOTE, () -> Items.AMETHYST_SHARD, (p, jeweler, m) -> open(p, jeweler, JewelerService.SET)),
            ResidentLanding.Entry.service(REFINE, JewelerLang.LANDING_REFINE, JewelerLang.LANDING_REFINE_HINT,
                    JewelerLang.LANDING_REFINE_NOTE, () -> RiftfallItems.GEM_GRIT.get(), (p, jeweler, m) -> open(p, jeweler, JewelerService.REFINE)),
            ResidentLanding.Entry.service(APPRAISE, JewelerLang.LANDING_APPRAISE, JewelerLang.LANDING_APPRAISE_HINT,
                    JewelerLang.LANDING_APPRAISE_NOTE, () -> Items.SPYGLASS, (p, jeweler, m) -> open(p, jeweler, JewelerService.APPRAISE)));

    private JewelerMenus() {}

    static void open(ServerPlayer player, UUID jeweler, int page) {
        ResidentMenus.open(player, jeweler, NpcRole.JEWELER, (id, inventory, p) -> new JewelerMenu(id, inventory, jeweler, page));
    }
}
