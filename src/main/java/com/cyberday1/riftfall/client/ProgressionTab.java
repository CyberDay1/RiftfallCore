package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.skill.Respec;
import com.cyberday1.riftfall.skill.SkillBook;
import com.cyberday1.riftfall.skill.SkillNode;
import com.cyberday1.riftfall.skill.SkillTable.LeverType;
import com.cyberday1.riftfall.stat.AttributePoints;
import com.cyberday1.riftfall.stat.StatBlock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The pages of the progression book, in the order their bookmarks hang, and every line each page draws.
 *
 * <p>
 * Each bookmark's opener is {@link ProgressionScreen#open}, a switch with no default, so a page added
 * here without a screen fails to compile rather than drawing a dead bookmark. The lines are listed so a
 * test can check each one was generated into the lang file.
 */
public enum ProgressionTab {
    CHARACTER("sheet", sheetLines()),
    SKILLS("skills", skillLines()),
    CLASSES("classes", classLines()),
    NOTES("notes", noteLines()),
    SOCIAL("social", socialLines());

    private final String page;
    private final List<String> lines;

    ProgressionTab(String page, List<String> lines) {
        this.page = page;
        this.lines = List.copyOf(lines);
    }

    private static List<String> sheetLines() {
        List<String> lines = new ArrayList<>(List.of("tier", "level", "no_class", "class_hint", "choose_class", "main", "second",
                "can_ascend", "ascend_ready", "ascended", "ascend_button", "ascend_title", "ascend_choose", "ascend_confirm",
                "ascend_yes", "points", "points_one", "skills_link", "rift_log_link", "rift_log_hint", "mix_separator", "mix_lever", "mix_passives",
                "mix_passives_one", "mix_none",
                "experience", "next_tier", "capped", "into", "to_go", "health", "mana_of", "shield", "mana_now",
                "mana_base", "mana_passives", "mana_from", "mana_gear", "mana_regen", "defence", "armor", "toughness",
                "resist", "guard", "percent", "resist_hint", "resist_hint_rift", "guard_hint", "attributes", "class_block", "no_block", "respec_link", "respec_title",
                "respec_confirm", "respec_yes", "respec_back", "respec_costs", "respec_in_rift", "respec_short",
                "respec_nothing", "attribute_points", "attribute_points_one", "alloc_value", "alloc_staged", "stage_title",
                "stage_confirm", "stage_confirm_one", "stage_yes", "stage_back", "next_point", "plus_hint"));
        // A refused Confirm or Reset: one line per answer that is not OK.
        for (AttributePoints.Allocate answer : AttributePoints.Allocate.values()) {
            if (answer != AttributePoints.Allocate.OK) {
                lines.add("result_allocate_" + answer.name().toLowerCase(Locale.ROOT));
            }
        }
        for (Respec.Result answer : Respec.Result.values()) {
            if (answer != Respec.Result.OK) {
                lines.add("result_respec_stats_" + answer.name().toLowerCase(Locale.ROOT));
            }
        }
        for (SkillBook.Ascend answer : SkillBook.Ascend.values()) {
            if (answer != SkillBook.Ascend.OK) {
                lines.add("result_ascend_" + answer.name().toLowerCase(Locale.ROOT));
            }
        }
        for (StatBlock.Primary primary : StatBlock.Primary.values()) {
            lines.add("tag_" + primary.key());
        }
        for (String label : CharacterSheet.effectLabels()) {
            lines.add("effect_" + label);
        }
        lines.addAll(List.of("effect_line", "effect_line_percent", "effect_line_channels", "effect_line_percent_channels"));
        return lines;
    }

    /** The Party page's lines. */
    private static List<String> socialLines() {
        return List.of("badge", "badge_one", "loading", "tab_party", "tab_team", "tab_allies", "party_title",
                "party_count", "party_none", "party_hint", "leader", "away", "you", "remove",
                "create", "leave", "disband", "invites", "invites_none", "party_from", "accept",
                "decline", "invite_title", "invite", "online_none", "in_your_party", "on_your_team",
                "more", "team_title", "team_count", "team_count_one", "team_none", "team_hint",
                "no_opac", "rank_4", "rank_3", "rank_2", "rank_1", "rank_0", "team_from", "join",
                "allies_title", "allies_hint", "allies_none", "teams_title", "teams_none",
                "teams_need_team", "team_of", "add_title", "ally", "ally_team", "back", "end",
                "c_party_leave_t", "c_party_leave", "c_party_leave_lead", "c_party_remove_t",
                "c_party_remove", "c_party_disband_t", "c_party_disband", "c_team_leave_t",
                "c_team_leave", "c_team_kick_t", "c_team_kick", "c_team_disband_t",
                "c_team_disband", "c_ally_remove_t", "c_ally_remove", "c_team_unally_t",
                "c_team_unally", "why_in_party", "why_no_party", "why_not_leader", "why_full",
                "why_their_full", "why_target_party", "why_on_team", "why_no_team", "why_rank",
                "why_rank_target", "why_owner_leave", "why_not_owner", "why_target_team",
                "why_target_no_team", "why_allied", "why_not_home_owner", "why_ally_already");
    }

    /** The Recovered Notes spread's lines, then one name per tile with notes (and the wilderness). */
    private static List<String> noteLines() {
        List<String> lines = new ArrayList<>(List.of("heading", "found_of", "loading", "pick_hint", "scroll_more",
                "none_yet", "unknown_tile", "rank", "count", "missing", "more_out_there", "page"));
        for (var tile : com.cyberday1.riftfall.lore.LoreNotes.tiles()) {
            lines.add("tile_" + tile.id());
        }
        return lines;
    }

    /** The class spread's fixed lines, then one status and one result line per {@code SkillBook.Choose} answer. */
    private static List<String> classLines() {
        List<String> lines = new ArrayList<>(List.of("heading_choose", "heading_yours", "sub_choose", "sub_chosen",
                "loading", "in_rift", "pick_hint", "change_hint", "weapon", "skills", "skills_count",
                "more", "none_yet", "ascendancies", "choose", "confirm_q", "confirm", "back", "class_set", "now_a",
                "open_skills"));
        for (SkillBook.Choose choose : SkillBook.Choose.values()) {
            lines.add("status_" + choose.name().toLowerCase(Locale.ROOT));
            lines.add("result_" + choose.name().toLowerCase(Locale.ROOT));
        }
        return lines;
    }

    /** The skills spread's fixed lines, one set per lever, then one status and one result line per verb answer. */
    private static List<String> skillLines() {
        List<String> lines = new ArrayList<>(List.of("class_none", "class_hint", "choose_class", "loadout", "set_outside",
                "locked_in_rift", "ultimate", "opens_ascend", "ultimate_u", "empty", "rank_short", "rank_short_plain",
                "key_none", "this_book", "base_actives", "base_passives", "universal_passives", "of", "ascendancy",
                "asc_to_go", "asc_ready", "loadout_hint", "not_equipped", "not_learned", "slot_u", "slot_key",
                "group_universal", "group_base", "back", "rank_of", "col_rank", "col_now", "at_rank", "scales_on", "scales_gun",
                "area", "area_around", "area_aimed", "area_beam", "area_self", "also", "cast", "seconds",
                "mana", "cooldown", "fig_damage", "fig_heal", "fig_shield", "fig_buff", "fig_owner", "fig_time",
                "rank_up", "equip", "equip_slot", "mastered", "next_rank_one", "next_rank_many", "you_have",
                "to_learn", "point_a_rank", "points_a_rank", "req_your_class", "req_level", "req_you_are",
                "req_closed", "req_choose", "req_once", "closes_others", "ascend_command", "always_on",
                "attribute", "total_now", "who_can_buy", "every_class", "class_only", "loadout_slot",
                "none_always_on", "now_in", "equip_hint", "equip_hint_ultimate", "read_page", "cancel",
                "universal_sub", "points_n", "points_n_one", "choose_at", "choose_now", "nothing_matches", "universal_note",
                "asc_none", "actives_none", "passives_none", "band_actives", "band_passives", "band_universal",
                "band_closed", "band_shut", "band_actives_chip", "of_shown", "band_actives_n", "band_passives_n",
                "band_universal_n", "chosen_at", "sub_ultimate", "sub_actives", "sub_passives", "sub_ultimate_n",
                "sub_actives_chip", "sub_passives_n", "row_ultimate", "row_opens_short", "row_closed",
                "plus_points", "passive_effect", "passive_base", "passive_stat_bonus_strike",
                "passive_stat_cooldown_removed", "passive_stat_hit_reduction", "passive_stat_max_mana", "chip_passive", "glyph_passive", "respec_button", "respec_confirm",
                "respec_yes", "respec_done", "respec_costs", "respec_in_rift", "respec_short", "respec_nothing",
                "slot_glyph_ultimate", "basis_before_defenses", "basis_utility", "heart_note", "scaling_line",
                "scaling_line_gun", "cast_line", "scroll_more", "figure_rank"));
        for (CodexBrowser.Tab tab : CodexBrowser.Tab.values()) {
            lines.add("tab_" + tab.name().toLowerCase(Locale.ROOT));
        }
        for (CodexBrowser.Filter filter : CodexBrowser.Filter.values()) {
            lines.add("filter_" + filter.name().toLowerCase(Locale.ROOT));
        }
        for (LeverType lever : LeverType.values()) {
            lines.add("lever_" + lever.key());
            lines.add("chip_" + lever.key());
            lines.add("glyph_" + lever.key());
        }
        for (SkillNode.Engine engine : SkillNode.Engine.values()) {
            lines.add("engine_" + engine.name().toLowerCase(Locale.ROOT));
        }
        for (SkillBook.Spend spend : SkillBook.Spend.values()) {
            lines.add("status_" + spend.name().toLowerCase(Locale.ROOT));
        }
        Set<String> results = new LinkedHashSet<>();
        for (SkillBook.Spend spend : SkillBook.Spend.values()) {
            results.add("result_" + spend.name().toLowerCase(Locale.ROOT));
        }
        for (SkillBook.Equip equip : SkillBook.Equip.values()) {
            results.add("result_" + equip.name().toLowerCase(Locale.ROOT));
        }
        for (Respec.Result respec : Respec.Result.values()) {
            results.add("result_" + respec.name().toLowerCase(Locale.ROOT));
        }
        lines.addAll(results);
        return lines;
    }

    /** The page's title key, which is also its bookmark's tooltip. */
    public String titleKey() {
        return "screen." + RiftfallCore.MODID + "." + page + ".title";
    }

    /** The name of the key that opens this page; {@code key.riftfall.sheet} predates the bookmarks. */
    public String keyName() {
        return "key." + RiftfallCore.MODID + "." + page;
    }

    /** A line on the page. Listed in {@link #lines} so a test can check each one was generated. */
    public String key(String line) {
        return "screen." + RiftfallCore.MODID + "." + page + "." + line;
    }

    /** The bare line names, for the lang provider. */
    public String page() {
        return page;
    }

    public List<String> lines() {
        return lines;
    }
}
