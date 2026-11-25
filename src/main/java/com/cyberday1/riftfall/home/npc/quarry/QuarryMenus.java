package com.cyberday1.riftfall.home.npc.quarry;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentLanding;
import com.cyberday1.riftfall.home.npc.ResidentMenus;
import com.cyberday1.riftfall.init.RiftfallItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/** The Quarry Specialist's landing entries; both open the same page on a different list position. */
public final class QuarryMenus {
    public static final int REFINE = 1, SURVEY = 2;

    public static final List<ResidentLanding.Entry> LANDING = List.of(
            ResidentLanding.Entry.service(REFINE, QuarryLang.LANDING_REFINE, QuarryLang.LANDING_REFINE_HINT, QuarryLang.LANDING_REFINE_NOTE,
                    () -> RiftfallItems.COMMON_RUNESTONE.get(), (p, quarry, m) -> open(p, quarry, 0)),
            ResidentLanding.Entry.service(SURVEY, QuarryLang.LANDING_SURVEY, QuarryLang.LANDING_SURVEY_HINT, QuarryLang.LANDING_SURVEY_NOTE,
                    () -> Items.SPYGLASS, (p, quarry, m) -> open(p, quarry, QuarryService.SURVEY)));

    private QuarryMenus() {}

    static void open(ServerPlayer player, UUID quarry, int page) {
        ResidentMenus.open(player, quarry, NpcRole.QUARRY_SPECIALIST, (id, inventory, p) -> new QuarryMenu(id, inventory, quarry, page));
    }
}
