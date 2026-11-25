package com.cyberday1.riftfall.data;

import com.cyberday1.riftfall.RiftfallCore;

import java.util.LinkedHashMap;
import java.util.Map;

/** The English for the rift recap and the run history behind {@code /riftfall recap}. */
final class RecapLang {

    private static final String PREFIX = "screen." + RiftfallCore.MODID + ".recap.";

    private RecapLang() {
    }

    static Map<String, String> lines() {
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put(PREFIX + "title", "Rift Log");
        lines.put(PREFIX + "heading", "Rift Log");
        lines.put(PREFIX + "recorded", "%s runs recorded");
        lines.put(PREFIX + "recorded_one", "%s run recorded");
        lines.put(PREFIX + "recorded_none", "No runs recorded");
        lines.put(PREFIX + "empty", "No runs recorded yet. Finish a rift run and its recap is kept here.");
        lines.put(PREFIX + "pick_hint", "Choose a run to see its recap.");
        lines.put(PREFIX + "scroll_more", "Scroll for more");
        lines.put(PREFIX + "close", "Close");

        lines.put(PREFIX + "row_cleared", "Cleared");
        lines.put(PREFIX + "row_left_early", "Left early");
        lines.put(PREFIX + "row_fell", "Fell");
        lines.put(PREFIX + "row_timed_out", "Time ran out");
        lines.put(PREFIX + "outcome_cleared", "Rift cleared");
        lines.put(PREFIX + "outcome_left_early", "Left early");
        lines.put(PREFIX + "outcome_fell", "You fell");
        lines.put(PREFIX + "outcome_timed_out", "Time ran out");

        lines.put(PREFIX + "tier", "Tier %s");
        lines.put(PREFIX + "tier_time", "Tier %s, run time %s");
        lines.put(PREFIX + "age_now", "Just now");
        lines.put(PREFIX + "age_minutes", "%s minutes ago");
        lines.put(PREFIX + "age_minutes_one", "%s minute ago");
        lines.put(PREFIX + "age_hours", "%s hours ago");
        lines.put(PREFIX + "age_hours_one", "%s hour ago");
        lines.put(PREFIX + "age_days", "%s days ago");
        lines.put(PREFIX + "age_days_one", "%s day ago");

        lines.put(PREFIX + "xp_heading", "Experience");
        lines.put(PREFIX + "xp_enemies", "Enemies");
        lines.put(PREFIX + "xp_clear", "Clear bonus");
        lines.put(PREFIX + "xp_clear_none", "None");
        lines.put(PREFIX + "xp_amount", "%s XP");
        lines.put(PREFIX + "xp_total", "Total kept");
        lines.put(PREFIX + "xp_kept", "Kept (%s%%)");
        lines.put(PREFIX + "note_early",
                "You left before the boss was defeated: %s%% of enemy XP kept, no clear bonus.");
        lines.put(PREFIX + "note_lost",
                "The run was lost: %s%% of enemy XP kept, no clear bonus.");
        lines.put(PREFIX + "level_same", "Level %s");
        lines.put(PREFIX + "level_up", "%s levels gained (%s to %s)");
        lines.put(PREFIX + "level_up_one", "%s level gained (%s to %s)");

        lines.put(PREFIX + "run_heading", "Run");
        lines.put(PREFIX + "kills", "%s enemies killed");
        lines.put(PREFIX + "kills_one", "%s enemy killed");
        lines.put(PREFIX + "kills_none", "No enemies killed");
        lines.put(PREFIX + "boss_defeated", "Boss defeated");
        lines.put(PREFIX + "boss_not_defeated", "Boss not defeated");
        lines.put(PREFIX + "barrels", "%s barrels looted");
        lines.put(PREFIX + "barrels_one", "%s barrel looted");
        lines.put(PREFIX + "barrels_none", "No barrels looted");
        lines.put(PREFIX + "shards", "%s Rift Shards found");
        lines.put(PREFIX + "shards_one", "%s Rift Shard found");
        lines.put(PREFIX + "shards_none", "No Rift Shards found");
        lines.put(PREFIX + "gear", "%s gear pieces found");
        lines.put(PREFIX + "gear_one", "%s gear piece found");
        lines.put(PREFIX + "gear_none", "No gear found");
        lines.put(PREFIX + "gear_best", "Best gear rarity: %s");

        // Held gear handed back as the recap arrives (RiftAutoClaims).
        lines.put(PREFIX + "claim_heading", "Your gear");
        lines.put(PREFIX + "claim_back", "Back in your inventory: %s");
        lines.put(PREFIX + "claim_back_more", "Back in your inventory: %s and %s more");
        lines.put(PREFIX + "claim_left", "%s stacks did not fit and are still held. Make room, then claim the rest.");
        lines.put(PREFIX + "claim_left_one", "%s stack did not fit and is still held. Make room, then claim the rest.");
        lines.put(PREFIX + "claim_no_room", "Still no room for %s stacks. Make room in your inventory first.");
        lines.put(PREFIX + "claim_no_room_one", "Still no room for %s stack. Make room in your inventory first.");
        lines.put(PREFIX + "claim_rest", "Claim the rest");
        return lines;
    }
}
