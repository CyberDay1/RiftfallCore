package com.cyberday1.riftfall.home.npc.depth;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentRank;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Every line the resident depth layers show; the lang provider reads {@link #ENGLISH}. */
public final class DepthLang {
    private static final String RESIDENT = "resident." + RiftfallCore.MODID + ".";
    private static final String SCREEN = "screen." + RiftfallCore.MODID + ".resident.";
    private static final String MESSAGE = "message." + RiftfallCore.MODID + ".";

    public static final String XP = SCREEN + "xp";
    public static final String XP_TOP = SCREEN + "xp_top";
    public static final String NEXT = SCREEN + "next";
    public static final String BEST = SCREEN + "best";
    public static final String BEST_NONE = SCREEN + "best_none";
    public static final String TOP = SCREEN + "top";
    public static final String SHARED = SCREEN + "shared";

    public static final String RANK_UP = MESSAGE + "resident.rank_up";
    public static final String REQUEST_PROGRESS = MESSAGE + "resident.request_progress";
    public static final String REQUEST_DONE = MESSAGE + "resident.request_done";
    public static final String REWARDS = MESSAGE + "resident.rewards";
    public static final String REWARDS_WAITING = MESSAGE + "resident.rewards_waiting";
    public static final String ORDERS_COLLECTED = MESSAGE + "orders.collected";
    public static final String ORDERS_NOT_HOME = MESSAGE + "orders.not_home";
    public static final String ORDERS_LEFTOVER = MESSAGE + "orders.leftover";
    public static final String ORDERS_NONE = MESSAGE + "orders.none";
    public static final String ORDERS_SUMMARY = MESSAGE + "orders.summary";
    public static final String ORDERS_LINE = MESSAGE + "orders.line";
    public static final String ORDERS_LINE_READY = MESSAGE + "orders.line_ready";

    public static String rank(ResidentRank rank) { return RESIDENT + "rank." + rank.getSerializedName(); }
    /** What reaching {@code rank} opens at this resident; optional, the panel skips a missing key. */
    public static String opens(NpcRole role, ResidentRank rank) { return RESIDENT + role.id() + ".opens." + rank.getSerializedName(); }

    public static final Map<String, String> ENGLISH;

    static {
        Map<String, String> en = new LinkedHashMap<>();
        en.put(rank(ResidentRank.APPRENTICE), "Apprentice");
        en.put(rank(ResidentRank.JOURNEYMAN), "Journeyman");
        en.put(rank(ResidentRank.EXPERT), "Expert");
        en.put(rank(ResidentRank.MASTER), "Master");
        en.put(XP, "%s / %s XP");
        en.put(XP_TOP, "%s XP");
        en.put(NEXT, "Next: %s at %s XP and a tier %s clear");
        en.put(BEST, "Best clear from this home: tier %s");
        en.put(BEST_NONE, "No rift cleared from this home yet");
        en.put(TOP, "Highest rank reached");
        en.put(SHARED, "Ranks belong to the home, not to one player.");

        en.put(opens(NpcRole.BLACKSMITH, ResidentRank.JOURNEYMAN), "Opens: re-tempering weapons and armor, Fortune I-II");
        en.put(opens(NpcRole.BLACKSMITH, ResidentRank.EXPERT), "Opens: trinkets, a wider re-temper band and Fortune III");
        en.put(opens(NpcRole.BLACKSMITH, ResidentRank.MASTER), "Opens: Fortune IV-VIII and a specialization");
        en.put(opens(NpcRole.QUARRY_SPECIALIST, ResidentRank.JOURNEYMAN), "Opens: uncommon runestones, chisels, swaps, orders");
        en.put(opens(NpcRole.QUARRY_SPECIALIST, ResidentRank.EXPERT), "Opens: rare runestone and faceted crystal orders");
        en.put(opens(NpcRole.QUARRY_SPECIALIST, ResidentRank.MASTER), "Opens: exotic runestones and a specialization");
        en.put(opens(NpcRole.JEWELER, ResidentRank.JOURNEYMAN), "Opens: purity to normal and adding sockets");
        en.put(opens(NpcRole.JEWELER, ResidentRank.EXPERT), "Opens: purity to flawless as a work order");
        en.put(opens(NpcRole.JEWELER, ResidentRank.MASTER), "Opens: purity to perfect and a specialization");
        en.put(opens(NpcRole.RIFT_CARTOGRAPHER, ResidentRank.JOURNEYMAN), "Opens: attuning a Rift Key to a theme you have visited");
        en.put(opens(NpcRole.RIFT_CARTOGRAPHER, ResidentRank.EXPERT), "Opens: boss attunement, a Quartermaster request slot");
        en.put(opens(NpcRole.RIFT_CARTOGRAPHER, ResidentRank.MASTER), "Opens: a specialization");

        en.put(RANK_UP, "%s is now %s.");
        en.put(REQUEST_PROGRESS, "%s request: %s/%s");
        en.put(REQUEST_DONE, "%s request complete");
        en.put(REWARDS, "Request rewards added to your inventory.");
        en.put(REWARDS_WAITING, "Some request rewards did not fit. Make room: the rest comes after your next run or login.");
        en.put(ORDERS_COLLECTED, "Work orders collected: %s. Still running: %s.");
        en.put(ORDERS_NOT_HOME, "Collect work orders in your own Shattered Home.");
        en.put(ORDERS_LEFTOVER, "Some items did not fit. They stay with the order.");
        en.put(ORDERS_NONE, "You have no work orders. Start one from a resident's service page.");
        en.put(ORDERS_SUMMARY, "Work orders: %s ready, %s running.");
        en.put(ORDERS_LINE, "%s, %s: %s / %s runs | %s / %s min");
        en.put(ORDERS_LINE_READY, "%s, %s: ready to collect");

        en.put(ResidentDialogue.fallback(ResidentDialogue.Trigger.GREETING, 0), "Good to see you.");
        en.put(ResidentDialogue.fallback(ResidentDialogue.Trigger.GREETING, 1), "Back again? Let's get to work.");
        en.put(ResidentDialogue.fallback(ResidentDialogue.Trigger.RANK_UP, 0), "I can take on harder work now.");
        en.put(ResidentDialogue.fallback(ResidentDialogue.Trigger.RANK_UP, 1), "New tools. Let's try them.");
        en.put(ResidentDialogue.fallback(ResidentDialogue.Trigger.REQUEST, 0), "That request is done. Good work.");
        en.put(ResidentDialogue.fallback(ResidentDialogue.Trigger.REQUEST, 1), "Another request off the board. Thank you.");
        en.put(ResidentDialogue.fallback(ResidentDialogue.Trigger.BOSS, 0), "You brought down %s? You came back walking differently.");
        en.put(ResidentDialogue.fallback(ResidentDialogue.Trigger.BOSS, 1), "%s, fallen. I'd heard it twice before you put your pack down.");
        en.put(ResidentDialogue.fallback(ResidentDialogue.Trigger.TIER, 0), "Tier %s cleared. More ground under our feet.");
        en.put(ResidentDialogue.fallback(ResidentDialogue.Trigger.TIER, 1), "A tier %s clear. That opens doors for all of us.");
        en.put(ResidentDialogue.fallback(ResidentDialogue.Trigger.DEATHS, 0), "The rifts have been rough on you. Rest before the next one.");
        en.put(ResidentDialogue.fallback(ResidentDialogue.Trigger.DEATHS, 1), "Hard runs lately. Take care out there.");
        en.put(ResidentDialogue.fallback(ResidentDialogue.Trigger.ABSENCE, 0), "It's been a while. The rifts kept you busy?");
        en.put(ResidentDialogue.fallback(ResidentDialogue.Trigger.ABSENCE, 1), "There you are. I was starting to wonder.");
        roleLines(en);
        ENGLISH = Collections.unmodifiableMap(en);
    }

    /** The slice residents' own lines for five triggers, two variants each; other triggers use the shared lines. */
    private static void roleLines(Map<String, String> en) {
        line(en, NpcRole.QUARRY_SPECIALIST, ResidentDialogue.Trigger.RANK_UP, "New rank, new cuts. Bring me the rarer stones now.",
                "Bench is ready for the rarer stone.");
        line(en, NpcRole.QUARRY_SPECIALIST, ResidentDialogue.Trigger.REQUEST, "Bins are full again. That request is done.",
                "Good haul. That one is off the board.");
        line(en, NpcRole.QUARRY_SPECIALIST, ResidentDialogue.Trigger.BOSS, "%s, down? Its rift must have rich seams.",
                "You beat %s. Did you bring back any crystals?");
        line(en, NpcRole.QUARRY_SPECIALIST, ResidentDialogue.Trigger.TIER, "Tier %s cleared. Deeper rifts, better stone.",
                "A tier %s clear. I can work with harder material now.");
        line(en, NpcRole.QUARRY_SPECIALIST, ResidentDialogue.Trigger.DEATHS, "Rough runs. Stone waits; take your time.",
                "Leave the hauling today. Sit down.");
        line(en, NpcRole.BLACKSMITH, ResidentDialogue.Trigger.RANK_UP, "The forge burns hotter now. I can temper further.",
                "A new rank. Bring me your best steel.");
        line(en, NpcRole.BLACKSMITH, ResidentDialogue.Trigger.REQUEST, "Request done. That's the bar stock I was short of.",
                "That's one more off the board. Good work.");
        line(en, NpcRole.BLACKSMITH, ResidentDialogue.Trigger.BOSS, "%s? Then your kit has had a day. Put it on the bench.",
                "Let's see what %s did to your kit.");
        line(en, NpcRole.BLACKSMITH, ResidentDialogue.Trigger.TIER, "Tier %s. Let me check that gear.",
                "A tier %s clear. Time to re-temper something.");
        line(en, NpcRole.BLACKSMITH, ResidentDialogue.Trigger.DEATHS, "Dents and scorch marks. The rifts hit back hard.",
                "Hard runs. Check your gear before the next one.");
        line(en, NpcRole.JEWELER, ResidentDialogue.Trigger.RANK_UP, "I can work the flaws out of finer stones now.",
                "A new rank. Bring me your finest stones.");
        line(en, NpcRole.JEWELER, ResidentDialogue.Trigger.REQUEST, "That request is done. I was down to my last blue.",
                "Another request filled. My tray is full.");
        line(en, NpcRole.JEWELER, ResidentDialogue.Trigger.BOSS, "%s, defeated? Did it carry any gems?",
                "%s? Show me the stones you found.");
        line(en, NpcRole.JEWELER, ResidentDialogue.Trigger.TIER, "Tier %s. Let me see how those stones catch the light.",
                "A tier %s clear. Your settings deserve better stones.");
        line(en, NpcRole.JEWELER, ResidentDialogue.Trigger.DEATHS, "You look worn. Sit by the wheel a minute; it's warm there.",
                "Leave the polishing to me. Rest by the wheel.");
        line(en, NpcRole.RIFT_CARTOGRAPHER, ResidentDialogue.Trigger.RANK_UP, "I've checked the new routes. Bring your key.",
                "A new rank. The atlas has room for more.");
        line(en, NpcRole.RIFT_CARTOGRAPHER, ResidentDialogue.Trigger.REQUEST, "That fills a gap in the map.",
                "Good work. I've inked it into the atlas.");
        line(en, NpcRole.RIFT_CARTOGRAPHER, ResidentDialogue.Trigger.BOSS, "%s, down? I'll mark where you found it.",
                "You beat %s. That goes in the atlas.");
        line(en, NpcRole.RIFT_CARTOGRAPHER, ResidentDialogue.Trigger.TIER, "Tier %s cleared. The charts reach deeper now.",
                "A tier %s clear. New ground for the atlas.");
        line(en, NpcRole.RIFT_CARTOGRAPHER, ResidentDialogue.Trigger.DEATHS, "Show me where you went. We can try another route.",
                "The rifts have been unkind. Let's choose your next one together.");
    }

    private static void line(Map<String, String> en, NpcRole role, ResidentDialogue.Trigger trigger, String first, String second) {
        en.put(ResidentDialogue.key(role, trigger, 0), first);
        en.put(ResidentDialogue.key(role, trigger, 1), second);
    }

    private DepthLang() {}
}
