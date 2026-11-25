package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.client.RiftAmbience;
import com.cyberday1.riftfall.client.RiftClassSelect;
import com.cyberday1.riftfall.client.RiftManaHud;
import com.cyberday1.riftfall.client.RiftPartyHud;
import com.cyberday1.riftfall.client.RiftShieldHud;
import com.cyberday1.riftfall.client.RiftSheet;
import com.cyberday1.riftfall.client.RiftSkillCooldowns;
import com.cyberday1.riftfall.client.RiftSkills;
import com.cyberday1.riftfall.progression.RiftXp;
import com.cyberday1.riftfall.shield.ShieldSync;
import com.cyberday1.riftfall.skill.ClassSelectService;
import com.cyberday1.riftfall.skill.SkillKeys;
import com.cyberday1.riftfall.skill.SkillScreenService;
import com.cyberday1.riftfall.skill.ChargeSync;
import com.cyberday1.riftfall.skill.ChargeView;
import com.cyberday1.riftfall.skill.SkillSync;
import com.cyberday1.riftfall.stat.RiftStats;
import com.cyberday1.riftfall.worldgen.level.RiftChunkGenerator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Optional;

/**
 * Riftfall Core's network layer.
 *
 * <p>
 * Both halves live here because the payloads share their reason to be sent: registration on the mod
 * bus, and the three moments on the game bus at which a client is new or rebuilt &mdash; which is
 * when its copy of anything it cannot work out for itself is stale or absent. Handlers run on the
 * receiving side's main thread, which is this registrar's default.
 *
 * <p>
 * The handlers name client classes. Those are only reached when a clientbound payload arrives,
 * which on a dedicated server never happens, so the classes are never loaded there; the payloads
 * themselves are still registered on both sides, which is what the connection handshake checks.
 */
public final class RiftfallPayloads {

    private RiftfallPayloads() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RiftfallPayloads::registerPayloads);

        // Game bus: a player's rift-ness changes while the server runs, not at registration time.
        NeoForge.EVENT_BUS.addListener(RiftfallPayloads::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(RiftfallPayloads::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(RiftfallPayloads::onRespawn);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Bumped when a payload's wire shape changes, so a mismatched jar is refused at login, not mid-decode.
        PayloadRegistrar registrar = event.registrar("25");
        registrar.playToClient(TravelStartPayload.TYPE, TravelStartPayload.STREAM_CODEC,
                (payload, context) -> com.cyberday1.riftfall.client.ClientSkillTravel.start(payload));
        registrar.playToClient(TravelCuePayload.TYPE, TravelCuePayload.STREAM_CODEC,
                (payload, context) -> com.cyberday1.riftfall.client.TravelCue.accept(payload));
        registrar.playToClient(HomeBoundaryPayload.TYPE, HomeBoundaryPayload.STREAM_CODEC,
                (payload, context) -> com.cyberday1.riftfall.client.HomeBoundaryClient.accept(payload));
        registrar.playToClient(RiftAmbiencePayload.TYPE, RiftAmbiencePayload.STREAM_CODEC,
                (payload, context) -> RiftAmbience.themeIs(payload.theme().orElse(null)));
        registrar.playToClient(RiftSheetPayload.TYPE, RiftSheetPayload.STREAM_CODEC,
                (payload, context) -> RiftSheet.told(payload));
        registrar.playToClient(SkillScreenPayload.TYPE, SkillScreenPayload.STREAM_CODEC,
                (payload, context) -> RiftSkills.told(payload));
        // The first serverbound payload: it names a verb and an id, and SkillService decides the rest.
        registrar.playToServer(SkillRequestPayload.TYPE, SkillRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        SkillScreenService.handle(player, payload);
                    }
                });
        // The sheet's Confirm: seven deltas, and StatService decides the rest.
        registrar.playToServer(StatAllocatePayload.TYPE, StatAllocatePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        SkillScreenService.allocate(player, payload);
                    }
                });
        // A cast key names a slot and nothing else; SkillKeys hands it to SkillService#castSlot.
        registrar.playToServer(SkillCastPayload.TYPE, SkillCastPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        SkillKeys.handle(player, payload);
                    }
                });
        registrar.playToClient(SkillCooldownPayload.TYPE, SkillCooldownPayload.STREAM_CODEC,
                (payload, context) -> RiftSkillCooldowns.told(payload));
        // The class screen: a view out (which may ask the client to open it), a view or a choice back.
        registrar.playToClient(ClassSelectPayload.TYPE, ClassSelectPayload.STREAM_CODEC,
                (payload, context) -> RiftClassSelect.told(payload));
        // An empty hand on the World Core's orb: the server finds the Core from the aim and re-checks everything.
        registrar.playToServer(CoreTouchPayload.TYPE, CoreTouchPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        com.cyberday1.riftfall.block.holycore.WorldCoreAim.touched(player, payload.yRot(), payload.xRot());
                    }
                });
        registrar.playToServer(ClassRequestPayload.TYPE, ClassRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ClassSelectService.handle(player, payload);
                    }
                });
        registrar.playToClient(RiftManaPayload.TYPE, RiftManaPayload.STREAM_CODEC,
                (payload, context) -> RiftManaHud.told(payload));
        registrar.playToClient(RangedChargePayload.TYPE, RangedChargePayload.STREAM_CODEC,
                (payload, context) -> ChargeView.told(payload));
        registrar.playToClient(RiftClockPayload.TYPE, RiftClockPayload.STREAM_CODEC,
                (payload, context) -> com.cyberday1.riftfall.client.RiftClockHud.told(payload));
        registrar.playToClient(BossFinderPayload.TYPE, BossFinderPayload.STREAM_CODEC,
                (payload, context) -> com.cyberday1.riftfall.client.BossFinderHud.told(payload));
        registrar.playToClient(RiftShieldPayload.TYPE, RiftShieldPayload.STREAM_CODEC,
                (payload, context) -> RiftShieldHud.told(payload));
        registrar.playToClient(RiftPartyPayload.TYPE, RiftPartyPayload.STREAM_CODEC,
                (payload, context) -> RiftPartyHud.told(payload));
        // The relic stack icons: Berserker Rage, Soul Stacks, Momentum's steps.
        registrar.playToClient(RelicStacksPayload.TYPE, RelicStacksPayload.STREAM_CODEC,
                (payload, context) -> com.cyberday1.riftfall.client.RelicStackHud.told(payload));
        // A resident landing's Requests, Orders and Path tabs.
        registrar.playToClient(ResidentBoardPayload.TYPE, ResidentBoardPayload.STREAM_CODEC,
                (payload, context) -> com.cyberday1.riftfall.client.ResidentBoardClient.told(payload));
        // The Cartographer page's atlas list.
        registrar.playToClient(AtlasPayload.TYPE, AtlasPayload.STREAM_CODEC,
                (payload, context) -> com.cyberday1.riftfall.client.AtlasClient.told(payload));
        // The Codex's Recovered Notes page, and a note to show in the book view.
        registrar.playToClient(NotesPayload.TYPE, NotesPayload.STREAM_CODEC,
                (payload, context) -> com.cyberday1.riftfall.client.NotesClient.told(payload));
        registrar.playToClient(NoteFoundPayload.TYPE, NoteFoundPayload.STREAM_CODEC,
                (payload, context) -> com.cyberday1.riftfall.client.NotesClient.found(payload.id()));
        registrar.playToClient(NoteOpenPayload.TYPE, NoteOpenPayload.STREAM_CODEC,
                (payload, context) -> com.cyberday1.riftfall.client.NotesClient.open(payload.id()));
        // The end-of-run recap and the run history behind /riftfall recap.
        registrar.playToClient(RiftRecapPayload.TYPE, RiftRecapPayload.STREAM_CODEC,
                (payload, context) -> com.cyberday1.riftfall.client.RiftRecapClient.told(payload));
        // The Party bookmark: a verb out, run as its command; the view, the command's reply and the badge back.
        registrar.playToServer(SocialRequestPayload.TYPE, SocialRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        com.cyberday1.riftfall.social.SocialService.handle(player, payload);
                    }
                });
        registrar.playToClient(SocialViewPayload.TYPE, SocialViewPayload.STREAM_CODEC,
                (payload, context) -> com.cyberday1.riftfall.client.SocialClient.told(payload));
        registrar.playToClient(SocialReplyPayload.TYPE, SocialReplyPayload.STREAM_CODEC,
                (payload, context) -> com.cyberday1.riftfall.client.SocialClient.replied(payload));
        registrar.playToClient(SocialBadgePayload.TYPE, SocialBadgePayload.STREAM_CODEC,
                (payload, context) -> com.cyberday1.riftfall.client.SocialClient.badge(payload));
        // Held gear handed back on the recap, and its Claim the rest button.
        registrar.playToClient(RiftClaimPayload.TYPE, RiftClaimPayload.STREAM_CODEC,
                (payload, context) -> com.cyberday1.riftfall.client.RiftRecapClient.claimed(payload));
        registrar.playToServer(RiftClaimRequestPayload.TYPE, RiftClaimRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        com.cyberday1.riftfall.rift.RiftAutoClaims.requested(player);
                    }
                });
        registrar.playToServer(RiftRecapRequestPayload.TYPE, RiftRecapRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        com.cyberday1.riftfall.rift.RiftRecaps.history(player);
                    }
                });
        // A relic movement already made on the client: Springy Boot's air jump, Phantom Boot's wall kick.
        registrar.playToServer(RelicJumpPayload.TYPE, RelicJumpPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        switch (payload.move()) {
                            case AIR_JUMP -> com.cyberday1.riftfall.gear.RelicJump.handle(player);
                            case WALL_KICK -> com.cyberday1.riftfall.gear.RelicWallCling.handle(player);
                        }
                    }
                });
        registrar.playToServer(NotesRequestPayload.TYPE, NotesRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        com.cyberday1.riftfall.lore.NoteFinds.view(player);
                    }
                });
    }

    /** The usual way in and the usual way out. */
    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        tell(event.getEntity());
    }

    /** Logging back in inside a rift: the client is new and knows nothing. */
    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        tell(event.getEntity());
    }

    /**
     * Respawning moves a player between levels without a dimension-change event, and rebuilds the
     * client entity either way.
     */
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        tell(event.getEntity());
    }

    private static void tell(Player player) {
        if (player instanceof ServerPlayer server) {
            PacketDistributor.sendToPlayer(server, themeOf(server.serverLevel())
                    .map(RiftAmbiencePayload::of)
                    .orElse(RiftAmbiencePayload.NONE));
            // The same three moments, for the same reason: they are when a client is new or rebuilt
            // and its cached copy is stale or absent. The cap is read here rather than assumed on
            // the client, so the server stays the one place the level a total buys is decided.
            PacketDistributor.sendToPlayer(server, new RiftSheetPayload(
                    RiftStats.of(server.server).xpFor(server.getUUID()), RiftXp.LEVEL_CAP));
            // Every slot's ready-at tick (CD1: they survive the relog) and the mana bar (V2).
            SkillSync.tell(server);
            ChargeSync.tell(server);
            // The run's clock, or none: a client that left a rift must stop counting.
            com.cyberday1.riftfall.rift.RiftClock.tell(server);
            // The boss room's arrow, or none: leaving a rift clears it too.
            com.cyberday1.riftfall.boss.BossFinder.tell(server);
            // The shield pool as stored; a respawn starts with none, and the client drains it forward.
            ShieldSync.tell(server);
        }
    }

    /**
     * Asked of the generator rather than of the dimension id, for the reason {@code RiftSpawns}
     * gives: the shipped {@code riftfall:rift} dimension is a rift and its id does not say so.
     */
    private static Optional<ResourceLocation> themeOf(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator
                ? Optional.of(generator.theme(level).location())
                : Optional.empty();
    }
}
