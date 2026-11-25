package com.cyberday1.riftfall.data;

import java.util.List;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.barrel.BarrelTier;
import com.cyberday1.riftfall.block.barrel.RiftBarrels;
import com.cyberday1.riftfall.block.marker.MarkerKind;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.block.marker.PoiAnchorKind;
import com.cyberday1.riftfall.block.marker.PoiAnchors;
import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import com.cyberday1.riftfall.block.processor.ProcessorBlockFamily;
import com.cyberday1.riftfall.block.processor.ProcessorBlocks;
import com.cyberday1.riftfall.block.processor.ProcessorSlot;
import com.cyberday1.riftfall.block.processor.ProcessorVariant;
import com.cyberday1.riftfall.client.ProgressionTab;
import com.cyberday1.riftfall.init.RiftfallAttributes;
import com.cyberday1.riftfall.init.RiftfallEffects;
import com.cyberday1.riftfall.gear.StarterKits;
import com.cyberday1.riftfall.home.HomeCommands;
import com.cyberday1.riftfall.tool.ToolLang;
import com.cyberday1.riftfall.skill.GuideLang;
import com.cyberday1.riftfall.skill.SkillWeapons;
import com.cyberday1.riftfall.skill.RespecCommand;
import com.cyberday1.riftfall.skill.SkillCommand;
import com.cyberday1.riftfall.stat.StatBlock;
import com.cyberday1.riftfall.init.RiftfallBlocks;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.skill.SkillTable;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;


/**
 * en_us for Riftfall Core.
 *
 * <p>
 * The slot semantics in the placeholder names are the builder-facing contract, inherited from the
 * structure library these placeholders come from:
 *
 * <pre>
 * 1 Wall | 2 Path | 3 Floor | 4 Alt Wall | 5 Alt Floor | 6 Planks | 7 Bricks | 8 Alt Path
 * 9..15  (generic accents, no semantic name)
 * </pre>
 *
 * A room author picks slots by that meaning, so the bracketed label has to stay on the name — a
 * bare "Processor Block 4" tells nobody it is the alt wall.
 */
public class RiftfallLanguageProvider extends LanguageProvider {

    private final PackOutput output;

    public RiftfallLanguageProvider(PackOutput output, String locale) {
        super(output, RiftfallCore.MODID, locale);
        this.output = output;
    }

    @Override
    protected void addTranslations() {
        add("entity.riftfall.summon_guardian_spirit", "Guardian Spirit");
        add("entity.riftfall.unseated_preview", "The Unseated (Preview)");
        add("entity.riftfall.unseated", "The Unseated");
        add(com.cyberday1.riftfall.boss.UnseatedText.TETHER_NAME, "Tether");
        // Everything a player reads in the Unseated fight, and the god's three lines (story review S9).
        com.cyberday1.riftfall.boss.UnseatedText.english().forEach(this::add);
        add("entity.riftfall.summon_spirit_healer", "Spirit Healer");
        add("entity.riftfall.summon_plague_servant", "Plague Servant");
        add("entity.riftfall.summon_wraith", "Wraith");
        add("entity.riftfall.summon_ancestral_spirit", "Ancestral Spirit");
        add("entity.riftfall.summon_storm_elemental", "Storm Elemental");
        add("entity.riftfall.summon_fire_elemental", "Fire Elemental");
        add("entity.riftfall.summon_demon_knight", "Demon Knight");
        add("entity.riftfall.summon_lich", "Lich");
        add("entity.riftfall.summon_ghoul", "Ghoul");
        add("entity.riftfall.summon_skeleton", "Skeleton");
        add("entity.riftfall.summon_sprite", "Sprite");
        add("entity.riftfall.summon_imp", "Imp");
        add("itemGroup." + RiftfallCore.MODID + ".processor_blocks", "Riftfall Processor Blocks");

        for (ProcessorBlockFamily family : ProcessorBlocks.families()) {
            for (ProcessorVariant variant : ProcessorVariant.values()) {
                addBlock(family.holder(variant), displayName(family.slot(), variant));
            }
        }

        add("itemGroup." + RiftfallCore.MODID + ".marker_blocks", "Riftfall Rift Markers");
        for (MarkerKind kind : MarkerKind.ALL) {
            addBlock(RiftfallMarkers.holder(kind), kind.displayName());
        }
        for (PoiAnchorKind kind : PoiAnchorKind.ALL) {
            addBlock(PoiAnchors.holder(kind), kind.displayName());
        }
        // Named here rather than beside the key forge: it shares the markers' drawer, not survival's.
        for (NpcRole role : NpcRole.values()) {
            addBlock(RiftfallBlocks.NPC_SPAWN_MARKERS.get(role), "NPC Spawn Marker: " + role.displayName());
            add(role.serviceTranslationKey(), "Services: " + role.services());
        }
        add("tooltip.riftfall.npc_spawn.position", "Place at NPC feet height above a solid floor. Arrow points forward.");
        add("tooltip.riftfall.npc_spawn.controls", "Creative, empty hand: use = radius 0-8; sneak-use = turn.");
        add("tooltip.riftfall.npc_spawn.planned", "Home resident (needs CustomNPCs). Services open as each resident is built.");
        add("message.riftfall.npc_spawn.configured", "%s | Facing %s | Roam radius %s blocks (0 = stationary)");
        addBlock(RiftfallBlocks.ONE_WAY_GLASS, "One-Way Glass");
        // Story notes; the lore.* text is the draft, English only until the voice pass.
        addBlock(RiftfallBlocks.NOTE_MARKER, "Note Marker");
        addBlock(RiftfallBlocks.GEM_SEAM, "Gem Seam");
        addBlock(RiftfallBlocks.ICE_OPAL_ORE, "Ice Opal Ore");
        addBlock(RiftfallBlocks.RIFTSTONE, "Riftstone");
        RiftfallBlocks.CRYSTAL_HOST_ORES.forEach((id, block) -> addBlock(block, java.util.Arrays.stream(id.split("_"))
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1)).collect(java.util.stream.Collectors.joining(" "))));
        addItem(RiftfallItems.LOST_NOTE, "Lost Note");
        add("item.riftfall.lost_note.book", "Worn Book");
        add("item.riftfall.lost_note.letter", "Folded Note");
        add("item.riftfall.lost_note.card", "Card");
        add("item.riftfall.lost_note.map", "Old Chart");
        add("item.riftfall.lost_note.stick", "Carving");
        add("item.riftfall.lost_note.clock", "Stopped Clock");
        add("item.riftfall.lost_note.sign", "Sign");
        add("item.riftfall.lost_note.hint", "Use to read.");
        add("toast.riftfall.note_found", "Note recovered");
        add("toast.riftfall.note_found.hint", "Kept in Recovered Notes");
        com.cyberday1.riftfall.lore.LoreNotes.english().forEach(this::add);
        // Residents' names, recognition lines and swapped lines (tools/lore/import_residents.py), draft too.
        com.cyberday1.riftfall.lore.ResidentStory.english().forEach(this::add);
        // Rift-side hooks and the respawn lines (draft v3 section 8), draft too.
        add(com.cyberday1.riftfall.lore.ShatteredShardItem.ORIGIN, "%1$s, of %2$s");
        add(com.cyberday1.riftfall.lore.ShatteredShardItem.ORIGIN_ALONE, "%s");
        add(com.cyberday1.riftfall.lore.ShatteredShardItem.GUARDED, "Guarded by %s");
        add(com.cyberday1.riftfall.lore.ShatteredShardItem.WORLD, "Of %s");
        add(com.cyberday1.riftfall.lore.BossDeathLine.BEFORE, "%1$s falls. Somewhere above, the sky creaks.");
        add(com.cyberday1.riftfall.lore.BossDeathLine.AFTER, "%1$s falls. It was the last thing guarding %2$s.");
        // What the two lines above call the defender (lore/defenders.json): per theme, placeholder text.
        com.cyberday1.riftfall.lore.DefenderNames.english().forEach(this::add);
        com.cyberday1.riftfall.lore.RespawnLines.english().forEach(this::add);
        // The Core's line when it takes a shard (lore/core_handin.json): placeholder text for the voice pass.
        com.cyberday1.riftfall.lore.CoreHandInLines.english().forEach(this::add);
        // The Core's line when a home tile lands (lore/core_beats.json): draft v3 section 6 as drafted.
        com.cyberday1.riftfall.lore.CoreTileBeats.english().forEach(this::add);
        // The opening's scenes (story S2): the Core's greeting, beats 1 and 2, the first return, Penk's first words.
        com.cyberday1.riftfall.story.StoryLines.english().forEach(this::add);

        for (BarrelTier tier : BarrelTier.ALL) {
            addBlock(RiftBarrels.holder(tier), tier.displayName());
        }
        // One title for all four: the screen is the same screen, and the tier is already legible
        // from the barrel the player is standing at.
        add("container." + RiftfallCore.MODID + ".rift_barrel", "Rift Barrel");

        // A rift-dropped gun's grade: rarity and tier, then one pull's damage.
        add("tooltip." + RiftfallCore.MODID + ".gun_grade", "Rift Tier %2$s: %1$s");
        add("tooltip." + RiftfallCore.MODID + ".gun_damage", "%s Bullet Damage");
        // Its rarity's handling bonuses, one line each, none at common.
        add("tooltip." + RiftfallCore.MODID + ".gun_stat.fire_rate", "%s Fire Rate");
        add("tooltip." + RiftfallCore.MODID + ".gun_stat.recoil", "%s Recoil");
        add("tooltip." + RiftfallCore.MODID + ".gun_stat.reload", "%s Reload Time");
        add("tooltip." + RiftfallCore.MODID + ".gun_stat.ads", "%s Aim Time");
        add("tooltip." + RiftfallCore.MODID + ".gun_stat.spread", "%s Spread");
        add("tooltip." + RiftfallCore.MODID + ".equipment_grade", "Rift Tier %2$s: %1$s");
        add("tooltip." + RiftfallCore.MODID + ".no_affix_rarity", "No Affix Rarity");
        add("tooltip." + RiftfallCore.MODID + ".unique_weapon", "Unique Weapon");

        add("itemGroup." + RiftfallCore.MODID + ".rift", "Riftfall");
        addItem(RiftfallItems.RIFT_KEY, "Rift Key");
        addItem(RiftfallItems.RIFT_SHARD, "Rift Shard");
        addItem(RiftfallItems.GEM_GRIT, "Gem Grit");
        addItem(RiftfallItems.SOCKET_CHISEL, "Socket Chisel");
        addItem(RiftfallItems.FACETED_CRYSTAL, "Faceted Crystal");
        addItem(RiftfallItems.TEMPERING_STOCK, "Tempering Stock");
        addItem(RiftfallItems.RIFT_ANCHOR, "Rift Anchor");
        addItem(RiftfallItems.RIFT_FLARE, "Rift Flare");
        addItem(RiftfallItems.LOOTING_PICK, "Riftfall Looting Pick");
        addItem(RiftfallItems.COMMON_RUNESTONE, "Common Runestone");
        addItem(RiftfallItems.UNCOMMON_RUNESTONE, "Uncommon Runestone");
        addItem(RiftfallItems.RARE_RUNESTONE, "Rare Runestone");
        addItem(RiftfallItems.EXOTIC_RUNESTONE, "Exotic Runestone");
        addBlock(RiftfallBlocks.KEY_FORGE, "Key Forge");
        add("container." + RiftfallCore.MODID + ".key_forge", "Key Forge");
        // The label beside the tier box, and what the box says when the number in it buys nothing.
        add("container." + RiftfallCore.MODID + ".key_forge.tier", "Tier");
        add("container." + RiftfallCore.MODID + ".key_forge.cost", "%s shards");
        add("container." + RiftfallCore.MODID + ".key_forge.level", "Tier %s needs level %s");
        // The portal has no item form, but a block still gets named by the debug screen and by
        // anything that reports a block position, and an untranslated one reads as a bug.
        addBlock(RiftfallBlocks.RIFT_PORTAL, "Rift Portal");
        addBlock(RiftfallBlocks.RIFT_TEAR, "Rift Tear");
        addBlock(RiftfallBlocks.WORLD_BREAKER, "World Breaker");
        addBlock(RiftfallBlocks.WORLD_RESURRECTION_MACHINE, "World Core Cradle");
        addBlock(RiftfallBlocks.HOLY_CORE, "World Core");
        addBlock(RiftfallBlocks.WORLD_RESURRECTION_MACHINE_PART, "World Core Pedestal");
        addItem(RiftfallItems.SHATTERED_WORLD_SHARD, "Shattered World Shard");
        add("message.riftfall.core.outside_home", "The World Core can only restore a Shattered Home.");
        add("message.riftfall.core.limit", "This home has reached its expansion limit.");
        add("message.riftfall.core.unloaded", "Waiting for the surrounding pedestal chunks to load.");
        add("message.riftfall.core.missing_part", "Missing pedestal part at %s, %s, %s. Restore it with an empty hand.");
        add("message.riftfall.core.unsupported", "The pedestal needs a solid 3 x 3 foundation. Missing support at %s, %s, %s.");
        add("message.riftfall.core.obstructed", "Clear the space above the Core. Obstruction at %s, %s, %s.");
        add(com.cyberday1.riftfall.skill.ClassSelectService.REMINDER, "You have not chosen a class yet. Type %s or click it to choose one.");
        add(com.cyberday1.riftfall.skill.ClassSelectService.REMINDER_HOVER, "Open the class screen");
        add(com.cyberday1.riftfall.compat.SpearLoyalty.TOOLTIP, "Returns to you when thrown");
        add(com.cyberday1.riftfall.story.FirstLoginGuide.BOOK_GIVEN, "Your quest book is in your inventory: right-click it to see what to do.");
        add("message.riftfall.core.progress", "World Core: phase %s of 5. Shards given: %s. Sections restored: %s.");
        add("message.riftfall.core.layout_unsupported", "This home's layout version is not supported. Your shard was not consumed.");
        add("message.riftfall.core.section_unavailable", "The next planned section is not available. Your shard was not consumed.");
        add("message.riftfall.core.section_failed", "The world section could not be restored. Your shard was not consumed.");
        // Takes the tier as an argument rather than baking it into the name, so a translation can
        // put the number where its language wants it.
        add("item." + RiftfallCore.MODID + ".rift_key.tier", "Tier %s");
        add(com.cyberday1.riftfall.rift.RiftKeyItem.ATTUNED_KEY, "Attuned: %s rifts");
        add(com.cyberday1.riftfall.rift.RiftKeyItem.ATTUNED_TWO_KEY, "Attuned: %s or %s rifts");
        add(com.cyberday1.riftfall.rift.RiftKeyItem.BOSS_KEY, "Boss: %s");
        // The two ways a lit frame can refuse somebody. Both are worth saying, because the portal
        // looks exactly the same either way and a silent refusal reads as the mod being broken.
        add("message." + RiftfallCore.MODID + ".rift_locked_out", "You have already been into this rift.");
        add("message." + RiftfallCore.MODID + ".rift_expired",
                "The rift beyond this frame is gone. Light it again with a new key.");
        add("message." + RiftfallCore.MODID + ".rift_roster_locked",
                "This rift's party has gone on ahead. Its roster closed behind them.");
        // Why a teleport did nothing, in the two places one can be refused. Both name the rift as
        // the thing refusing, because otherwise the item reads as broken rather than as disallowed.
        add("message." + RiftfallCore.MODID + ".teleport_denied_boundary",
                "Nothing carries you across a rift's edge.");
        add("message." + RiftfallCore.MODID + ".teleport_denied_inside",
                "The rift will not let you teleport.");
        // A held relic refused inside a rift.
        add("message." + RiftfallCore.MODID + ".relic_rift_silenced",
                "This relic falls silent inside a rift.");
        // The two ways a key can refuse, which until now it did in silence. The first does not claim
        // to know whether the ring has a gap in it or was never built, because the scan cannot tell
        // those apart; it is only sent at all when the block clicked was frame material.
        add("message." + RiftfallCore.MODID + ".key_no_frame",
                "This copper is not a closed frame. A rift needs an unbroken ring.");
        add("message." + RiftfallCore.MODID + ".key_already_lit",
                "This frame is already lit. Break the portal to clear it.");
        // Why a breaker can refuse: the tear is three blocks tall and it will not grow into a
        // ceiling. Says what to clear rather than that something went wrong.
        add("message." + RiftfallCore.MODID + ".breaker_no_room",
                "There is no room above the breaker. A tear needs 3 clear blocks.");
        add("message." + RiftfallCore.MODID + ".breaker_already_open",
                "This breaker is already open. Walk in, or let the rift close.");
        // The 30 s after any tear on a breaker closes. Says it will pass, and that the key was not taken.
        add("message." + RiftfallCore.MODID + ".breaker_cooling",
                "This breaker is still cooling. Your key was not used.");
        // The two ways the anchor can do nothing. They are separate because the answers are: one
        // means you are holding it in the wrong place, the other means this particular rift has no
        // record of where you came from and never will.
        add("message." + RiftfallCore.MODID + ".anchor_outside_rift",
                "The anchor only pulls from inside a rift.");
        add("message." + RiftfallCore.MODID + ".anchor_no_way_back",
                "Nothing here remembers where you came from.");
        // The two ways a flare can do nothing, and they are different problems: the first is
        // the wrong place entirely, the second is the right place with nothing in it worth
        // pulling. Neither spends the flare, so both have to say why.
        add("message." + RiftfallCore.MODID + ".flare_outside_rift",
                "Nothing out here answers a flare.");
        add("message." + RiftfallCore.MODID + ".flare_nobody_looking",
                "Nothing within sight of the flare is worth pulling.");
        // What a rift death says. The first line names the wear, or the player finds it later and
        // reads it as a bug. Gear comes back by itself once they are out (RiftAutoClaims).
        add("message." + RiftfallCore.MODID + ".claim_held",
                "The rift did not get your things, but dying in it wore them. They come back once you are out.");
        // The same death when the loot rule is on, which is the default: both halves, or the player
        // goes looking for the rest on the floor of a dungeon that no longer exists.
        add("message." + RiftfallCore.MODID + ".claim_loot_lost",
                "The rift kept what it gave you. What you carried in is worn, and comes back once you are out.");
        // At login, when the run they were away from wiped: the run's end first, then its cost.
        add("message." + RiftfallCore.MODID + ".claim_run_wiped",
                "The rift you were in wiped while you were away. It kept what it gave you, "
                        + "and what you carried in comes back worn.");
        add("message." + RiftfallCore.MODID + ".claim_waiting",
                "%s stacks of your gear are held for you: make room in your inventory and they come back.");
        add("message." + RiftfallCore.MODID + ".claim_auto", "Your held gear is back: %s stacks.");
        add("message." + RiftfallCore.MODID + ".claim_auto_partial",
                "%s stacks of your held gear are back. %s did not fit and are still held: make room and they follow.");
        add("message." + RiftfallCore.MODID + ".claim_nothing", "Nothing of yours is being held.");
        add("message." + RiftfallCore.MODID + ".claim_in_rift",
                "Your things are waiting outside a rift, not in one.");
        add("message." + RiftfallCore.MODID + ".claim_recovered", "Recovered %s stacks.");
        add("message." + RiftfallCore.MODID + ".claim_partial",
                "Recovered %s stacks: %s would not fit. Make room and claim again.");

        // Said only when a beaten arena had no way home to open, which is an authoring error
        // rather than a rule. It names the consequence AND that the clear still counts,
        // because being pulled out of a dungeon with no explanation reads as losing one.
        add("message." + RiftfallCore.MODID + ".run_no_way_home",
                "The way home did not open here. The rift is closing and your clear still counts.");
        add("message." + RiftfallCore.MODID + ".rift_forming", "The rift is forming...");
        // Boss gate countdown and an absent member's boss share: placeholders for the voice pass.
        add("message." + RiftfallCore.MODID + ".boss_gate_countdown", "The boss gate closes in %s...");
        add("message." + RiftfallCore.MODID + ".boss_share_sent", "Your share of the boss's reward is in your pack.");

        addParty();
        SkillCommand.ENGLISH.forEach(this::add);
        RespecCommand.ENGLISH.forEach(this::add);
        com.cyberday1.riftfall.skill.StatCommand.ENGLISH.forEach(this::add);
        StarterKits.ENGLISH.forEach(this::add);
        ToolLang.ENGLISH.forEach(this::add);
        com.cyberday1.riftfall.home.npc.ResidentLang.ENGLISH.forEach(this::add);
        com.cyberday1.riftfall.home.npc.jeweler.JewelerLang.ENGLISH.forEach(this::add);
        com.cyberday1.riftfall.home.npc.depth.DepthLang.ENGLISH.forEach(this::add);
        com.cyberday1.riftfall.home.npc.quarry.QuarryLang.ENGLISH.forEach(this::add);
        com.cyberday1.riftfall.home.npc.cartographer.CartographerLang.ENGLISH.forEach(this::add);
        com.cyberday1.riftfall.home.npc.ResidentPaths.ENGLISH.forEach(this::add);
        com.cyberday1.riftfall.home.npc.smith.SmithLang.ENGLISH.forEach(this::add);
        com.cyberday1.riftfall.home.npc.smith.ToolWorkLang.ENGLISH.forEach(this::add);
        com.cyberday1.riftfall.home.npc.smith.BulkScrapLang.ENGLISH.forEach(this::add);
        com.cyberday1.riftfall.tool.PickCommand.ENGLISH.forEach(this::add);
        com.cyberday1.riftfall.home.npc.depth.BoardLang.ENGLISH.forEach(this::add);
        com.cyberday1.riftfall.compat.viewer.ViewerLang.ENGLISH.forEach(this::add);
        HomeCommands.ENGLISH.forEach(this::add);
        com.cyberday1.riftfall.social.SocialService.ENGLISH.forEach(this::add);
        com.cyberday1.riftfall.home.HomeTileCommand.ENGLISH.forEach(this::add);
        GuideLang.ENGLISH.forEach(this::add);
        addInterface();
        addCodexNames();
        addAttributes();
        addProgression();
        addAffixNames();
        addSpells();

        // The Tr column's loot categories. Apotheosis builds both keys off the category's own id.
        add("loot_category." + RiftfallCore.MODID + ".trinket", "Trinket");
        add("loot_category." + RiftfallCore.MODID + ".trinket.plural", "Trinkets");
        add("loot_category." + RiftfallCore.MODID + ".necklace", "Necklace");
        add("loot_category." + RiftfallCore.MODID + ".necklace.plural", "Necklaces");
        add("loot_category." + RiftfallCore.MODID + ".gun", "Gun");
        add("loot_category." + RiftfallCore.MODID + ".gun.plural", "Guns");
    }

    /**
     * The designed affix pool's item-name words. Apotheosis reads {@code affix.<id>} for the prefix
     * and {@code affix.<id>.suffix} for the suffix, with the affix's full resource id &mdash; colon
     * and all &mdash; embedded in the key.
     */
    private void addAffixNames() {
        addAffix("tiered/armor", "Ironclad", "of the Bastion");
        addAffix("tiered/toughness", "Tempered", "of the Anvil");
        addAffix("tiered/health", "Stout", "of Long Life");
        addAffix("flat/mana", "Attuned", "of the Deep Well");
        addAffix("flat/trinket_mana", "Brimming", "of the Still Pool");
        addAffix("tiered/attack_damage", "Honed", "of the Edge");
        // Its own row rather than a category on the weapon one: the design prices a trinket's
        // attack damage at 1.0 against a weapon's 3.5, and one affix carries one values map.
        addAffix("tiered/trinket_attack_damage", "Wrathful", "of the Clenched Fist");
        addAffix("tiered/ranged_damage", "Weighted", "of the Heavy Draw");
        addAffix("flat/attack_damage_percent", "Brutal", "of Ruin");
        addAffix("flat/attack_speed", "Quickened", "of the Flurry");
        addAffix("flat/crit_chance", "Keen", "of the Weak Point");
        addAffix("flat/crit_damage", "Savage", "of the Deep Cut");
        addAffix("flat/life_steal", "Thirsting", "of the Leech");
        addAffix("flat/attack_knockback", "Concussive", "of the Hammerfall");
        addAffix("flat/reach", "Overlong", "of the Long Arm");
        addAffix("flat/true_damage", "Rending", "of the Unmaking");
        addAffix("flat/arrow_damage", "Barbed", "of the Broadhead");
        addAffix("flat/arrow_velocity", "Tapered", "of the Flat Arc");
        addAffix("flat/draw_speed", "Quickdraw", "of the Ready Hand");
        addAffix("flat/projectile_damage", "Driving", "of Hard Impact");
        addAffix("flat/resistance", "Warded", "of the Aegis");
        addAffix("flat/tenacity", "Unyielding", "of Defiance");
        addAffix("flat/guard", "Braced", "of the Bulwark");
        addAffix("flat/knockback_resistance", "Planted", "of the Anchor");
        addAffix("flat/dodge", "Weaving", "of the Near Miss");
        addAffix("flat/healing_received", "Mending", "of Recovery");
        addAffix("flat/overheal", "Bolstered", "of the Second Wind");
        addAffix("flat/movement_speed", "Fleet", "of the Long Stride");
        addAffix("flat/swim_speed", "Finned", "of the Undertow");
        addAffix("flat/casting_movespeed", "Striding", "of the Steady Cast");
        addAffix("flat/magic_power", "Runic", "of the Ley Line");
        // The only affix whose own text has to be written: a multi_attr names its own desc key, and
        // the two rolls are one stat to the player even though they are two attributes.
        add("affix." + RiftfallCore.MODID + ":flat/magic_power.desc",
                "Spell power is raised by %s under Iron's Spellbooks and %s under Spell Engine.");
        addAffix("flat/mana_regen", "Replenishing", "of the Wellspring");
        addAffix("flat/summon_damage", "Commanding", "of the Risen Host");
        // School power is one design row and nine files: no affix type in the pack picks its
        // attribute at roll time, so a school per file is the only shape. They are mutually
        // exclusive, so an item carries at most one of these nine names.
        addAffix("flat/school_power/blood", "Sanguine", "of the Red Rite");
        addAffix("flat/school_power/eldritch", "Maddening", "of the Whispering Dark");
        addAffix("flat/school_power/ender", "Voidtouched", "of the Far Step");
        addAffix("flat/school_power/evocation", "Arcanist's", "of the Spoken Word");
        addAffix("flat/school_power/fire", "Smoldering", "of the Ember Crown");
        addAffix("flat/school_power/holy", "Consecrated", "of the Dawnlight");
        addAffix("flat/school_power/ice", "Rimebound", "of the Hoarfrost");
        addAffix("flat/school_power/lightning", "Galvanic", "of the Thunderhead");
        addAffix("flat/school_power/nature", "Verdant", "of the Green Bough");
        // School resist is the armour-side twin of the row above, same shape and same reason.
        // Each name answers its school's power name, so a set reads as the counter to a caster.
        addAffix("flat/school_resist/blood", "Bloodless", "of the Closed Vein");
        addAffix("flat/school_resist/eldritch", "Lucid", "of the Unbroken Mind");
        addAffix("flat/school_resist/ender", "Tethered", "of the Fixed Point");
        addAffix("flat/school_resist/evocation", "Unspoken", "of the Sundered Sigil");
        addAffix("flat/school_resist/fire", "Unscorched", "of the Quenching");
        addAffix("flat/school_resist/holy", "Profane", "of the Turned Prayer");
        addAffix("flat/school_resist/ice", "Hearthbound", "of the Banked Coals");
        addAffix("flat/school_resist/lightning", "Grounded", "of the Iron Rod");
        addAffix("flat/school_resist/nature", "Barren", "of the Salted Earth");
        addAffix("flat/luck", "Auspicious", "of Good Omen");
        addAffix("flat/experience", "Insightful", "of the Quick Study");
        addAffix("tiered/scavenging", "Gleaning", "of the Full Haul");
        // The relic places' half-value twins read as their trinket row does.
        for (String twin : RELIC_AFFIX_TWINS) {
            String[] words = affixWords.get(twin);
            addAffix(twin.replaceFirst("/", "/relic/"), words[0], words[1]);
        }

        // The tooltip line Riftfall draws for each of the above: the affix's name, then the stat it
        // is granting. Apotheosis names effect affixes and never attribute ones, so an all-attribute
        // pool has to say this itself.
        add("tooltip." + RiftfallCore.MODID + ".affix_line", "%s: %s");
        // The header over a relic's fixed lines.
        add("tooltip." + RiftfallCore.MODID + ".relic", "Relic");
    }

    /**
     * The authored abilities' names and tooltips. Spell Engine reads {@code spell.<ns>.<path>.name} and
     * {@code .description}, and fills the {@code {token}}s from the ability file, so no number is here.
     */
    private void addSpells() {
        // Riftfall pages for the remaining pack-bound abilities. Their engine tooltips can describe
        // charged item casts, whereas the skill bar invokes a single paid release.
        spell("arcanist_arcane_shield", "Arcane Shield",
                "Grants you a damage shield that absorbs {shield}, and divine protection for {effect_duration} "
                + "seconds. Protection strength scales with your power and has a cap.");
        spell("monk_aerial_strike", "Aerial Strike",
                "Delivers a ground slam for {damage} physical damage in a {impact_range}-block impact radius. This slam does not carry you toward an enemy.");
        spell("monk_ground_smash", "Ground Smash",
                "Smashes the ground, dealing {damage} physical damage in a {impact_range}-block impact radius.");
        spell("phantom_weakening_strike", "Dread Shout",
                "Deals {damage} damage nearby and weakens enemy attacks for {effect_duration} seconds. The weakening effect has a target-health limit and stacks to a cap.");
        spell("ranger_shadow_step", "Shadow Step",
                "Blinks behind your target and grants Shadow Step for {effect_duration} seconds.");
        spell("ranger_shock_trap", "Shock Burst",
                "Releases a nearby stunning burst for {effect_duration} seconds. It affects enemies under the health limit shown below; it does not leave a persistent trap.");
        spell("ranger_smoke_veil", "Smoke Veil",
                "Enters stealth for {effect_duration} seconds. Acting or taking damage breaks stealth.");
        spell("ranger_wind_step", "Tailwind",
                "Grants movement speed and knockback resistance for {effect_duration} seconds. You control the movement; this is a speed buff.");
        spell("shifter_blood_frenzy", "Blood Frenzy",
                "Enters a battle trance. Melee hits during the trance build a stacking power buff; adding stacks does not refresh its remaining duration.");
        spell("shifter_wolf_snare", "Wolf Snare",
                "Throws a net for {damage} damage and pins eligible targets for {effect_duration} seconds. The pin has the target-health limit shown below.");
        spell("summoner_blink", "Blink",
                "Blinks up to {teleport_distance} blocks forward, stopping at a safe destination.");
        spell("gunner_explosive_round", "Explosive Round",
                "Fires a round that bursts on impact, dealing {damage} physical damage to enemies within "
                + "{impact_range} blocks.");
        spell("gunner_shrapnel_blast", "Shrapnel Blast",
                "Fires a shell that bursts into shrapnel, dealing {damage} physical damage to enemies within "
                + "{impact_range} blocks.");
        spell("gunner_firestorm_shell", "Firestorm Shell",
                "Lobs a heavy shell that erupts on impact, dealing {damage} physical damage to enemies "
                + "within {impact_range} blocks.");
        spell("gunner_wind_shot", "Wind Shot",
                "A concussive shot that deals {damage} physical damage and hurls the target back.");
        spell("ranger_tornado_arrow", "Tornado Arrow",
                "Looses an arrow that bursts into a whirlwind, dealing {damage} physical damage to enemies "
                + "within {impact_range} blocks and throwing them into the air.");
        spell("ranger_corrupt_shot", "Corrupt Shot",
                "A corrupting shot that deals {damage} physical damage and envenoms the target.");
        spell("phantom_mark_for_death", "Mark for Death",
                "Marks the target for {effect_duration_1} seconds, shattering its armor and making it glow.");
        spell("phantom_poison_blade", "Poison Blade",
                "Strikes with a venomed blade for {damage} physical damage and envenoms the target.");
        spell("phantom_phase_trap", "Phase Trap",
                "Opens a void snare at the target for {cloud_duration} seconds, rooting enemies within "
                + "{cloud_radius} blocks in place.");
        spell("phantom_void_pull", "Void Pull",
                "Tears at the void around you, dealing {damage} physical damage to nearby enemies and "
                + "dragging them toward you.");
        spell("phantom_shadow_plague", "Shadow Plague",
                "Strikes the target with shadow for {damage} physical damage and envenoms it.");
        spell("shifter_primal_roar", "Primal Roar",
                "A primal roar that draws the attention of nearby enemies and frightens them for "
                + "{effect_duration} seconds.");
        spell("shifter_guardian_roar", "Guardian Roar",
                "A guardian's roar that draws the attention of nearby enemies and grants you a damage shield that "
                + "absorbs {shield}.");
        spell("shifter_boulder_toss", "Boulder Toss",
                "Hurls a boulder that deals {damage} physical damage to enemies within {impact_range} blocks "
                + "of where it lands.");
        spell("shifter_predatory_mark", "Predatory Mark",
                "Marks the target for {effect_duration_1} seconds, shattering its armor and making it glow.");
        spell("shifter_venom_strike", "Venom Strike",
                "A venomous strike for {damage} physical damage that envenoms the target.");
        spell("shifter_toxic_spit", "Toxic Spit",
                "Spits a toxic glob at the target for {damage} physical damage and envenoms it.");
        spell("reaper_blood_mark", "Blood Mark",
                "Marks the target for {effect_duration_1} seconds, shattering its armor and making it glow.");
        spell("reaper_soul_burn", "Soul Burn",
                "Sears the target's soul for {damage} physical damage and sets it burning.");
        spell("reaper_gravity_well", "Gravity Well",
                "Opens a gravity well at the target for {cloud_duration} seconds, dealing {damage} physical "
                + "damage every second to enemies within {cloud_radius} blocks and pulling them inward.");
        spell("monk_palm_strike", "Palm Strike",
                "A driving palm strike that deals {damage} physical damage and knocks the target back.");
        spell("monk_iron_body", "Iron Body",
                "Hardens your body, dealing {damage} physical damage to nearby enemies and granting you "
                + "Resistance for {effect_duration} seconds.");
        spell("monk_sweeping_kick", "Sweeping Kick",
                "A sweeping kick that deals {damage} physical damage to nearby enemies and slows them for "
                + "{effect_duration} seconds.");
        spell("monk_burning_palm", "Burning Palm",
                "A searing palm strike for {damage} physical damage that sets the target burning.");
        spell("monk_blazing_kick", "Blazing Kick",
                "A blazing kick for {damage} physical damage that sets the target burning.");
        spell("monk_iron_wall", "Iron Wall",
                "Braces like a wall, granting Resistance II for {effect_duration} seconds and a damage shield that "
                + "absorbs {shield}.");
        spell("monk_storm_kick", "Storm Kick",
                "A storm-charged kick that deals {damage} physical damage to enemies within {impact_range} "
                + "blocks of the target and slows them for {effect_duration} seconds.");
        spell("monk_gale_punch", "Gale Punch",
                "A gale-force punch that deals {damage} physical damage and blasts the target back.");
        spell("monk_cyclone_smash", "Cyclone Smash",
                "Smashes the ground in a cyclone, dealing {damage} physical damage to nearby enemies and "
                + "throwing them into the air.");
        spell("monk_poison_palm", "Poison Palm",
                "A poisoned palm strike for {damage} physical damage that envenoms the target and poisons it for "
                + "{effect_duration} seconds.");
        spell("sword_immortal_ether_slash", "Ether Slash",
                "Releases a wave of sword energy that passes through enemies, dealing {damage} physical "
                + "damage to each.");
        spell("sword_immortal_radiant_slash", "Radiant Slash",
                "Releases a radiant wave of sword energy that passes through enemies, dealing {damage} "
                + "physical damage to each.");
        spell("sword_immortal_blade_tornado", "Blade Tornado",
                "Spins a tornado of blades around you for {cloud_duration} seconds, dealing {damage} "
                + "physical damage every second to enemies within {cloud_radius} blocks.");
        spell("sword_immortal_gale_strike", "Gale Strike",
                "A gale-driven strike that deals {damage} physical damage and hurls the target back.");
        spell("sword_immortal_volt_pierce", "Volt Pierce",
                "A piercing bolt of sword energy that passes through enemies, dealing {damage} physical "
                + "damage to each and shattering their armor for {effect_duration} seconds.");
        spell("phantom_drain_life", "Drain Life",
                "Drains the target for {damage} physical damage and heals you for {heal}.");
        spell("reaper_soul_harvest", "Soul Harvest",
                "Reaps the souls around you, dealing {damage} physical damage to each nearby enemy and "
                + "healing you for {heal} per enemy struck.");
        spell("reaper_leech_burst", "Leech Burst",
                "Bursts with leeching force, dealing {damage} physical damage to each nearby enemy and "
                + "healing you for {heal} per enemy struck.");
        spell("reaper_drain_channel", "Drain Channel",
                "Drains the target's life for {damage} physical damage and heals you for {heal}.");
        spell("monk_soothing_strike", "Soothing Strike",
                "A centring strike that deals {damage} physical damage and heals you for {heal}.");
        spell("monk_harmony_flow", "Harmony Flow",
                "Heals you and nearby allies for {heal} and attunes them for {effect_duration} seconds, "
                + "raising healing received by 15%%.");
        spell("ranger_poison_arrow", "Poison Arrow",
                "Looses three arrows in quick succession, each dealing {damage} physical damage and envenoming the "
                + "target.");
        addMonkSpells();
        addPaladinSpells();
        addTacticianSpells();
        addClericSpells();
        addGunnerSpells();
        addRangerSpells();
        addSummonerSpells();
        addShifterSpells();
        addPhantomSpells();
        addReaperSpells();
        addArcanistSpells();
        addSwordImmortalSpells();
        addTricksterSpells();
        addSpiritRehomeSpells();
        addPassives();
    }

    /** The Gunner batch (2026-10-10). STR, so every hit is physical. */
    private void addGunnerSpells() {
        spell("gunner_precision_shot", "Precision Shot",
                "A precise shot for {damage} physical damage.");
        spell("gunner_burst_fire", "Burst Fire",
                "Fires three rounds in quick succession, each dealing {damage} physical damage.");
        spell("gunner_suppression_fire", "Suppression Fire",
                "Sprays a cone of suppressing fire ahead, slowing enemies in it for {effect_duration} seconds.");
        spell("gunner_grapple_roll", "Grapple Roll",
                "Hooks terrain up to 12 blocks away and reels you toward it, including elevated surfaces. Sneak to release. Evades for {effect_duration} seconds, dodging most attacks. Requires a clear anchor.");
        spell("gunner_piercing_shot", "Piercing Shot",
                "A shot that pierces every enemy in its line for {damage} physical damage and leaves them "
                + "vulnerable for {effect_duration} seconds, raising the damage they take. The vulnerability is "
                + "Marked (Hunter's Mark), so a Ranger's bonuses against marked targets apply to it too.");
        spell("gunner_frag_grenade", "Frag Grenade",
                "Throws a grenade that bursts, dealing {damage} physical damage to enemies within "
                + "{impact_range} blocks.");
        spell("gunner_overcharge", "Overcharge",
                "Overcharges your weapon, granting Strength for {effect_duration} seconds. Strength also raises "
                + "the damage of a TaCZ gun's bullets.");
        spell("gunner_flashbang", "Flashbang",
                "Throws a flashbang that deals {damage} physical damage to enemies within {impact_range} blocks "
                + "and blinds them for {effect_duration} seconds, so they lose sight of their target.");
        spell("gunner_tactical_reload", "Tactical Reload",
                "A shot for {damage} physical damage that reloads everything: every other skill's cooldown "
                + "ends, except your ultimate's, and the TaCZ gun in your hand refills its magazine from your ammo.");
        spell("gunner_mega_bomb", "Mega Bomb",
                "Lobs a heavy bomb that deals {damage} physical damage to enemies within {impact_range} blocks.");
        spell("gunner_mine_cluster", "Mine Cluster",
                "Scatters mines at the target for {cloud_duration} seconds, dealing {damage} physical damage "
                + "every second to enemies within {cloud_radius} blocks.");
        spell("gunner_rocket_barrage", "Rocket Barrage",
                "Fires four rockets in quick succession, each dealing {damage} physical damage.");
        spell("gunner_concussion_grenade", "Concussion Grenade",
                "Throws a concussion grenade that deals {damage} physical damage to enemies within "
                + "{impact_range} blocks and stuns them for {effect_duration} second.");
        spell("gunner_detonation_chain", "Detonation Chain",
                "Sets off a chain of detonations at the target, dealing {damage} physical damage to enemies "
                + "within {impact_range} blocks.");
        spell("gunner_blast_shield", "Blast Shield",
                "Blasts nearby enemies away and grants you a damage shield that absorbs {shield}.");
        spell("gunner_seismic_charge", "Seismic Charge",
                "Charges to the target and knocks it away.");
        spell("gunner_demolition_rush", "Demolition Rush",
                "Rushes to the target and strikes it for {damage} physical damage, knocking it back.");
        spell("gunner_siege_barrage", "Siege Barrage",
                "A rail shot that pierces every enemy in its line for {damage} physical damage and shatters their "
                + "armor for {effect_duration} seconds (Shield Reave).");
        spell("gunner_deadeye_shot", "Deadeye Shot",
                "A long-range shot for {damage} physical damage.");
        spell("gunner_mark_target", "Mark Target",
                "Marks the target for 8 seconds, raising the damage it takes and revealing it. The mark is "
                + "Marked (Hunter's Mark), so a Ranger's bonuses against marked targets apply to it too.");
        spell("gunner_focus_aim", "Focus Aim",
                "Focuses your aim for {effect_duration} seconds, raising critical strike chance by 10%% and "
                + "tightening a TaCZ gun's spread by 40%%.");
        spell("gunner_headshot", "Headshot",
                "A headshot for {damage} physical damage.");
        spell("gunner_piercing_line", "Piercing Line",
                "A shot that pierces every enemy in its line for {damage} physical damage.");
        spell("gunner_camouflage_field", "Camouflage Field",
                "Camouflages you and nearby allies for 8 seconds, hiding you from enemies.");
        spell("gunner_quick_scope", "Quick Scope",
                "A quick shot for {damage} physical damage.");
        spell("gunner_target_sweep", "Target Sweep",
                "Sweeps the area ahead with fire, dealing {damage} physical damage to every enemy in the cone.");
        spell("gunner_tactical_roll", "Tactical Roll",
                "Rolls clear, dealing {damage} physical damage to nearby enemies and evading for "
                + "{effect_duration} seconds, dodging most attacks.");
        spell("gunner_one_shot_doctrine", "One Shot Doctrine",
                "A rail shot that pierces every enemy in its line for {damage} physical damage and shatters their "
                + "armor for {effect_duration} seconds (Shield Reave).");
        spell("gunner_deploy_turret", "Deploy Turret",
                "Deploys a turret at the target for {cloud_duration} seconds, dealing {damage} physical damage "
                + "every second to enemies within {cloud_radius} blocks.");
        spell("gunner_flame_turret", "Flame Turret",
                "Deploys a flame turret at the target for {cloud_duration} seconds, dealing {damage} physical "
                + "damage every second to enemies within {cloud_radius} blocks.");
        spell("gunner_shield_generator", "Shield Generator",
                "Deploys a shield generator that grants you and every ally within 6 blocks a damage shield that "
                + "absorbs {shield}.");
        spell("gunner_mine_wall", "Mine Wall",
                "Lays a wall of mines at the target for {cloud_duration} seconds, dealing {damage} physical damage "
                + "every second to enemies within {cloud_radius} blocks. You gain a damage shield that absorbs "
                + "{shield}.");
        spell("gunner_shock_net", "Shock Net",
                "Fires a shock net for {damage} physical damage, rooting the target for {effect_duration} "
                + "seconds.");
        spell("gunner_repair_drone", "Repair Drone",
                "Sends out a repair drone, healing you and nearby allies for {heal}.");
        spell("gunner_emp_pulse", "EMP Pulse",
                "Releases a pulse that deals {damage} physical damage to nearby enemies and stuns their "
                + "casting for {effect_duration} seconds.");
        spell("gunner_barricade", "Barricade",
                "Slams down a barricade, dealing {damage} physical damage to nearby enemies and knocking them "
                + "away.");
        spell("gunner_overclock", "Overclock",
                "Deals {damage} physical damage to nearby enemies and overclocks you and nearby allies for "
                + "{effect_duration} seconds: +12%% ability haste, and TaCZ guns fire and reload 12%% faster.");
        spell("gunner_rocket_platform", "Rocket Platform",
                "Deploys a rocket platform at the target for {cloud_duration} seconds, dealing {damage} "
                + "physical damage every second to enemies within {cloud_radius} blocks.");
        spell("gunner_fortress_protocol", "Fortress Protocol",
                "A rail shot that pierces every enemy in its line for {damage} physical damage and shatters their "
                + "armor for {effect_duration} seconds (Shield Reave).");
        spell("gunner_twin_shot", "Twin Shot",
                "Fires two rounds in quick succession, each dealing {damage} physical damage.");
        spell("gunner_dash_shot", "Dash Shot",
                "Dashes forward in an instant.");
        spell("gunner_ricochet_round", "Ricochet Round",
                "A round that ricochets on impact, dealing {damage} physical damage to enemies within "
                + "{impact_range} blocks of the target.");
        spell("gunner_spin_barrage", "Spin Barrage",
                "Spins and fires in every direction, dealing {damage} physical damage to every nearby enemy.");
        spell("gunner_quick_reload", "Quick Reload",
                "Deals {damage} physical damage to nearby enemies and reloads you and nearby allies for "
                + "{effect_duration} seconds: +12%% ability haste, and TaCZ guns fire and reload 12%% faster. "
                + "Every TaCZ gun in your hands and theirs refills its magazine from its holder's ammo.");
        spell("gunner_flash_kick", "Flash Kick",
                "Dashes to the target and kicks it for {damage} physical damage, knocking it back.");
        spell("gunner_bullet_storm", "Bullet Storm",
                "Rains bullets on the target for {cloud_duration} seconds, dealing {damage} physical damage "
                + "every second to enemies within {cloud_radius} blocks.");
        spell("gunner_grapple_pull", "Grapple Pull",
                "Fires a grapple for {damage} physical damage that drags the target toward you.");
        spell("gunner_piercing_burst", "Piercing Burst",
                "A burst that pierces every enemy in its line for {damage} physical damage.");
        spell("gunner_overdrive", "Overdrive",
                "Deals {damage} physical damage to nearby enemies and drives you and nearby allies into "
                + "overdrive for 8 seconds: Strength and Speed. Strength also raises the damage of a TaCZ gun's "
                + "bullets.");
        spell("gunner_storm_of_lead", "Storm of Lead",
                "A rail shot that pierces every enemy in its line for {damage} physical damage and shatters their "
                + "armor for {effect_duration} seconds (Shield Reave).");
        spell("gunner_marking_shot", "Marking Shot",
                "Marks the target for 8 seconds, raising the damage it takes and revealing it. The mark is "
                + "Marked (Hunter's Mark), so a Ranger's bonuses against marked targets apply to it too.");
        spell("gunner_suppression_zone", "Suppression Zone",
                "Suppresses the area at the target for {cloud_duration} seconds, dealing {damage} physical "
                + "damage every second to enemies within {cloud_radius} blocks and slowing them.");
        spell("gunner_armor_breaker", "Armor Breaker",
                "A round for {damage} physical damage that breaks the target's armor for {effect_duration} "
                + "seconds, raising the damage it takes. The break is Marked (Hunter's Mark), so a Ranger's "
                + "bonuses against marked targets apply to it too.");
        spell("gunner_flash_barrage", "Flash Barrage",
                "Dashes to the target and fires into it for {damage} physical damage.");
        spell("gunner_rally_fire", "Rally Fire",
                "Deals {damage} physical damage to nearby enemies and rallies you and nearby allies with "
                + "Strength for {effect_duration} seconds. Strength also raises the damage of a TaCZ gun's "
                + "bullets.");
        spell("gunner_disrupt_round", "Disrupt Round",
                "A disrupting round for {damage} physical damage that stuns the target's casting for "
                + "{effect_duration} seconds.");
        spell("gunner_target_lock", "Target Lock",
                "Locks onto the target for 12 seconds, raising the damage it takes and revealing it. The lock "
                + "is Marked (Hunter's Mark), so a Ranger's bonuses against marked targets apply to it too.");
        spell("gunner_smoke_screen", "Smoke Screen",
                "Lays a smoke screen that hides you and nearby allies for 5 seconds.");
        spell("gunner_coordinated_strike", "Coordinated Strike",
                "A coordinated strike that deals {damage} physical damage to enemies within {impact_range} "
                + "blocks of the target.");
        spell("gunner_tactical_retreat", "Tactical Retreat",
                "Dashes forward and gains Speed for {effect_duration} seconds.");
        spell("gunner_war_doctrine", "War Doctrine",
                "A rail shot that pierces every enemy in its line for {damage} physical damage and shatters their "
                + "armor for {effect_duration} seconds (Shield Reave).");
    }

    /** The Cleric batch (2026-10-10). The three INT rows, the divine school. */
    /** The Ranger batch (2026-10-10): STR, so physical. The four bound rogues rows keep the pack's text. */
    private void addRangerSpells() {
        spell("ranger_quick_shot", "Quick Shot",
                "A quick shot for {damage} physical damage.");
        spell("ranger_power_arrow", "Power Arrow",
                "A heavy arrow that pierces every enemy in its line for {damage} physical damage.");
        spell("ranger_multi_shot", "Multi Shot",
                "Fans a volley of arrows ahead, dealing {damage} physical damage to enemies in the cone.");
        spell("ranger_camouflage", "Camouflage",
                "Blends you into your surroundings for 8 seconds, hiding you from enemies.");
        spell("ranger_trap_snare", "Trap Snare",
                "Sets snares at the target for {cloud_duration} seconds, rooting enemies within {cloud_radius} "
                + "blocks.");
        spell("ranger_explosive_arrow", "Explosive Arrow",
                "An arrow that explodes on impact, dealing {damage} physical damage to enemies within "
                + "{impact_range} blocks.");
        spell("ranger_evasion_roll", "Evasion Roll",
                "Rolls clear, dealing {damage} physical damage to nearby enemies and evading for "
                + "{effect_duration} seconds, dodging most attacks.");
        spell("ranger_hunters_mark", "Hunter's Mark",
                "Marks the target for {effect_duration_1} seconds (Hunter's Mark), raising the damage it takes and "
                + "revealing it.");
        spell("ranger_deadly_shot", "Deadly Shot",
                "A deadly shot for {damage} physical damage.");
        spell("ranger_piercing_rain", "Piercing Rain",
                "Rains arrows on the target for {cloud_duration} seconds, dealing {damage} physical damage every "
                + "second to enemies within {cloud_radius} blocks.");
        spell("ranger_focus_aim", "Focus Aim",
                "Focuses your aim for {effect_duration} seconds, raising critical strike chance by 10%%.");
        spell("ranger_snipe_burst", "Snipe Burst",
                "A long-range snipe for {damage} physical damage.");
        spell("ranger_rapid_fire", "Rapid Fire",
                "Fires three arrows in quick succession, each dealing {damage} physical damage.");
        spell("ranger_wind_arrow", "Wind Arrow",
                "A gale-driven arrow that deals {damage} physical damage and hurls the target back.");
        spell("ranger_eagle_eye", "Eagle Eye",
                "Deals {damage} physical damage to nearby enemies and sharpens your eye and nearby allies' for "
                + "{effect_duration} seconds, raising critical strike damage by 20%%.");
        spell("ranger_precision_volley", "Precision Volley",
                "Fires five arrows in quick succession, each dealing {damage} physical damage.");
        spell("ranger_mark_execution", "Mark Execution",
                "Marks the target for execution for 12 seconds (Hunter's Mark), raising the damage it takes and "
                + "revealing it.");
        spell("ranger_silent_kill", "Silent Kill",
                "A silent shot for {damage} physical damage.");
        spell("ranger_arrow_of_oblivion", "Arrow of Oblivion",
                "A piercing arrow that deals {damage} physical damage to every enemy in its line, more to those "
                + "below 30%% health.");
        spell("ranger_summon_wolf", "Summon Wolf",
                "Summons two spirit wolves that fight at your side, empowered by your attack damage. They last "
                + "{summon_duration} seconds.");
        spell("ranger_pack_assault", "Pack Assault",
                "Sets the pack on the target, dealing {damage} physical damage to enemies within {impact_range} "
                + "blocks.");
        spell("ranger_beast_heal", "Beast Heal",
                "Heals you and every ally within 8 blocks for {heal}.");
        spell("ranger_feral_charge", "Feral Charge",
                "Charges to the target and drives it away from you.");
        spell("ranger_howl_buff", "Howl Buff",
                "Howls, dealing {damage} physical damage to nearby enemies and granting you and nearby allies "
                + "Strength for {effect_duration} seconds.");
        spell("ranger_claw_swipe", "Claw Swipe",
                "Rakes the enemies ahead for {damage} physical damage.");
        spell("ranger_predator_mark", "Predator Mark",
                "Marks the target for {effect_duration_1} seconds, shattering its armor and making it glow.");
        spell("ranger_savage_leap", "Savage Leap",
                "Leaps to the target.");
        spell("ranger_beast_guard", "Beast Guard",
                "Grants you and every ally within 6 blocks a damage shield that absorbs {shield}.");
        spell("ranger_spirit_roar", "Spirit Roar",
                "Roars, granting you and nearby allies Strength and Speed for 8 seconds.");
        spell("ranger_wild_dominion", "Wild Dominion",
                "A piercing arrow that deals {damage} physical damage to every enemy in its line, more to those "
                + "below 30%% health.");
        spell("ranger_spike_trap", "Spike Trap",
                "Springs a spike trap at the target, dealing {damage} physical damage to enemies within "
                + "{impact_range} blocks and rooting them for {effect_duration} seconds.");
        spell("ranger_fire_trap", "Fire Trap",
                "Sets a fire trap at the target for {cloud_duration} seconds, dealing {damage} physical damage "
                + "every second to enemies within {cloud_radius} blocks.");
        spell("ranger_poison_trap", "Poison Trap",
                "Sets a poison trap at the target for {cloud_duration} seconds, dealing {damage} physical damage "
                + "every second to enemies within {cloud_radius} blocks.");
        spell("ranger_snare_field", "Snare Field",
                "Lays snares at the target for {cloud_duration} seconds, slowing enemies within {cloud_radius} "
                + "blocks.");
        spell("ranger_explosive_net", "Explosive Net",
                "Throws an explosive net, dealing {damage} physical damage to enemies within {impact_range} "
                + "blocks and rooting them for {effect_duration} seconds.");
        spell("ranger_trap_barrage", "Trap Barrage",
                "Scatters traps at the target for {cloud_duration} seconds, dealing {damage} physical damage every "
                + "second to enemies within {cloud_radius} blocks and slowing them.");
        spell("ranger_mirage_trap", "Mirage Trap",
                "Bursts a mirage around you, dealing {damage} physical damage to nearby enemies and veiling you "
                + "and nearby allies for 4 seconds.");
        spell("ranger_collapse_trap", "Collapse Trap",
                "Collapses the ground at the target, dealing {damage} physical damage to enemies within "
                + "{impact_range} blocks and slowing them for {effect_duration} seconds.");
        spell("ranger_ambush_trap", "Ambush Trap",
                "Springs an ambush at the target, dealing {damage} physical damage to enemies within "
                + "{impact_range} blocks and stunning them for {effect_duration} seconds.");
        spell("ranger_forest_of_death", "Forest of Death",
                "A piercing arrow that deals {damage} physical damage to every enemy in its line, more to those "
                + "below 30%% health, and leaves them bleeding.");
        spell("ranger_gale_dash", "Gale Dash",
                "Dashes forward on the wind.");
        spell("ranger_wind_barrage", "Wind Barrage",
                "Fires eight arrows in quick succession, each dealing {damage} physical damage.");
        spell("ranger_aerial_shot", "Aerial Shot",
                "A rising shot that deals {damage} physical damage and throws the target into the air.");
        spell("ranger_storm_leap", "Storm Leap",
                "Leaps forward in a crackle of storm.");
        spell("ranger_swift_strike", "Swift Strike",
                "A swift shot for {damage} physical damage.");
        spell("ranger_haste_field", "Haste Field",
                "Raises a gale around you for {cloud_duration} seconds, dealing {damage} physical damage every "
                + "second to enemies within {cloud_radius} blocks and slowing them, while you and allies in it "
                + "gain Speed.");
        spell("ranger_sky_volley", "Sky Volley",
                "Looses a volley skyward that rains on the target for {cloud_duration} seconds, dealing {damage} "
                + "physical damage every second to enemies within {cloud_radius} blocks.");
        spell("ranger_slipstream", "Slipstream",
                "Slips through the air, dealing {damage} physical damage to nearby enemies and raising your dodge "
                + "chance by 8%% for {effect_duration} seconds.");
        spell("ranger_rapid_escape", "Rapid Escape",
                "Dashes forward and gains Speed for {effect_duration} seconds.");
        spell("ranger_tempest_hunt", "Tempest Hunt",
                "A piercing arrow that deals {damage} physical damage to every enemy in its line, more to those "
                + "below 30%% health.");
        spell("ranger_dark_arrow", "Dark Arrow",
                "A shadowed arrow for {damage} physical damage.");
        spell("ranger_night_trap", "Night Trap",
                "Sets a night trap at the target for {cloud_duration} seconds, dealing {damage} physical damage "
                + "every second to enemies within {cloud_radius} blocks and rooting them.");
        spell("ranger_bleed_arrow", "Bleed Arrow",
                "A barbed arrow that deals {damage} physical damage and leaves the target bleeding.");
        spell("ranger_void_volley", "Void Volley",
                "Fires five void arrows in quick succession, each dealing {damage} physical damage.");
        spell("ranger_silence_shot", "Silence Shot",
                "A choking shot that stuns the target's casting for {effect_duration} seconds.");
        spell("ranger_shadow_burst", "Shadow Burst",
                "Bursts with shadow, dealing {damage} physical damage to nearby enemies.");
        spell("ranger_phantom_strike", "Phantom Strike",
                "Flickers behind the target and shoots it for {damage} physical damage.");
        spell("ranger_eclipse_barrage", "Eclipse Barrage",
                "A piercing arrow that deals {damage} physical damage to every enemy in its line, more to those "
                + "below 30%% health.");
    }

    private void addClericSpells() {
        spell("cleric_holy_smite", "Holy Smite",
                "Smites the target for {damage} holy damage.");
        spell("cleric_radiant_burst", "Radiant Burst",
                "Bursts with radiance, dealing {damage} holy damage to nearby enemies and weakening them for "
                + "{effect_duration} seconds, lowering the damage they deal.");
        spell("cleric_smite_strike", "Smite Strike",
                "A smiting strike for {damage} holy damage.");
    }

    /** The Tactician batch (2026-10-10). INT rows are the command school, Command Strike physical. */
    private void addTacticianSpells() {
        spell("tactician_command_strike", "Command Strike",
                "A commanding strike for {damage} physical damage.");
        spell("tactician_formation_wall", "Formation Wall",
                "Forms a wall, dealing {damage} damage to nearby enemies and granting you and nearby allies "
                + "Resistance for {effect_duration} seconds. You gain a damage shield that absorbs {shield}.");
        spell("tactician_rally_cry", "Rally Cry",
                "Rallies you and nearby allies with Strength for {effect_duration} seconds, raising the damage "
                + "you deal.");
        spell("tactician_shield_advance", "Shield Advance",
                "Advances behind your shield, knocking nearby enemies away and granting you a damage shield that "
                + "absorbs {shield}.");
        spell("tactician_tactical_mark", "Tactical Mark",
                "Marks the target for 8 seconds, raising the damage it takes and revealing it. "
                + "The mark is Marked (Hunter's Mark), so a Ranger's bonuses against marked targets apply to it "
                + "too.");
        spell("tactician_reinforce_armor", "Reinforce Armor",
                "Deals {damage} damage to nearby enemies and reinforces you and nearby allies with Resistance "
                + "for {effect_duration} seconds.");
        spell("tactician_shockwave_banner", "Shockwave Banner",
                "Plants a banner at the target for {cloud_duration} seconds that slows enemies within "
                + "{cloud_radius} blocks.");
        spell("tactician_battlefield_shift", "Battlefield Shift",
                "Shifts the battlefield at the target for {cloud_duration} seconds, dealing {damage} damage "
                + "every second to enemies within {cloud_radius} blocks.");
        spell("tactician_hold_the_line", "Hold the Line",
                "Calls to hold the line, drawing the attention of nearby enemies.");
        spell("tactician_strategic_retreat", "Strategic Retreat",
                "Dashes forward and gains Speed for {effect_duration} seconds.");
        spell("tactician_crushing_order", "Crushing Order",
                "A crushing order that deals {damage} damage to the target.");
        spell("tactician_siege_formation", "Siege Formation",
                "Deals {damage} damage to nearby enemies and grants you and nearby allies Strength for "
                + "{effect_duration} seconds.");
        spell("tactician_banner_of_fury", "Banner of Fury",
                "Raises a banner of fury, granting you and nearby allies Strength for {effect_duration} seconds.");
        spell("tactician_war_cry", "War Cry",
                "A war cry that deals {damage} damage to nearby enemies and grants you and nearby allies "
                + "Strength for {effect_duration} seconds.");
        spell("tactician_shield_break", "Shield Break",
                "Breaks the target's guard for {effect_duration} seconds, raising the damage it takes, and grants you "
                + "a damage shield that absorbs {shield}. The break is Marked (Hunter's Mark), so a Ranger's bonuses "
                + "against marked targets apply to it too.");
        spell("tactician_command_rush", "Command Rush",
                "Rushes to the target and knocks it away.");
        spell("tactician_battle_standard", "Battle Standard",
                "Plants a battle standard, dealing {damage} damage to nearby enemies and granting you and "
                + "nearby allies Strength and Resistance for 8 seconds.");
        spell("tactician_tactical_slam", "Tactical Slam",
                "Slams the ground, dealing {damage} damage to every nearby enemy.");
        spell("tactician_iron_guard", "Iron Guard",
                "Grants you an iron damage shield that absorbs {shield}.");
        spell("tactician_war_surge", "War Surge",
                "Surges you and nearby allies with Strength and Speed for 8 seconds.");
        spell("tactician_march_of_dominion", "March of Dominion",
                "A dominating shout in a cone ahead: enemies take {damage} damage and are weakened, lowering "
                + "the damage they deal; you and allies in it gain Strength and Speed.");
        spell("tactician_fortress_stance", "Fortress Stance",
                "Takes a fortress stance, gaining Resistance for {effect_duration} seconds.");
        spell("tactician_guardian_wall", "Guardian Wall",
                "Raises a guardian wall that grants you and every ally within 6 blocks a damage shield that absorbs "
                + "{shield}.");
        spell("tactician_shield_barrage", "Shield Barrage",
                "Grants you a damage shield that absorbs {shield}.");
        spell("tactician_defensive_rally", "Defensive Rally",
                "Rallies the defense, granting you and every ally within 8 blocks a damage shield that absorbs "
                + "{shield}.");
        spell("tactician_iron_formation", "Iron Formation",
                "Within 5 blocks, deals {damage} damage to enemies and grants you and allies Resistance for "
                + "{effect_duration} seconds.");
        spell("tactician_taunt_pulse", "Taunt Pulse",
                "A taunting pulse that draws the attention of nearby enemies.");
        spell("tactician_cover_line", "Cover Line",
                "Covers the line, granting you and every ally within 8 blocks a damage shield that absorbs {shield}.");
        spell("tactician_armor_boost", "Armor Boost",
                "Within 4 blocks, deals {damage} damage to enemies and grants you and allies Resistance for "
                + "{effect_duration} seconds.");
        spell("tactician_shock_shield", "Shock Shield",
                "Grants you a damage shield that absorbs {shield} and, for 8 seconds, reflects 20%% of the damage "
                + "enemies deal you back at them.");
        spell("tactician_hold_ground", "Hold Ground",
                "Deals {damage} damage to nearby enemies and wards you and nearby allies for "
                + "{effect_duration} seconds: a harmful effect is lifted, and the next ones are refused.");
        spell("tactician_citadel_command", "Citadel Command",
                "A commanding shout in a cone ahead: enemies take {damage} damage and are weakened; you and "
                + "allies in it gain Resistance II.");
        spell("tactician_precision_orders", "Precision Orders",
                "Deals {damage} damage to nearby enemies and sharpens you and nearby allies for "
                + "{effect_duration} seconds, raising critical strike chance by 10%%.");
        spell("tactician_weak_point_mark", "Weak Point Mark",
                "Marks the target's weak point for 8 seconds, raising the damage it takes and "
                + "revealing it. The mark is Marked (Hunter's Mark), so a Ranger's bonuses against marked "
                + "targets apply to it too.");
        spell("tactician_coordinated_assault", "Coordinated Assault",
                "A coordinated assault that deals {damage} damage to enemies within {impact_range} blocks of "
                + "the target.");
        spell("tactician_flank_command", "Flank Command",
                "Rushes to the target, striking it for {damage} damage on arrival.");
        spell("tactician_tactical_burst", "Tactical Burst",
                "A tactical burst for {damage} damage.");
        spell("tactician_speed_banner", "Speed Banner",
                "Deals {damage} damage to nearby enemies and hastens you and nearby allies for "
                + "8 seconds: +12%% ability haste and Speed.");
        spell("tactician_critical_focus", "Critical Focus",
                "Focuses for {effect_duration} seconds, raising your critical strike damage by 30%%.");
        spell("tactician_battlefield_control", "Battlefield Control",
                "Controls the field at the target for {cloud_duration} seconds, slowing enemies within "
                + "{cloud_radius} blocks.");
        spell("tactician_reposition", "Reposition",
                "Repositions you forward in an instant.");
        spell("tactician_morale_surge", "Morale Surge",
                "Lifts you and nearby allies for 8 seconds: +12%% ability haste and Strength.");
        spell("tactician_perfect_execution", "Perfect Execution",
                "A shout in a cone ahead: enemies take {damage} damage and are marked, raising the damage they "
                + "take; you and allies in it gain +10%% critical strike chance and Strength. The mark is Marked "
                + "(Hunter's Mark), so a Ranger's bonuses against marked targets apply to it too.");
        spell("tactician_deploy_cannon", "Deploy Cannon",
                "Deploys a cannon at the target for {cloud_duration} seconds, dealing {damage} damage every "
                + "second to enemies within {cloud_radius} blocks.");
        spell("tactician_explosive_volley", "Explosive Volley",
                "Fires four explosive shots in quick succession, each dealing {damage} damage.");
        spell("tactician_shield_platform", "Shield Platform",
                "Raises a shield platform that grants you and every ally within 6 blocks a damage shield that absorbs "
                + "{shield}.");
        spell("tactician_siege_wall", "Siege Wall",
                "Raises a siege wall that grants you and every ally within 6 blocks a damage shield that absorbs "
                + "{shield}, and you Resistance for {effect_duration} seconds.");
        spell("tactician_mortar_strike", "Mortar Strike",
                "Calls a mortar strike, dealing {damage} damage to enemies within {impact_range} blocks of the "
                + "target.");
        spell("tactician_reinforce_turret", "Reinforce Turret",
                "Reinforces you and nearby allies with Strength and Resistance for 8 seconds.");
        spell("tactician_shock_barrage", "Shock Barrage",
                "Fires four shock bolts in quick succession, each dealing {damage} damage.");
        spell("tactician_battlefield_repair", "Battlefield Repair",
                "Repairs the field, healing you and nearby allies for {heal}.");
        spell("tactician_iron_barricade", "Iron Barricade",
                "Grants you a damage shield that absorbs {shield}.");
        spell("tactician_overclock_siege", "Overclock Siege",
                "Deals {damage} damage to nearby enemies and overclocks you and nearby allies for "
                + "{effect_duration} seconds: +12%% ability haste.");
        spell("tactician_iron_rain_protocol", "Iron Rain Protocol",
                "Calls iron rain in a cone ahead: enemies take {damage} damage and are slowed; you and allies "
                + "in it gain Strength.");
        spell("tactician_heroic_call", "Heroic Call",
                "A heroic call that deals {damage} damage to nearby enemies and grants you and nearby allies "
                + "Strength for {effect_duration} seconds.");
        spell("tactician_healing_banner", "Healing Banner",
                "Raises a healing banner, healing you and nearby allies for {heal}.");
        spell("tactician_resist_aura", "Resist Aura",
                "Within 6 blocks, deals {damage} damage to enemies and grants you and allies Resistance for "
                + "{effect_duration} seconds.");
        spell("tactician_morale_heal", "Morale Heal",
                "Heals you for {heal}.");
        spell("tactician_spirit_boost", "Spirit Boost",
                "Boosts you and nearby allies for {effect_duration} seconds, restoring 4 more mana a second.");
        spell("tactician_protective_formation", "Protective Formation",
                "Forms up, granting you and every ally within 8 blocks a damage shield that absorbs {shield}.");
        spell("tactician_encouraging_shout", "Encouraging Shout",
                "An encouraging shout that deals {damage} damage to nearby enemies and removes every harmful "
                + "effect from you and nearby allies.");
        spell("tactician_renewal_wave", "Renewal Wave",
                "A wave of renewal that heals you and nearby allies for {heal}.");
        spell("tactician_unity_shield", "Unity Shield",
                "Grants you and every ally within 8 blocks a damage shield that absorbs {shield}.");
        spell("tactician_battle_hymn", "Battle Hymn",
                "A battle hymn that deals {damage} damage to nearby enemies and grants you and nearby allies "
                + "Strength, Resistance and Speed for 8 seconds.");
        spell("tactician_anthem_of_victory", "Anthem of Victory",
                "An anthem in a cone ahead: enemies take {damage} damage and are weakened; you and allies in it "
                + "gain Strength, Resistance and +12%% ability haste.");
    }

    /** The Paladin batch (2026-10-10). CON rows are holy, STR rows physical. */
    private void addPaladinSpells() {
        spell("paladin_shield_slam", "Shield Slam",
                "Slams the target for {damage} physical damage, stunning it for {effect_duration} seconds and "
                + "granting you a damage shield that absorbs {shield}.");
        spell("paladin_smite", "Smite",
                "Smites the target for {damage} physical damage.");
        spell("paladin_divine_charge", "Divine Charge",
                "Charges to the target and strikes it for {damage} physical damage, knocking it back.");
        spell("paladin_consecration", "Consecration",
                "Consecrates the ground around you for {cloud_duration} seconds, dealing {damage} physical "
                + "damage every second to enemies within {cloud_radius} blocks.");
        spell("paladin_taunting_roar", "Taunting Roar",
                "A roar that draws the attention of nearby enemies.");
        spell("paladin_judgment", "Judgment",
                "Passes judgment on the target for {damage} physical damage and leaves it vulnerable for "
                + "{effect_duration} seconds, raising the damage it takes. The vulnerability is Marked "
                + "(Hunter's Mark), so a Ranger's bonuses against marked targets apply to it too.");
        spell("paladin_radiant_bash", "Radiant Bash",
                "A radiant bash that deals {damage} physical damage to nearby enemies and weakens them for "
                + "{effect_duration} seconds, lowering the damage they deal.");
        spell("paladin_shield_fortress", "Shield Fortress",
                "Becomes a fortress, granting Resistance II for {effect_duration} seconds and a damage shield that "
                + "absorbs {shield}.");
        spell("paladin_iron_bash", "Iron Bash",
                "Bashes every nearby enemy, stunning them for {effect_duration} seconds.");
        spell("paladin_holy_barrier", "Holy Barrier",
                "Raises a holy barrier that grants you and every ally within 6 blocks a damage shield that absorbs "
                + "{shield}.");
        spell("paladin_guardians_call", "Guardians Call",
                "Calls out as a guardian, drawing the attention of nearby enemies and granting you a damage shield "
                + "that absorbs {shield}.");
        spell("paladin_fortify_armor", "Fortify Armor",
                "Deals {damage} holy damage to nearby enemies, then grants you Resistance for {effect_duration} "
                + "seconds and a damage shield that absorbs {shield}.");
        spell("paladin_sacred_ground", "Sacred Ground",
                "Hallows the ground, dealing {damage} holy damage to nearby enemies and granting you and "
                + "nearby allies Resistance for {effect_duration} seconds.");
        spell("paladin_shield_rush", "Shield Rush",
                "Rushes to the target and knocks it away, granting you a damage shield that absorbs {shield}.");
        spell("paladin_light_guard", "Light Guard",
                "Grants you and every ally within 8 blocks a damage shield of light that absorbs {shield}.");
        spell("paladin_bulwark_slam", "Bulwark Slam",
                "Slams the ground, knocking nearby enemies away and granting you a damage shield that absorbs "
                + "{shield}.");
        spell("paladin_bastion_of_light", "Bastion of Light",
                "Becomes a bastion of light: you and every ally within 8 blocks gain a damage shield that absorbs "
                + "{shield}, and you gain Resistance II for {effect_duration} seconds.");
        spell("paladin_holy_strike", "Holy Strike",
                "A holy strike for {damage} physical damage.");
        spell("paladin_zealous_charge", "Zealous Charge",
                "Charges to the target and smashes it away.");
        spell("paladin_cleaving_light", "Cleaving Light",
                "Cleaves with light, dealing {damage} holy damage to every nearby enemy.");
        spell("paladin_burning_judgment", "Burning Judgment",
                "A burning judgment for {damage} holy damage that sets the target burning.");
        spell("paladin_faith_shield", "Faith Shield",
                "Grants you a damage shield of faith that absorbs {shield}.");
        spell("paladin_divine_combo", "Divine Combo",
                "A divine chain of blows for {damage} holy damage.");
        spell("paladin_wrath_burst", "Wrath Burst",
                "Bursts with holy wrath, dealing {damage} holy damage to every nearby enemy.");
        spell("paladin_armor_break", "Armor Break",
                "A breaking blow for {damage} holy damage that sunders the target for {effect_duration} "
                + "seconds, raising the damage it takes. The sunder is Marked (Hunter's Mark), so a Ranger's "
                + "bonuses against marked targets apply to it too.");
        spell("paladin_holy_roar", "Holy Roar",
                "A holy roar that frightens nearby enemies for {effect_duration} seconds.");
        spell("paladin_shield_bash", "Shield Bash",
                "Bashes the target, stunning it for {effect_duration} seconds and granting you a damage shield that "
                + "absorbs {shield}.");
        spell("paladin_chains_of_light", "Chains of Light",
                "Binds the target in chains of light, rooting it for {effect_duration} seconds.");
        spell("paladin_judgment_flame", "Judgment Flame",
                "Calls down judgment's flame, dealing {damage} holy damage to enemies within {impact_range} "
                + "blocks of the target and setting them burning.");
        spell("paladin_holy_bind", "Holy Bind",
                "Binds the target with holy power, stunning it for {effect_duration} seconds.");
        spell("paladin_condemn", "Condemn",
                "Condemns the target for {damage} holy damage.");
        spell("paladin_purging_strike", "Purging Strike",
                "A purging strike for {damage} holy damage that strips every beneficial effect from the "
                + "target.");
        spell("paladin_sacred_whip", "Sacred Whip",
                "Lashes the target for {damage} holy damage and pulls it toward you.");
        spell("paladin_smite_wave", "Smite Wave",
                "Releases a wave of holy force that passes through enemies, dealing {damage} holy damage to "
                + "each.");
        spell("paladin_light_cage", "Light Cage",
                "Cages the target's area in light for {cloud_duration} seconds, dealing {damage} holy damage "
                + "every second to enemies within {cloud_radius} blocks and rooting them.");
        spell("paladin_holy_brand", "Holy Brand",
                "Brands the target for {damage} holy damage, leaving it vulnerable for {effect_duration} "
                + "seconds and raising the damage it takes. The brand is Marked (Hunter's Mark), so a Ranger's "
                + "bonuses against marked targets apply to it too.");
        spell("paladin_radiant_lash", "Radiant Lash",
                "A radiant lash for {damage} holy damage that sets the target burning.");
        spell("paladin_light_bless", "Light Bless",
                "Blesses you and nearby allies with Strength for {effect_duration} seconds, raising the "
                + "damage you deal.");
        spell("paladin_healing_aura", "Healing Aura",
                "Heals you and every ally within 6 blocks for {heal}.");
        spell("paladin_radiant_shield", "Radiant Shield",
                "Grants you a radiant damage shield that absorbs {shield}.");
        spell("paladin_purifying_wave", "Purifying Wave",
                "A purifying wave that deals {damage} holy damage to nearby enemies and removes every harmful "
                + "effect from you and nearby allies.");
        spell("paladin_spirit_heal", "Spirit Heal",
                "Heals you for {heal}.");
        spell("paladin_faith_ward", "Faith Ward",
                "Grants you Resistance for {effect_duration} seconds and a damage shield that absorbs {shield}.");
        spell("paladin_sacred_pulse", "Sacred Pulse",
                "A sacred pulse that heals you and nearby allies for {heal}.");
        spell("paladin_guardian_light", "Guardian Light",
                "A guardian light grants you and every ally within 8 blocks a damage shield that absorbs {shield}.");
        spell("paladin_holy_step", "Holy Step",
                "Steps forward in a flash of light.");
        spell("paladin_divine_protection", "Divine Protection",
                "Grants you a damage shield of divine protection that absorbs {shield}.");
        spell("paladin_execution_strike", "Execution Strike",
                "An executing strike for {damage} holy damage, harder still against a target below 30%% "
                + "health.");
        spell("paladin_sentence", "Sentence",
                "Sentences the target for {damage} holy damage, raising the damage it takes for "
                + "{effect_duration} seconds. The sentence is Marked (Hunter's Mark), so a Ranger's bonuses "
                + "against marked targets apply to it too.");
        spell("paladin_chains_of_verdict", "Chains of Verdict",
                "Binds the target in chains, rooting it for {effect_duration} seconds.");
        spell("paladin_smite_barrage", "Smite Barrage",
                "Looses eight bolts of holy force in quick succession, each dealing {damage} holy damage.");
        spell("paladin_armor_crush", "Armor Crush",
                "A crushing blow for {damage} holy damage that sunders the target for {effect_duration} "
                + "seconds, raising the damage it takes. The sunder is Marked (Hunter's Mark), so a Ranger's "
                + "bonuses against marked targets apply to it too.");
        spell("paladin_holy_cleave", "Holy Cleave",
                "A holy cleave that deals {damage} holy damage to every nearby enemy.");
        spell("paladin_condemn_wave", "Condemn Wave",
                "A wave of condemnation that knocks nearby enemies away.");
        spell("paladin_faith_break", "Faith Break",
                "Breaks the target's faith, weakening it for {effect_duration} seconds and lowering the "
                + "damage it deals.");
        spell("paladin_radiant_slash", "Radiant Slash",
                "Releases a radiant slash that passes through enemies, dealing {damage} holy damage to each.");
        spell("paladin_judgment_shield", "Judgment Shield",
                "Grants you a damage shield of judgment that absorbs {shield}.");
        // The four "Unbreakable Vanguard" ultimates: a charge line, a pool sized by enemies struck.
        spell("paladin_crusade_of_light", "Crusade of Light",
                "Charges down a line of up to 8 enemies ahead, striking each for {damage} holy damage and knocking it "
                + "into the air. You gain a damage shield that absorbs {shield_per_enemy} for every enemy struck, up to "
                + "6 enemies.");
        spell("paladin_judgment_day", "Judgment Day",
                "Charges down a line of up to 8 enemies ahead, striking each for {damage} holy damage, knocking it "
                + "into the air and stunning it for {effect_duration} seconds. You gain a damage shield that absorbs "
                + "{shield_per_enemy} for every enemy struck, up to 6 enemies.");
        spell("paladin_beacon_of_hope", "Beacon of Hope",
                "Charges down a line of up to 8 enemies ahead, knocking each into the air, and heals you and every "
                + "ally in the line for {heal}. You and every ally within 8 blocks gain a damage shield that absorbs "
                + "{shield_per_enemy} for every enemy struck, up to 6 enemies.");
        spell("paladin_final_verdict", "Final Verdict",
                "Charges down a line of up to 8 enemies ahead, striking each for {damage} holy damage, harder still "
                + "against a target below 30%% health, and knocking it into the air. You gain a damage shield that "
                + "absorbs {shield_per_enemy} for every enemy struck, up to 6 enemies.");
    }

    /** The Monk batch (2026-10-09). Numbers come from the ability file through Spell Engine's tokens. */
    private void addMonkSpells() {
        spell("monk_jab_combo", "Jab Combo",
                "A rapid flurry of jabs for {damage} physical damage.");
        spell("monk_rising_kick", "Rising Kick",
                "A rising kick that deals {damage} physical damage and launches the target into the air.");
        spell("monk_dash_strike", "Dash Strike",
                "Dashes to the target and strikes it for {damage} physical damage.");
        spell("monk_pressure_point", "Pressure Point",
                "Strikes a pressure point for {damage} physical damage, stunning the target for "
                + "{effect_duration} seconds.");
        spell("monk_dragon_strike", "Dragon Strike",
                "A blazing dragon strike for {damage} physical damage in a single hit.");
        spell("monk_crushing_combo", "Crushing Combo",
                "A crushing chain of blows for {damage} physical damage.");
        spell("monk_sky_uppercut", "Sky Uppercut",
                "An uppercut that deals {damage} physical damage and launches the target into the air.");
        spell("monk_power_slam", "Power Slam",
                "A heavy slam for {damage} physical damage that weakens the target for {effect_duration} "
                + "seconds, lowering the damage it deals.");
        spell("monk_dragon_roar", "Dragon Roar",
                "A dragon's roar that frightens nearby enemies for {effect_duration} seconds.");
        spell("monk_execution_blow", "Execution Blow",
                "A finishing blow for {damage} physical damage, harder still against a target below 30%% "
                + "health.");
        spell("monk_combo_flurry", "Combo Flurry",
                "A relentless flurry of strikes for {damage} physical damage.");
        spell("monk_stone_fists", "Stone Fists",
                "A stone-hard blow for {damage} physical damage that sunders the target for "
                + "{effect_duration} seconds, raising the damage it takes. The sunder is Marked (Hunter's "
                + "Mark), so a Ranger's bonuses against marked targets apply to it too.");
        spell("monk_guardian_taunt", "Guardian Taunt",
                "Plants your feet, drawing the attention of nearby enemies and granting you a damage shield that "
                + "absorbs {shield}.");
        spell("monk_shock_punch", "Shock Punch",
                "A jolting punch for {damage} physical damage that stuns the target for {effect_duration} "
                + "seconds.");
        spell("monk_defense_roll", "Defense Roll",
                "Rolls through nearby enemies for {damage} physical damage, then evades almost every attack "
                + "for {effect_duration} seconds.");
        spell("monk_shield_bash", "Shield Bash",
                "A bracing bash that deals {damage} physical damage, knocks the target back and grants you a damage "
                + "shield that absorbs {shield}.");
        spell("monk_blessed_kick", "Blessed Kick",
                "A blessed kick for {damage} physical damage that weakens the target for {effect_duration} "
                + "seconds, lowering the damage it deals.");
        spell("monk_lightning_dash", "Lightning Dash",
                "Dashes to the target and strikes it for {damage} physical damage.");
        spell("monk_whirlwind_combo", "Whirlwind Combo",
                "A whirling combo that deals {damage} physical damage to every nearby enemy.");
        spell("monk_speed_burst", "Speed Burst",
                "Bursts into motion, dealing {damage} physical damage to nearby enemies, then quickens your "
                + "stride and grants 12 ability haste for {effect_duration_1} seconds.");
        spell("monk_wind_step", "Wind Step",
                "Steps forward on the wind, shaking off every slow and root on you.");
        spell("monk_tempest_rush", "Tempest Rush",
                "Rushes to the target and strikes it for {damage} physical damage.");
        spell("monk_flash_combo", "Flash Combo",
                "Dashes to the target and unleashes a combo for {damage} physical damage.");
        spell("monk_dark_jab", "Dark Jab",
                "A shadowed jab for {damage} physical damage in a single hit.");
        spell("monk_smoke_step", "Smoke Step",
                "Vanishes into smoke for {effect_duration_1} seconds, moving faster while unseen.");
        spell("monk_corrupt_combo", "Corrupt Combo",
                "A corrupting combo for {damage} physical damage that weakens the target for "
                + "{effect_duration} seconds, lowering the damage it deals.");
        spell("monk_flow_stance", "Flow Stance",
                "Settles into a flowing stance for {effect_duration_1} seconds, gaining 12 ability haste and "
                + "8%% dodge chance.");
        spell("monk_life_drain_hit", "Life Drain Hit",
                "A draining strike that deals {damage} physical damage and heals you for {heal}.");
        spell("monk_execution_kick", "Execution Kick",
                "A merciless kick for {damage} physical damage that envenoms the target, harder still against a "
                + "poisoned target.");
    }

    /** The Summoner batch (2026-10-10): INT, the summoning school, arcane damage. Blink keeps the pack's text. */
    private void addSummonerSpells() {
        spell("summoner_summon_imp", "Summon Imp",
                "Summons a fire imp that hurls fireballs at your enemies, empowered by your attack damage. It lasts "
                + "{summon_duration} seconds.");
        spell("summoner_summon_guardian", "Summon Guardian",
                "Summons a stone guardian that slams and pelts your enemies, empowered by your attack damage. It "
                + "lasts {summon_duration} seconds.");
        spell("summoner_summon_sprite", "Summon Sprite",
                "Calls a sprite that follows you and heals one wounded ally at a time. It can be attacked. It lasts "
                + "{summon_duration} seconds.");
        spell("summoner_command_assault", "Command Assault",
                "Strikes the target for {damage} arcane damage and sets your summons on it.");
        spell("summoner_spirit_shield", "Spirit Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("summoner_arcane_lash", "Arcane Lash",
                "Lashes the target for {damage} arcane damage.");
        spell("summoner_life_transfer", "Life Transfer",
                "Heals you and every ally within 8 blocks for {heal}.");
        spell("summoner_recall_minions", "Recall Minions",
                "Calls your summons back to your side and makes them drop their targets.");
        spell("summoner_empower_summons", "Empower Summons",
                "Grants you, nearby allies and your summons Strength for {effect_duration} seconds.");
        spell("summoner_summon_bear", "Summon Bear",
                "Summons a bear that mauls your enemies, empowered by your attack damage. It lasts {summon_duration} "
                + "seconds.");
        spell("summoner_summon_wolf", "Summon Wolf",
                "Summons two spirit wolves that fight at your side, empowered by your attack damage. They last "
                + "{summon_duration} seconds.");
        spell("summoner_pack_assault", "Pack Assault",
                "Strikes the target for {damage} arcane damage and sends your pack after it.");
        spell("summoner_feral_roar", "Feral Roar",
                "Roars, dealing {damage} arcane damage to nearby enemies and granting you and nearby allies "
                + "Strength for {effect_duration} seconds.");
        spell("summoner_beast_heal", "Beast Heal",
                "Heals you and every ally within 8 blocks for {heal}.");
        spell("summoner_savage_charge", "Savage Charge",
                "Charges to the target.");
        spell("summoner_predator_mark", "Predator Mark",
                "Marks the target for {effect_duration_1} seconds, shattering its armor and making it glow.");
        spell("summoner_wild_guard", "Wild Guard",
                "Grants you and every ally within 6 blocks a damage shield that absorbs {shield}.");
        spell("summoner_spirit_howl", "Spirit Howl",
                "Howls, granting you, nearby allies and your summons Strength and Speed for 8 seconds.");
        spell("summoner_stampede", "Stampede",
                "A stampede that deals {damage} arcane damage to enemies ahead and drives them back.");
        spell("summoner_primal_dominion", "Primal Dominion",
                "Looses a beast swarm on the target for {cloud_duration} seconds, dealing {damage} arcane damage "
                + "every second to enemies within {cloud_radius} blocks and leaving them vulnerable.");
        spell("summoner_summon_demon_knight", "Summon Demon Knight",
                "Summons a demon knight with a sword and shield. It fights up close, with more health and armor than "
                + "your ranged summons. It lasts {summon_duration} seconds.");
        spell("summoner_hellfire_blast", "Hellfire Blast",
                "Hellfire that deals {damage} arcane damage to enemies within {impact_range} blocks of the "
                + "target.");
        spell("summoner_infernal_shield", "Infernal Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("summoner_demonic_command", "Demonic Command",
                "Grants you, nearby allies and your summons Strength II for {effect_duration} seconds.");
        spell("summoner_flame_portal", "Flame Portal",
                "Steps through a portal of flame to a point ahead.");
        spell("summoner_corruption_aura", "Corruption Aura",
                "Wreathes you in corruption for {cloud_duration} seconds, dealing {damage} arcane damage every "
                + "second to enemies within {cloud_radius} blocks.");
        spell("summoner_chaos_bolt", "Chaos Bolt",
                "A bolt of chaos for {damage} arcane damage.");
        spell("summoner_shadow_rift", "Shadow Rift",
                "Tears a rift that deals {damage} arcane damage to enemies within {impact_range} blocks of the "
                + "target.");
        spell("summoner_soul_burn", "Soul Burn",
                "Burns the target's soul for {damage} arcane damage and sets it burning.");
        spell("summoner_infernal_surge", "Infernal Surge",
                "Grants you Strength II for {effect_duration} seconds.");
        spell("summoner_legion_of_hell", "Legion of Hell",
                "Opens a legion's gate on the target for {cloud_duration} seconds, dealing {damage} arcane damage "
                + "every second to enemies within {cloud_radius} blocks and leaving them vulnerable.");
        spell("summoner_summon_fire_elemental", "Summon Fire Elemental",
                "Summons a fire elemental that hurls fireballs at your enemies, empowered by your attack damage. It "
                + "lasts {summon_duration} seconds.");
        spell("summoner_summon_ice_elemental", "Summon Ice Elemental",
                "Summons a frost elemental that fights at your side, empowered by your attack damage. It lasts "
                + "{summon_duration} seconds.");
        spell("summoner_summon_storm_elemental", "Summon Storm Elemental",
                "Calls a storm elemental that follows you and fires lightning-shaped bolts dealing arcane damage. It "
                + "lasts {summon_duration} seconds.");
        spell("summoner_elemental_burst", "Elemental Burst",
                "A burst that deals {damage} arcane damage to enemies within {impact_range} blocks of the target.");
        spell("summoner_elemental_shield", "Elemental Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("summoner_elemental_surge", "Elemental Surge",
                "Charges you for {effect_duration} seconds, raising critical strike chance by 10%%.");
        spell("summoner_elemental_chains", "Elemental Chains",
                "Binds the target in place for {effect_duration} seconds.");
        spell("summoner_elemental_wave", "Elemental Wave",
                "A wave that pierces every enemy in its line for {damage} arcane damage.");
        spell("summoner_elemental_empower", "Elemental Empower",
                "Grants you, nearby allies and your summons Strength and Resistance for {effect_duration_1} "
                + "seconds.");
        spell("summoner_elemental_collapse", "Elemental Collapse",
                "Collapses the elements on the target for {damage} arcane damage.");
        spell("summoner_elemental_convergence", "Elemental Convergence",
                "Converges the elements on the target for {cloud_duration} seconds, dealing {damage} arcane damage "
                + "every second to enemies within {cloud_radius} blocks and leaving them vulnerable.");
        spell("summoner_summon_skeleton", "Summon Skeleton",
                "Raises a skeleton archer that follows you and shoots enemies. Its damage scales with your attack "
                + "damage. Its health scales with your maximum health. It lasts {summon_duration} seconds.");
        spell("summoner_summon_ghoul", "Summon Ghoul",
                "Raises a ghoul that mauls your enemies, empowered by your attack damage. It lasts {summon_duration} "
                + "seconds.");
        spell("summoner_summon_lich", "Summon Lich",
                "Raises a lich that follows you and fires frost bolts from range. It lasts {summon_duration} seconds.");
        spell("summoner_dark_blast", "Dark Blast",
                "A dark blast that deals {damage} arcane damage to enemies within {impact_range} blocks of the "
                + "target.");
        spell("summoner_corpse_explosion", "Corpse Explosion",
                "An explosion that deals {damage} arcane damage to enemies within {impact_range} blocks of the "
                + "target.");
        spell("summoner_bone_shield", "Bone Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("summoner_raise_army", "Raise Army",
                "Raises three skeleton archers that follow you and shoot enemies. Each has its own health and attack "
                + "cooldown. All count toward your summon limit. They last {summon_duration} seconds.");
        spell("summoner_soul_drain", "Soul Drain",
                "Drains the target for {damage} arcane damage and heals you for {heal}.");
        spell("summoner_death_mark", "Death Mark",
                "Marks the target for {effect_duration_1} seconds, raising the damage it takes and making it glow.");
        spell("summoner_plague_wave", "Plague Wave",
                "Spreads a plague on the target for {cloud_duration} seconds, dealing {damage} arcane damage "
                + "every second to enemies within {cloud_radius} blocks.");
        spell("summoner_undying_legion", "Undying Legion",
                "Raises an undying legion on the target for {cloud_duration} seconds, dealing {damage} arcane "
                + "damage every second to enemies within {cloud_radius} blocks and leaving them vulnerable.");
        spell("summoner_summon_ancestral_spirit", "Summon Ancestral Spirit",
                "Calls an ancestral spirit that follows you and fires soul bolts dealing arcane damage. It lasts "
                + "{summon_duration} seconds.");
        spell("summoner_spirit_heal", "Spirit Heal",
                "Heals you and every ally within 8 blocks for {heal}.");
        spell("summoner_spirit_strike", "Spirit Strike",
                "Slips behind the target as a spirit.");
        spell("summoner_spirit_ward", "Spirit Ward",
                "Grants you and every ally within 8 blocks a damage shield that absorbs {shield}.");
        spell("summoner_spirit_surge", "Spirit Surge",
                "Hastens your abilities for {effect_duration} seconds.");
        spell("summoner_soul_bind", "Soul Bind",
                "Binds the target's soul in place for {effect_duration} seconds.");
        spell("summoner_ethereal_wave", "Ethereal Wave",
                "A wave that pierces every enemy in its line for {damage} arcane damage.");
        spell("summoner_phantom_guard", "Phantom Guard",
                "Grants you and every ally within 6 blocks a damage shield that absorbs {shield}.");
        spell("summoner_spirit_bless", "Spirit Bless",
                "Blesses you and nearby allies with Strength and Resistance for {effect_duration_1} seconds.");
        spell("summoner_choir_of_ancients", "Choir of Ancients",
                "Calls a choir of ancients around you for {cloud_duration} seconds: each second, you and allies in "
                + "it gain Strength and Resistance and enemies in it are left vulnerable.");
    }

    /** The Shifter batch (2026-10-10): STR, so every hit is physical. Blood Frenzy and Wolf Snare keep the pack's text. */
    private void addShifterSpells() {
        spell("shifter_feral_slash", "Feral Slash",
                "A feral slash for {damage} physical damage.");
        spell("shifter_bestial_charge", "Bestial Charge",
                "Charges to the target and stuns it for {effect_duration} seconds.");
        spell("shifter_claw_flurry", "Claw Flurry",
                "A flurry of claws for {damage} physical damage in a single hit.");
        spell("shifter_hardened_hide", "Hardened Hide",
                "Hardens your hide, dealing {damage} physical damage to nearby enemies and granting you and "
                + "nearby allies Resistance for {effect_duration} seconds.");
        spell("shifter_rend_flesh", "Rend Flesh",
                "Rends the target's flesh for {damage} physical damage and leaves it bleeding.");
        spell("shifter_leap_strike", "Leap Strike",
                "Leaps among the enemies within {impact_range} blocks of the target and throws them into "
                + "the air.");
        spell("shifter_wild_regeneration", "Wild Regeneration",
                "Calls up a wild spring that heals wounded allies near it, empowered by your attack damage. It lasts "
                + "{summon_duration} seconds.");
        spell("shifter_savage_swipe", "Savage Swipe",
                "A wide swipe that deals {damage} physical damage to enemies ahead.");
        spell("shifter_aspect_shift", "Aspect Shift",
                "Shifts your aspect, dealing {damage} physical damage to nearby enemies. Bear Aspect raises "
                + "your health and threat, Tiger Aspect your attack damage and critical chance; taking one "
                + "ends the other.");
        spell("shifter_maul_slam", "Maul Slam",
                "A mauling slam for {damage} physical damage.");
        spell("shifter_iron_fur", "Iron Fur",
                "Bristles with iron fur, dealing {damage} physical damage to nearby enemies and granting "
                + "you and nearby allies Resistance II for {effect_duration} seconds.");
        spell("shifter_ground_quake", "Ground Quake",
                "Shakes the ground, dealing {damage} physical damage to nearby enemies and slowing them for "
                + "{effect_duration} seconds.");
        spell("shifter_thick_hide", "Thick Hide",
                "Grants you a damage shield that absorbs {shield}.");
        spell("shifter_crushing_grip", "Crushing Grip",
                "A crushing grip for {damage} physical damage.");
        spell("shifter_earth_stomp", "Earth Stomp",
                "Stomps the earth, slowing nearby enemies for {effect_duration} seconds.");
        spell("shifter_vital_surge", "Vital Surge",
                "Heals you and every ally within 4 blocks for {heal}.");
        spell("shifter_defensive_charge", "Defensive Charge",
                "Charges to the target.");
        spell("shifter_colossus_of_the_wild", "Colossus of the Wild",
                "Leaps on the target and strikes it for {damage} physical damage, then leaps on to 5 more "
                + "enemies nearby, one at a time: each leap goes to the enemy nearest where you look.");
        spell("shifter_razor_pounce", "Razor Pounce",
                "Pounces to the target.");
        spell("shifter_savage_combo", "Savage Combo",
                "A savage combo for {damage} physical damage in a single hit.");
        spell("shifter_quick_rend", "Quick Rend",
                "Rends the target for {damage} physical damage and leaves it bleeding.");
        spell("shifter_whirl_claws", "Whirl Claws",
                "Whirls with claws out, dealing {damage} physical damage to nearby enemies.");
        spell("shifter_shadow_dash", "Shadow Dash",
                "Dashes forward through the shadows.");
        spell("shifter_lethal_leap", "Lethal Leap",
                "Leaps to the target.");
        spell("shifter_finishing_swipe", "Finishing Swipe",
                "A finishing swipe for {damage} physical damage, harder still against a target below 30%% "
                + "health.");
        spell("shifter_rapid_assault", "Rapid Assault",
                "A rapid assault for {damage} physical damage in a single hit.");
        spell("shifter_apex_predator", "Apex Predator",
                "Leaps on the target and strikes it for {damage} physical damage, then leaps on to 5 more "
                + "enemies nearby, one at a time: each leap goes to the enemy nearest where you look.");
        spell("shifter_pack_charge", "Pack Charge",
                "Charges to the target.");
        spell("shifter_howl_of_fury", "Howl of Fury",
                "Howls, granting you and nearby allies Strength for {effect_duration} seconds.");
        spell("shifter_spirit_wolves", "Spirit Wolves",
                "Summons two spirit wolves that fight at your side, empowered by your attack damage. They last "
                + "{summon_duration} seconds.");
        spell("shifter_hunting_leap", "Hunting Leap",
                "Leaps forward.");
        spell("shifter_pack_heal", "Pack Heal",
                "Heals you and every ally within 8 blocks for {heal}.");
        spell("shifter_chase_mark", "Chase Mark",
                "Marks the target for {effect_duration_1} seconds, shattering its armor and making it "
                + "glow.");
        spell("shifter_coordinated_assault", "Coordinated Assault",
                "Strikes the target for {damage} physical damage and sends your wolves after it.");
        spell("shifter_savage_bite", "Savage Bite",
                "A savage bite for {damage} physical damage.");
        spell("shifter_rallying_howl", "Rallying Howl",
                "Howls, dealing {damage} physical damage to nearby enemies and granting you and nearby "
                + "allies Strength for {effect_duration} seconds.");
        spell("shifter_alphas_call", "Alpha's Call",
                "Leaps on the target and strikes it for {damage} physical damage, then leaps on to 5 more "
                + "enemies nearby, one at a time: each leap goes to the enemy nearest where you look.");
        spell("shifter_dive_bomb", "Dive Bomb",
                "Dives among the enemies within {impact_range} blocks of the target, striking each for "
                + "{damage} physical damage.");
        spell("shifter_wing_dash", "Wing Dash",
                "Dashes forward on the wing.");
        spell("shifter_talon_slash", "Talon Slash",
                "A talon slash for {damage} physical damage.");
        spell("shifter_sky_burst", "Sky Burst",
                "A burst of wind that deals {damage} physical damage to nearby enemies.");
        spell("shifter_wind_shield", "Wind Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("shifter_cyclone_kick", "Cyclone Kick",
                "A cyclone kick that deals {damage} physical damage to nearby enemies and throws them into "
                + "the air.");
        spell("shifter_glide_step", "Glide Step",
                "Glides forward, then evades almost every attack for {effect_duration} seconds.");
        spell("shifter_feather_storm", "Feather Storm",
                "Looses eight razor feathers in quick succession, each dealing {damage} physical damage.");
        spell("shifter_predatory_strike", "Predatory Strike",
                "Swoops to the target and strikes it for {damage} physical damage.");
        spell("shifter_aerial_sweep", "Aerial Sweep",
                "A sweeping strike that deals {damage} physical damage to enemies ahead.");
        spell("shifter_storm_talon_descent", "Storm Talon Descent",
                "Leaps on the target and strikes it for {damage} physical damage, then leaps on to 5 more "
                + "enemies nearby, one at a time: each leap goes to the enemy nearest where you look.");
        spell("shifter_coil_trap", "Coil Trap",
                "Lays coils at the target for {cloud_duration} seconds, rooting enemies within "
                + "{cloud_radius} blocks.");
        spell("shifter_poison_cloud", "Poison Cloud",
                "Spits a poison cloud at the target for {cloud_duration} seconds, dealing {damage} physical "
                + "damage every second to enemies within {cloud_radius} blocks.");
        spell("shifter_serpent_shield", "Serpent Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("shifter_constrict", "Constrict",
                "Constricts the target, stunning it for {effect_duration} seconds.");
        spell("shifter_venom_surge", "Venom Surge",
                "Sprays venom, poisoning nearby enemies for {effect_duration} seconds.");
        spell("shifter_shadow_slither", "Shadow Slither",
                "Slithers forward through the shadows.");
        spell("shifter_corrosive_bite", "Corrosive Bite",
                "A corrosive bite for {damage} physical damage that shatters the target's armor for "
                + "{effect_duration} seconds.");
        spell("shifter_venom_burst", "Venom Burst",
                "A burst of venom that deals {damage} physical damage to nearby enemies.");
        spell("shifter_venom_apocalypse", "Venom Apocalypse",
                "Leaps on the target and strikes it for {damage} physical damage, envenoming it, then leaps on to 5 "
                + "more enemies nearby, one at a time: each leap goes to the enemy nearest where you look.");
    }

    /** The Phantom batch (2026-10-10): STR rows physical, the two INT rows shadow. Dread Shout keeps the pack's text. */
    private void addPhantomSpells() {
        spell("phantom_shadow_strike", "Shadow Strike",
                "Dashes to the target and strikes it for {damage} physical damage.");
        spell("phantom_backstab", "Backstab",
                "Slips behind the target and stabs it for {damage} physical damage.");
        spell("phantom_smoke_veil", "Smoke Veil",
                "Vanishes into smoke, unseen by enemies for {effect_duration_1} seconds.");
        spell("phantom_phantom_slash", "Phantom Slash",
                "A phantom slash that deals {damage} physical damage to enemies ahead.");
        spell("phantom_shadow_step", "Shadow Step",
                "Steps through the shadows to appear behind the target.");
        spell("phantom_bleeding_cut", "Bleeding Cut",
                "A deep cut for {damage} physical damage that leaves the target bleeding.");
        spell("phantom_dark_shuriken", "Dark Shuriken",
                "Hurls a dark shuriken for {damage} physical damage.");
        spell("phantom_evasion_roll", "Evasion Roll",
                "Rolls clear, dealing {damage} physical damage to nearby enemies, then evades almost every attack "
                + "and shrugs off harm for {effect_duration_1} seconds.");
        spell("phantom_shadow_burst", "Shadow Burst",
                "A burst of shadow that deals {damage} soul damage to nearby enemies.");
        spell("phantom_execution_slash", "Execution Slash",
                "A slash for {damage} physical damage, harder still against a target below 30%% health.");
        spell("phantom_rapid_stabs", "Rapid Stabs",
                "A flurry of stabs for {damage} physical damage in a single hit.");
        spell("phantom_shadow_infusion", "Shadow Infusion",
                "Infuses you with shadow, granting Strength for {effect_duration} seconds.");
        spell("phantom_blink_kill", "Blink Kill",
                "Blinks behind the target.");
        spell("phantom_bleed_flurry", "Bleed Flurry",
                "A flurry of cuts for {damage} physical damage that leaves the target bleeding.");
        spell("phantom_smoke_bomb", "Smoke Bomb",
                "Throws a smoke bomb, dealing {damage} physical damage to nearby enemies and blinding them for "
                + "{effect_duration} seconds.");
        spell("phantom_dark_rush", "Dark Rush",
                "Dashes between up to 8 enemies within {impact_range} blocks of the target.");
        spell("phantom_lethal_edge", "Lethal Edge",
                "Hones your edge, dealing {damage} physical damage to nearby enemies and raising your and nearby "
                + "allies' critical chance and damage for {effect_duration_1} seconds.");
        spell("phantom_piercing_strike", "Piercing Strike",
                "A piercing strike for {damage} physical damage that shatters the target's armor for "
                + "{effect_duration} seconds.");
        spell("phantom_finisher", "Finisher",
                "A finishing blow for {damage} physical damage, harder still against a target below 30%% "
                + "health.");
        spell("phantom_shadow_execution", "Shadow Execution",
                "Leaps between up to 8 enemies within {impact_range} blocks of the target, striking each for {damage} physical damage. A second later you detonate for {release_damage|300} around you, 25%% more for each enemy struck.");
        spell("phantom_phase_shift", "Phase Shift",
                "Blinks forward and fades from sight for {effect_duration_1} seconds.");
        spell("phantom_shadow_shield", "Shadow Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("phantom_ghost_slash", "Ghost Slash",
                "A ghostly slash for {damage} physical damage.");
        spell("phantom_fear_strike", "Fear Strike",
                "Strikes terror into the target for {effect_duration} seconds.");
        spell("phantom_fade_out", "Fade Out",
                "Fades out, lifting every harmful effect and hiding you for {effect_duration_1} seconds.");
        spell("phantom_dark_pulse", "Dark Pulse",
                "A pulse of darkness that deals {damage} physical damage to nearby enemies.");
        spell("phantom_phantom_chains", "Phantom Chains",
                "Binds the target in phantom chains, rooting it for {effect_duration} seconds.");
        spell("phantom_cloak_of_shadows", "Cloak of Shadows",
                "Wraps you in shadow, granting Resistance II for {effect_duration} seconds.");
        spell("phantom_shadow_ambush", "Shadow Ambush",
                "Ambushes the target from behind for {damage} physical damage.");
        spell("phantom_vanish_of_the_void", "Vanish of the Void",
                "Slips through every enemy within {impact_range} blocks of the target, blinding each for "
                + "{effect_duration} seconds. A second later you detonate, blinding every enemy around you.");
        spell("phantom_hemorrhage", "Hemorrhage",
                "Opens a wound for {damage} physical damage that leaves the target bleeding.");
        spell("phantom_crimson_cut", "Crimson Cut",
                "A crimson cut for {damage} physical damage that leaves the target bleeding.");
        spell("phantom_shadow_rake", "Shadow Rake",
                "Rakes the enemies ahead for {damage} physical damage and leaves them bleeding.");
        spell("phantom_blood_trap", "Blood Trap",
                "Sets a trap at the target for {cloud_duration} seconds that roots and bleeds enemies within "
                + "{cloud_radius} blocks.");
        spell("phantom_mark_of_pain", "Mark of Pain",
                "Marks the target for {effect_duration_1} seconds, shattering its armor and making it glow.");
        spell("phantom_rapid_slice", "Rapid Slice",
                "A rapid slice for {damage} physical damage in a single hit.");
        spell("phantom_dark_harvest", "Dark Harvest",
                "Harvests the target for {damage} physical damage, leaves it bleeding and heals you for {heal}.");
        spell("phantom_bleed_spread", "Bleed Spread",
                "Strikes every enemy within {impact_range} blocks of the target for {damage} physical damage and "
                + "leaves them bleeding.");
        spell("phantom_nightstalker_shadow_burst", "Shadow Burst",
                "A burst of shadow that deals {damage} physical damage to nearby enemies.");
        spell("phantom_blood_moon_carnage", "Blood Moon Carnage",
                "Leaps between up to 8 enemies within {impact_range} blocks of the target, striking each for {damage} physical damage and leaving it bleeding. A second later you detonate for {release_damage|300} around you, 25%% more for each enemy struck.");
        spell("phantom_rift_step", "Rift Step",
                "Steps far forward through a rift.");
        spell("phantom_void_slash", "Void Slash",
                "A void slash for {damage} soul damage.");
        spell("phantom_dimensional_strike", "Dimensional Strike",
                "A strike across dimensions for {damage} physical damage.");
        spell("phantom_shadow_field", "Shadow Field",
                "Spreads a field of shadow at the target for {cloud_duration} seconds, slowing enemies within "
                + "{cloud_radius} blocks.");
        spell("phantom_warp_assault", "Warp Assault",
                "Warps from enemy to enemy within {impact_range} blocks of the target.");
        spell("phantom_rift_shield", "Rift Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("phantom_disrupt_pulse", "Disrupt Pulse",
                "A disrupting pulse that stuns nearby enemies for {effect_duration} seconds.");
        spell("phantom_dark_surge", "Dark Surge",
                "A surge of darkness that hastens you for {effect_duration_1} seconds.");
        spell("phantom_rift_collapse", "Rift Collapse",
                "Warps through every enemy within {impact_range} blocks of the target, stunning each for "
                + "{effect_duration} seconds. A second later you detonate, stunning every enemy around you.");
        spell("phantom_terror_slash", "Terror Slash",
                "A terrifying slash that fears the target for {effect_duration} seconds.");
        spell("phantom_curse_blade", "Curse Blade",
                "A cursed blade that weakens the target for {effect_duration} seconds.");
        spell("phantom_dark_chains", "Dark Chains",
                "Binds the target in dark chains, rooting it for {effect_duration} seconds.");
        spell("phantom_horrify_pulse", "Horrify Pulse",
                "A horrifying pulse that fears nearby enemies for {effect_duration} seconds.");
        spell("phantom_void_mark", "Void Mark",
                "Marks the target for {effect_duration_1} seconds, shattering its armor and making it glow.");
        spell("phantom_nightfall", "Nightfall",
                "Brings night on the target for {damage} physical damage, blinding it for {effect_duration} seconds.");
        spell("phantom_dark_burst", "Dark Burst",
                "A burst of darkness that deals {damage} physical damage to nearby enemies.");
        spell("phantom_soul_drain", "Soul Drain",
                "Drains the target for {damage} physical damage and heals you for {heal}.");
        spell("phantom_reign_of_terror", "Reign of Terror",
                "Sweeps between up to 8 enemies within {impact_range} blocks of the target, fearing each for {effect_duration} seconds. A second later you detonate, fearing every enemy around you.");
    }

    /** The Reaper batch (2026-10-10): STR rows physical, the base INT rows shadow. */
    private void addReaperSpells() {
        spell("reaper_soul_slash", "Soul Slash",
                "Slashes at the souls around you, dealing {damage} soul damage to nearby enemies.");
        spell("reaper_plague_bolt", "Plague Bolt",
                "A plague bolt for {damage} soul damage that envenoms the target.");
        spell("reaper_drain_life", "Drain Life",
                "Drains the target for {damage} soul damage and heals you for {heal}.");
        spell("reaper_death_grip", "Death Grip",
                "Seizes the target for {damage} physical damage and drags it toward you.");
        spell("reaper_corrupt_field", "Corrupt Field",
                "Corrupts the ground at the target for {cloud_duration} seconds, dealing {damage} "
                + "physical damage each second to enemies within {cloud_radius} blocks.");
        spell("reaper_shadow_shield", "Shadow Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("reaper_weakening_curse", "Weakening Curse",
                "Curses nearby enemies, weakening them for {effect_duration} seconds.");
        spell("reaper_rot_burst", "Rot Burst",
                "A burst of rot that deals {damage} soul damage to nearby enemies.");
        spell("reaper_dark_step", "Dark Step",
                "Steps forward through the dark.");
        spell("reaper_virulent_blast", "Virulent Blast",
                "A virulent blast that deals {damage} physical damage to enemies within "
                + "{impact_range} blocks of the target.");
        spell("reaper_pestilence_field", "Pestilence Field",
                "Spreads pestilence at the target for {cloud_duration} seconds, dealing {damage} "
                + "physical damage each second to enemies within {cloud_radius} blocks.");
        spell("reaper_spread_infection", "Spread Infection",
                "Strikes every enemy within {impact_range} blocks of the target for {damage} "
                + "physical damage and envenoms them.");
        spell("reaper_rotting_strike", "Rotting Strike",
                "A rotting strike for {damage} physical damage.");
        spell("reaper_corruption_wave", "Corruption Wave",
                "A wave of corruption that deals {damage} physical damage to enemies ahead and "
                + "envenoms them.");
        spell("reaper_plague_shield", "Plague Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("reaper_necrotic_chains", "Necrotic Chains",
                "Binds the target in necrotic chains, rooting it for {effect_duration} seconds.");
        spell("reaper_decay_pulse", "Decay Pulse",
                "A pulse of decay that deals {damage} physical damage to nearby enemies.");
        spell("reaper_blight_curse", "Blight Curse",
                "Blights the target for {effect_duration} seconds, raising the damage it takes.");
        spell("reaper_toxic_eruption", "Toxic Eruption",
                "A toxic eruption that deals {damage} physical damage to enemies within "
                + "{impact_range} blocks of the target.");
        spell("reaper_pandemic_ascension", "Pandemic Ascension",
                "Reaps a scythe arc ahead for {damage} physical damage, storing a soul for each enemy cut. Cast again "
                + "within 8 seconds to slam a scythe wave forward for {release_damage|600}, 10%% more for each "
                + "soul, that pierces every enemy in its line and envenoms them. Souls fade one a second once 3 seconds "
                + "pass without a new one.");
        spell("reaper_blood_rend", "Blood Rend",
                "Rends the target for {damage} physical damage.");
        spell("reaper_soul_feast", "Soul Feast",
                "Feasts on gathered souls, healing you for {heal}.");
        spell("reaper_vampiric_aura", "Vampiric Aura",
                "Deals {damage} physical damage to nearby enemies and makes you and nearby allies "
                + "vampiric for {effect_duration} seconds, healing for part of every physical hit.");
        spell("reaper_shadow_cleave", "Shadow Cleave",
                "Cleaves around you for {damage} physical damage to each nearby enemy.");
        spell("reaper_grasp_of_hunger", "Grasp of Hunger",
                "Grasps at nearby enemies, dealing {damage} physical damage and dragging them "
                + "toward you.");
        spell("reaper_dark_fortify", "Dark Fortify",
                "Grants you a damage shield that absorbs {shield}.");
        spell("reaper_soul_charge", "Soul Charge",
                "Charges to the target.");
        spell("reaper_devour", "Devour",
                "Devours the target for {damage} physical damage, harder still against a target "
                + "below 30%% health.");
        spell("reaper_crimson_reign", "Crimson Reign",
                "Reaps a scythe arc ahead for {damage} physical damage, storing a soul for each enemy cut. Cast again "
                + "within 8 seconds to slam a scythe wave forward for {release_damage|600}, 10%% more for each "
                + "soul, that pierces every enemy in its line, healing you for each enemy it cuts. Souls fade one a "
                + "second once 3 seconds pass without a new one.");
        spell("reaper_summon_wraith", "Summon Wraith",
                "Calls two wraiths that follow you and strike enemies in close combat. They last {summon_duration} "
                + "seconds.");
        spell("reaper_soul_cage", "Soul Cage",
                "Cages the target's soul, rooting it for {effect_duration} seconds.");
        spell("reaper_dark_command", "Dark Command",
                "Grants you, nearby allies and your summons Strength for {effect_duration} seconds "
                + "and sets your summons on a nearby enemy.");
        spell("reaper_necro_blast", "Necro Blast",
                "A necrotic blast that deals {damage} physical damage to enemies within "
                + "{impact_range} blocks of the target.");
        spell("reaper_plague_servant", "Plague Servant",
                "Raises a plague-ridden undead servant that follows you and fights up close. Its spores are visual. "
                + "Its attacks deal physical damage. It lasts {summon_duration} seconds.");
        spell("reaper_shadow_barrier", "Shadow Barrier",
                "Grants you a damage shield that absorbs {shield}.");
        spell("reaper_fear_pulse", "Fear Pulse",
                "A pulse of dread that fears nearby enemies for {effect_duration} seconds.");
        spell("reaper_soul_surge", "Soul Surge",
                "A surge of souls that grants you Strength for {effect_duration} seconds.");
        spell("reaper_death_wave", "Death Wave",
                "A wave of death that pierces every enemy in its line for {damage} physical damage.");
        spell("reaper_legion_of_the_damned", "Legion of the Damned",
                "Reaps a scythe arc ahead for {damage} physical damage, storing a soul for each enemy cut. Cast again "
                + "within 8 seconds to slam a scythe wave forward for {release_damage|600}, 10%% more for each "
                + "soul, that pierces every enemy in its line and sets your summons on them. Souls fade one a second "
                + "once 3 seconds pass without a new one.");
        spell("reaper_decapitate", "Decapitate",
                "A beheading blow for {damage} physical damage, harder still against a target below "
                + "30%% health.");
        spell("reaper_death_mark", "Death Mark",
                "Marks the target for {effect_duration_1} seconds, shattering its armor and making "
                + "it glow.");
        spell("reaper_shadow_rush", "Shadow Rush",
                "Rushes toward the target through the shadows, striking on arrival.");
        spell("reaper_mortal_cleave", "Mortal Cleave",
                "A mortal cleave for {damage} physical damage to each nearby enemy.");
        spell("reaper_ruin_strike", "Ruin Strike",
                "A ruinous strike for {damage} physical damage.");
        spell("reaper_dark_combo", "Dark Combo",
                "A dark combo for {damage} physical damage in a single hit.");
        spell("reaper_fear_slash", "Fear Slash",
                "A terrifying slash that fears the target for {effect_duration} seconds.");
        spell("reaper_void_smash", "Void Smash",
                "Smashes the void open, dealing {damage} physical damage to enemies within "
                + "{impact_range} blocks of the target.");
        spell("reaper_final_blow", "Final Blow",
                "A final blow for {damage} physical damage.");
        spell("reaper_reapers_judgment", "Reaper's Judgment",
                "Reaps a scythe arc ahead for {damage} physical damage, storing a soul for each enemy cut. Cast again "
                + "within 8 seconds to slam a scythe wave forward for {release_damage|600}, 10%% more for each "
                + "soul, that pierces every enemy in its line, more to those below 30%% health. Souls fade one a second "
                + "once 3 seconds pass without a new one.");
        spell("reaper_void_pull", "Void Pull",
                "Tears at the void around you, dealing {damage} physical damage to nearby enemies "
                + "and dragging them toward you.");
        spell("reaper_shadow_prison", "Shadow Prison",
                "Raises a shadow prison at the target for {cloud_duration} seconds, rooting enemies "
                + "within {cloud_radius} blocks.");
        spell("reaper_dark_implosion", "Dark Implosion",
                "A dark implosion that deals {damage} physical damage to enemies within "
                + "{impact_range} blocks of the target.");
        spell("reaper_corrupt_shield", "Corrupt Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("reaper_void_spike", "Void Spike",
                "A spike of void for {damage} physical damage.");
        spell("reaper_soul_snare", "Soul Snare",
                "Snares the target's soul, rooting it for {effect_duration} seconds.");
        spell("reaper_shadow_burst", "Shadow Burst",
                "A burst of shadow that deals {damage} physical damage to nearby enemies.");
        spell("reaper_dimensional_step", "Dimensional Step",
                "Steps far forward across dimensions.");
        spell("reaper_void_curse", "Void Curse",
                "Curses the target with the void, weakening it for {effect_duration} seconds.");
        spell("reaper_abyss_collapse", "Abyss Collapse",
                "Reaps a scythe arc ahead for {damage} physical damage, storing a soul for each enemy cut. Cast again "
                + "within 8 seconds to slam a scythe wave forward for {release_damage|600}, 10%% more for each "
                + "soul, that pierces every enemy in its line and stuns them for 2 seconds. Souls fade one a second "
                + "once 3 seconds pass without a new one.");
    }

    /** The Arcanist batch (2026-10-10): every row INT, on the arcana school, landing arcane. */
    private void addArcanistSpells() {
        spell("arcanist_arcane_bolt", "Arcane Bolt",
                "A bolt of arcane force that deals {damage} arcane damage to the target.");
        spell("arcanist_elemental_burst", "Elemental Burst",
                "A burst of raw elements that deals {damage} arcane damage to enemies within {impact_range} blocks "
                + "of the target.");
        spell("arcanist_flame_brand", "Flame Brand",
                "Brands the target for {damage} arcane damage and sets it burning.");
        spell("arcanist_frost_shard", "Frost Shard",
                "A shard of ice that deals {damage} arcane damage and slows the target for {effect_duration} seconds.");
        spell("arcanist_lightning_strike", "Lightning Strike",
                "Calls lightning onto the target for {damage} arcane damage.");
        spell("arcanist_mana_surge", "Mana Surge",
                "Grants you Strength II for {effect_duration} seconds.");
        spell("arcanist_arcane_wave", "Arcane Wave",
                "Sends an arcane wave forward that pierces every enemy in its line for {damage} arcane damage.");
        spell("arcanist_elemental_chain", "Elemental Chain",
                "Elemental force that leaps between enemies, dealing {damage} arcane damage to those within "
                + "{impact_range} blocks of the target and slowing them for {effect_duration} seconds.");
        spell("arcanist_blink", "Blink",
                "Blinks you forward.");
        spell("arcanist_firestorm", "Firestorm",
                "Calls a firestorm on the target for {cloud_duration} seconds, dealing {damage} arcane damage every "
                + "second to enemies within {cloud_radius} blocks.");
        spell("arcanist_ice_field", "Ice Field",
                "Freezes the ground at the target for {cloud_duration} seconds, slowing enemies within "
                + "{cloud_radius} blocks.");
        spell("arcanist_lightning_rain", "Lightning Rain",
                "Rains lightning on the target for {cloud_duration} seconds, dealing {damage} arcane damage every "
                + "second to enemies within {cloud_radius} blocks.");
        spell("arcanist_lava_burst", "Lava Burst",
                "A burst of lava that deals {damage} arcane damage to enemies within {impact_range} blocks of the "
                + "target.");
        spell("arcanist_flame_wall", "Flame Wall",
                "Grants you a damage shield that absorbs {shield}, and a flame barrier for 8 seconds, returning 20%% "
                + "of incoming damage to nearby attackers.");
        spell("arcanist_frost_nova", "Frost Nova",
                "A nova of frost that freezes nearby enemies in place for {effect_duration} seconds.");
        spell("arcanist_storm_pulse", "Storm Pulse",
                "A pulse of storm that deals {damage} arcane damage to nearby enemies.");
        spell("arcanist_molten_chains", "Molten Chains",
                "Binds the target in molten chains, rooting it for {effect_duration} seconds.");
        spell("arcanist_elemental_surge", "Elemental Surge",
                "Grants you Strength II for {effect_duration} seconds.");
        spell("arcanist_earth_shatter", "Earth Shatter",
                "Shatters the earth around you, throwing nearby enemies into the air.");
        spell("arcanist_cataclysm_field", "Cataclysm Field",
                "Opens a rift where you aim. After 1.5 seconds it pulls in enemies within 6 blocks for 2 seconds, "
                + "then implodes for {release_damage|980|arcane}.");
        spell("arcanist_corrupt_bolt", "Corrupt Bolt",
                "A corrupting bolt for {damage} arcane damage that envenoms the target.");
        spell("arcanist_void_pool", "Void Pool",
                "Opens a pool of void at the target for {cloud_duration} seconds, dealing {damage} arcane damage "
                + "every second to enemies within {cloud_radius} blocks.");
        spell("arcanist_soul_drain", "Soul Drain",
                "Drains the target for {damage} arcane damage and heals you for {heal}.");
        spell("arcanist_dark_chains", "Dark Chains",
                "Binds the target in dark chains, rooting it for {effect_duration} seconds.");
        spell("arcanist_void_burst", "Void Burst",
                "A burst of void that deals {damage} arcane damage to nearby enemies.");
        spell("arcanist_plague_spread", "Plague Spread",
                "Spreads a plague that deals {damage} arcane damage to enemies within {impact_range} blocks of the "
                + "target and envenoms them.");
        spell("arcanist_abyss_spike", "Abyss Spike",
                "A spike from the abyss that deals {damage} arcane damage to the target.");
        spell("arcanist_shadow_veil", "Shadow Veil",
                "Deals {damage} arcane damage to nearby enemies and veils you and nearby allies, granting "
                + "Resistance for {effect_duration} seconds.");
        spell("arcanist_corrupt_shield", "Corrupt Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("arcanist_doom_mark", "Doom Mark",
                "Marks the target for {effect_duration_1} seconds, raising the damage it takes and making it glow.");
        spell("arcanist_void_cataclysm", "Void Cataclysm",
                "Opens a rift where you aim. After 1.5 seconds it pulls in enemies within 6 blocks for 2 seconds, "
                + "then implodes for {release_damage|900|arcane}, weakening them.");
        spell("arcanist_time_bolt", "Time Bolt",
                "A bolt of bent time that deals {damage} arcane damage to the target.");
        spell("arcanist_slow_time", "Slow Time",
                "Slows time around you, slowing nearby enemies for {effect_duration} seconds.");
        spell("arcanist_temporal_shield", "Temporal Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("arcanist_rewind", "Rewind",
                "Turns back your wounds, healing you for {heal}.");
        spell("arcanist_haste_field", "Haste Field",
                "Raises a field of quickened time around you for {cloud_duration} seconds, dealing {damage} arcane "
                + "damage every second to enemies within {cloud_radius} blocks, while you and allies in it are "
                + "hastened.");
        spell("arcanist_time_snare", "Time Snare",
                "Snares the target in time, rooting it for {effect_duration} seconds.");
        spell("arcanist_accelerate", "Accelerate",
                "Deals {damage} arcane damage to nearby enemies and hastens you and nearby allies for "
                + "{effect_duration} seconds.");
        spell("arcanist_chrono_burst", "Chrono Burst",
                "A burst of broken time that deals {damage} arcane damage to enemies within {impact_range} blocks of "
                + "the target.");
        spell("arcanist_phase_step", "Phase Step",
                "Steps forward out of time.");
        spell("arcanist_time_mark", "Time Mark",
                "Marks the target for {effect_duration_1} seconds, shattering its armor and making it glow.");
        spell("arcanist_time_fracture", "Time Fracture",
                "Fractures time where you aim. After 1.5 seconds it freezes enemies within 6 blocks every half second "
                + "for 2 seconds, then shatters, freezing them again and hastening you and allies in it.");
        spell("arcanist_arcane_slash", "Arcane Slash",
                "An arcane slash that deals {damage} arcane damage to enemies in front of you.");
        spell("arcanist_elemental_infusion", "Elemental Infusion",
                "Grants you Strength II for {effect_duration} seconds.");
        spell("arcanist_blink_strike", "Blink Strike",
                "Blinks behind the target.");
        spell("arcanist_flame_sweep", "Flame Sweep",
                "A sweep of flame that deals {damage} arcane damage to nearby enemies.");
        spell("arcanist_frost_cut", "Frost Cut",
                "A frozen cut that slows the target for {effect_duration} seconds.");
        spell("arcanist_lightning_thrust", "Lightning Thrust",
                "A lightning thrust that deals {damage} arcane damage to the target.");
        spell("arcanist_mana_shield", "Mana Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("arcanist_arcane_flurry", "Arcane Flurry",
                "Fires three arcane missiles in quick succession, each dealing {damage} arcane damage.");
        spell("arcanist_parry_spell", "Parry Spell",
                "Deals {damage} arcane damage to nearby enemies, then for 3 seconds reflects 20%% of the damage you "
                + "take back at its source and turns arrows and other projectiles back at whoever shot them.");
        spell("arcanist_surge_leap", "Surge Leap",
                "Leaps you forward on a surge of wind.");
        spell("arcanist_arcane_overdrive", "Arcane Overdrive",
                "Opens a rift where you aim. After 1.5 seconds it pulls in enemies within 6 blocks for 2 seconds, "
                + "then implodes for {release_damage|900|arcane}.");
        spell("arcanist_lightning_dash", "Lightning Dash",
                "Dashes to the target as lightning.");
        spell("arcanist_thunder_bolt", "Thunder Bolt",
                "A thunder bolt that deals {damage} arcane damage to the target.");
        spell("arcanist_storm_orb", "Storm Orb",
                "An orb of storm that deals {damage} arcane damage to enemies within {impact_range} blocks of the "
                + "target.");
        spell("arcanist_chain_lightning", "Chain Lightning",
                "Lightning that leaps between enemies, dealing {damage} arcane damage to those within "
                + "{impact_range} blocks of the target and stunning them for {effect_duration} second.");
        spell("arcanist_wind_shield", "Wind Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("arcanist_tempest_burst", "Tempest Burst",
                "A burst of tempest that deals {damage} arcane damage to nearby enemies and throws them back.");
        spell("arcanist_haste_surge", "Haste Surge",
                "Grants you Speed II for {effect_duration} seconds.");
        spell("arcanist_static_field", "Static Field",
                "Charges the ground at the target for {cloud_duration} seconds, slowing enemies within "
                + "{cloud_radius} blocks.");
        spell("arcanist_sky_strike", "Sky Strike",
                "Dives to the target and strikes it for {damage} arcane damage.");
        spell("arcanist_storm_savant_storm_pulse", "Storm Pulse",
                "A pulse of storm that deals {damage} arcane damage to enemies within {impact_range} blocks of the "
                + "target.");
        spell("arcanist_hurricane_ascension", "Hurricane Ascension",
                "Opens a rift where you aim. After 1.5 seconds it pulls in enemies within 6 blocks for 2 seconds, "
                + "then implodes for {release_damage|900|arcane}, slowing them.");
    }

    /** The Sword Immortal batch (2026-10-10). STR, so every hit is physical. */
    private void addSwordImmortalSpells() {
        spell("sword_immortal_flying_blade", "Flying Blade",
                "Hurls a blade at the target for {damage} physical damage.");
        spell("sword_immortal_blade_dash", "Blade Dash",
                "Dashes to the target and strikes it for {damage} physical damage, raising your critical chance by 10%% for {effect_duration} seconds.");
        spell("sword_immortal_crescent_cleave", "Crescent Cleave",
                "A wide crescent cleave for {damage} physical damage to every enemy in front of you.");
        spell("sword_immortal_blade_rain", "Blade Rain",
                "Rains blades on the target, dealing {damage} physical damage to enemies within {impact_range} "
                + "blocks of it.");
        spell("sword_immortal_focus_flow", "Focus Flow",
                "Focuses your blade for {effect_duration} seconds, raising critical chance by 10%%.");
        spell("sword_immortal_wind_step", "Wind Step",
                "Steps forward on the wind and gains Speed for {effect_duration} seconds.");
        spell("sword_immortal_divine_cleave", "Divine Cleave",
                "Cleaves the enemies ahead for {damage} physical damage.");
        spell("sword_immortal_heavenfall_strike", "Heavenfall Strike",
                "Falls on the target on arrival for {damage} physical damage, stunning it for {effect_duration} second.");
        spell("sword_immortal_spirit_overdrive", "Spirit Overdrive",
                "Overdrives your spirit, granting you Strength II for {effect_duration} seconds.");
        spell("sword_immortal_blade_nova", "Blade Nova",
                "Spins in a nova of blades, dealing {damage} physical damage to every nearby enemy.");
        spell("sword_immortal_heavenly_dash", "Heavenly Dash",
                "Dashes to the target and strikes it for {damage} physical damage.");
        spell("sword_immortal_focus_ascension", "Focus Ascension",
                "Ascends your focus for {effect_duration} seconds, raising critical damage by 20%%.");
        spell("sword_immortal_piercing_judgment", "Piercing Judgment",
                "A piercing judgment for {damage} physical damage that shatters the target's armor for "
                + "{effect_duration} seconds.");
        spell("sword_immortal_final_heaven_strike", "Final Heaven Strike",
                "A final strike for {damage} physical damage, harder still against a target below 30%% health.");
        spell("sword_immortal_ascendant_blade_of_the_heavens", "Ascendant Blade of the Heavens",
                "Sends the blade of the heavens through every enemy in its line for {damage} physical damage, "
                + "shattering their armor for {effect_duration} seconds.");
        spell("sword_immortal_cyclone_slash", "Cyclone Slash",
                "Spins like a cyclone, dealing {damage} physical damage to every nearby enemy.");
        spell("sword_immortal_wind_dash", "Wind Dash",
                "Dashes to the target and strikes it for {damage} physical damage, slowing it for {effect_duration} seconds.");
        spell("sword_immortal_storm_cleave", "Storm Cleave",
                "Cleaves the enemies ahead for {damage} physical damage; if it cuts anything, your cooldowns run "
                + "12%% faster for {effect_duration} seconds.");
        spell("sword_immortal_air_step", "Air Step",
                "Leaps forward through the air, carrying you far ahead.");
        spell("sword_immortal_swift_barrage", "Swift Barrage",
                "Throws five blades in quick succession, each dealing {damage} physical damage.");
        spell("sword_immortal_sky_cutter", "Sky Cutter",
                "A cutting wave that passes through enemies, dealing {damage} physical damage to each and weakening "
                + "them for {effect_duration} seconds.");
        spell("sword_immortal_hurricane_flow", "Hurricane Flow",
                "Deals {damage} physical damage to nearby enemies and grants you and nearby allies Speed and "
                + "Strength for {effect_duration_1} seconds.");
        spell("sword_immortal_tempest_blade_storm", "Tempest Blade Storm",
                "Raises a storm of blades around you for {cloud_duration} seconds, dealing {damage} physical damage "
                + "every second to enemies within {cloud_radius} blocks and slowing them.");
        spell("sword_immortal_mystic_slash", "Mystic Slash",
                "A mystic slash for {damage} physical damage.");
        spell("sword_immortal_serenity_guard", "Serenity Guard",
                "Grants you Resistance for {effect_duration} seconds and a damage shield that absorbs {shield}.");
        spell("sword_immortal_ethereal_dash", "Ethereal Dash",
                "Blinks forward, shaking off every slow and root.");
        spell("sword_immortal_crimson_slash", "Crimson Slash",
                "A crimson slash for {damage} physical damage that leaves the target bleeding.");
        spell("sword_immortal_bleeding_wave", "Bleeding Wave",
                "A wave that passes through enemies, dealing {damage} physical damage to each and leaving them "
                + "bleeding.");
        spell("sword_immortal_blood_drain", "Blood Drain",
                "Drains the target for {damage} physical damage and heals you for {heal}.");
        spell("sword_immortal_scarlet_dash", "Scarlet Dash",
                "Dashes to the target and strikes it for {damage} physical damage, leaving it freshly bleeding.");
        spell("sword_immortal_hemorrhage_burst", "Hemorrhage Burst",
                "Bursts open every enemy within {impact_range} blocks of the target for {damage} physical damage "
                + "and leaves them bleeding.");
        spell("sword_immortal_life_siphon", "Life Siphon",
                "Siphons the target's life for {damage} physical damage and heals you for {heal}.");
        spell("sword_immortal_bleed_spread", "Bleed Spread",
                "Strikes every enemy within {impact_range} blocks of the target for {damage} physical damage and "
                + "leaves them bleeding.");
        spell("sword_immortal_dark_blade_combo", "Dark Blade Combo",
                "A dark combo of blade strikes for {damage} physical damage.");
        spell("sword_immortal_execution_cut", "Execution Cut",
                "A cut for {damage} physical damage, 30%% more against a bleeding target, that leaves it bleeding.");
        spell("sword_immortal_blood_ascension", "Blood Ascension",
                "A blood wave that passes through enemies, dealing {damage} physical damage to each and wounding "
                + "them grievously for {effect_duration_1} seconds (less healing, more damage taken); each enemy it "
                + "cuts sends you into a frenzy of Strength and Speed for {effect_duration_2} seconds.");
        spell("sword_immortal_thunder_slash", "Thunder Slash",
                "A thundering slash for {damage} physical damage.");
        spell("sword_immortal_lightning_dash", "Lightning Dash",
                "Dashes to the target and strikes it for {damage} physical damage.");
        spell("sword_immortal_storm_blade_combo", "Storm Blade Combo",
                "A storm of blade strikes for {damage} physical damage.");
        spell("sword_immortal_flash_step", "Flash Step",
                "Flashes far forward, raising your critical chance by 15%% for {effect_duration} seconds.");
        spell("sword_immortal_thunder_rain", "Thunder Rain",
                "Calls down blade strikes on the target, dealing {damage} physical damage to enemies within "
                + "{impact_range} blocks of it.");
        spell("sword_immortal_lightning_cyclone", "Lightning Cyclone",
                "Spins in a crackling cyclone, dealing {damage} physical damage to every nearby enemy.");
        spell("sword_immortal_heavens_lightning_judgment", "Heaven's Lightning Judgment",
                "Calls judgment on the target, dealing {damage} physical damage to enemies within {impact_range} "
                + "blocks of it and stunning them for {effect_duration} seconds.");
    }

    /** The Trickster batch (2026-10-10). STR rows are physical, Arcane Throw arcane. */
    private void addTricksterSpells() {
        spell("trickster_illusion_strike", "Illusion Strike",
                "Flickers behind the target and strikes it for {damage} physical damage.");
        spell("trickster_arcane_throw", "Arcane Throw",
                "Throws a charged blade at the target for {damage} arcane damage.");
        spell("trickster_smoke_trap", "Smoke Trap",
                "Sets a smoke trap at the target for {cloud_duration} seconds, dealing {damage} physical damage "
                + "every second to enemies within {cloud_radius} blocks and blinding them.");
        spell("trickster_mirror_clone", "Mirror Clone",
                "Calls up a decoy of yourself that draws nearby enemies and bursts when struck.");
        spell("trickster_flash_step", "Flash Step",
                "Steps forward in a flash.");
        spell("trickster_trick_blade", "Trick Blade",
                "A trick blade for {damage} physical damage that leaves the target bleeding.");
        spell("trickster_chaos_bomb", "Chaos Bomb",
                "A chaos bomb that deals {damage} physical damage to enemies within {impact_range} blocks of the "
                + "target, each of which may be slowed, weakened, blinded or sundered.");
        spell("trickster_snare_wire", "Snare Wire",
                "Snares the target in wire, rooting it for {effect_duration} seconds.");
        spell("trickster_evasive_mirage", "Evasive Mirage",
                "Slips into stealth behind a mirage, dodging more often while it lasts.");
        spell("trickster_shatter_clone", "Shatter Clone",
                "Shatters a clone around you, dealing {damage} physical damage to nearby enemies and stunning them "
                + "for {effect_duration} seconds.");
        spell("trickster_mass_mirror", "Mass Mirror",
                "Deals {damage} physical damage to nearby enemies and calls up three decoys of yourself.");
        spell("trickster_clone_ambush", "Clone Ambush",
                "Appears behind the target and strikes it for {damage} physical damage.");
        spell("trickster_phantom_army", "Phantom Army",
                "Sends phantoms at the target for {cloud_duration} seconds, dealing {damage} physical damage every "
                + "second to enemies within {cloud_radius} blocks.");
        spell("trickster_reflect_image", "Reflect Image",
                "Deals {damage} physical damage to nearby enemies, then for 8 seconds reflects 20%% of the damage you "
                + "take back at its source.");
        spell("trickster_blink_swap", "Blink Swap",
                "Leaves a decoy where you stand and blinks forward.");
        spell("trickster_illusion_barrage", "Illusion Barrage",
                "Looses eight illusory blades in quick succession, each dealing {damage} physical damage.");
        spell("trickster_confuse_pulse", "Confuse Pulse",
                "A pulse that deals {damage} physical damage to nearby enemies, clouding their sight and slowing "
                + "them.");
        spell("trickster_decoy_trap", "Decoy Trap",
                "Sets out a decoy of yourself that draws nearby enemies and bursts when struck.");
        spell("trickster_shatter_wave", "Shatter Wave",
                "Shatters a clone at the target, dealing {damage} physical damage to enemies within {impact_range} "
                + "blocks and stunning them for {effect_duration} seconds.");
        spell("trickster_shadow_mirage", "Shadow Mirage",
                "Slips into stealth and leaves two decoys of yourself behind.");
        spell("trickster_legion_of_lies", "Legion of Lies",
                "Throws a beacon where you aim and calls up three decoys of yourself. Cast again within 6 seconds to "
                + "detonate it in a cone toward where you look, dealing {release_damage|900} and blinding enemies "
                + "for 4 seconds; left alone, it goes off the way it was thrown at 6 seconds.");
        spell("trickster_explosive_trap", "Explosive Trap",
                "Springs an explosive trap at the target, dealing {damage} physical damage to enemies within "
                + "{impact_range} blocks.");
        spell("trickster_snare_field", "Snare Field",
                "Lays a snare field at the target for {cloud_duration} seconds, slowing enemies within "
                + "{cloud_radius} blocks.");
        spell("trickster_poison_trap", "Poison Trap",
                "Springs a poison trap at the target, dealing {damage} physical damage to enemies within "
                + "{impact_range} blocks and envenoming them.");
        spell("trickster_flash_trap", "Flash Trap",
                "Springs a flash trap at the target, dealing {damage} physical damage to enemies within "
                + "{impact_range} blocks and blinding them for {effect_duration} seconds.");
        spell("trickster_shock_trap", "Shock Trap",
                "Sets a shock trap at the target for {cloud_duration} seconds, stunning enemies within "
                + "{cloud_radius} blocks.");
        spell("trickster_razor_wire", "Razor Wire",
                "Strings razor wire at the target for {cloud_duration} seconds, dealing {damage} physical damage "
                + "every second to enemies within {cloud_radius} blocks.");
        spell("trickster_collapse_trap", "Collapse Trap",
                "Collapses a trap on the target, dealing {damage} physical damage to enemies within {impact_range} "
                + "blocks and slowing them for {effect_duration} seconds.");
        spell("trickster_chain_trap", "Chain Trap",
                "Sets a chain trap at the target for {cloud_duration} seconds, rooting enemies within "
                + "{cloud_radius} blocks.");
        spell("trickster_silence_mine", "Silence Mine",
                "Sets off a mine at the target, stunning enemies within {impact_range} blocks for "
                + "{effect_duration} seconds.");
        spell("trickster_mirage_mine", "Mirage Mine",
                "Sets off a mirage mine at the target, dealing {damage} physical damage to enemies within "
                + "{impact_range} blocks, clouding their sight and slowing them.");
        spell("trickster_death_grid", "Death Grid",
                "Throws a trap beacon where you aim. Cast again within 6 seconds to detonate it in a cone toward "
                + "where you look, dealing {release_damage|900} and blinding enemies for 4 seconds and leaving "
                + "them bleeding; left alone, it goes off the way it was thrown at 6 seconds.");
        spell("trickster_wild_slash", "Wild Slash",
                "A wild slash that deals {damage} physical damage to enemies ahead.");
        spell("trickster_chaos_orb", "Chaos Orb",
                "A chaos orb for {damage} physical damage that may slow, weaken, blind or sunder the target.");
        spell("trickster_lucky_strike", "Lucky Strike",
                "Deals {damage} physical damage to nearby enemies and sharpens you and nearby allies for "
                + "{effect_duration} seconds, raising critical strike chance.");
        spell("trickster_unstable_blink", "Unstable Blink",
                "Blinks forward on unstable footing.");
        spell("trickster_bomb_toss", "Bomb Toss",
                "Tosses a bomb that deals {damage} physical damage to enemies within {impact_range} blocks of the "
                + "target.");
        spell("trickster_curse_flip", "Curse Flip",
                "Flips a curse onto the target, which may slow, weaken, blind or sunder it.");
        spell("trickster_frenzy_buff", "Frenzy Buff",
                "Deals {damage} physical damage to nearby enemies and drives you and nearby allies into a frenzy "
                + "for {effect_duration} seconds, raising attack speed.");
        spell("trickster_madness_pulse", "Madness Pulse",
                "A pulse of madness that deals {damage} physical damage to nearby enemies, fearing them and "
                + "clouding their sight.");
        spell("trickster_roulette_strike", "Roulette Strike",
                "A roulette strike for {damage} physical damage.");
        spell("trickster_wild_mirage", "Wild Mirage",
                "Deals {damage} physical damage to nearby enemies and may call up a decoy of yourself.");
        spell("trickster_carnival_of_chaos", "Carnival of Chaos",
                "Throws a beacon of chaos where you aim. Cast again within 6 seconds to detonate it in a cone toward "
                + "where you look, dealing {release_damage|900} and blinding enemies and possibly slowing, "
                + "weakening or sundering them; left alone, it goes off the way it was thrown at 6 seconds.");
        spell("trickster_quick_slash", "Quick Slash",
                "A quick slash for {damage} physical damage.");
        spell("trickster_riposte", "Riposte",
                "Takes a riposte stance for {effect_duration} seconds: the next enemy to strike you within reach is "
                + "parried and struck back for {counter_damage|260}.");
        // The reaction triggers.
        spell("sword_immortal_parry_counter", "Parry Counter",
                "Takes a parry stance for {effect_duration} seconds: the next enemy to strike you within reach is "
                + "parried, struck back for {counter_damage|220} and stunned.");
        spell("monk_night_strike", "Night Strike",
                "A strike from the shadows for {damage} physical damage, 20%% more when you strike from behind.");
        spell("trickster_flurry_combo", "Flurry Combo",
                "A flurry of blows for {damage} physical damage.");
        spell("trickster_shadow_dash", "Shadow Dash",
                "Dashes through the shadows to the target.");
        spell("trickster_precision_strike", "Precision Strike",
                "A precise strike for {damage} physical damage that shatters the target's armor for "
                + "{effect_duration} seconds.");
        spell("trickster_blade_storm", "Blade Storm",
                "A storm of blades that deals {damage} physical damage to nearby enemies.");
        spell("trickster_feint", "Feint",
                "Deals {damage} physical damage to nearby enemies and feints, evading attacks for "
                + "{effect_duration} seconds.");
        spell("trickster_disarm", "Disarm",
                "Disarms the target, weakening it for {effect_duration} seconds.");
        spell("trickster_piercing_thrust", "Piercing Thrust",
                "A thrust that pierces every enemy in its line for {damage} physical damage.");
        spell("trickster_lethal_sweep", "Lethal Sweep",
                "A lethal sweep that deals {damage} physical damage to enemies ahead.");
        spell("trickster_masters_flurry", "Master's Flurry",
                "Throws a feint beacon where you aim. Cast again within 6 seconds to detonate it in a cone toward "
                + "where you look, dealing {release_damage|900} and blinding enemies for 4 seconds; left alone, "
                + "it goes off the way it was thrown at 6 seconds.");
        spell("trickster_bomb_plant", "Bomb Plant",
                "Plants a bomb that deals {damage} physical damage to enemies within {impact_range} blocks of the "
                + "target.");
        spell("trickster_burning_charge", "Burning Charge",
                "Charges to the target and sets it burning.");
        spell("trickster_corrosive_blast", "Corrosive Blast",
                "A corrosive blast that deals {damage} physical damage to enemies within {impact_range} blocks of "
                + "the target and shatters their armor for {effect_duration} seconds.");
        spell("trickster_toxic_cloud", "Toxic Cloud",
                "Releases a toxic cloud at the target for {cloud_duration} seconds, dealing {damage} physical "
                + "damage every second to enemies within {cloud_radius} blocks.");
        spell("trickster_remote_detonate", "Remote Detonate",
                "Detonates a charge that deals {damage} physical damage to enemies within {impact_range} blocks of "
                + "the target.");
        spell("trickster_sticky_grenade", "Sticky Grenade",
                "A sticky grenade that deals {damage} physical damage to enemies within {impact_range} blocks of "
                + "the target and slows them for {effect_duration} seconds.");
        spell("trickster_shrapnel_burst", "Shrapnel Burst",
                "A burst of shrapnel that deals {damage} physical damage to nearby enemies.");
        spell("trickster_demolish", "Demolish",
                "A demolishing blow for {damage} physical damage.");
        spell("trickster_mine_field", "Mine Field",
                "Lays a mine field at the target for {cloud_duration} seconds, dealing {damage} physical damage "
                + "every second to enemies within {cloud_radius} blocks.");
        spell("trickster_volatile_surge", "Volatile Surge",
                "Grants you Strength for {effect_duration} seconds.");
        spell("trickster_armageddon_cache", "Armageddon Cache",
                "Throws a cache where you aim. Cast again within 6 seconds to detonate it in a cone toward where you "
                + "look, dealing {release_damage|900} and blinding enemies for 4 seconds and hurling them back; "
                + "left alone, it goes off the way it was thrown at 6 seconds.");
    }

    /** The SPIRIT rehome (2026-10-10): every SPIRIT row on INT. */
    private void addSpiritRehomeSpells() {
        spell("cleric_heal", "Heal",
                "Heals a friend (or you, aimed at no one) for {heal}.");
        spell("cleric_greater_heal", "Greater Heal",
                "Heals a friend (or you, aimed at no one) for {heal}.");
        spell("cleric_renew", "Renew",
                "Heals a friend (or you, aimed at no one) for {heal}.");
        spell("cleric_divine_light", "Divine Light",
                "Heals you and every ally within 6 blocks for {heal}.");
        spell("cleric_bless", "Bless",
                "Blesses a friend (or you) with a heal for {heal}, raising the healing they receive by 21%% for "
                + "{effect_duration} seconds.");
        spell("cleric_purify", "Purify",
                "Heals you and every ally within 6 blocks for {heal} and lifts every harmful effect from "
                + "you all.");
        spell("cleric_shield_of_faith", "Shield of Faith",
                "Grants you a damage shield that absorbs {shield}.");
        spell("cleric_spirit_surge", "Spirit Surge",
                "Heals a friend (or you, aimed at no one) for {heal}.");
        spell("cleric_mass_heal", "Mass Heal",
                "Heals you and every ally within 8 blocks for {heal}.");
        spell("cleric_prayer_of_renewal", "Prayer of Renewal",
                "Blesses the ground at the target for {cloud_duration} seconds, healing allies within "
                + "{cloud_radius} blocks for {heal} every second.");
        spell("cleric_divine_ward", "Divine Ward",
                "Grants you and every ally within 6 blocks a damage shield that absorbs {shield}.");
        spell("cleric_healing_wave", "Healing Wave",
                "Heals you and every ally within 8 blocks for {heal}.");
        spell("cleric_light_blessing", "Light Blessing",
                "Blesses a friend (or you) with a heal for {heal}, raising the healing they receive by 24%% for "
                + "{effect_duration} seconds.");
        spell("cleric_purifying_light", "Purifying Light",
                "Heals you and every ally within 8 blocks for {heal} and lifts every harmful effect from "
                + "you all.");
        spell("cleric_sacred_ground", "Sacred Ground",
                "Blesses the ground at the target for {cloud_duration} seconds, healing allies within "
                + "{cloud_radius} blocks for {heal} every second.");
        spell("cleric_spirit_mend", "Spirit Mend",
                "Heals a friend (or you, aimed at no one) for {heal}.");
        spell("cleric_radiant_aura", "Radiant Aura",
                "Blesses the ground around you for {cloud_duration} seconds, healing allies within "
                + "{cloud_radius} blocks for {heal} every second.");
        spell("cleric_flash_heal", "Flash Heal",
                "Heals a friend (or you, aimed at no one) for {heal}.");
        spell("cleric_miracle_of_renewal", "Miracle of Renewal",
                "Raises a sanctuary at the target for {cloud_duration} seconds: each second allies within "
                + "{cloud_radius} blocks are healed for {heal} and enemies there are weakened.");
        spell("cleric_divine_slash", "Divine Slash",
                "A sweeping cut for {damage} holy damage to every enemy in front of you.");
        spell("cleric_sacred_heal", "Sacred Heal",
                "Heals a friend (or you, aimed at no one) for {heal}.");
        spell("cleric_zeal_buff", "Zeal Buff",
                "Deals {damage} holy damage to nearby enemies and hastens your abilities and nearby allies' "
                + "for {effect_duration} seconds.");
        spell("cleric_shield_bash", "Shield Bash",
                "Bashes the target, stunning it briefly, and grants you a damage shield that absorbs {shield}.");
        spell("cleric_holy_nova", "Holy Nova",
                "A burst that deals {damage} holy damage to nearby enemies.");
        spell("cleric_lifelight_strike", "Lifelight Strike",
                "A strike for {damage} holy damage that heals you for {heal}.");
        spell("cleric_crusader_leap", "Crusader Leap",
                "Leaps to the target and stuns it briefly.");
        spell("cleric_faith_guard", "Faith Guard",
                "Grants you a damage shield that absorbs {shield}.");
        spell("cleric_judgment_flame", "Judgment Flame",
                "Calls judgment on the target for {damage} holy damage to enemies near it, setting them "
                + "burning.");
        spell("cleric_avatar_of_faith", "Avatar of Faith",
                "Becomes a sanctuary for {cloud_duration} seconds: each second you and allies within "
                + "{cloud_radius} blocks are healed for {heal} and strengthened, warded and given more healing, "
                + "and enemies there are weakened.");
        spell("cleric_foresight_shield", "Foresight Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("cleric_prophecy_heal", "Prophecy Heal",
                "Heals a friend (or you, aimed at no one) for {heal}.");
        spell("cleric_time_blessing", "Time Blessing",
                "Heals you and nearby allies for {heal} and hastens your abilities for {effect_duration} "
                + "seconds.");
        spell("cleric_spirit_barrier", "Spirit Barrier",
                "Grants you and every ally within 8 blocks a damage shield that absorbs {shield}.");
        spell("cleric_fate_cleanse", "Fate Cleanse",
                "Heals you and every ally within 6 blocks for {heal} and lifts every harmful effect from "
                + "you all.");
        spell("cleric_divine_mark", "Divine Mark",
                "Marks the target for 8 seconds, leaving it vulnerable and revealing it.");
        spell("cleric_guardian_light", "Guardian Light",
                "Raises a guardian light that heals wounded allies near it, empowered by your attack damage. It lasts "
                + "{summon_duration} seconds.");
        spell("cleric_shield_surge", "Shield Surge",
                "Grants you and every ally within 8 blocks a damage shield that absorbs {shield}.");
        spell("cleric_holy_vision", "Holy Vision",
                "Deals {damage} holy damage to nearby enemies and raises your and nearby allies' critical "
                + "chance for {effect_duration} seconds.");
        spell("cleric_temporal_ward", "Temporal Ward",
                "Grants you a damage shield that absorbs {shield}, and wards off the next harmful effect for "
                + "{effect_duration} seconds.");
        spell("cleric_hand_of_providence", "Hand of Providence",
                "Weakens nearby enemies and grants you and every ally within 10 blocks Resistance and a damage shield "
                + "that absorbs {shield}.");
        spell("cleric_sacred_bloom", "Sacred Bloom",
                "Heals a friend (or you, aimed at no one) for {heal}.");
        spell("cleric_renewal_wave", "Renewal Wave",
                "Heals you and every ally within 6 blocks for {heal}.");
        spell("cleric_cleanse_flame", "Cleanse Flame",
                "A cleansing flame that deals {damage} holy damage to nearby enemies and lifts every harmful "
                + "effect from you and nearby allies.");
        spell("cleric_purity_shield", "Purity Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("cleric_healing_vines", "Healing Vines",
                "Heals a friend (or you, aimed at no one) for {heal}.");
        spell("cleric_nature_light", "Nature Light",
                "Heals you and every ally within 8 blocks for {heal}.");
        spell("cleric_vital_surge", "Vital Surge",
                "Heals a friend (or you, aimed at no one) for {heal}.");
        spell("cleric_revitalize", "Revitalize",
                "Heals you and every ally within 6 blocks for {heal}.");
        spell("cleric_sacred_roots", "Sacred Roots",
                "Roots nearby enemies for {effect_duration} seconds.");
        spell("cleric_grace_field", "Grace Field",
                "Blesses the ground at the target for {cloud_duration} seconds, healing allies within "
                + "{cloud_radius} blocks for {heal} every second.");
        spell("cleric_garden_of_grace", "Garden of Grace",
                "Grows a garden at the target for {cloud_duration} seconds: each second allies within "
                + "{cloud_radius} blocks are healed for {heal} and enemies there are weakened.");
        spell("cleric_summon_spirit_healer", "Summon Spirit Healer",
                "Calls a spirit healer that follows you and heals one wounded ally at a time. Protect it from enemy "
                + "attacks. It lasts {summon_duration} seconds.");
        spell("cleric_spirit_caller_spirit_mend", "Spirit Mend",
                "Heals you and every ally within 5 blocks for {heal}.");
        spell("cleric_spirit_shield", "Spirit Shield",
                "Grants you and every ally within 8 blocks a damage shield that absorbs {shield}.");
        spell("cleric_spirit_wave", "Spirit Wave",
                "Heals you and every ally within 8 blocks for {heal}.");
        spell("cleric_spirit_bind", "Spirit Bind",
                "Binds the target in place for {effect_duration} seconds.");
        spell("cleric_spirit_bless", "Spirit Bless",
                "Blesses you and nearby allies with Strength for {effect_duration} seconds.");
        spell("cleric_soul_transfer", "Soul Transfer",
                "Heals a friend (or you, aimed at no one) for {heal}.");
        spell("cleric_ethereal_step", "Ethereal Step",
                "Steps forward through the spirit world.");
        spell("cleric_spirit_burst", "Spirit Burst",
                "A burst that deals {damage} holy damage to nearby enemies.");
        spell("cleric_guardian_spirit", "Guardian Spirit",
                "Calls a guardian spirit with a sword and shield. It follows you and fights up close, with increased "
                + "health and armor. It lasts {summon_duration} seconds.");
        spell("cleric_ancestral_choir", "Ancestral Choir",
                "Calls the ancestral choir around you for {cloud_duration} seconds: each second you and "
                + "allies within {cloud_radius} blocks are healed for {heal} and enemies there are weakened.");
        spell("monk_focus_heal", "Focus Heal",
                "Heals you for {heal}.");
        spell("monk_chi_burst", "Chi Burst",
                "A burst that deals {damage} arcane damage to nearby enemies.");
        spell("monk_shielded_stance", "Shielded Stance",
                "Grants you a damage shield that absorbs {shield}.");
        spell("monk_fortify", "Fortify",
                "Fortifies you, healing you for {heal} and granting Resistance for {effect_duration} seconds.");
        spell("monk_reinforced_guard", "Reinforced Guard",
                "Grants you Resistance for {effect_duration} seconds and a damage shield that absorbs {shield}.");
        spell("monk_living_fortress", "Living Fortress",
                "Becomes a living fortress: deals {damage} physical damage to nearby enemies and grants you "
                + "Resistance for {effect_duration} seconds and a damage shield that absorbs {shield}.");
        spell("monk_healing_palm", "Healing Palm",
                "Heals a friend (or you, aimed at no one) for {heal}.");
        spell("monk_spirit_wave", "Spirit Wave",
                "A burst that deals {damage} arcane damage to nearby enemies.");
        spell("monk_renewal_aura", "Renewal Aura",
                "Blesses the ground around you for {cloud_duration} seconds, healing allies within "
                + "{cloud_radius} blocks for {heal} every second.");
        spell("monk_spirit_shield", "Spirit Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("monk_restoration_burst", "Restoration Burst",
                "Heals you and every ally within 6 blocks for {heal}.");
        spell("monk_purifying_touch", "Purifying Touch",
                "A purifying touch that heals a friend (or you) for {heal} and lifts every harmful effect.");
        spell("monk_vital_surge", "Vital Surge",
                "Heals you for {heal}.");
        spell("monk_avatar_of_serenity", "Avatar of Serenity",
                "Becomes serenity itself for {cloud_duration} seconds: each second enemies within {cloud_radius} "
                + "blocks take {damage} holy damage and you and allies there are healed for {heal}. You gain a damage "
                + "shield that absorbs {shield}.");
        spell("monk_shadow_burst", "Shadow Burst",
                "A burst that deals {damage} soul damage to nearby enemies.");
        spell("paladin_holy_light", "Holy Light",
                "Heals a friend (or you, aimed at no one) for {heal}.");
        spell("paladin_shield_of_faith", "Shield of Faith",
                "Grants you a damage shield that absorbs {shield}.");
        spell("paladin_guardian_leap", "Guardian Leap",
                "Leaps forward and grants you a damage shield that absorbs {shield}.");
        spell("paladin_aegis_wall", "Aegis Wall",
                "Raises an aegis: you and every ally within 6 blocks gain a damage shield that absorbs {shield}, and "
                + "you gain Resistance for {effect_duration} seconds.");
        spell("sword_immortal_energy_shield", "Energy Shield",
                "Grants you a damage shield that absorbs {shield}.");
        spell("sword_immortal_spirit_burst", "Spirit Burst",
                "A burst of spirit that deals {damage} arcane damage to nearby enemies and weakens them for "
                + "{effect_duration} seconds.");
        spell("sword_immortal_judgment_wave", "Judgment Wave",
                "Sends a wave of judgment ahead for {damage} arcane damage to every enemy it passes through, "
                + "leaving them vulnerable.");
        spell("sword_immortal_wind_shield", "Wind Shield",
                "Grants you a damage shield that absorbs {shield}, and wards off the next harmful effect for "
                + "{effect_duration} seconds.");
        spell("sword_immortal_energy_wave", "Energy Wave",
                "Sends a wave of energy ahead for {damage} arcane damage to every enemy it passes through.");
        spell("sword_immortal_spirit_barrier", "Spirit Barrier",
                "Grants you and every ally within 6 blocks a damage shield that absorbs {shield}.");
        spell("sword_immortal_floating_blades", "Floating Blades",
                "Looses three floating blades at the target, each dealing {damage} arcane damage.");
        spell("sword_immortal_soul_pierce", "Soul Pierce",
                "Pierces the target's soul for {damage} arcane damage, stunning it for {effect_duration} "
                + "seconds.");
        spell("sword_immortal_spirit_burst_field", "Spirit Burst Field",
                "Opens a field of spirit blades at the target for {cloud_duration} seconds, dealing {damage} "
                + "arcane damage every second to enemies within {cloud_radius} blocks.");
        spell("sword_immortal_spirit_pull", "Spirit Pull",
                "Pulls nearby enemies in, dealing {damage} arcane damage.");
        spell("sword_immortal_soul_storm", "Soul Storm",
                "Looses a storm of six spirit blades at the target, each dealing {damage} arcane damage.");
        spell("sword_immortal_celestial_spirit_tempest", "Celestial Spirit Tempest",
                "Calls a celestial tempest around you for {cloud_duration} seconds, dealing {damage} arcane damage "
                + "every second to enemies within {cloud_radius} blocks, and grants you a damage shield that absorbs "
                + "{shield}.");
        spell("sword_immortal_blood_shield", "Blood Shield",
                "Grants you Strength for {effect_duration} seconds and a damage shield that absorbs {shield}.");
        spell("sword_immortal_static_burst", "Static Burst",
                "A static burst that deals {damage} arcane damage to nearby enemies and slows them for "
                + "{effect_duration} seconds.");
        spell("sword_immortal_chain_lightning_strike", "Chain Lightning Strike",
                "Strikes the target with chain lightning for {damage} arcane damage to enemies near it.");
        spell("sword_immortal_electro_guard", "Electro Guard",
                "Grants you a damage shield that absorbs {shield} and, for 8 seconds, reflects 20%% of the damage "
                + "enemies deal you back at them.");
    }

    /** The passives' names and what each does, a rank and at rank 5; the figures are the node files'. */
    private void addPassives() {
        // Arcanist.
        spell("arcanist_cooldown_reduction", "Quickened Mind",
                "Grants 5 ability haste a rank, up to 25 at rank 5: all your skill cooldowns run that much "
                + "faster.");
        spell("arcanist_crit_chance", "Arcane Precision",
                "Raises critical chance by 5%% a rank, up to 25%% at rank 5.");
        spell("arcanist_mana_regen", "Mana Wellspring",
                "Restores 1 more mana a second a rank, up to 5 at rank 5.");
        spell("arcanist_shield_strength", "Reinforced Wards",
                "Raises the strength of the damage shields you grant by 10%% a rank, up to 50%% at rank 5.");
        spell("arcanist_spell_damage", "Arcane Mastery",
                "Raises the power of your spells by 10%% a rank, up to 50%% at rank 5. Physical damage is not "
                + "affected.");
        // Cleric.
        spell("cleric_debuff_resistance", "Unshaken Faith",
                "Gives a 5%% chance a rank to resist a harmful effect, up to 25%% at rank 5.");
        spell("cleric_heal_cooldown_reduced", "Swift Mending",
                "Your healing skills' cooldowns run 10%% faster a rank, up to 50%% faster at rank 5.");
        spell("cleric_healing_done", "Devotion",
                "Raises all healing you do by 10%% a rank, up to 50%% at rank 5.");
        spell("cleric_mana_regen", "Meditation",
                "Restores 1 more mana a second a rank, up to 5 at rank 5.");
        spell("cleric_shield_strength", "Blessed Aegis",
                "Raises the strength of the damage shields you grant by 10%% a rank, up to 50%% at rank 5.");
        // Gunner.
        spell("gunner_crit_chance_increased", "Steady Aim",
                "Raises critical chance by 2%% a rank, up to 10%% at rank 5. Bullets roll this chance too.");
        spell("gunner_increased_ranged_damage", "Heavy Rounds",
                "Adds 0.5 ranged weapon damage a rank, up to 2.5 at rank 5. With a gun, each rank is about 10%% "
                + "more bullet damage.");
        spell("gunner_movement_speed_boosted", "Run and Gun",
                "Raises movement speed by 5%% a rank, up to 25%% at rank 5.");
        spell("gunner_reduced_explosive_cooldowns", "Demolitions Expert",
                "Your explosive skills' cooldowns run 10%% faster a rank, up to 50%% faster at rank 5.");
        spell("gunner_reload_speed_increased", "Fast Hands",
                "Guns fire 5%% faster a rank, up to 25%% faster at rank 5. Bolt-action and pump guns cycle faster too.");
        // Monk: four base passives, then four an ascendancy.
        spell("monk_combo_discipline", "Combo Discipline",
                "Every third skill you cast within 10 seconds of the last raises your attack damage by 10%% a "
                + "rank for 6 seconds, up to 50%% at rank 5. Can trigger once every 5 seconds.");
        spell("monk_footwork", "Footwork",
                "Raises movement speed by 5%% a rank, up to 25%% at rank 5.");
        spell("monk_iron_breath", "Iron Breath",
                "Raises the healing you receive by 8%% a rank, up to 40%% at rank 5.");
        spell("monk_rhythm", "Rhythm",
                "Each weapon hit takes 0.25 seconds off your running skill cooldowns, rising to 0.45 seconds at "
                + "rank 5. Ultimates are not affected. Can trigger once every 0.5 seconds.");
        spell("monk_burning_spirit", "Burning Spirit",
                "Raises the damage of your burns by 10%% a rank, up to 50%% at rank 5.");
        spell("monk_fist_of_the_dragon", "Fist of the Dragon",
                "Raises critical damage by 12%% a rank, up to 60%% at rank 5.");
        spell("monk_momentum_chains", "Momentum Chains",
                "Landing two hits within 2 seconds grants 6 ability haste a rank for 5 seconds, up to 30 at rank "
                + "5. Can trigger once every 3 seconds.");
        spell("monk_relentless", "Relentless",
                "Your melee hits and damaging skills strike enemies at or below 30%% health again for 10%% of "
                + "your attack damage a rank, up to 50%% at rank 5.");
        spell("monk_unbroken_stance", "Unbroken Stance",
                "While your health is above 80%%, hits against you deal 6%% less damage a rank, up to 30%% less "
                + "at rank 5.");
        spell("monk_damage_resist", "Iron Skin",
                "Reduces the damage you take by 6%% a rank, up to 30%% at rank 5.");
        spell("monk_iron_constitution", "Iron Constitution",
                "Adds 2 maximum health a rank (1 heart), up to 10 at rank 5.");
        spell("monk_steadfast", "Steadfast",
                "Gives a 10%% chance a rank to resist a harmful effect, up to 50%% at rank 5.");
        spell("monk_evasion", "Evasion",
                "Raises your chance to dodge an attack by 6%% a rank, up to 30%% at rank 5.");
        spell("monk_night_discipline", "Night Discipline",
                "Your weapon hits strike enemies that have a harmful effect again for 8%% of your attack damage a"
                + " rank, up to 40%% at rank 5.");
        spell("monk_shadow_veins", "Shadow Veins",
                "Your physical damage heals you for 6%% of the damage dealt a rank, up to 30%% at rank 5.");
        spell("monk_venom_arts", "Venom Arts",
                "Raises the damage of your venom by 12%% a rank, up to 60%% at rank 5.");
        spell("monk_cleanse_mastery", "Cleanse Mastery",
                "Removing a harmful effect from yourself or an ally raises your movement speed by 10%% a rank for"
                + " 4 seconds, up to 50%% at rank 5. Can trigger once every 5 seconds.");
        spell("monk_gentle_guard", "Gentle Guard",
                "While you have a damage shield, hits against you deal 5%% less damage a rank, up to 25%% less at"
                + " rank 5.");
        spell("monk_spirit_reservoir", "Spirit Reservoir",
                "Restores 1.2 more mana a second a rank, up to 6 at rank 5.");
        spell("monk_tranquility", "Tranquility",
                "Raises the healing your own skills do to you by 10%% a rank, up to 50%% at rank 5.");
        spell("monk_electric_rhythm", "Electric Rhythm",
                "Landing three hits, each within 1 second of the last, raises critical chance by 5%% a rank for 4"
                + " seconds, up to 25%% at rank 5. Can trigger once every 3 seconds.");
        spell("monk_rapid_tempo", "Rapid Tempo",
                "Grants 6 ability haste a rank, up to 30 at rank 5: all your skill cooldowns run that much "
                + "faster.");
        spell("monk_slipstream", "Slipstream",
                "Using a movement skill raises your chance to dodge by 10%% a rank for 3 seconds, up to 50%% at "
                + "rank 5. Can trigger once every 5 seconds.");
        spell("monk_stormfoot", "Stormfoot",
                "Raises movement speed by 8%% a rank, up to 40%% at rank 5.");
        // Paladin.
        spell("paladin_cc_resistance", "Resolute",
                "Gives a 5%% chance a rank to resist a harmful effect, up to 25%% at rank 5.");
        spell("paladin_damage_resistance", "Bulwark",
                "Reduces the damage you take by 10%% a rank, up to 50%% at rank 5.");
        spell("paladin_healing_received", "Divine Favor",
                "Raises the healing you receive by 10%% a rank, up to 50%% at rank 5.");
        spell("paladin_increased_threat_generation", "Righteous Challenge",
                "Raises the threat you build against bosses by 10%% a rank, up to 50%% at rank 5.");
        spell("paladin_shield_strength", "Sacred Barrier",
                "Raises the strength of the damage shields you grant by 10%% a rank, up to 50%% at rank 5.");
        // Phantom.
        spell("phantom_bleed_damage_increased", "Deep Cuts",
                "Raises the damage of your bleeds by 10%% a rank, up to 50%% at rank 5.");
        spell("phantom_bonus_backstab_damage", "Ambusher",
                "Your melee hits and damaging skills from behind an enemy strike it again for 5%% of your attack "
                + "damage a rank, up to 25%% at rank 5.");
        spell("phantom_increased_crit_chance", "Lethal Precision",
                "Raises critical chance by 2%% a rank, up to 10%% at rank 5.");
        spell("phantom_movement_speed_increased", "Fleet Shadow",
                "Raises movement speed by 5%% a rank, up to 25%% at rank 5.");
        spell("phantom_reduced_stealth_cooldown", "Shroud Mastery",
                "Your stealth skills' cooldowns run 10%% faster a rank, up to 50%% faster at rank 5.");
        // Ranger.
        spell("ranger_bonus_damage_to_marked", "Marked Prey",
                "Your arrows and damaging skills strike enemies under Hunter's Mark or Predator Mark again for "
                + "10%% of your ranged weapon damage, rising by 5%% a rank to 30%% at rank 5.");
        spell("ranger_crit_chance_increased", "Keen Sight",
                "Raises critical chance by 2%% a rank, up to 10%% at rank 5.");
        spell("ranger_increased_ranged_damage", "Heavy Draw",
                "Adds 0.5 ranged weapon damage a rank, up to 2.5 at rank 5. Your bow skills gain from it while "
                + "you hold a bow or crossbow.");
        spell("ranger_movement_speed_increased", "Pathfinder",
                "Raises movement speed by 5%% a rank, up to 25%% at rank 5.");
        spell("ranger_trap_cooldowns_reduced", "Trapcraft",
                "Your trap skills' cooldowns run 10%% faster a rank, up to 50%% faster at rank 5.");
        // Reaper.
        spell("reaper_bonus_damage_to_plagued_enemies", "Blight Harvest",
                "Your melee hits and damaging skills strike envenomed enemies again for 10%% of your attack "
                + "damage, rising by 5%% a rank to 30%% at rank 5.");
        spell("reaper_damage_resistance", "Gravebound",
                "Reduces the damage you take by 5%% a rank, up to 25%% at rank 5.");
        spell("reaper_dot_duration_increased", "Lingering Rot",
                "Your damage over time effects last 1 second longer a rank, up to 5 seconds at rank 5.");
        spell("reaper_lifesteal_increased", "Soul Siphon",
                "Your physical damage heals you for 5%% of the damage dealt a rank, up to 25%% at rank 5.");
        spell("reaper_mana_regen_on_kill", "Reaped Essence",
                "Killing an enemy with a melee hit or a damaging skill restores 5%% of your maximum mana a rank, "
                + "up to 25%% at rank 5.");
        // Shifter.
        spell("shifter_bleed_damage_boosted", "Rending Claws",
                "Raises the damage of your bleeds by 10%% a rank, up to 50%% at rank 5.");
        spell("shifter_bonus_melee_damage", "Savage Strength",
                "Adds 0.5 attack damage a rank, up to 2.5 at rank 5.");
        spell("shifter_cc_resistance", "Untamed",
                "Gives a 5%% chance a rank to resist a harmful effect, up to 25%% at rank 5.");
        spell("shifter_increased_max_hp", "Hardy Frame",
                "Adds 2 maximum health a rank (1 heart), up to 10 at rank 5.");
        spell("shifter_reduced_form_cooldowns", "Fluid Forms",
                "Aspect Shift's cooldown runs 10%% faster a rank, up to 50%% faster at rank 5.");
        // Summoner.
        spell("summoner_mana_regen_increased", "Spirit Attunement",
                "Restores 1 more mana a second a rank, up to 5 at rank 5.");
        spell("summoner_reduced_summon_cooldowns", "Swift Summons",
                "Your summoning skills' cooldowns run 10%% faster a rank, up to 50%% faster at rank 5.");
        spell("summoner_shield_strength_increased", "Warding Pact",
                "Raises the strength of the damage shields you grant by 10%% a rank, up to 50%% at rank 5.");
        spell("summoner_summon_duration_increased", "Lasting Bond",
                "Your summons last 2 seconds longer a rank, up to 10 seconds at rank 5.");
        spell("summoner_summons_deal_increased_damage", "Empowered Summons",
                "Raises the power your summons draw from you by 10%% a rank, up to 50%% at rank 5.");
        // Sword Immortal.
        spell("sword_immortal_blade_intent", "Blade Intent",
                "Raises critical chance by 2%% a rank, up to 10%% at rank 5.");
        spell("sword_immortal_energy_channeling", "Energy Channeling",
                "Raises the power of your arcane skills by 10%% a rank, up to 50%% at rank 5. Physical damage is "
                + "not affected.");
        spell("sword_immortal_flow_state", "Flow State",
                "Using a movement skill raises your attack damage by 8%% a rank for 4 seconds, up to 40%% at rank"
                + " 5. Can trigger once every 5 seconds.");
        spell("sword_immortal_footwork", "Flowing Steps",
                "Your movement skills' cooldowns run 10%% faster a rank, up to 50%% faster at rank 5.");
        spell("sword_immortal_spirit_guard", "Spirit Guard",
                "Raises the strength of the damage shields you grant by 10%% a rank, up to 50%% at rank 5.");
        // Tactician.
        spell("tactician_bonus_hp", "Conditioning",
                "Adds 2 maximum health a rank (1 heart), up to 10 at rank 5.");
        spell("tactician_cooldowns_reduced", "Quick Orders",
                "Grants 6 ability haste a rank, up to 30 at rank 5: all your skill cooldowns run that much "
                + "faster.");
        spell("tactician_damage_resistance_increased", "Battle Hardened",
                "Reduces the damage you take by 5%% a rank, up to 25%% at rank 5.");
        spell("tactician_increased_threat_generation", "Commanding Presence",
                "Raises the threat you build against bosses by 10%% a rank, up to 50%% at rank 5.");
        spell("tactician_shield_strength", "Fortified Lines",
                "Raises the strength of the damage shields you grant by 10%% a rank, up to 50%% at rank 5.");
        // Trickster.
        spell("trickster_clone_damage_increased", "Volatile Decoys",
                "Adds 0.5 ranged weapon damage a rank, up to 2.5 at rank 5. Your decoys' burst scales with ranged"
                + " weapon damage.");
        spell("trickster_cooldown_reduction_after_dodge", "Narrow Escape",
                "Dodging an attack takes 0.5 seconds off your running skill cooldowns, rising by 0.25 seconds a "
                + "rank to 1.5 seconds at rank 5. Ultimates are not affected. Can trigger once every second.");
        spell("trickster_crit_chance_increased", "Sharp Wits",
                "Raises critical chance by 2%% a rank, up to 10%% at rank 5.");
        spell("trickster_increased_evasion", "Elusive",
                "Raises your chance to dodge an attack by 6%% a rank, up to 30%% at rank 5.");
        spell("trickster_movement_speed_boosted", "Quick Feet",
                "Raises movement speed by 5%% a rank, up to 25%% at rank 5.");
        // Universal.
        spell("universal_mana_pool", "Mana Reserves",
                "Adds 5 maximum mana a rank, up to 25 at rank 5.");
        spell("universal_mana_regen", "Mana Recovery",
                "Restores 1 more mana a second a rank, up to 5 at rank 5.");
    }

    private void spell(String path, String name, String description) {
        add("spell." + RiftfallCore.MODID + "." + path + ".name", name);
        add("spell." + RiftfallCore.MODID + "." + path + ".description", description);
    }

    /** The trinket rows copied at half value onto {@code riftfall:relic}. */
    private static final List<String> RELIC_AFFIX_TWINS = List.of("flat/crit_damage", "flat/projectile_damage",
            "flat/magic_power", "flat/summon_damage", "tiered/trinket_attack_damage", "flat/experience", "flat/luck",
            "flat/healing_received", "flat/school_power/blood", "flat/school_power/eldritch", "flat/school_power/ender",
            "flat/school_power/evocation", "flat/school_power/fire", "flat/school_power/holy", "flat/school_power/ice",
            "flat/school_power/lightning", "flat/school_power/nature");

    private final java.util.Map<String, String[]> affixWords = new java.util.HashMap<>();

    private void addAffix(String path, String prefix, String suffix) {
        affixWords.put(path, new String[] {prefix, suffix});
        add("affix." + RiftfallCore.MODID + ":" + path, prefix);
        add("affix." + RiftfallCore.MODID + ":" + path + ".suffix", suffix);
    }

    /**
     * The gear stats' tooltip names. Only ours: tenacity's name used to be borrowed here, and is
     * not since mrpg-lib 2.8.0 ships {@code attribute.name.mrpg_lib.tenacity} itself.
     */
    private void addAttributes() {
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("guard")), "Guard");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("true_damage")), "True Damage");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("scavenging")), "Scavenging");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("resistance")), "Resistance");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("damage_resistance")), "Damage Resistance");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("resist_pierce")), "Resistance Pierce");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("max_mana")), "Max Mana");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("mana_regen")), "Mana Regeneration");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("max_charge")), "Max Charge");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("charge_regen")), "Charge Regeneration");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("ability_haste")), "Ability Haste");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("self_healing")), "Self Healing");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("shield_strength")), "Shield Strength");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("threat_generation")), "Threat Generation");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("healing_done")), "Healing Done");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("fire_rate")), "Gun Fire Rate");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("summon_power")), "Summon Power");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("summon_duration")), "Summon Duration");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("heal_haste")), "Heal Haste");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("movement_haste")), "Movement Haste");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("summon_haste")), "Summon Haste");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("trap_haste")), "Trap Haste");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("explosive_haste")), "Explosive Haste");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("form_haste")), "Form Haste");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("stealth_haste")), "Stealth Haste");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("shielded_reduction")), "Damage Reduction While Shielded");
        addEffect(RiftfallEffects.HASTENED, "Hastened");
        addEffect(RiftfallEffects.EVASIVE, "Evasive");
        addEffect(RiftfallEffects.KEEN, "Keen");
        addEffect(RiftfallEffects.LETHAL, "Lethal");
        addEffect(RiftfallEffects.INVIGORATED, "Invigorated");
        addEffect(RiftfallEffects.BLINDED, "Blinded");
        addEffect(RiftfallEffects.BEAR_ASPECT, "Bear Aspect");
        addEffect(RiftfallEffects.TIGER_ASPECT, "Tiger Aspect");
        // H6's DoTs: the effects, their damage bonus and their death messages.
        addEffect(RiftfallEffects.BLEEDING, "Bleeding");
        addEffect(RiftfallEffects.BURNING, "Burning");
        addEffect(RiftfallEffects.BERSERK, "Berserk");
        addEffect(RiftfallEffects.ENVENOMED, "Envenomed");
        // The Reaper batch.
        addEffect(RiftfallEffects.VAMPIRIC, "Vampiric");
        addEffect(RiftfallEffects.PARRYING, "Parrying");
        addEffect(RiftfallEffects.REFLECTING, "Reflecting");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("dot_duration")), "DoT Duration");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("bleed_damage")), "Bleed Damage");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("burn_damage")), "Burn Damage");
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("venom_damage")), "Venom Damage");
        // The Arcanist batch.
        add(RiftfallAttributes.descriptionId(RiftfallCore.id("spell_damage")), "Spell Damage");
        add("death.attack.riftfall.bleed", "%1$s bled out");
        add("death.attack.riftfall.bleed.player", "%1$s bled out at the hands of %2$s");
        add("death.attack.riftfall.burn", "%1$s burned away");
        add("death.attack.riftfall.burn.player", "%1$s was burned away by %2$s");
        add("death.attack.riftfall.venom", "%1$s succumbed to venom");
        add("death.attack.riftfall.venom.player", "%1$s succumbed to %2$s's venom");
        add("death.attack.riftfall.unseated", "%1$s was struck down by %2$s");
        // Relic signature procs and costs.
        add("death.attack.riftfall.relic", "%1$s was undone by their own relic");
        add("death.attack.riftfall.relic.player", "%1$s fell to %2$s's relic");
        add("tooltip.riftfall.relic_signature.double_jump", "Double Jump");
        add("tooltip.riftfall.relic_signature.double_jump.effect", "One extra jump in mid-air, back when you land");
        add("tooltip.riftfall.relic_signature.wall_cling", "Wall Cling");
        add("tooltip.riftfall.relic_signature.wall_cling.effect",
                "Hold sneak against a wall in mid-air to slide down slowly, up to 2 s a fall; jump to kick off, once a wall");
        add("tooltip.riftfall.relic_signature.cost", "Cost: %s");
        add("tooltip.riftfall.relic_signature.dormant", "Dormant: two other signatures are already active");
        add("tooltip.riftfall.relic_signature.momentum", "Momentum");
        add("tooltip.riftfall.relic_signature.momentum.effect",
                "Sprinting builds speed, +2%% each half second up to +12%%; a hit, a stop or a second in the air ends it");
        add("tooltip.riftfall.relic_signature.nightstalker", "Nightstalker");
        add("tooltip.riftfall.relic_signature.nightstalker.effect", "+0.20 crit damage at light %s or less");
        add("tooltip.riftfall.relic_signature.nightstalker.cost", "-10%% damage at light %s or more");
        add("tooltip.riftfall.relic_signature.nightstalker.light", "Light here: %s");
        add("tooltip.riftfall.relic_signature.fireborn", "Fireborn");
        add("tooltip.riftfall.relic_signature.fireborn.effect",
                "Fire, lava and burning hurt you 75%% less; +15%% damage while you burn");
        add("tooltip.riftfall.relic_signature.fireborn.cost", "water and rain hurt you for 2%% of max health a second");
        add("tooltip.riftfall.relic_signature.dead_weight", "Dead Weight");
        add("tooltip.riftfall.relic_signature.dead_weight.effect", "+15%% max health, +0.25 knockback resistance");
        add("tooltip.riftfall.relic_signature.dead_weight.cost", "-10%% movement speed, -30%% swim speed");
        add("tooltip.riftfall.relic_signature.pack_leader", "Pack Leader");
        add("tooltip.riftfall.relic_signature.pack_leader.effect", "+25%% summon power, +10 s summon duration");
        add("tooltip.riftfall.relic_signature.pack_leader.cost", "-10%% max health");
        add("tooltip.riftfall.relic_signature.berserker_rage", "Berserker Rage");
        add("tooltip.riftfall.relic_signature.berserker_rage.effect",
                "Each hit adds a stack, 5 at most, gone 3 s after the last: +5%% damage a stack");
        add("tooltip.riftfall.relic_signature.berserker_rage.cost", "+4%% damage taken a stack");
        add("tooltip.riftfall.relic_signature.ricochet", "Ricochet");
        add("tooltip.riftfall.relic_signature.ricochet.effect",
                "15%% of hits throw a glaive at up to 3 more enemies within 8 blocks, 25%% of the hit each (once a second at most)");
        add("tooltip.riftfall.relic_signature.spore_burst", "Spore Burst");
        add("tooltip.riftfall.relic_signature.spore_burst.effect",
                "Dropping below 35%% health envenoms enemies within 5 blocks and halves their healing for 5 s; once a minute");
        add("tooltip.riftfall.relic_signature.last_rites", "Last Rites");
        add("tooltip.riftfall.relic_signature.last_rites.effect", "A kill heals 3%% of your max health, 6%% for the undead");
        add("tooltip.riftfall.relic_signature.soul_stacks", "Soul Stacks");
        add("tooltip.riftfall.relic_signature.soul_stacks.effect", "Each kill: +2%% spell damage, up to +20%% at 10 stacks");
        add("tooltip.riftfall.relic_signature.soul_stacks.cost",
                "a hit of 15%% of your max health or more halves the stacks; dying clears them");
        add("tooltip.riftfall.relic_signature.torment", "Torment");
        add("tooltip.riftfall.relic_signature.torment.effect",
                "Magic hits torment for 3 s: 1%% of the target's max health a second, at most your attack damage");
        add("tooltip.riftfall.relic_signature.ground_pound", "Ground Pound");
        add("tooltip.riftfall.relic_signature.ground_pound.effect",
                "Sneak through a fall of 4+ blocks: no fall damage, and the landing hits enemies within 3 blocks for 20%% attack damage per block fallen (10 at most); every 8 s");
        add("tooltip.riftfall.relic_signature.greed", "Greed");
        add("tooltip.riftfall.relic_signature.greed.effect", "+0.05 scavenging, +15%% experience");
        add("tooltip.riftfall.relic_signature.greed.cost", "spawners you trigger add a mob; +25%% threat generation");
        add("tooltip.riftfall.relic_signature.unstable_step", "Unstable Step");
        add("tooltip.riftfall.relic_signature.unstable_step.effect", "+30 movement haste");
        add("tooltip.riftfall.relic_signature.unstable_step.cost", "each movement skill costs 10 more mana");
        add("tooltip.riftfall.relic_signature.last_stand", "Last Stand");
        add("tooltip.riftfall.relic_signature.last_stand.effect",
                "Below 30%% health: +25%% damage and +0.20 damage resistance");
        add("tooltip.riftfall.relic_signature.last_stand.cost", "your hunger bar heals you no more");
        add("tooltip.riftfall.relic_signature.berserk", "Berserk");
        add("tooltip.riftfall.relic_signature.berserk.effect",
                "Melee hits have a 10%% chance of +30%% attack speed for 6 s (once in 15 s at most)");
        add("tooltip.riftfall.relic_signature.berserk.cost", "no skills while Berserk lasts");
        add("tooltip.riftfall.relic_signature.frostbound", "Frostbound");
        add("tooltip.riftfall.relic_signature.frostbound.effect",
                "Enemies within 4 blocks deal 12%% less damage to you and move 15%% slower");
        add("tooltip.riftfall.relic_signature.frostbound.cost", "-10%% movement speed");
        add("message.riftfall.berserk_no_cast", "Berserk: no skills until it passes");
        add("tooltip.riftfall.relic_signature.ground_pound.effect_knockback",
                "Sneak through a fall of 4+ blocks: no fall damage, and the landing knocks back enemies within 3 blocks; every 8 s");

        // Walked rather than listed, so a primary added to the enum arrives with a name instead of
        // a raw key on the tooltip. The display name is the enum's own, title-cased.
        for (StatBlock.Primary primary : StatBlock.Primary.values()) {
            add(RiftfallAttributes.descriptionId(RiftfallCore.id(primary.key())), title(primary.key()));
        }
    }

    /**
     * Ascendancy and school names, which the book and chat otherwise make readable from the id. The
     * English is that same readable form, so nothing on screen changes until a translation names them.
     * Read from the skill nodes beside the output: src/generated/resources -> src/main/resources.
     */
    private void addCodexNames() {
        Path nodes = output.getOutputFolder().getParent().getParent()
                .resolve(Path.of("main", "resources", "data", RiftfallCore.MODID, "skill_node"));
        Set<String> ascendancies = new TreeSet<>();
        Set<String> schools = new TreeSet<>();
        try (Stream<Path> files = Files.list(nodes)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject node = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (node.has("class") && node.get("class").getAsString().equals(SkillTable.DEV_CLASS)) {
                    continue;
                }
                if (node.has("ascendancy")) {
                    ascendancies.add(node.get("ascendancy").getAsString());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("skill nodes not found at " + nodes, e);
        }
        Path spells = nodes.resolveSibling("spell");
        try (Stream<Path> files = Files.walk(spells)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject spell = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (spell.has("school")) {
                    String school = spell.get("school").getAsString();
                    schools.add(school.substring(school.indexOf(':') + 1));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("spells not found at " + spells, e);
        }
        ascendancies.forEach(id -> add("ascendancy." + RiftfallCore.MODID + "." + id, SkillCommand.readable(id)));
        schools.forEach(path -> add("school." + RiftfallCore.MODID + "." + path, SkillCommand.readable(path)));
        // Class weapon families, named in the skill book and the wrong-weapon line; the English is the data's.
        Set<String> families = new TreeSet<>();
        Path loot = nodes.resolveSibling("class_loot");
        try (Stream<Path> files = Files.list(loot)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject classLoot = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                classLoot.getAsJsonArray("families").forEach(f -> families.add(f.getAsJsonObject().get("name").getAsString()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("class loot not found at " + loot, e);
        }
        families.forEach(name -> add(SkillWeapons.familyKey(name), name));
    }

    /** HUD bar labels, the HUD editor, the inventory's recipe button, the pause menu's sky credits and the credits roll. */
    private void addInterface() {
        add("hud." + RiftfallCore.MODID + ".mana", "Mana");
        add("hud." + RiftfallCore.MODID + ".charge", "Charge");
        add("hud." + RiftfallCore.MODID + ".rift_clock", "Rift");
        add("hud." + RiftfallCore.MODID + ".shield", "Shield");
        add("hud." + RiftfallCore.MODID + ".party", "Party");
        add("hud." + RiftfallCore.MODID + ".party.offline", "offline");
        add("hud." + RiftfallCore.MODID + ".relic_stacks", "Relic Stacks");
        // The boss finder's word and distance: placeholders for the voice pass.
        add("hud." + RiftfallCore.MODID + ".boss_finder", "Boss");
        add("hud." + RiftfallCore.MODID + ".boss_finder.distance", "%sm");
        String editor = "screen." + RiftfallCore.MODID + ".hud_editor";
        add(editor, "HUD editor");
        add(editor + ".open", "Edit HUD");
        add(editor + ".hint", "Drag to move. Click to select, then rotate or resize.");
        add(editor + ".rotate", "Rotate");
        add(editor + ".split", "Split");
        add(editor + ".merge", "Merge");
        add(editor + ".reset", "Reset");
        add(editor + ".hide", "Hide");
        add(editor + ".show", "Show");
        add(editor + ".scale", "Size: %s%%");
        add("screen." + RiftfallCore.MODID + ".inventory.recipe_book", "Recipe book");
        String credits = "screen." + RiftfallCore.MODID + ".sky_credits.";
        add(credits + "title", "Shattered sky: image credits");
        add(credits + "open", "Sky image credits");
        add(credits + "nasa_link", "NASA source & credits");
        add(credits + "eso_link", "ESO source & credits");
        add(credits + "license_link", "Carina license: CC BY 4.0");
        // Attribution lines: translate the labels, keep the credited names exactly.
        add(credits + "stars", "Stars: NASA's Scientific Visualization Studio");
        add(credits + "stars_map", "Deep Star Maps 2020, Ernie Wright");
        add(credits + "carina", "Carina: ESO/J. Emerson/M. Irwin/J. Lewis");
        add(credits + "adapted", "Adapted: color, projection, layering and animation.");
        add(credits + "no_endorsement", "Sources do not endorse Riftfall.");
        // The credits roll's fixed lines; the credited names and ATTRIBUTION.md's prose stay literal in credits.json.
        String roll = AttributionCredits.KEY;
        add(roll + "open", "Credits");
        add(roll + "title", "Credits");
        add(roll + "hint", "Hold Space to speed up, scroll to scrub, T to tilt or flatten, Esc to close");
        add(roll + "created_by", "Created by");
        add(roll + "wotr", "Dungeon pieces from the Wanderers of the Rift community");
        add(roll + "total", "%s pieces by %s builders");
        add(roll + "pieces", "%s pieces");
        add(roll + "piece", "%s piece");
        add(roll + "unidentified", "Builders not yet identified");
        add(roll + "unidentified.note", "Credit gladly added on request.");
        add(roll + "built_for", "Built for Riftfall");
        add(roll + "models", "Models");
        add(roll + "sky", "The sky");
        add(roll + "end", "Thank you for playing Riftfall.");
        add(roll + "unavailable", "The credits could not be loaded.");
    }

    /** The character sheet. The tier is a number, so the ladder needs no keys of its own. */
    private void addProgression() {
        // Every key starts "Riftfall" so the controls search finds them all; RiftKeysTest holds this.
        add("key.categories." + RiftfallCore.MODID, "Riftfall");
        add("key." + RiftfallCore.MODID + ".sheet", "Riftfall Character Sheet");
        add("key." + RiftfallCore.MODID + ".skills", "Riftfall Skills Screen");
        add("key." + RiftfallCore.MODID + ".classes", "Riftfall Class Screen");
        add("key." + RiftfallCore.MODID + ".notes", "Riftfall Recovered Notes");
        add("key." + RiftfallCore.MODID + ".social", "Riftfall Party Screen");
        // The class screen's per-class lines: name, role, blurb, weapon and icon glyph.
        ClassLang.lines().forEach(this::add);
        // The rift recap and the run history behind /riftfall recap.
        RecapLang.lines().forEach(this::add);
        for (int slot = 1; slot <= 8; slot++) {
            add("key." + RiftfallCore.MODID + ".cast_slot_" + slot, "Riftfall Cast Skill " + slot);
        }
        add("key." + RiftfallCore.MODID + ".cast_ultimate", "Riftfall Cast Ultimate");
        add("key." + RiftfallCore.MODID + ".edit_hud", "Riftfall Edit HUD");
        // The tab list's objective name (TabLevel), seen in /scoreboard and a sidebar.
        add(com.cyberday1.riftfall.progression.TabLevel.NAME_KEY, "Level");
        // The tab list suffix while a player is in a rift (RiftTabName).
        add(com.cyberday1.riftfall.rift.RiftTabName.KEY, com.cyberday1.riftfall.rift.RiftTabName.FALLBACK);
        // The book's pages: every line a page lists, in BookLang's English.
        for (ProgressionTab tab : ProgressionTab.values()) {
            add(tab.titleKey(), BookLang.text(tab.page(), "title"));
            for (String line : tab.lines()) {
                add(tab.key(line), BookLang.text(tab.page(), line));
            }
        }
    }

    private static String title(String key) {
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    /**
     * Everything {@code /riftfall party} says.
     *
     * <p>
     * Split out because it is half the strings in the file and none of it has anything to do with the
     * rest. Every refusal names what to do instead where there is something to do: a player who is
     * told only that a command did not work has been given a puzzle rather than an answer.
     */
    private void addParty() {
        String prefix = "message." + RiftfallCore.MODID + ".party_";

        add(prefix + "created", "You have started a party. Invite someone on the Party bookmark (J).");
        add(prefix + "already_in", "You are already in a party. Leave it first.");
        add(prefix + "not_in", "You are not in a party. Start one on the Party bookmark (J).");
        add(prefix + "full", "Your party is already at its limit of %s.");

        add(prefix + "invite_self", "You cannot invite yourself.");
        add(prefix + "invite_taken", "%s is already in a party.");
        // Deliberately does not say how long the offer stands. The number lives in one place, in
        // RiftInvites, and a sentence here repeating it is a sentence that goes quietly wrong.
        add(prefix + "invite_sent", "Invited %s.");
        // The same thing said by somebody who had no party a moment ago. Inviting starts one, and
        // that is worth one clause rather than being left to be noticed.
        add(prefix + "invite_sent_new", "You have started a party, and invited %s.");
        // The line the invitation arrives on. It ends with a space because the two buttons are
        // appended to it, and a translator moving the name has to keep that.
        add(prefix + "invite_offer", "%s has invited you to their party. ");
        add(prefix + "invite_accept", "[Accept]");
        add(prefix + "invite_decline", "[Decline]");
        add(prefix + "invite_accept_hover", "Join %s's party");
        add(prefix + "invite_decline_hover", "Turn down %s's invitation");
        add(prefix + "invite_none", "%s has not invited you to a party.");
        add(prefix + "invite_expired", "%s's invitation has expired. Ask them to send another.");
        add(prefix + "invite_gone", "That party no longer exists.");
        add(prefix + "invites_none", "Nobody has invited you to a party.");
        add(prefix + "invites_header", "%s invitation(s) waiting:");

        add(prefix + "joined", "You joined the party.");
        add(prefix + "joined_others", "%s joined the party.");
        add(prefix + "declined", "You turned down %s's invitation.");
        add(prefix + "declined_others", "%s turned down your invitation.");

        add(prefix + "left", "You left the party.");
        add(prefix + "left_others", "%s left the party.");
        add(prefix + "leads_now", "%s now leads the party.");
        add(prefix + "not_leader", "Only the party leader can do that.");
        add(prefix + "not_member", "%s is not in your party.");
        add(prefix + "remove_self", "You cannot remove yourself: leave the party instead.");
        add(prefix + "removed", "You were removed from the party.");
        add(prefix + "removed_others", "%s was removed from the party.");
        add(prefix + "disbanded", "The party has disbanded.");

        add(prefix + "header", "Your party, %s of %s:");
        add(prefix + "member", "  %s");
        add(prefix + "member_leader", "  %s (leader)");
        // Wraps a member line rather than replacing it, so the leader marker and the away marker can
        // both be on one person without a fourth string saying so.
        add(prefix + "member_offline", "%s (away)");
    }

    /**
     * {@code Processor Block 1 Stairs [Wall]}, {@code Processor Block 9}.
     */
    private static String displayName(ProcessorSlot slot, ProcessorVariant variant) {
        StringBuilder name = new StringBuilder("Processor Block ").append(slot.number());
        if (!variant.displayName().isEmpty()) {
            name.append(' ').append(variant.displayName());
        }
        if (slot.semanticName() != null) {
            name.append(" [").append(slot.semanticName()).append(']');
        }
        return name.toString();
    }
}
