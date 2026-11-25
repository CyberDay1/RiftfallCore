package com.cyberday1.riftfall.home;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class HomeCommands {
    private HomeCommands() {}

    private static final String HOME = "message." + RiftfallCore.MODID + ".home.";
    private static final String TEAM = "message." + RiftfallCore.MODID + ".team.";
    static final String ALLIES = HOME + "allies";
    static final String ALLIES_NONE = HOME + "allies_none";
    static final String ALLIES_SEPARATOR = HOME + "allies_separator";
    static final String ALLY_UNKNOWN = HOME + "ally_unknown";
    static final String ALLY_ADDED = HOME + "ally_added";
    static final String ALLY_REMOVED = HOME + "ally_removed";
    static final String ALLY_NOT_OWNER = HOME + "ally_not_owner";
    static final String ALLY_SELF = HOME + "ally_self";
    static final String UNKNOWN_PLAYER = HOME + "unknown_player";
    static final String NO_HOME = HOME + "no_home";
    static final String VISIT_MEMBER = HOME + "visit_member";
    static final String VISIT_GUEST = HOME + "visit_guest";
    static final String NO_SAFE_SPAWN = HOME + "no_safe_spawn";
    static final String DISMOUNT = HOME + "dismount";
    static final String IN_RIFT = HOME + "in_rift";
    static final String NO_ARRIVAL = HOME + "no_arrival";
    static final String TRAVEL_BLOCKED = HOME + "travel_blocked";
    public static final String CLAIMS_OWNER_ONLY = HOME + "claims_owner_only";
    public static final String NO_SERVICE_ACCESS = HOME + "no_service_access";
    /** Action bar, on a refused break or place on a tile with a resident (ResidentTileProtection). */
    public static final String RESIDENT_TILE = HOME + "resident_tile";
    static final String TEAMS_UNAVAILABLE = TEAM + "unavailable";
    static final String DISBAND_CONFIRM = TEAM + "disband_confirm";
    static final String NO_TEAM = TEAM + "no_team";

    /** {@code /riftfall home}, {@code visit}, {@code overworld} and {@code team} replies; the lang provider reads this. */
    public static final Map<String, String> ENGLISH = Map.ofEntries(
            Map.entry(ALLIES, "Home allies: %s"),
            Map.entry(ALLIES_NONE, "Home allies: none"),
            // Between names in the list; CJK locales usually want their own comma.
            Map.entry(ALLIES_SEPARATOR, ", "),
            // An ally whose profile the server no longer has cached; the argument is the start of their UUID.
            Map.entry(ALLY_UNKNOWN, "Unknown player (%s)"),
            Map.entry(ALLY_ADDED, "%s can now interact in your home."),
            Map.entry(ALLY_REMOVED, "%s is no longer your ally. Team and party access still apply."),
            Map.entry(ALLY_NOT_OWNER, "Only the home owner can change individual allies."),
            Map.entry(ALLY_SELF, "You already own this home."),
            Map.entry(UNKNOWN_PLAYER, "That player is not known to this server."),
            Map.entry(NO_HOME, "That player has not established a home yet."),
            Map.entry(VISIT_MEMBER, "Visiting with interaction access."),
            Map.entry(VISIT_GUEST, "Visiting as a guest. The host must ally you or share a party with you to grant interaction access."),
            Map.entry(NO_SAFE_SPAWN, "No safe ground near the Overworld spawn. Ask an administrator to set a safe world spawn."),
            Map.entry(DISMOUNT, "Stand up and dismount before traveling."),
            Map.entry(IN_RIFT, "Leave the rift before using home travel."),
            Map.entry(NO_ARRIVAL, "No safe arrival position is available in that home."),
            Map.entry(TRAVEL_BLOCKED, "Travel was prevented by the server."),
            Map.entry(CLAIMS_OWNER_ONLY, "Only the home owner can manage claims here."),
            Map.entry(NO_SERVICE_ACCESS, "The home owner must ally you first: Party bookmark, Allies tab."),
            Map.entry(RESIDENT_TILE, "This tile belongs to a resident: it cannot be changed."),
            Map.entry(TEAMS_UNAVAILABLE, "Teams require Open Parties and Claims on this server."),
            Map.entry(DISBAND_CONFIRM, "Use /riftfall team disband confirm. Everyone returns to their personal home; no home is deleted."),
            Map.entry(NO_TEAM, "That player is not on a team."));

    static CommandSyntaxException error(String key, Object... args) {
        return new SimpleCommandExceptionType(Component.translatable(key, args)).create();
    }

    /** A cached profile's name, else a translated stand-in rather than a raw UUID. */
    static Component allyName(java.util.Optional<GameProfile> profile, UUID id) {
        return profile.<Component>map(p -> Component.literal(p.getName()))
                .orElseGet(() -> Component.translatable(ALLY_UNKNOWN, id.toString().substring(0, 8)));
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (HomeAccess.teamsAvailable()) HomeTeams.install(event.getServer());
    }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var home = Commands.literal("home").executes(ctx -> home(ctx.getSource()));
        for (String action : new String[]{"ally", "unally"}) {
            home.then(Commands.literal(action).then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                            ctx.getSource().getServer().getPlayerNames(), builder))
                    .executes(ctx -> ally(ctx.getSource(), StringArgumentType.getString(ctx, "player"), action.equals("ally")))));
        }
        home.then(Commands.literal("allies").executes(ctx -> {
            var source = ctx.getSource();
            UUID owner = HomeAccess.homeOwner(source.getServer(), source.getPlayerOrException().getUUID());
            var names = HomeAccessData.of(source.getServer()).allies(owner).stream()
                    .map(id -> allyName(source.getServer().getProfileCache().get(id), id))
                    .sorted(java.util.Comparator.comparing(Component::getString)).toList();
            source.sendSuccess(() -> names.isEmpty() ? Component.translatable(ALLIES_NONE)
                    : Component.translatable(ALLIES, ComponentUtils.formatList(names, Component.translatable(ALLIES_SEPARATOR))), false);
            return 1;
        }));
        var overworld = Commands.literal("overworld").executes(ctx -> overworld(ctx.getSource()));
        var visit = Commands.literal("visit").then(Commands.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                        ctx.getSource().getServer().getPlayerNames(), builder))
                .executes(ctx -> visit(ctx.getSource(), StringArgumentType.getString(ctx, "player"))));
        var team = Commands.literal("team").executes(ctx -> teamCommand(ctx.getSource(), "about"));
        for (String action : new String[]{"create", "leave", "info"})
            team.then(Commands.literal(action).executes(ctx -> teamVerb(ctx.getSource(), action)));
        team.then(Commands.literal("disband").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.translatable(DISBAND_CONFIRM), false);
            return 1;
        }).then(Commands.literal("confirm").executes(ctx -> teamVerb(ctx.getSource(), "disband"))));
        for (String action : new String[]{"invite", "join", "kick", "ally", "unally"})
            team.then(Commands.literal(action).then(Commands.argument("player", StringArgumentType.word())
                    .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                            ctx.getSource().getServer().getPlayerNames(), builder))
                    .executes(ctx -> {
                        requireTeams();
                        var source = ctx.getSource();
                        return teamTarget(source, action, knownPlayer(source, StringArgumentType.getString(ctx, "player")));
                    })));
        event.getDispatcher().register(Commands.literal("riftfall").then(home).then(overworld).then(visit).then(team));
    }

    private static void requireTeams() throws CommandSyntaxException {
        if (!HomeAccess.teamsAvailable()) throw error(TEAMS_UNAVAILABLE);
    }
    private static int teamCommand(CommandSourceStack source, String command) throws CommandSyntaxException {
        requireTeams(); source.getPlayerOrException();
        return HomeTeams.command(source, command);
    }

    /** {@code team create|leave|info|disband}, shared with the Party bookmark; disband here is the confirmed one. */
    public static int teamVerb(CommandSourceStack source, String verb) throws CommandSyntaxException {
        return teamCommand(source, switch (verb) {
            case "create", "leave" -> verb;
            case "info" -> "about";
            case "disband" -> "destroy confirm";
            default -> throw new IllegalArgumentException("team verb " + verb);
        });
    }

    /** {@code team invite|join|kick|ally|unally <player>}, shared with the Party bookmark. Join names any member. */
    public static int teamTarget(CommandSourceStack source, String action, GameProfile target) throws CommandSyntaxException {
        requireTeams();
        String command = switch (action) {
            case "join" -> "join " + HomeTeams.teamId(source.getServer(), target.getId());
            case "ally" -> "ally add " + target.getName();
            case "unally" -> "ally remove " + target.getName();
            case "invite", "kick" -> "member " + action + " " + target.getName();
            default -> throw new IllegalArgumentException("team action " + action);
        };
        return teamCommand(source, command);
    }
    private static GameProfile knownPlayer(CommandSourceStack source, String name) throws CommandSyntaxException {
        // Only server-known profiles: visiting must not create homes for arbitrary account names.
        var online = source.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) return online.getGameProfile();
        for (var level : source.getServer().getAllLevels()) {
            UUID id = HomeAccess.owner(level.dimension().location());
            if (id == null) continue;
            var profile = source.getServer().getProfileCache().get(id);
            if (profile.isPresent() && profile.get().getName().equalsIgnoreCase(name)) return profile.get();
        }
        throw error(UNKNOWN_PLAYER);
    }
    private static int ally(CommandSourceStack source, String name, boolean allow) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        UUID owner = HomeAccess.homeOwner(source.getServer(), player.getUUID());
        if (!owner.equals(player.getUUID())) throw error(ALLY_NOT_OWNER);
        return setAlly(source, knownPlayer(source, name), allow);
    }

    /** {@code home ally|unally}, shared with the Party bookmark: owner only, never yourself. */
    public static int setAlly(CommandSourceStack source, GameProfile target, boolean allow) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        UUID owner = HomeAccess.homeOwner(source.getServer(), player.getUUID());
        if (!owner.equals(player.getUUID())) throw error(ALLY_NOT_OWNER);
        if (target.getId().equals(owner)) throw error(ALLY_SELF);
        HomeAccessData.of(source.getServer()).setAlly(owner, target.getId(), allow);
        source.sendSuccess(() -> Component.translatable(allow ? ALLY_ADDED : ALLY_REMOVED, target.getName()), false);
        return 1;
    }
    static int home(CommandSourceStack source) throws CommandSyntaxException {
        var player = traveler(source);
        UUID owner = HomeAccess.homeOwner(source.getServer(), player.getUUID());
        return travel(player, ShatteredHomeLevels.getOrCreate(source.getServer(), owner), null);
    }
    private static int visit(CommandSourceStack source, String name) throws CommandSyntaxException {
        var player = traveler(source);
        var target = knownPlayer(source, name);
        UUID owner = HomeAccess.homeOwner(source.getServer(), target.getId());
        if (source.getServer().getLevel(ShatteredHomeLevels.key(owner)) == null
                && !ShatteredHomeLevels.existsOnDisk(source.getServer(), owner))
            throw error(NO_HOME);
        int result = travel(player, ShatteredHomeLevels.getOrCreate(source.getServer(), owner), null);
        player.sendSystemMessage(Component.translatable(HomeAccess.canInteract(source.getServer(), owner, player.getUUID())
                ? VISIT_MEMBER : VISIT_GUEST));
        return result;
    }
    private static int overworld(CommandSourceStack source) throws CommandSyntaxException {
        var player = traveler(source);
        var level = source.getServer().overworld();
        BlockPos origin = level.getSharedSpawnPos();
        for (int radius = 0; radius <= 32; radius++)
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = origin.getX() + dx, z = origin.getZ() + dz;
                    var pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                    if (level.getWorldBorder().isWithinBounds(pos) && ShatteredHomeSpawn.safe(level, pos))
                        return travel(player, level, Vec3.atBottomCenterOf(pos));
                }
        throw error(NO_SAFE_SPAWN);
    }
    private static ServerPlayer traveler(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!player.isAlive() || player.isSleeping() || player.isPassenger() || player.isVehicle())
            throw error(DISMOUNT);
        if (RiftLevels.isRiftWorld(player.serverLevel())) throw error(IN_RIFT);
        return player;
    }
    private static int travel(ServerPlayer player, ServerLevel destination, Vec3 position) throws CommandSyntaxException {
        Vec3 arrival;
        try { arrival = position == null ? ShatteredHomeLevels.arrival(destination) : position; }
        catch (IllegalStateException e) { throw error(NO_ARRIVAL); }
        if (player.changeDimension(new DimensionTransition(destination, arrival, Vec3.ZERO,
                player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING)) == null)
            throw error(TRAVEL_BLOCKED);
        player.fallDistance = 0;
        ShatteredHomeLevels.syncBorderTo(player);
        return 1;
    }
}
