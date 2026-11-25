package com.cyberday1.riftfall.block.marker;

/** Stable authoring IDs for home residents and the services each is planned to offer. */
public enum NpcRole {
    BLACKSMITH("blacksmith", "Fitter", "orange", "Runestones, re-tempering and salvage"),
    QUARRY_SPECIALIST("quarry_specialist", "Quarry Specialist", "gray", "Runestones, tempering stock and crystal refining"),
    RIFT_CARTOGRAPHER("rift_cartographer", "Rift Cartographer", "blue", "Rift atlas and key attunement"),
    RELIC_SCHOLAR("relic_scholar", "Relic Scholar", "purple", "Relic catalog and research"),
    JEWELER("jeweler", "Jeweler", "cyan", "Setting, unsetting and refining gems"),
    APOTHECARIST("apothecarist", "Medic", "magenta", "Remedies and resist draughts"),
    QUARTERMASTER("quartermaster", "Quartermaster", "brown", "Rift-death claims and the request board"),
    COMBAT_MENTOR("combat_mentor", "Combat Mentor", "red", "Training dummy and drills"),
    DRUID("druid", "Botanist", "green", "Rift flora plots"),
    COOK("cook", "Cook", "yellow", "Run provisions"),
    RIFT_STABILIZER("rift_stabilizer", "Rift Stabilizer", "black", "Optional rift modifiers"),
    RESTORATION_KEEPER("restoration_keeper", "Restoration Keeper", "light_blue", "Restoring boss uniques");

    private final String id;
    private final String displayName;
    private final String color;
    private final String services;

    NpcRole(String id, String displayName, String color, String services) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.services = services;
    }

    public String id() { return id; }
    public String blockId() { return "npc_spawn_" + id; }
    public String displayName() { return displayName; }
    public String color() { return color; }
    public String services() { return services; }
    public String serviceTranslationKey() { return "tooltip.riftfall.npc_spawn." + id; }
}
