package com.cyberday1.riftfall.data;

import com.cyberday1.riftfall.RiftfallCore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The class screen's English for each of the 13 classes: name, role, blurb, weapon and the two-letter
 * placeholder glyph. Placeholder copy (assumed defaults), written from the class's
 * ascendancy names and its physical-vs-magic row; placeholder text, to be rewritten.
 */
final class ClassLang {

    private record Row(String name, String role, String blurb, String weapon, String glyph) {
    }

    private static final Map<String, Row> ROWS = new LinkedHashMap<>();

    static {
        row("arcanist", "Arcanist", "Caster", "Bends raw arcane force into bolts, storms and twisted time.",
                "Staves and wands", "Ar");
        row("cleric", "Cleric", "Healer", "Keeps the party standing with heals, wards and holy light.",
                "Staves and maces", "Cl");
        row("gunner", "Gunner", "Ranged", "Fires shells, lays mines and deploys gadgets from range.",
                "Guns and shells", "Gu");
        row("monk", "Monk", "Melee", "Strikes fast with fists and kicks, channeling spirit in stances.",
                "Fists and kicks", "Mo");
        row("paladin", "Paladin", "Tank", "Holds the line with holy strikes, shields and auras.",
                "Weapon and shield", "Pa");
        row("phantom", "Phantom", "Melee", "Slips through shadow to strike with blades and poison.",
                "Blades and poisons", "Ph");
        row("ranger", "Ranger", "Ranged", "Shoots from range, sets traps and fights beside beasts.",
                "Bow and traps", "Ra");
        row("reaper", "Reaper", "Melee", "Reaps with a scythe, draining life and spreading decay.",
                "Scythe", "Re");
        row("shifter", "Shifter", "Melee", "Takes the shape of bear, wolf, tiger, serpent or raptor.",
                "Axes and claws", "Sh");
        row("summoner", "Summoner", "Summoner", "Calls beasts, spirits, demons and the dead to fight for them.",
                "Staves and relics", "Su");
        row("sword_immortal", "Sword Immortal", "Melee",
                "A sword cultivator: wind, lightning and spirit ride the blade.", "Sword", "SI");
        row("tactician", "Tactician", "Support", "Leads the fight with commands, banners and siege craft.",
                "Polearms and flails", "Ta");
        row("trickster", "Trickster", "Melee", "Fights dirty with blades, throws, illusions and traps.",
                "Blades and throws", "Tr");
    }

    private ClassLang() {
    }

    private static void row(String id, String name, String role, String blurb, String weapon, String glyph) {
        ROWS.put(id, new Row(name, role, blurb, weapon, glyph));
    }

    /** Every key and its English: {@code class.riftfall.<id>} is the name, the rest are its parts. */
    static Map<String, String> lines() {
        Map<String, String> lines = new LinkedHashMap<>();
        ROWS.forEach((id, row) -> {
            String key = "class." + RiftfallCore.MODID + "." + id;
            lines.put(key, row.name());
            lines.put(key + ".role", row.role());
            lines.put(key + ".blurb", row.blurb());
            lines.put(key + ".weapon", row.weapon());
            lines.put(key + ".glyph", row.glyph());
        });
        return lines;
    }
}
