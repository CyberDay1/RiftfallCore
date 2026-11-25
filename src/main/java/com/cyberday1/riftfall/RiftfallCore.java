package com.cyberday1.riftfall;

import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import com.cyberday1.riftfall.boss.ArenaSeal;
import com.cyberday1.riftfall.boss.BossGallery;
import com.cyberday1.riftfall.boss.BossGate;
import com.cyberday1.riftfall.boss.BossSpawn;
import com.cyberday1.riftfall.boss.RiftBosses;
import com.cyberday1.riftfall.block.processor.ProcessorBlocks;
import com.cyberday1.riftfall.data.RiftfallDataGenerators;
import com.cyberday1.riftfall.home.ShatteredHomeJoinHandler;
import com.cyberday1.riftfall.home.ShatteredHomeLevels;
import com.cyberday1.riftfall.gear.AffixTiers;
import com.cyberday1.riftfall.gear.ClassLoot;
import com.cyberday1.riftfall.gear.CombatStats;
import com.cyberday1.riftfall.gear.EnchantmentBan;
import com.cyberday1.riftfall.gear.EquipmentSetBan;
import com.cyberday1.riftfall.gear.GearPool;
import com.cyberday1.riftfall.gear.RiftArms;
import com.cyberday1.riftfall.gear.RiftTrinkets;
import com.cyberday1.riftfall.gear.StarterKits;
import com.cyberday1.riftfall.gear.WeaponFloor;
import com.cyberday1.riftfall.init.RiftfallAttributes;
import com.cyberday1.riftfall.init.RiftfallEffects;
import com.cyberday1.riftfall.init.RiftfallBlockEntities;
import com.cyberday1.riftfall.init.RiftfallBlocks;
import com.cyberday1.riftfall.init.RiftfallCreativeTabs;
import com.cyberday1.riftfall.init.RiftfallAttachments;
import com.cyberday1.riftfall.init.RiftfallDataComponents;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.init.RiftfallLootEntries;
import com.cyberday1.riftfall.init.RiftfallMenus;
import com.cyberday1.riftfall.init.RiftfallRecipes;
import com.cyberday1.riftfall.init.RiftfallParticles;
import com.cyberday1.riftfall.init.RiftfallProcessors;
import com.cyberday1.riftfall.init.RiftfallRegistries;
import com.cyberday1.riftfall.init.RiftfallSounds;
import com.cyberday1.riftfall.network.RiftfallPayloads;
import com.cyberday1.riftfall.rift.RiftCommand;
import com.cyberday1.riftfall.rift.RiftDeaths;
import com.cyberday1.riftfall.rift.RiftIntegrity;
import com.cyberday1.riftfall.rift.RiftInvites;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.cyberday1.riftfall.rift.RiftRejoin;
import com.cyberday1.riftfall.rift.RiftRuns;
import com.cyberday1.riftfall.rift.RiftSpawns;
import com.cyberday1.riftfall.rift.RiftTeleports;
import com.cyberday1.riftfall.rift.RiftWarmup;
import com.cyberday1.riftfall.rift.RoomCaption;
import com.cyberday1.riftfall.rift.PartyHudSync;
import com.cyberday1.riftfall.shield.ShieldSync;
import com.cyberday1.riftfall.shield.ShieldedReduction;
import com.cyberday1.riftfall.skill.AbilityGrants;
import com.cyberday1.riftfall.skill.GunnerGuns;
import com.cyberday1.riftfall.skill.PassiveModifiers;
import com.cyberday1.riftfall.skill.ManaCombat;
import com.cyberday1.riftfall.skill.RangedArrows;
import com.cyberday1.riftfall.skill.RangedCharge;
import com.cyberday1.riftfall.skill.RangedChargeStore;
import com.cyberday1.riftfall.skill.RiftMana;
import com.cyberday1.riftfall.skill.SkillAmmo;
import com.cyberday1.riftfall.skill.RiftManaStore;
import com.cyberday1.riftfall.skill.ShifterForms;
import com.cyberday1.riftfall.skill.SkillCooldownStore;
import com.cyberday1.riftfall.skill.SkillScreenService;
import com.cyberday1.riftfall.skill.SkillSync;
import com.cyberday1.riftfall.skill.SkillTableLoader;
import com.cyberday1.riftfall.stat.SingleCrit;
import com.cyberday1.riftfall.stat.StatModifiers;
import com.cyberday1.riftfall.theme.RiftTheme;
import com.cyberday1.riftfall.theme.family.BlockFamilyTableLoader;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

/**
 * Riftfall Core — lead mod of the Riftfall ARPG modpack.
 *
 * <p>
 * Current scope is the <em>processor block</em> subsystem: 15 families x 12 variants of abstract
 * placeholder building blocks. Dungeon rooms are authored as structure NBT against these
 * placeholders ("material 1, stairs") rather than against real materials ("stone_stairs"), and a
 * theme processor substitutes concrete blocks at generation time. One authored room therefore
 * serves every visual theme.
 */
@Mod(RiftfallCore.MODID)
public final class RiftfallCore {

    public static final String MODID = "riftfall";

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * @param modContainer this mod's own container, which is how a config file gets attached to a
     *                     mod rather than to the game. FML injects it the same way it injects the
     *                     bus: both are asked for by type, and a constructor may take either, both
     *                     or neither.
     */
    public RiftfallCore(IEventBus modEventBus, ModContainer modContainer) {
        // Registered here rather than in a setup event because a config has to exist before anything
        // could read it, and the constructor is the earliest point at which this mod exists at all.
        // SERVER, for the reasons RiftfallConfig gives at length.
        modContainer.registerConfig(ModConfig.Type.SERVER, RiftfallConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, RiftfallClientConfig.SPEC);

        RiftfallBlocks.register(modEventBus);
        RiftfallItems.register(modEventBus);
        RiftfallBlockEntities.register(modEventBus);
        RiftfallMenus.register(modEventBus);
        RiftfallRecipes.register(modEventBus);
        RiftfallCreativeTabs.register(modEventBus);
        // Weapons Expanded inserts after vanilla combat items Better Weaponry removes.
        com.cyberday1.riftfall.compat.CreativeTabAnchors.register(modEventBus);
        RiftfallDataComponents.register(modEventBus);
        RiftfallAttachments.register(modEventBus);
        RiftfallAttributes.register(modEventBus);
        RiftfallEffects.register(modEventBus);
        RiftfallRegistries.register(modEventBus);
        RiftfallProcessors.register(modEventBus);
        RiftfallLootEntries.register(modEventBus);
        RiftfallSounds.register(modEventBus);
        RiftfallParticles.register(modEventBus);
        com.cyberday1.riftfall.init.RiftfallEntities.register(modEventBus);
        com.cyberday1.riftfall.boss.UnseatedCommands.register();
        com.cyberday1.riftfall.boss.UnseatedArena.register();
        if (net.neoforged.fml.ModList.get().isLoaded("spell_engine")) {
            com.cyberday1.riftfall.skill.RiftSummons.register(modEventBus);
            com.cyberday1.riftfall.skill.BladeRains.register(modEventBus);
            com.cyberday1.riftfall.skill.PhantomSignatures.register(modEventBus);
        }
        // Gems go into gear only at the Jeweler (Apotheosis takeover).
        if (net.neoforged.fml.ModList.get().isLoaded("apotheosis")) com.cyberday1.riftfall.home.npc.jeweler.SocketingGate.register();
        RiftfallDataGenerators.register(modEventBus);
        RiftfallPayloads.register(modEventBus);

        // Behind its own three-mod check: the trinket loot category can only be built out of
        // registries that three other mods own.
        RiftTrinkets.register(modEventBus);
        // The gun loot category, so rift guns carry sockets.
        if (net.neoforged.fml.ModList.get().isLoaded("apotheosis")
                && net.neoforged.fml.ModList.get().isLoaded("apothic_attributes")) {
            com.cyberday1.riftfall.gear.GunCategory.register(modEventBus);
        }

        modEventBus.addListener(this::commonSetup);

        // Game bus, not the mod bus: the family table is a server data resource, so it reloads with
        // the datapacks rather than once at startup.
        NeoForge.EVENT_BUS.addListener(RiftfallCore::addReloadListeners);
        NeoForge.EVENT_BUS.addListener(RiftfallCore::reportThemes);

        // Rift levels are made and destroyed while the server runs, so everything about them hangs
        // off the game bus rather than off registration.
        RiftLevels.register();
        ShatteredHomeLevels.register();
        ShatteredHomeJoinHandler.register();
        RiftTeleports.register();
        RiftRuns.register();
        com.cyberday1.riftfall.rift.RiftRecaps.register();
        com.cyberday1.riftfall.rift.RiftAutoClaims.register();
        RiftWarmup.register();
        RiftDeaths.register();
        // After RiftDeaths, so a login that owes a toll is charged before the window is opened over
        // it: the two touch the same player on the same event and only one of them is a payment.
        RiftRejoin.register();
        RiftSpawns.register();
        com.cyberday1.riftfall.mob.SporeEncounters.register();
        RiftIntegrity.register();
        RiftBosses.register();
        // Before the gate, so that on the tick a party walks in the boss exists by the time the gate
        // asks whether the fight is on.
        BossSpawn.register();
        BossGate.register();
        com.cyberday1.riftfall.skill.SkillShowcase.register();
        ArenaSeal.register();
        BossGallery.register();
        RoomCaption.register();
        RiftCommand.register();
        RiftInvites.register();

        // Not rift-scoped: the enchantment allowlist is pack-wide, so it registers beside them
        // rather than inside a run. See EnchantmentBan for why the datapack half is not sufficient.
        EnchantmentBan.register();
        com.cyberday1.riftfall.gear.FortuneGrant.register();
        // Same hook and the same LOWEST reasoning as the enchantment ban, for the mods that answer
        // over the weapon floor's component. See WeaponFloor#register.
        WeaponFloor.register();
        // Same hook at LOW: after apothic materialises affix modifiers, before the floor's last word.
        AffixTiers.register();
        CombatStats.register();
        SingleCrit.register();
        ShieldSync.register();
        ShieldedReduction.register();
        PartyHudSync.register();
        com.cyberday1.riftfall.social.SocialSync.register();
        com.cyberday1.riftfall.rift.PartyCombat.register();
        GearPool.register();
        ClassLoot.register();
        // Transient modifiers, so a player's allocation has to be put back on every login and
        // every respawn. See StatModifiers for why permanent ones would be a second record.
        StatModifiers.register();
        RiftMana.register();
        ManaCombat.register();
        PassiveModifiers.register();
        SkillCooldownStore.register();
        // The Shifter's forms as Woodwalkers models; nothing without the mod.
        ShifterForms.register();
        // The Gunner's TaCZ guns; nothing without the mod.
        GunnerGuns.register();
        RiftManaStore.register();
        // Charge, what a bow's arrow or a gun's bullet costs instead of ammo.
        RangedCharge.register();
        RangedChargeStore.register();
        RangedArrows.register();
        SkillAmmo.register();
        SkillSync.register();
        com.cyberday1.riftfall.progression.TabLevel.register();
        com.cyberday1.riftfall.rift.RiftTabName.register();
        SkillScreenService.register();
        AbilityGrants.register(modEventBus);

        // Probe commands: rig jar only, and only under -Driftfall.probe=true.
        com.cyberday1.riftfall.devhooks.RigHooks.register();
    }

    private static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new BlockFamilyTableLoader());
        event.addListener(new EquipmentSetBan());
        event.addListener(new SkillTableLoader());
        event.addListener(new StarterKits.Loader());
        event.addListener(new RiftArms.Loader());
        event.addListener(new ClassLoot.Loader());
        event.addListener(new com.cyberday1.riftfall.home.npc.jeweler.JewelerCosts.Loader());
        event.addListener(new com.cyberday1.riftfall.home.npc.depth.commission.CommissionPool.Loader());
    }

    /**
     * Says out loud what the theme registry actually ended up containing.
     *
     * <p>
     * A datapack registry that decodes nothing is <em>silent</em>: a mistyped directory or a theme
     * whose codec failed leaves no trace at the point of use, and the first symptom is a dungeon
     * built entirely out of the built-in stone fallback. One INFO line at startup turns that into a
     * thing an operator can read.
     */
    private static void reportThemes(ServerAboutToStartEvent event) {
        Registry<RiftTheme> themes = event.getServer().registryAccess()
                .registry(RiftfallRegistries.RIFT_THEMES).orElse(null);
        if (themes == null || themes.size() == 0) {
            LOGGER.error("No rift themes loaded. Every dungeon will use the built-in stone fallback.");
            return;
        }
        LOGGER.info("Loaded {} rift theme(s): {}", themes.size(), themes.keySet());
        if (themes.get(RiftfallRegistries.DEFAULT_THEME) == null) {
            LOGGER.error("Rift theme '{}' is missing. Rooms that name no theme, and rooms naming a "
                    + "theme that does not exist, will use the built-in stone fallback.",
                    RiftfallRegistries.DEFAULT_THEME.location());
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Warm the placeholder->slot/variant reverse map while we are still on the mod-loading
        // thread, so the theme processor never pays the build cost mid-worldgen. Same for the
        // marker block->kind map, which the post-placement passes query per room.
        event.enqueueWork(() -> {
            ProcessorBlocks.buildLookup();
            RiftfallMarkers.buildLookup();
            // Item work orders name their edit by id; collect applies it.
            com.cyberday1.riftfall.home.npc.depth.WorkOrders.registerEdit(
                    com.cyberday1.riftfall.home.npc.smith.SmithService.RETEMPER_EDIT, com.cyberday1.riftfall.home.npc.smith.SmithService::orderEdit);
            com.cyberday1.riftfall.home.npc.jeweler.JewelerOrders.register();
        });
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
