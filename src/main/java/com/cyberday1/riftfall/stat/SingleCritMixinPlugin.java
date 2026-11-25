package com.cyberday1.riftfall.stat;

import com.cyberday1.riftfall.skill.SpellEngineCompat;
import net.neoforged.fml.loading.LoadingModList;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Applies each mixin in {@code riftfall.compat.mixins.json} only when every mod it targets is loaded,
 * and Spell Engine only at a version {@link SpellEngineCompat} supports: the heal and ability-gate
 * mixins (offer filter, melee hit, the price on its own cast path) reach into its internals, which
 * is the part most likely to move between versions.
 */
public final class SingleCritMixinPlugin implements IMixinConfigPlugin {

    /** Mixin simple name to the mod ids it needs. A mixin missing here never applies. */
    static final Map<String, List<String>> REQUIRES = Map.ofEntries(
            Map.entry("MixinStellarQuestIcons", List.of("ftbquests", "ftblibrary")),
            Map.entry("MixinStellarCuriosScreen", List.of("curios")),
            Map.entry("MixinStellarCuriosCosmetics", List.of("curios")),
            Map.entry("MixinStellarCuriosPages", List.of("curios")),
            Map.entry("MixinStellarCuriosVisibility", List.of("curios")),
            Map.entry("MixinStellarCuriosToggle", List.of("curios")),
            Map.entry("MixinStellarNpcTabs", List.of("customnpcs")),
            Map.entry("MixinCnpcResidentName", List.of("customnpcs")),
            Map.entry("MixinSporeEvolution", List.of("spore")),
            Map.entry("MixinSporeSlasherDamage", List.of("spore")),
            Map.entry("MixinSporeAreaAttack", List.of("spore")),
            Map.entry("MixinSporeCorrosion", List.of("spore")),
            Map.entry("MixinSporeTumor", List.of("spore")),
            Map.entry("MixinSporeHyperEvolution", List.of("spore")),
            Map.entry("MixinSporeInfected", List.of("spore")),
            Map.entry("MixinSporeConsumeRemains", List.of("spore")),
            Map.entry("MixinSporeBrute", List.of("spore")),
            Map.entry("MixinSporeThrownBlock", List.of("spore")),
            Map.entry("MixinSporeAcid", List.of("spore")),
            Map.entry("MixinSporeInfection", List.of("spore")),
            // Spore's spread outside rifts.
            Map.entry("MixinSporeFoliageSpread", List.of("spore")),
            Map.entry("MixinSporeCasing", List.of("spore")),
            Map.entry("MixinSporeFishing", List.of("spore")),
            Map.entry("MixinSporePickaxe", List.of("spore")),
            Map.entry("MixinStellarAetherInventory", List.of("aether", "accessories")),
            Map.entry("MixinStellarAccessoryPatches", List.of("accessories", "owo")),
            Map.entry("MixinStellarAccessorySurfaces", List.of("accessories", "owo")),
            Map.entry("MixinStellarAccessorySlots", List.of("accessories", "owo")),
            Map.entry("MixinStellarAccessorySlot", List.of("accessories", "owo")),
            Map.entry("MixinStellarAccessoryToggle", List.of("accessories")),
            Map.entry("MixinStellarSophisticatedHelper", List.of("sophisticatedcore")),
            Map.entry("MixinStellarSophisticatedScreen", List.of("sophisticatedcore")),
            Map.entry("MixinStellarSophisticatedSettings", List.of("sophisticatedcore")),
            Map.entry("MixinStellarSophisticatedLabel", List.of("sophisticatedcore")),
            Map.entry("MixinStellarSophisticatedSearch", List.of("sophisticatedcore")),
            Map.entry("MixinStellarSophisticatedScroll", List.of("sophisticatedcore")),
            Map.entry("MixinStellarScrollPanelColors", List.of("sophisticatedcore")),
            Map.entry("MixinBuildASpellSave", List.of("buildaspell")),
            Map.entry("MixinBuildASpellCasting", List.of("buildaspell")),
            Map.entry("MixinPuffishNoTreesToast", List.of("puffish_skills")),
            Map.entry("MixinEnhancedBossBarsStack", List.of("enhanced_boss_bars")),
            Map.entry("MixinScytheDeferredDrops", List.of("scythes")),
            Map.entry("MixinScytheSoulStorage", List.of("scythes")),
            Map.entry("MixinScytheActivation", List.of("scythes")),
            Map.entry("MixinScytheBloodPassive", List.of("scythes")),
            Map.entry("MixinScytheFireHeld", List.of("scythes")),
            Map.entry("MixinScytheFrozenHeld", List.of("scythes")),
            Map.entry("MixinScytheGoldenHeld", List.of("scythes")),
            Map.entry("MixinScytheFarmerHeld", List.of("scythes")),
            Map.entry("MixinOpScytheActivation", List.of("op_scythes")),
            Map.entry("MixinOpScytheEffects", List.of("op_scythes")),
            Map.entry("MixinLegendaryHelmetTeleport", List.of("legendary_monsters")),
            // Relics curios keep Riftfall affixes only.
            Map.entry("MixinCurioSilence", List.of("curios")),
            Map.entry("MixinRelicsEquipped", List.of("relics")),
            Map.entry("MixinRelicsCastSlot", List.of("relics")),
            // Held relics do nothing inside a rift (2026-11-20).
            Map.entry("MixinRelicsRiftAbility", List.of("relics")),
            Map.entry("MixinRelicsRiftPortal", List.of("relics")),
            // The ham's Marinade is off everywhere (2026-11-24).
            Map.entry("MixinRelicsHamMarinade", List.of("relics")),
            Map.entry("MixinCurioSilenceSpells", List.of("spell_engine", "curios")),
            // A silenced relic's dead spell text is off its tooltip.
            Map.entry("MixinSpellTooltipSilence", List.of("spell_engine")),
            Map.entry("MixinBetterCombatWeapons", List.of("bettercombat")),
            Map.entry("MixinBetterCombatParties", List.of("bettercombat")),
            Map.entry("MixinSpellParties", List.of("spell_engine")),
            Map.entry("MixinBetterMobCombat", List.of("better_mob_combat_reimagined")),
            Map.entry("MixinBetterCombatCasting", List.of("bettercombat", "spell_engine")),
            Map.entry("MixinBetterCombatInput", List.of("bettercombat", "spell_engine")),
            Map.entry("MixinSpellAnimationCombat", List.of("bettercombat", "spell_engine")),
            Map.entry("SpellAnimationAccess", List.of("bettercombat", "spell_engine")),
            Map.entry("MixinPermanentHomeTeams", List.of("openpartiesandclaims")),
            Map.entry("MixinSpellPowerResult", List.of("spell_power")),
            Map.entry("MixinCritLogic", List.of("critical_strike")),
            Map.entry("MixinWeaponImplicitDamage", List.of("simplyswords")),
            Map.entry("MixinSimplySwordsSpearLoyalty", List.of("simplyswords")),
            Map.entry("MixinALConfig", List.of("apothic_attributes")),
            Map.entry("MixinApotheosisTutorial", List.of("apotheosis")),
            Map.entry("MixinApotheosisAugments", List.of("apotheosis")),
            Map.entry("MixinApotheosisTierJoin", List.of("apotheosis")),
            Map.entry("MixinApotheosisTierCommand", List.of("apotheosis")),
            Map.entry("MixinApotheosisTierKey", List.of("apotheosis")),
            // The Apotheosis takeover: anvils and free socketing, then CEI's belt machines.
            Map.entry("MixinApotheosisGemSmashing", List.of("apotheosis")),
            Map.entry("MixinApotheosisFreeSocketing", List.of("apotheosis")),
            Map.entry("MixinCeiGemCutter", List.of("apotheosis", "create_enchantment_industry")),
            Map.entry("MixinCeiAffixAugmentor", List.of("apotheosis", "create_enchantment_industry")),
            Map.entry("MixinSpellImpactsHeal", List.of("spell_engine", "apothic_attributes")),
            Map.entry("MixinSpellContainerSource", List.of("spell_engine")),
            Map.entry("MixinMelee", List.of("spell_engine")),
            Map.entry("MixinOrientedBoundingBox", List.of("spell_engine")),
            Map.entry("MixinSpellExecution", List.of("spell_engine")),
            Map.entry("MixinBlinkDestination", List.of("spell_engine")),
            Map.entry("MixinSpellTarget", List.of("spell_engine")),
            Map.entry("MixinSkillProjectileRenderer", List.of("spell_engine")),
            Map.entry("MixinOrientedSpellModels", List.of("spell_engine")),
            Map.entry("MixinSpellProjectile", List.of("spell_engine")),
            Map.entry("MixinSpellCost", List.of("spell_engine")),
            Map.entry("MixinAmmo", List.of("spell_engine")),
            Map.entry("MixinSpellImpactsDrain", List.of("spell_engine")),
            Map.entry("MixinIronsSpell", List.of("irons_spellbooks")),
            Map.entry("MixinIronsAffixCast", List.of("irons_spellbooks", "irons_apothic")),
            // The reaction triggers' dodge event.
            Map.entry("MixinIronsDodge", List.of("irons_lib")),
            Map.entry("MixinApothicDodge", List.of("apothic_attributes")),
            // The Shifter's Woodwalkers forms.
            Map.entry("MixinWalkersBody", List.of("walkers")),
            Map.entry("MixinWalkersPlayerBody", List.of("walkers")),
            // A player's gun needs no ammo, and a free magazine never comes back as rounds.
            Map.entry("MixinTaczAmmoCheck", List.of("tacz")),
            Map.entry("MixinTaczDropAllAmmo", List.of("tacz")),
            // The 1.2.0-hotfix Sable trace skips tacz-tweaks' raytracer, so bullets hit nothing.
            Map.entry("MixinTaczSableRayTrace", List.of("tacz", "tacztweaks")),
            // Iris sky/border fixes for shattered_home (Issue 1 + Issue 5).
            Map.entry("MixinIrisLevelRendererSky", List.of("iris")),
            // Rifts take the shader pack's End lighting.
            Map.entry("MixinIrisRiftLighting", List.of("iris")),
            // The Null Servant's void-sky shader draws nothing under a pack.
            Map.entry("MixinNullServantShaderPack", List.of("voidscape", "iris")),
            Map.entry("MixinWorldBorderAlpha", List.of()));

    /** {@code versionOf} answers a loaded mod's version, or empty when the mod is absent. */
    static boolean applies(String mixinClassName, Function<String, Optional<String>> versionOf) {
        List<String> mods = REQUIRES.get(mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1));
        return mods != null && mods.stream().allMatch(mod -> versionOf.apply(mod)
                .filter(version -> !mod.equals("spell_engine") || SpellEngineCompat.supports(version))
                .isPresent());
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        LoadingModList mods = LoadingModList.get();
        return mods != null && applies(mixinClassName, mod -> mods.getMods().stream()
                .filter(info -> info.getModId().equals(mod))
                .findFirst()
                .map(info -> info.getVersion().toString()));
    }

    /** The one line a gated-off Spell Engine gets, or empty when it is absent or supported. */
    static Optional<String> gateWarning(Optional<String> spellEngineVersion) {
        return spellEngineVersion.filter(version -> !SpellEngineCompat.supports(version))
                .map(version -> "Spell Engine " + version + " is outside the range Riftfall supports, "
                        + SpellEngineCompat.range() + ": its Spell Engine mixins are off");
    }

    // log4j by name: this runs before any mod class is constructed.
    @Override
    public void onLoad(String mixinPackage) {
        LoadingModList mods = LoadingModList.get();
        if (mods == null) return;
        gateWarning(mods.getMods().stream()
                .filter(info -> info.getModId().equals("spell_engine"))
                .findFirst()
                .map(info -> info.getVersion().toString()))
                .ifPresent(LogManager.getLogger("riftfall")::warn);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
