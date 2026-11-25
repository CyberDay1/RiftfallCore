package com.cyberday1.riftfall.data;

import java.util.HashMap;
import java.util.Map;

/**
 * The English for every line the progression book draws, keyed {@code <page>.<line>} as
 * {@code ProgressionTab} lists them. The provider walks the tab's list, so a listed line with no text
 * here fails the data run instead of shipping a raw key.
 */
final class BookLang {

    private static final Map<String, String> TEXT = new HashMap<>();

    static {
        // The character page.
        sheet("title", "Character");
        sheet("tier", "Tier %s");
        sheet("level", "Level %s");
        sheet("no_class", "No class yet");
        sheet("class_hint", "Your class is permanent.");
        sheet("choose_class", "Choose a class");
        sheet("main", "Main");
        sheet("second", "Second");
        sheet("can_ascend", "Can ascend at %s");
        sheet("ascend_ready", "Ready to ascend");
        sheet("ascended", "Ascended: %s");
        sheet("ascend_button", "Ascend");
        sheet("ascend_title", "Ascend");
        sheet("ascend_choose", "Choose one. It is permanent and closes the others.");
        sheet("ascend_confirm", "Ascend as %s? This cannot be undone.");
        sheet("ascend_yes", "Ascend");
        sheet("result_ascend_unknown", "That ascendancy does not exist.");
        sheet("result_ascend_in_rift", "Only outside a rift.");
        sheet("result_ascend_already_chosen", "You have already ascended.");
        sheet("result_ascend_too_low", "Your level is too low to ascend.");
        sheet("result_ascend_no_class", "Choose a class before ascending.");
        sheet("result_ascend_wrong_class", "That ascendancy belongs to another class.");
        sheet("points", "%s points unspent");
        sheet("points_one", "%s point unspent");
        sheet("skills_link", "Skills ›");
        sheet("rift_log_link", "Rift Log ›");
        sheet("rift_log_hint", "Your past runs, newest first.");
        sheet("mix_separator", " · ");
        sheet("mix_lever", "%s %s");
        sheet("mix_passives", "%s passives");
        sheet("mix_passives_one", "%s passive");
        sheet("mix_none", "No class skills written yet");
        sheet("experience", "Experience");
        sheet("next_tier", "Tier %s at %s");
        sheet("capped", "%s XP: max level");
        sheet("into", "%s / %s");
        sheet("to_go", "%s to go");
        sheet("health", "Health");
        sheet("mana_of", "of %s");
        sheet("shield", "Shield");
        sheet("mana_now", "Mana %s / %s");
        sheet("mana_base", "%s base");
        sheet("mana_passives", "%s from passives");
        sheet("mana_from", "%s from %s");
        sheet("mana_gear", "%s from gear");
        sheet("mana_regen", "Regenerates %s/s, %s/s out of combat");
        sheet("defence", "Defense");
        sheet("armor", "Armor");
        sheet("toughness", "Toughness");
        sheet("resist", "Resist");
        sheet("guard", "Guard");
        sheet("percent", "%s%%");
        sheet("resist_hint", "Share of each hit turned away outside a rift. Gear resistance turns away more in higher rift tiers.");
        sheet("resist_hint_rift", "Share of each hit turned away in this tier %s rift.");
        sheet("guard_hint", "Share turned away from a hit that gets past your raised shield.");
        sheet("attributes", "Attributes");
        sheet("class_block", "class block, %s");
        sheet("no_block", "no class block");
        sheet("tag_strength", "STR");
        sheet("tag_dexterity", "DEX");
        sheet("tag_intellect", "INT");
        sheet("tag_vitality", "VIT");
        sheet("tag_spirit", "SPI");
        sheet("tag_agility", "AGI");
        sheet("tag_luck", "LCK");
        sheet("respec_link", "Reset stats");
        sheet("respec_title", "Reset stats");
        sheet("respec_confirm", "Refund all %s attribute points for %s XP? Your class block stays.");
        sheet("respec_yes", "Reset");
        sheet("respec_back", "Back");
        sheet("respec_costs", "Costs %s XP; refunds %s points.");
        sheet("respec_in_rift", "Only outside a rift.");
        sheet("respec_short", "Costs %s XP from this level; you need %s more.");
        sheet("respec_nothing", "Nothing to reset: you have placed no attribute points.");
        // Attribute points: staged with +, sent by Confirm (plan-attribute-points S5).
        sheet("attribute_points", "%s attribute points");
        sheet("attribute_points_one", "%s attribute point");
        sheet("alloc_value", "%s + %s");
        sheet("alloc_staged", " (+%s)");
        sheet("stage_title", "Place attribute points");
        sheet("stage_confirm", "Place %s attribute points? Only a paid stat reset takes them back.");
        sheet("stage_confirm_one", "Place %s attribute point? Only a paid stat reset takes it back.");
        // The server's answer to a refused Confirm or Reset.
        sheet("result_allocate_in_rift", "Not placed: attribute points can only be placed outside a rift.");
        sheet("result_allocate_no_class", "Not placed: choose a class first.");
        sheet("result_allocate_bad_request", "Not placed: place at least one point.");
        sheet("result_allocate_not_enough_points", "Not placed: you do not have that many attribute points.");
        sheet("result_respec_stats_in_rift", "Not reset: stats can only be reset outside a rift.");
        sheet("result_respec_stats_nothing", "Not reset: you have placed no attribute points.");
        sheet("result_respec_stats_not_enough_xp", "Not reset: not enough XP into this level to pay for it.");
        sheet("stage_yes", "Confirm");
        sheet("stage_back", "Cancel");
        sheet("next_point", "Next point:");
        sheet("plus_hint", "Shift-click to stage 10.");
        sheet("effect_armor", "armor");
        sheet("effect_health", "health");
        sheet("effect_armor_toughness", "armor toughness");
        sheet("effect_attack_damage", "attack damage");
        sheet("effect_attack_knockback", "attack knockback");
        sheet("effect_attack_speed", "attack speed");
        sheet("effect_crit", "crit");
        sheet("effect_crit_damage", "crit dmg");
        sheet("effect_max_charge", "max charge");
        sheet("effect_max_mana", "max mana");
        sheet("effect_spell_power", "spell power");
        sheet("effect_magic_resist", "magic resist");
        sheet("effect_healing_received", "healing received");
        sheet("effect_tenacity", "tenacity");
        sheet("effect_move_speed", "move speed");
        sheet("effect_dodge", "dodge");
        sheet("effect_knockback_resist", "knockback resist");
        sheet("effect_charge_regen", "charge regen");
        sheet("effect_luck", "luck");
        sheet("effect_scavenging", "scavenging");
        // D9: Apothic's experience_gained multiplies vanilla orbs, not Riftfall XP.
        sheet("effect_xp", "vanilla xp");
        // A primary's effect: number, then the effect_ label, then how many channels share it.
        sheet("effect_line", "+%s %s");
        sheet("effect_line_percent", "+%s%% %s");
        sheet("effect_line_channels", "+%s %s (%s)");
        sheet("effect_line_percent_channels", "+%s%% %s (%s)");

        // The skills spread: loadout leaf.
        skills("title", "Skills");
        skills("class_none", "No class chosen yet");
        skills("class_hint", "Your class is permanent, and the book fills in once you have one.");
        skills("choose_class", "Choose a class");
        skills("loadout", "Loadout");
        skills("set_outside", "set outside rifts");
        skills("locked_in_rift", "locked in a rift");
        skills("ultimate", "Ultimate");
        skills("opens_ascend", "opens when you ascend");
        skills("ultimate_u", "Ultimate, U");
        skills("empty", "empty");
        skills("rank_short", "rank %s / %s");
        skills("rank_short_plain", "%s/%s");
        skills("key_none", "-");
        skills("this_book", "This book");
        skills("base_actives", "Base actives");
        skills("base_passives", "Base passives");
        skills("universal_passives", "Universal passives");
        skills("of", "%s / %s");
        skills("ascendancy", "Ascendancy");
        skills("asc_to_go", "at %s, %s to go");
        skills("asc_ready", "open from %s");
        skills("loadout_hint", "Actives go in the eight slots, the ultimate in U. Passives are always on and never take a slot. Right-click a slot to empty it.");
        skills("not_equipped", "not equipped");
        skills("not_learned", "not learned");
        skills("slot_u", "slot U");
        skills("slot_key", "slot %s, key %s");
        skills("group_universal", "Universal");
        skills("group_base", "%s, base");

        // A skill's page.
        skills("back", "< Loadout");
        skills("rank_of", "Rank %s / %s");
        skills("col_rank", "rank %s");
        skills("col_now", "now");
        skills("at_rank", "At rank %s: %s");
        skills("scales_on", "Scales on");
        skills("scales_gun", " + held gun");
        skills("area", "Area");
        skills("area_around", "%s blocks, around you");
        skills("area_aimed", "%s blocks, aimed");
        skills("area_beam", "%s block beam");
        skills("area_self", "you");
        skills("also", "Also");
        skills("cast", "Cast");
        skills("seconds", "%s s");
        skills("mana", "Mana");
        skills("cooldown", "Cooldown");
        skills("fig_damage", "Damage");
        skills("fig_heal", "Heal");
        skills("fig_shield", "Shield size");
        skills("fig_buff", "Buff size");
        skills("fig_owner", "Owner scaling");
        skills("fig_time", "Duration");
        skills("rank_up", "Rank up");
        skills("equip", "Equip");
        skills("equip_slot", "Equip: slot...");
        skills("mastered", "Mastered");
        skills("next_rank_one", "Next rank: %s point");
        skills("next_rank_many", "Next rank: %s points");
        skills("you_have", "you have %s");
        skills("to_learn", "To learn this");
        skills("point_a_rank", "%s point a rank");
        skills("points_a_rank", "%s points a rank");
        skills("req_your_class", "your class");
        skills("req_level", "Reach level %s");
        skills("req_you_are", "you are %s");
        skills("req_closed", "Closed: you chose %s");
        skills("req_choose", "Choose %s");
        skills("req_once", "once");
        skills("closes_others", "The others close for good.");
        skills("ascend_command", "To ascend, use the Ascend button on the Character page.");
        skills("always_on", "always on");
        skills("attribute", "Attribute");
        skills("total_now", "Your total now");
        skills("who_can_buy", "Who can buy");
        skills("every_class", "every class");
        skills("class_only", "%s only");
        skills("loadout_slot", "Loadout slot");
        skills("none_always_on", "none, always on");

        // Equip mode.
        skills("now_in", "Now in");
        skills("equip_hint", "Click a dotted slot to put it there; its old slot empties. The ultimate slot takes ultimates only.");
        skills("equip_hint_ultimate", "Click slot U to put it there. Only an ultimate goes in U.");
        skills("read_page", "Read page");
        skills("cancel", "Cancel");

        // The browser.
        skills("tab_base", "Base");
        skills("tab_ascendancy", "Ascendancy");
        skills("tab_universal", "Universal");
        skills("filter_all", "All");
        skills("filter_owned", "Owned");
        skills("filter_can_learn", "Can learn");
        skills("universal_sub", "Passives for every class");
        skills("points_n", "%s points");
        skills("points_n_one", "%s point");
        skills("choose_at", "Choose one at %s. You are %s.");
        skills("choose_now", "Open to you now: use Ascend on the Character page. The choice is permanent.");
        skills("nothing_matches", "Nothing here under this filter.");
        skills("universal_note", "Any class can buy these from level 1: 1 point a rank, 5 ranks. Like every passive they are always on and take no loadout slot.");
        skills("asc_none", "No ascendancy skills are written for this class yet.");
        skills("actives_none", "No actives are written for this yet.");
        skills("passives_none", "No passives are written for this yet.");
        skills("band_actives", "Actives");
        skills("band_passives", "Passives");
        skills("band_universal", "Universal passives");
        skills("band_closed", "Closed for good");
        skills("band_shut", "+ %s");
        skills("band_actives_chip", "%s of %s, %s");
        skills("of_shown", "%s of %s");
        skills("band_actives_n", "%s, equip to a slot");
        skills("band_passives_n", "%s, always on, no slot");
        skills("band_universal_n", "%s, no slot");
        skills("chosen_at", "chosen at %s");
        skills("sub_ultimate", "Ultimate");
        skills("sub_actives", "Actives");
        skills("sub_passives", "Passives");
        skills("sub_ultimate_n", "slot U, 3 points a rank");
        skills("sub_actives_chip", "%s %s");
        skills("sub_passives_n", "%s, no slot");
        skills("row_ultimate", "ultimate, slot U");
        skills("row_opens_short", "when you ascend");
        skills("row_closed", "closed");
        skills("plus_points", "+%s");
        skills("passive_effect", "%s %s a rank");
        skills("passive_base", "Rank 1: %s");
        skills("passive_stat_bonus_strike", "Bonus strike");
        skills("passive_stat_cooldown_removed", "Cooldown removed");
        skills("passive_stat_hit_reduction", "Hit reduction");
        skills("passive_stat_max_mana", "Maximum mana on kill");

        // Skill types: the name, the chip, and the placeholder icon's glyph.
        lever("damage", "Damage", "DMG", "Dm");
        lever("dot", "Damage over time", "DOT", "Dt");
        lever("control", "Control", "CC", "Cc");
        lever("heal", "Heal", "HEAL", "He");
        lever("shield", "Shield", "SHD", "Sh");
        lever("movement", "Movement", "MOV", "Mv");
        lever("summon", "Summon", "SUM", "Su");
        lever("buff", "Buff", "BUFF", "Bf");
        skills("chip_passive", "PAS");
        skills("glyph_passive", "Pa");
        skills("engine_spell_engine", "Spell Engine");
        skills("engine_irons_spellbooks", "Iron's Spellbooks");

        // What a spend would answer, and what the last request did.
        skills("status_ok", "Next rank buyable");
        skills("status_unknown", "Unknown skill");
        skills("status_in_rift", "Locked in a rift");
        skills("status_max_rank", "Highest rank");
        skills("status_no_class", "Choose a class first");
        skills("status_wrong_class", "Another class's skill");
        skills("status_not_ascended", "Opens when you ascend, at level 60 or later");
        skills("status_other_ascendancy", "Closed: another ascendancy's skill");
        skills("status_not_enough_points", "Not enough points");
        skills("result_ok", "Done.");
        skills("result_unknown", "That skill no longer exists.");
        skills("result_in_rift", "Your build is locked inside a rift.");
        skills("result_max_rank", "Already at the highest rank.");
        skills("result_no_class", "Choose a class first.");
        skills("result_wrong_class", "That skill is another class's.");
        skills("result_not_ascended", "That skill opens when you ascend.");
        skills("result_other_ascendancy", "That skill is another ascendancy's.");
        skills("result_not_enough_points", "Not enough points.");
        skills("result_bad_slot", "There is no such slot.");
        skills("result_slot_locked", "The ultimate slot opens when you ascend.");
        skills("result_automatic", "The ultimate slot fills itself.");
        skills("result_wrong_slot", "Ultimates go in slot U, and only there.");
        skills("result_not_owned", "Buy a rank before equipping it.");
        skills("result_empty_slot", "That slot is already empty.");
        skills("result_nothing", "Nothing to reset.");
        skills("result_not_enough_xp", "Not enough XP into this level to pay for it.");
        skills("respec_button", "Reset skill points");
        skills("respec_confirm", "Refund all %s skill points for %s XP? Your class and ascension stay.");
        skills("respec_yes", "Reset");
        skills("respec_done", "Every skill point is yours to spend again. Your class and ascension are unchanged.");
        skills("respec_costs", "Costs %s XP; refunds %s points.");
        skills("respec_in_rift", "Only outside a rift.");
        skills("respec_short", "Costs %s XP from this level; you need %s more.");
        skills("respec_nothing", "No skill points spent.");
        // The detail page's fixed guide lines; the guide's own figures and notes come from the server.
        skills("slot_glyph_ultimate", "U");
        skills("basis_before_defenses", "Before crits / target defenses");
        skills("basis_utility", "Utility / effects");
        skills("heart_note", "2 damage = 1 heart. Estimates use your equipment when this book opened.");
        skills("scaling_line", "Scaling: %s.");
        skills("scaling_line_gun", "Scaling: %s and your held gun.");
        skills("cast_line", "Cast: %s");
        skills("scroll_more", "Scroll for full effects");
        skills("figure_rank", "Rank %s: %s");
    }

    static {
        // The class spread.
        // The Recovered Notes page.
        notes("title", "Recovered Notes");
        notes("heading", "Recovered Notes");
        notes("found_of", "%s of %s found");
        notes("loading", "Asking the server...");
        notes("pick_hint", "Choose a found note to read it. Notes are hidden around the home: on lecterns, in chests and barrels, in frames and on signs.");
        notes("scroll_more", "Scroll for more");
        notes("none_yet", "Nothing found yet.");
        notes("unknown_tile", "Unknown ground");
        notes("rank", "Piece %s");
        notes("count", "%s/%s");
        notes("missing", "Not yet found");
        notes("more_out_there", "%s more out there");
        notes("page", "%s / %s");
        notes("tile_sanctuary_100x100", "The Sanctuary");
        notes("tile_forge_100x100", "The Forge");
        notes("tile_ashen_quarry", "Ashen Quarry");
        notes("tile_03_brook_crossing", "Brook Crossing");
        notes("tile_08_sedge_meadow", "Sedge Meadow");
        notes("tile_02_stillwater_field", "Stillwater Field");
        notes("tile_04_birch_hollow", "Birch Hollow");
        notes("tile_07_clover_rise", "Clover Rise");
        notes("tile_10_stonefield_crossing", "Stonefield Crossing");
        notes("tile_observatory_100x100", "The Observatory");
        notes("tile_crystal_hollow", "Crystal Hollow");
        notes("tile_06_waystation_alpha", "The Waystation");
        notes("tile_01_meadow_ruin", "Meadow Ruin");
        notes("tile_05_forsaken_outpost", "Forsaken Outpost");
        notes("tile_supply_depot", "Supply Depot");
        notes("tile_riftscar_basin", "Riftscar Basin");
        notes("tile_windworn_plateau", "Windworn Plateau");
        notes("tile_09_orchard_rest", "Orchard Rest");
        notes("tile_sunken_gardens", "Sunken Gardens");
        notes("tile_fungal_grotto", "Fungal Grotto");
        notes("tile_reedwater_mill", "Reedwater Mill");
        notes("tile_flooded_cloister", "Flooded Cloister");
        notes("tile_rootbound_archive", "Rootbound Archive");
        notes("tile_frostpine_ridge", "Frostpine Ridge");
        notes("tile_fallen_aqueduct", "Fallen Aqueduct");
        notes("tile_wilderness", "The Wilderness");

        classes("title", "Classes");
        classes("heading_choose", "Choose your class");
        classes("heading_yours", "Classes");
        classes("sub_choose", "Choosing a class is permanent.");
        classes("sub_chosen", "You are %s.");
        classes("loading", "Asking the server...");
        classes("in_rift", "A class is chosen outside a rift.");
        classes("pick_hint", "Every class levels the same way.");
        classes("change_hint", "Only an operator can reset it.");
        classes("weapon", "Fights with");
        classes("skills", "Base skills");
        classes("skills_count", "%s active, %s passive");
        classes("more", "+%s more");
        classes("none_yet", "None written yet");
        classes("ascendancies", "Ascendancies at level %s");
        classes("choose", "Choose %s");
        classes("confirm_q", "Pick %s for good?");
        classes("confirm", "Confirm");
        classes("back", "Back");
        classes("class_set", "Your class is %s.");
        classes("now_a", "You are now %s.");
        classes("open_skills", "Open skills");
        classes("status_ok", "You can choose this class.");
        classes("status_unknown", "This class cannot be chosen.");
        classes("status_in_rift", "Choose outside a rift.");
        classes("status_already_chosen", "Your class is already chosen.");
        classes("status_owns_other_class", "You own skills of another class.");
        classes("result_ok", "Chosen.");
        classes("result_unknown", "The server refused that class.");
        classes("result_in_rift", "You are inside a rift.");
        classes("result_already_chosen", "Your class is already chosen.");
        classes("result_owns_other_class", "You own skills of another class.");

        // The Party page.
        social("title", "Party");
        social("badge", "%s invitations waiting");
        social("badge_one", "%s invitation waiting");
        social("loading", "Asking the server...");
        social("tab_party", "Expedition");
        social("tab_team", "Home team");
        social("tab_allies", "Allies");
        social("party_title", "Expedition party");
        social("party_count", "%s of %s");
        social("party_none", "Not in a party");
        social("party_hint", "A party runs rifts together. Invite someone from the list: that starts a party if you have none.");
        social("leader", "Leader");
        social("away", "away");
        social("you", "you");
        social("remove", "Remove");
        social("create", "Create");
        social("leave", "Leave");
        social("disband", "Disband");
        social("invites", "Invitations");
        social("invites_none", "No invitations.");
        social("party_from", "%s's party (%s)");
        social("accept", "Accept");
        social("decline", "Decline");
        social("invite_title", "Invite a player");
        social("invite", "Invite");
        social("online_none", "No one else is online.");
        social("in_your_party", "In your party");
        social("on_your_team", "On your team");
        social("more", "+%s more");
        social("team_title", "Home team");
        social("team_count", "%s members");
        social("team_count_one", "%s member");
        social("team_none", "Not on a team");
        social("team_hint", "A team shares one home and its progress. Create one, or join a team that invited you.");
        social("no_opac", "Home teams need Open Parties and Claims on this server.");
        social("rank_4", "Owner");
        social("rank_3", "Admin");
        social("rank_2", "Moderator");
        social("rank_1", "Claimer");
        social("rank_0", "Member");
        social("team_from", "%s's team (%s)");
        social("join", "Join");
        social("allies_title", "Home allies");
        social("allies_hint", "Allies can use things in your home. So can your team, allied teams and your party.");
        social("allies_none", "No allies yet.");
        social("teams_title", "Allied teams");
        social("teams_none", "No allied teams.");
        social("teams_need_team", "Join a home team to ally with other teams.");
        social("team_of", "%s's team");
        social("add_title", "Add an ally");
        social("ally", "Ally");
        social("ally_team", "Ally team");
        social("back", "Back");
        social("end", "End");
        social("c_party_leave_t", "Leave the party?");
        social("c_party_leave", "You can be invited back later.");
        social("c_party_leave_lead", "You lead it: the next member will lead.");
        social("c_party_remove_t", "Remove %s?");
        social("c_party_remove", "They leave the party. You can invite them again.");
        social("c_party_disband_t", "Disband the party?");
        social("c_party_disband", "Everyone in it leaves.");
        social("c_team_leave_t", "Leave the team?");
        social("c_team_leave", "You go back to your personal home.");
        social("c_team_kick_t", "Remove %s?");
        social("c_team_kick", "They leave the team and go back to their personal home.");
        social("c_team_disband_t", "Disband the team?");
        social("c_team_disband", "Everyone goes back to their personal home. No home is deleted.");
        social("c_ally_remove_t", "Remove %s?");
        social("c_ally_remove", "They lose their own access to your home. Access through a team, alliance or party stays.");
        social("c_team_unally_t", "End the alliance?");
        social("c_team_unally", "Your team and %s's team stop being allies.");
        social("why_in_party", "You are already in a party. Leave it first.");
        social("why_no_party", "You are not in a party.");
        social("why_not_leader", "Only the party leader can do this.");
        social("why_full", "Your party is full: %s players.");
        social("why_their_full", "Their party is full.");
        social("why_target_party", "Already in a party.");
        social("why_on_team", "You are already on a team. Leave it first.");
        social("why_no_team", "You are not on a team.");
        social("why_rank", "Needs Moderator rank or higher on your team.");
        social("why_rank_target", "You can only remove members ranked below you.");
        social("why_owner_leave", "The owner can't leave. Disband the team instead.");
        social("why_not_owner", "Only the team owner can do this.");
        social("why_target_team", "Already on a team.");
        social("why_target_no_team", "Not on a team.");
        social("why_allied", "Your teams are already allies.");
        social("why_not_home_owner", "Only the home owner can change allies.");
        social("why_ally_already", "Already an ally.");
    }

    private BookLang() {
    }

    private static void notes(String line, String text) {
        put("notes." + line, text);
    }

    private static void social(String line, String text) {
        put("social." + line, text);
    }

    private static void classes(String line, String text) {
        put("classes." + line, text);
    }

    private static void sheet(String line, String text) {
        put("sheet." + line, text);
    }

    private static void skills(String line, String text) {
        put("skills." + line, text);
    }

    private static void lever(String key, String name, String chip, String glyph) {
        skills("lever_" + key, name);
        skills("chip_" + key, chip);
        skills("glyph_" + key, glyph);
    }

    private static void put(String key, String text) {
        if (TEXT.put(key, text) != null) {
            throw new IllegalStateException("book line written twice: " + key);
        }
    }

    /** The English for {@code page.line}; a line the book draws with no text here stops the data run. */
    static String text(String page, String line) {
        String text = TEXT.get(page + "." + line);
        if (text == null) {
            throw new IllegalStateException("no English for screen line " + page + "." + line);
        }
        return text;
    }
}
