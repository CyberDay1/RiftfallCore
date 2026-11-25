package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.skill.ChargeView;
import com.cyberday1.riftfall.client.particle.RiftEmberParticle;
import com.cyberday1.riftfall.client.particle.RiftGroundEmberParticle;
import com.cyberday1.riftfall.block.glass.OneWayGlassBlock;
import com.cyberday1.riftfall.client.particle.RiftMoteParticle;
import com.cyberday1.riftfall.client.particle.RiftVortexParticle;
import com.cyberday1.riftfall.init.RiftfallBlockEntities;
import com.cyberday1.riftfall.init.RiftfallBlocks;
import com.cyberday1.riftfall.init.RiftfallMenus;
import com.cyberday1.riftfall.init.RiftfallParticles;
import com.cyberday1.riftfall.rift.RiftPortalBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;
import com.cyberday1.riftfall.home.ShatteredHomeLevels;

/**
 * Client-only wiring for Riftfall Core.
 *
 * <p>
 * Dist-gated at the subscriber rather than checked inside the method: a screen class only exists on
 * the client, and a server that so much as loaded this class to find its annotations would be
 * resolving one.
 *
 * <p>
 * No {@code bus} is named. NeoForge 21.1 works it out from the event's own type and has deprecated
 * saying it for removal, so naming the mod bus here would be a warning rather than a clarification.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class RiftfallClient {

    /**
     * What the bottom rung's pane is multiplied by. Not lower: the pane still has to read as a hole
     * from across a room at tier 1, and past about half it starts reading as an unlit frame instead.
     */
    private static final float DIMMEST = 0.55F;

    /** Multiplied onto a white pane, so these are the colours the wall's two sides actually come out. */
    private static final int PASSABLE_GREEN = 0x4CD964;
    private static final int BLOCKED_RED = 0xE0453C;

    private RiftfallClient() {
    }

    @SubscribeEvent
    private static void registerKeys(RegisterKeyMappingsEvent event) {
        RiftKeys.all().forEach(event::register);
    }

    @SubscribeEvent
    private static void registerSkillIcons(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(SkillIcons.INSTANCE);
        event.registerReloadListener(WorldBreakerGroundLight.INSTANCE);
        event.registerReloadListener(WorldCoreMesh.INSTANCE);
        event.registerReloadListener(UnseatedPreviewRenderer.RELOAD);
        event.registerReloadListener(StellarSophisticated.RELOAD);
        event.registerReloadListener(StellarContainers.RELOAD);
    }

    @SubscribeEvent
    private static void registerShatteredSkyShader(net.neoforged.neoforge.client.event.RegisterShadersEvent event)
            throws java.io.IOException {
        ShatteredHomeSkyEffects.releaseBuffers();
        event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),
                RiftfallCore.id("shattered_sky"), com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION),
                ShatteredSkyRenderer::setShader);
    }

    @SubscribeEvent
    private static void registerSkyEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ShatteredHomeSkyEffects.EFFECTS_ID, new ShatteredHomeSkyEffects());
    }

    @SubscribeEvent
    private static void renderHomeSkyAfterShaderPack(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        var client = Minecraft.getInstance();
        if (client.level == null || !ShatteredHomeLevels.isShatteredHome(client.level.dimension())
                || !ShatteredSkyRenderer.shaderPackActive()) return;
        ShaderPackHomeSky.draw(event);
    }

    /** Opens a progression tab from what the server last said, and sends nothing to ask. */
    @SubscribeEvent
    private static void openFromKeys(ClientTickEvent.Post event) {
        RiftMenuKeys.tick(Minecraft.getInstance());
        RiftHudEditorScreen.tick(Minecraft.getInstance());
        // The class screen the server offered (the class command, or after an admin reset), once nothing is open.
        RiftClassSelect.tick(Minecraft.getInstance());
    }

    /** A press sends a slot and nothing else; the answer comes back on the action bar. */
    @SubscribeEvent
    private static void castFromKeys(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        RiftCastKeys.tick(client.screen == null && client.player != null && client.getConnection() != null);
    }

    /**
     * Above air, so docked bars take the next free rows of both columns: after Overloaded Armor Bar's layer above
     * ARMOR_LEVEL on the left. Mana, Charge, rift clock and shield are one layer; the HUD editor places them. The
     * relic stack icons sit beside the hotbar and take no column rows.
     */
    @SubscribeEvent
    private static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, RiftfallCore.id("bars"), RiftHudBars::render);
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, RiftfallCore.id("party"), RiftPartyHud::render);
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, RiftfallCore.id("relic_stacks"), RelicStackHud::render);
        // Right after the bars, so the docked cooldown row sits above whatever they took from the columns.
        event.registerAbove(RiftfallCore.id("bars"), RiftfallCore.id("skill_cooldowns"), SkillCooldownHud::render);
        // After the boss bars, so the panel under them reads this frame's reach (BossBarExtent).
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, RiftfallCore.id("unseated_cues"), UnseatedCombatCues::hud);
    }

    /** The HUD editor from the pause menu, top-left like NeoOrigins' own; the sky credits hold bottom-left. */
    @SubscribeEvent
    private static void editHudButton(ScreenEvent.Init.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (!(event.getScreen() instanceof PauseScreen pause) || !pause.showsPauseMenu() || client.level == null) return;
        event.addListener(Button.builder(Component.translatable("screen.riftfall.hud_editor.open"),
                b -> client.setScreen(new RiftHudEditorScreen())).bounds(8, 8, 80, 20).build());
    }

    @SubscribeEvent
    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(RiftfallMenus.KEY_FORGE.get(), KeyForgeScreen::new);
        event.register(RiftfallMenus.RIFT_BARREL.get(), RiftBarrelScreen::new);
        event.register(RiftfallMenus.BLACKSMITH.get(), BlacksmithScreen::new);
        event.register(RiftfallMenus.RESIDENT_LANDING.get(), ResidentLandingScreen::new);
        event.register(RiftfallMenus.BLACKSMITH_APPEARANCE.get(), BlacksmithAppearanceScreen::new);
        event.register(RiftfallMenus.JEWELER.get(), JewelerScreen::new);
        event.register(RiftfallMenus.QUARRY.get(), QuarryScreen::new);
        event.register(RiftfallMenus.CARTOGRAPHER.get(), CartographerScreen::new);
        event.register(RiftfallMenus.SMITH.get(), SmithScreen::new);
        event.register(RiftfallMenus.SMITH_TOOLS.get(), ToolWorkScreen::new);
        event.register(RiftfallMenus.SMITH_BULK_SCRAP.get(), BulkScrapScreen::new);
    }

    /**
     * The types are registered on the mod bus like anything else; only the thing that draws them is
     * client-side, and this is the only event that hands one a sprite set to draw with.
     */
    @SubscribeEvent
    private static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(RiftfallParticles.RIFT_MOTE.get(), RiftMoteParticle.Provider::new);
        event.registerSpriteSet(RiftfallParticles.RIFT_EMBER.get(), RiftEmberParticle.Provider::new);
        event.registerSpriteSet(RiftfallParticles.RIFT_VORTEX.get(), RiftVortexParticle.Provider::new);
        event.registerSpriteSet(RiftfallParticles.RIFT_GROUND_EMBER.get(),
                RiftGroundEmberParticle.Provider::new);
    }

    /**
     * The tear is the one block here that draws nothing from its model. It is a picture bigger than
     * the blocks it stands in, and it has to turn to face the camera, and a baked model can do
     * neither.
     */
    @SubscribeEvent
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.cyberday1.riftfall.init.RiftfallEntities.UNSEATED_PREVIEW.get(),
                UnseatedPreviewRenderer::new);
        event.registerEntityRenderer(com.cyberday1.riftfall.init.RiftfallEntities.UNSEATED.get(),UnseatedRenderer::new);
        event.registerEntityRenderer(com.cyberday1.riftfall.init.RiftfallEntities.UNSEATED_TETHER.get(),UnseatedTetherRenderer::new);
        if (net.neoforged.fml.ModList.get().isLoaded("spell_engine")) {
            RiftSummonRenderers.register(event);
            BladeRainRenderer.register(event);
            PhantomSignatureRenderer.register(event);
        }
        event.registerBlockEntityRenderer(RiftfallBlockEntities.RIFT_TEAR.get(), RiftTearRenderer::new);
        event.registerBlockEntityRenderer(RiftfallBlockEntities.WORLD_BREAKER.get(), WorldBreakerRenderer::new);
        event.registerBlockEntityRenderer(RiftfallBlockEntities.HOLY_CORE.get(), HolyCoreRenderer::new);
    }

    /** The World Breaker's floating shards are models no block state names, so they are asked for here. */
    @SubscribeEvent
    private static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(WorldBreakerRenderer.TIP);
        event.register(WorldBreakerRenderer.CHARGE_CRYSTALS);
        event.register(WorldBreakerRenderer.CHIP_LARGE);
        event.register(WorldBreakerRenderer.CHIP_SMALL);
        HolyCoreRenderer.registerModels(event::register);
        WorldCoreMesh.registerModels(event::register);
    }

    @SubscribeEvent
    private static void pairBarrelModels(ModelEvent.ModifyBakingResult event) {
        RiftBarrelModels.pairUp(event);
    }

    /**
     * How bright a rift's pane burns, which is the only thing about its tier you can see head-on.
     *
     * <p>
     * The pane was the same three textures at every tier: a tier-1 rift and a tier-8 one differed only
     * in light level and in how many motes they threw, and side by side that read as the same portal.
     * A tint is the cheapest fix that changes the surface itself — {@link RiftPortalBlock#INTENSITY} is
     * already part of the blockstate, so keying off it costs no new state and no new texture.
     *
     * <p>
     * A block colour bakes into the chunk mesh and only re-evaluates on rebuild, which rules it out for
     * anything animated but is exactly right here: a lit portal's intensity never changes, and the
     * block change that sets it rebuilds the chunk anyway.
     *
     * <p>
     * Multiply-only, so the textures are authored at the top rung and the lower ones are pulled down
     * from it. {@code state} is null for the item form, which the portal has none of; the fallback is
     * the top rung regardless.
     */
    @SubscribeEvent
    private static void tintThePortalByTier(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            int intensity = state == null
                    ? RiftPortalBlock.MAX_INTENSITY
                    : state.getValue(RiftPortalBlock.INTENSITY);
            float lit = DIMMEST + (1.0F - DIMMEST) * intensity / RiftPortalBlock.MAX_INTENSITY;
            int channel = Math.round(lit * 255.0F);
            return channel << 16 | channel << 8 | channel;
        }, RiftfallBlocks.RIFT_PORTAL.get());
    }

    /**
     * Which way the one-way wall lets you through, said in colour.
     *
     * <p>
     * A block colour is handed the tint index and not the face, so the model numbers its six faces by
     * {@link net.minecraft.core.Direction#get3DDataValue()} and this asks which of those the state is
     * facing. Doing it here rather than by rotating the model is what makes the block six-way for
     * free: one model, one blockstate variant, and no {@code x}/{@code y} rotation to get backwards.
     */
    @SubscribeEvent
    private static void tintTheOneWayWall(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) ->
                        tintIndex == state.getValue(OneWayGlassBlock.FACING).get3DDataValue()
                                ? PASSABLE_GREEN
                                : BLOCKED_RED,
                RiftfallBlocks.ONE_WAY_GLASS.get());
    }

    /**
     * The same wall in the hand and in a creative tab, where it has no facing to answer with.
     *
     * <p>
     * A block colour is never asked about an item, so without this the icon was the bare sprite: a
     * flat white cube, which is neither of the two colours the block is ever drawn in. An item
     * colour is handed the same tint index the model numbered its faces with and has no state to
     * read, so every face takes the passable colour &mdash; the block held is the side you can walk
     * through, and colouring five faces red would claim a facing the stack does not have.
     */
    @SubscribeEvent
    private static void tintTheOneWayWallInHand(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> PASSABLE_GREEN, RiftfallBlocks.ONE_WAY_GLASS.get());
    }

    /**
     * A rift brings its own music. Overridden rather than merely set, so a later listener cannot put
     * the overworld's back; null both stops what is playing and keeps the next track from starting.
     */
    @SubscribeEvent
    private static void silenceVanillaMusic(SelectMusicEvent event) {
        if (RiftAmbience.inRift()) {
            event.overrideMusic(null);
        }
    }

    /**
     * A resource reload or a change of output device restarts the sound engine, which drops every
     * sound playing in it. This event is the only notice of that the client gets.
     */
    @SubscribeEvent
    private static void soundEngineLoaded(SoundEngineLoadEvent event) {
        RiftAmbience.soundEngineLoaded();
    }

    /** The one caller of the ambience's fade-out backstop. Does nothing at all outside a rift exit. */
    @SubscribeEvent
    private static void watchTheFadeOut(ClientTickEvent.Post event) {
        RiftAmbience.clientTick();
    }


    /** Spell Engine's tooltips read Riftfall's description tokens once the world's spells have arrived. */
    @SubscribeEvent
    private static void registerTooltipTokens(ClientPlayerNetworkEvent.LoggingIn event) {
        AbilityLookup.registerTooltipTokens();
    }

    /** Otherwise the main menu inherits a rift's silence from the world we just left. */
    @SubscribeEvent
    private static void forgetRift(ClientPlayerNetworkEvent.LoggingOut event) {
        ShatteredHomeSkyEffects.releaseBuffers();
        RiftAmbience.forget();
        // And one character's XP must not be what the next world's sheet opens showing.
        RiftSheet.forget();
        RiftSkills.forget();
        RiftClassSelect.forget();
        RiftSkillCooldowns.forget();
        RiftManaHud.forget();
        RiftClockHud.forget();
        ChargeView.forget();
        RiftShieldHud.forget();
        RiftPartyHud.forget();
        RelicStackHud.forget();
    }
}
