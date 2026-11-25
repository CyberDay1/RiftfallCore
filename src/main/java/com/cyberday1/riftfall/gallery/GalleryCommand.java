package com.cyberday1.riftfall.gallery;

import com.cyberday1.riftfall.home.TilePlacementGuard;
import com.cyberday1.riftfall.init.RiftfallRegistries;
import com.cyberday1.riftfall.rift.RiftTeleports;
import com.cyberday1.riftfall.theme.RiftTheme;
import com.cyberday1.riftfall.worldgen.level.GalleryDimension;
import com.cyberday1.riftfall.worldgen.processor.ThemeSubstitutionProcessor;
import com.cyberday1.riftfall.worldgen.space.TemplatePlacement;
import com.cyberday1.riftfall.worldgen.space.TripleMirror;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * {@code /riftfall piece} &mdash; look at one dungeon piece instead of a whole dungeon.
 *
 * <p>
 * Everything about the room library was, until this existed, only observable through a generated
 * rift: to see a room you rolled a seed and went hunting, and to see the room you had in mind you
 * rolled again. That is a bad way to review 124 rooms and several hundred props, and it gets worse
 * as soon as the library has to be judged as a set &mdash; which is exactly what tier scaling and a
 * boss room will need.
 *
 * <h2>The raw/themed switch is the presence of the argument, not its value</h2>
 * {@code place <template>} adds no {@link ThemeSubstitutionProcessor} at all, so the
 * {@code riftfall:processor_block_*} placeholders survive into the world and the author sees what
 * they actually built. {@code place <template> <theme>} adds one. The distinction cannot be carried
 * by the processor's own argument, because its codec reads an absent theme as
 * {@code riftfall:default} &mdash; a sensible default for worldgen and the opposite of what is
 * wanted here.
 *
 * <h2>{@code place <template> edit} is the one verb that writes back</h2>
 * The other two show a piece as a rift would build it, which is the wrong thing to edit: the jigsaws
 * are already gone. {@code edit} keeps them and drops a structure block under the piece, so the loop
 * is stamp, change, save. What it saves lands in the <em>world's</em> {@code generated/} folder, and
 * the template manager reads that folder before it reads the mod's own resources &mdash; so the edit
 * is live in that world immediately, and is still nowhere near the repository. Making it permanent is
 * a file copy into {@code data/riftfall/structure/}, which nothing here does on an author's behalf.
 *
 * <h2>Nothing here is generation code</h2>
 * The settings below are {@code RiftChunkGenerator.place}'s, minus the two things that only make
 * sense inside a chunk: there is no bounding box, because nothing is being clipped to a chunk
 * column, and there is no furnishing or corridor blending, because a gallery piece is deliberately
 * the authored shell and not the dressed result.
 */
public final class GalleryCommand {

    /** {@code RiftChunkGenerator}'s, and for the same reason: notify clients, skip the shape-update pass. */
    private static final int PLACE_FLAGS = 18;

    /** Above this, {@code list} prints a count instead of a wall of text nobody scrolls back through. */
    private static final int LIST_LIMIT = 40;

    /** Air between the tallest piece in a run and the plane its labels hang on. */
    private static final double LABEL_CLEARANCE = 3.0;

    /** Text-display glyphs are authored small; this is what makes an id legible from the next aisle. */
    private static final float LABEL_SCALE = 3.0F;

    private static final DynamicCommandExceptionType NO_SUCH_TEMPLATE = new DynamicCommandExceptionType(
            id -> Component.literal("No structure template " + id));

    private static final DynamicCommandExceptionType NO_SUCH_THEME = new DynamicCommandExceptionType(
            id -> Component.literal("No rift theme " + id));

    private static final DynamicCommandExceptionType NOTHING_MATCHED = new DynamicCommandExceptionType(
            filter -> Component.literal("No pieces under " + filter));

    private static final DynamicCommandExceptionType NO_GALLERY = new DynamicCommandExceptionType(
            id -> Component.literal("The " + id + " dimension is not loaded. A world created before it"
                    + " was added has to be started once with the datapack present."));

    /**
     * Every piece the template manager has, offered from the server.
     *
     * <p>
     * Not optional: nobody types one of 124 room ids from memory, and the ids are long enough
     * ({@code riftfall:rift/room/chaos/222/thingy-1-kiara}) that a typo is the expected outcome
     * rather than the unlucky one.
     */
    private static final SuggestionProvider<CommandSourceStack> PIECES = (context, builder) ->
            SharedSuggestionProvider.suggestResource(
                    context.getSource().getLevel().getStructureManager().listTemplates()
                            .filter(GalleryPieces::isPiece),
                    builder);

    /**
     * Every directory a piece lives in, which is the set of filters that can select anything.
     *
     * <p>
     * Derived from the ids rather than listed by hand, so a datapack that adds
     * {@code rift/room/boss/} gets a suggestion for it without anyone editing this file.
     */
    private static final SuggestionProvider<CommandSourceStack> FILTERS = (context, builder) -> {
        TreeSet<String> directories = new TreeSet<>();
        context.getSource().getLevel().getStructureManager().listTemplates()
                .filter(GalleryPieces::isPiece)
                .forEach(id -> {
                    String path = id.getPath();
                    for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1)) {
                        directories.add(path.substring(0, slash));
                    }
                });
        return SharedSuggestionProvider.suggest(directories, builder);
    };

    private GalleryCommand() {
    }

    /**
     * The subtree, handed to {@code RiftCommand} to hang under {@code /riftfall} rather than
     * registered on its own.
     *
     * <p>
     * Two registrations of the same root would work &mdash; brigadier merges children into the
     * existing literal &mdash; but the merge keeps whichever node was registered first and silently
     * discards the second one's {@code requires}, so the permission level of {@code /riftfall} would
     * depend on listener order. One root, declared once, cannot go wrong that way.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("piece")
                .then(Commands.literal("place")
                        .then(Commands.argument("template", ResourceLocationArgument.id()).suggests(PIECES)
                                .executes(context -> place(context, null, TripleMirror.IDENTITY))
                                .then(Commands.literal("edit").executes(GalleryCommand::edit))
                                // Orientation before theme among the siblings, because an integer
                                // parse rejects `riftfall:cave` outright while a resource location
                                // would happily accept `5` as `minecraft:5`.
                                .then(orientation()
                                        .executes(context -> place(context, null, orientationOf(context))))
                                .then(Commands.argument("theme", ResourceKeyArgument.key(RiftfallRegistries.RIFT_THEMES))
                                        .executes(context -> place(context, theme(context), TripleMirror.IDENTITY))
                                        .then(orientation()
                                                .executes(context ->
                                                        place(context, theme(context), orientationOf(context)))))))
                .then(Commands.literal("gallery")
                        .then(Commands.argument("filter", ResourceLocationArgument.id()).suggests(FILTERS)
                                .executes(context -> gallery(context, null, false))
                                // Literal before the theme argument for the reader's sake only:
                                // brigadier matches literals ahead of arguments whatever the order,
                                // which is what keeps `edit` from parsing as a theme.
                                .then(Commands.literal("edit")
                                        .executes(context -> gallery(context, null, true)))
                                .then(Commands.argument("theme", ResourceKeyArgument.key(RiftfallRegistries.RIFT_THEMES))
                                        .executes(context -> gallery(context, theme(context), false)))))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource(), ""))
                        .then(Commands.argument("filter", ResourceLocationArgument.id()).suggests(FILTERS)
                                .executes(context -> list(context.getSource(), filter(context)))));
    }

    /** One of the eight {@link TripleMirror#PERMUTATIONS}, by index, so the mirror code can be eyeballed. */
    private static RequiredArgumentBuilder<CommandSourceStack, Integer> orientation() {
        return Commands.argument("orientation", IntegerArgumentType.integer(0, TripleMirror.PERMUTATIONS.size() - 1));
    }

    // -----------------------------------------------------------------------------------------------
    // Verbs
    // -----------------------------------------------------------------------------------------------

    /**
     * Stamps one piece where the caller stands, in whatever level they are standing in.
     *
     * <p>
     * Not forced into the gallery dimension. Half the value of this verb is dropping a piece next to
     * something else to compare the two, and the something else is as likely to be a rift under
     * inspection as it is another gallery cell.
     */
    private static int place(CommandContext<CommandSourceStack> context, @Nullable ResourceKey<RiftTheme> theme,
                             TripleMirror mirror) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ResourceLocation id = ResourceLocationArgument.getId(context, "template");
        ServerLevel level = source.getLevel();
        BlockPos corner = BlockPos.containing(source.getPosition());

        Vec3i size = stamp(level, id, theme, mirror, corner, false);
        source.sendSuccess(() -> Component.literal("Placed " + id + " at " + corner.toShortString()
                + " (" + describe(theme) + ", orientation " + mirror.index() + ", "
                + size.getX() + "x" + size.getY() + "x" + size.getZ() + ")"), true);
        return 1;
    }

    /**
     * Stamps one piece to be edited and saved back, with a structure block already aimed at it.
     *
     * <h2>Why this is a named mode, not a flag</h2>
     * {@code place} and {@code gallery} exist to look at the library, and what they show has to be
     * what a rift shows: jigsaws resolved to their final state, nothing standing in the room that a
     * player would not find there. Editing wants the opposite on every point, so it is spelled out as
     * its own word &mdash; here, and on {@code gallery} for a whole run at once &mdash; rather than a
     * switch that quietly changes what the review views mean.
     *
     * <h2>Neither a theme nor an orientation is offered</h2>
     * Both would be baked into whatever gets saved. A theme resolves the
     * {@code riftfall:processor_block_*} placeholders to real blocks, and saving that back writes a
     * room that can only ever be that one theme; an orientation writes a room that is permanently
     * turned, in a library whose whole convention is that rooms are authored one way and turned at
     * generation time. Neither loss announces itself &mdash; the saved file looks fine &mdash; so the
     * arguments are not there to be passed by mistake.
     */
    private static int edit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ResourceLocation id = ResourceLocationArgument.getId(context, "template");
        ServerLevel level = source.getLevel();
        BlockPos corner = BlockPos.containing(source.getPosition());

        Vec3i size = stamp(level, id, null, TripleMirror.IDENTITY, corner, true);
        BlockPos control = corner.below();
        saveBlock(level, control, id, size);

        source.sendSuccess(() -> Component.literal("Placed " + id + " at " + corner.toShortString()
                + " to edit (" + size.getX() + "x" + size.getY() + "x" + size.getZ()
                + "), structure block at " + control.toShortString()), true);
        return 1;
    }

    /**
     * Lays every piece under {@code filter} out on a grid in {@code riftfall:gallery} and takes the
     * caller there.
     *
     * @param editing the authoring view for the whole run rather than one piece: jigsaws left
     *                standing, placeholders left raw, and a save-mode structure block under every
     *                cell. A theme is refused alongside it for the reason {@link #edit} gives &mdash;
     *                it would be baked into anything saved back.
     */
    private static int gallery(CommandContext<CommandSourceStack> context, @Nullable ResourceKey<RiftTheme> theme,
                               boolean editing) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String filter = filter(context);

        ServerLevel level = source.getServer().getLevel(GalleryDimension.GALLERY_LEVEL);
        if (level == null) {
            throw NO_GALLERY.create(GalleryDimension.GALLERY_LEVEL.location());
        }

        StructureTemplateManager templates = level.getStructureManager();
        List<ResourceLocation> pieces = GalleryPieces.select(templates.listTemplates(), filter);
        if (pieces.isEmpty()) {
            throw NOTHING_MATCHED.create(filter);
        }

        // Sized off the pieces themselves rather than off the 47-block authored maximum, so a
        // datapack that adds something larger gets a grid that still separates its cells.
        int widest = 0;
        int tallest = 0;
        for (ResourceLocation id : pieces) {
            Vec3i size = templates.get(id).map(StructureTemplate::getSize).orElse(Vec3i.ZERO);
            widest = Math.max(widest, Math.max(size.getX(), size.getZ()));
            tallest = Math.max(tallest, size.getY());
        }

        GalleryGrid grid = GalleryGrid.of(filter, pieces.size(), widest);

        // One height for every label in the run, taken from the tallest piece in it. Per-piece
        // heights would step up and down with the rooms, and a 47-tall room would stand in front of
        // the label belonging to the 15-tall one behind it.
        double labelY = grid.origin().getY() + tallest + LABEL_CLEARANCE;

        // The player goes first and watches it build. The alternative is arriving after the fact,
        // which costs the one moment where a piece that lands wrong is obvious.
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            RiftTeleports.permit(player, level);
            player.changeDimension(new DimensionTransition(level,
                    Vec3.atBottomCenterOf(grid.viewpoint()), Vec3.ZERO, -45.0F, 0.0F,
                    DimensionTransition.DO_NOTHING));
        }

        int placed = pieces.size();
        source.sendSuccess(() -> Component.literal("Laying out " + placed + " piece(s) under " + filter
                + " on a " + grid.columns() + "x" + grid.rows() + " grid at "
                + grid.origin().toShortString() + " ("
                + (editing ? "to edit: jigsaws kept, structure block under each cell" : describe(theme))
                + ")"), true);
        new Run(source, level, pieces, grid, theme, labelY, editing).schedule();
        return placed;
    }

    /**
     * One gallery run, placed a few pieces at a time.
     *
     * <h2>Why this cannot be a loop</h2>
     * A command runs to completion inside one tick, and the whole shipped room library measured 29
     * seconds of block writes in that one tick even with every chunk already generated. A dedicated
     * server's watchdog kills the process at 60, so a large enough filter does not merely stutter, it
     * takes the server down &mdash; and a datapack can always make the filter larger. Even under the
     * limit a thirty-second freeze is a hang: no player moves, nothing ticks, and the log fills with
     * "can't keep up".
     *
     * <p>
     * So the run yields. Each tick it places pieces until its time budget is spent and hands the rest
     * to the next one, which turns the freeze into a gallery that visibly builds itself.
     *
     * <h2>Time, not a fixed count</h2>
     * A budget in milliseconds rather than "n pieces per tick" because the pieces are not alike: a
     * 47-block room is three hundred times the volume of a 7-block prop, and any count that keeps the
     * rooms smooth would take an hour over the props. The budget is checked after each piece rather
     * than before, so a single piece too large for it still gets placed &mdash; overshooting one tick
     * is the correct failure here, and stalling forever is not.
     */
    private static final class Run implements Runnable {

        /** Well under a 50 ms tick, so the server still has room to do everything else it owes. */
        private static final long BUDGET_NANOS = 25_000_000L;

        private final CommandSourceStack source;
        private final ServerLevel level;
        private final List<ResourceLocation> pieces;
        private final GalleryGrid grid;
        @Nullable
        private final ResourceKey<RiftTheme> theme;
        private final double labelY;
        private final boolean editing;

        private int next;
        private int failed;

        private Run(CommandSourceStack source, ServerLevel level, List<ResourceLocation> pieces,
                    GalleryGrid grid, @Nullable ResourceKey<RiftTheme> theme, double labelY, boolean editing) {
            this.source = source;
            this.level = level;
            this.pieces = pieces;
            this.grid = grid;
            this.theme = theme;
            this.labelY = labelY;
            this.editing = editing;
        }

        /**
         * Queues the next batch for a later tick.
         *
         * <p>
         * {@code TickTask} with a tick number ahead of the current one, not {@code server.execute}:
         * the server drains its plain task queue inside the tick that scheduled it, which would run
         * every batch back to back and reproduce exactly the freeze this exists to avoid.
         */
        private void schedule() {
            MinecraftServer server = source.getServer();
            server.tell(new TickTask(server.getTickCount() + 1, this));
        }

        @Override
        public void run() {
            long deadline = System.nanoTime() + BUDGET_NANOS;
            while (next < pieces.size()) {
                ResourceLocation id = pieces.get(next++);
                BlockPos corner = grid.cellCorner(next - 1);
                try {
                    Vec3i size = stamp(level, id, theme, TripleMirror.IDENTITY, corner, editing);
                    // After the stamp, not before it: stamping is what guarantees this cell's chunks
                    // are loaded, and an entity in an unloaded chunk cannot be found to be removed.
                    clearLabels(level, corner, grid.cell());
                    label(level, id, corner, size, labelY);
                    if (editing) {
                        // Under the corner, in the hover gap, so the same (0, 1, 0) offset that
                        // `edit` relies on frames the cell above it.
                        saveBlock(level, corner.below(), id, size);
                    }
                } catch (CommandSyntaxException exception) {
                    // Only reachable if a reload pulled the template out from under a run that
                    // already listed it. One missing cell is not a reason to abandon the other 600.
                    failed++;
                }
                if (System.nanoTime() >= deadline) {
                    break;
                }
            }

            if (next < pieces.size()) {
                schedule();
                return;
            }
            int placed = pieces.size() - failed;
            source.sendSuccess(() -> Component.literal("Laid out " + placed + " piece(s)"
                    + (failed > 0 ? ", " + failed + " missing" : "")), true);
        }
    }

    private static int list(CommandSourceStack source, String filter) throws CommandSyntaxException {
        List<ResourceLocation> pieces =
                GalleryPieces.select(source.getLevel().getStructureManager().listTemplates(), filter);
        if (pieces.isEmpty()) {
            throw NOTHING_MATCHED.create(filter.isEmpty() ? GalleryPieces.PIECE_PREFIX : filter);
        }
        for (ResourceLocation id : pieces.subList(0, Math.min(pieces.size(), LIST_LIMIT))) {
            source.sendSuccess(() -> Component.literal(id.toString()), false);
        }
        int total = pieces.size();
        if (total > LIST_LIMIT) {
            source.sendSuccess(() -> Component.literal("... and " + (total - LIST_LIMIT) + " more"), false);
        }
        source.sendSuccess(() -> Component.literal(total + " piece(s)"), false);
        return total;
    }

    // -----------------------------------------------------------------------------------------------
    // Placement
    // -----------------------------------------------------------------------------------------------

    /**
     * Puts one template's minimum corner on {@code corner} and returns its oriented extent.
     *
     * @param theme null for the authoring view: no substitution processor is added at all, so the
     *              placeholders land as placeholders
     * @param keepJigsaws leaves the {@code minecraft:jigsaw} blocks standing instead of resolving
     *                    them. Only {@link #edit} wants this, and it is the whole reason that verb
     *                    exists: the jigsaws are where a room's sockets and every one of its prop
     *                    anchors are recorded, and the anchors are recorded <em>nowhere else</em>.
     *                    Editing a room that had been placed without them and saving it back would
     *                    write a file that still looks like a room and has quietly lost every POI in
     *                    it &mdash; 37 of the first 60 templates carry jigsaws, so that is the normal
     *                    case rather than the unlucky one.
     */
    private static Vec3i stamp(ServerLevel level, ResourceLocation id, @Nullable ResourceKey<RiftTheme> theme,
                               TripleMirror mirror, BlockPos corner, boolean keepJigsaws)
            throws CommandSyntaxException {
        StructureTemplateManager templates = level.getStructureManager();
        StructureTemplate template = templates.get(id)
                .orElseThrow(() -> NO_SUCH_TEMPLATE.create(id));

        // The authored extent, untransformed. TemplatePlacement.anchor applies the orientation
        // itself, and handing it an already-transformed size applies it twice -- the same trap
        // RiftPlan.of documents.
        Vec3i size = template.getSize();

        StructurePlaceSettings settings = TemplatePlacement.settings(mirror)
                .setKnownShape(true)
                .setIgnoreEntities(false)
                .setFinalizeEntities(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        if (!keepJigsaws) {
            settings.addProcessor(JigsawReplacementProcessor.INSTANCE);
        }
        if (theme != null) {
            settings.addProcessor(new ThemeSubstitutionProcessor(Optional.of(theme)));
        }

        BoundingBox bounds = TemplatePlacement.boundsAt(size, mirror, corner);
        loadChunks(level, bounds);

        BlockPos anchor = TemplatePlacement.anchor(size, mirror, corner);
        // Seeded from the id so that stamping the same piece twice gives the same result. The theme
        // processor picks materials from world-position noise and does not read this, but the
        // jigsaw replacement and the liquid handling do.
        // A live level runs onPlace, where Dusty 2.3.x lures and papers throw one time in five.
        TilePlacementGuard.placing(id, () -> template.placeInWorld(level, anchor, anchor, settings,
                RandomSource.create(id.hashCode()), PLACE_FLAGS));
        return mirror.transformSize(size);
    }

    /**
     * Puts a structure block under the piece, already filled in to save it back over itself.
     *
     * <p>
     * Below the minimum corner rather than beside it, because that is where vanilla's own default
     * offset of {@code (0, 1, 0)} already points: an author who breaks this block and puts a fresh one
     * down gets the same framing without typing anything.
     *
     * <h2>Entities are not ignored</h2>
     * {@code ignoreEntities} defaults to <em>true</em>, and 18 of the 620 shipped templates carry
     * entities &mdash; up to 21 of them, in pieces like {@code rift/poi/free/7/gallows-1-wout}. Left
     * at the default, saving one of those back writes a gallows with no entities on it and reports
     * success. So it is turned off here rather than left to be noticed.
     *
     * <h2>The block entity has to be pushed to the client</h2>
     * {@code setBlock} tells the client the block changed; it does not send what is behind it. Without
     * the update below, the structure block arrives with an empty name and a zero size, and the first
     * thing the author does is retype what this method already knew.
     */
    private static void saveBlock(ServerLevel level, BlockPos pos, ResourceLocation id, Vec3i size) {
        BlockState state = Blocks.STRUCTURE_BLOCK.defaultBlockState().setValue(StructureBlock.MODE, StructureMode.SAVE);
        level.setBlock(pos, state, PLACE_FLAGS);
        if (level.getBlockEntity(pos) instanceof StructureBlockEntity control) {
            control.setStructureName(id);
            control.setStructurePos(new BlockPos(0, 1, 0));
            control.setStructureSize(size);
            control.setIgnoreEntities(false);
            control.setShowBoundingBox(true);
            control.setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
    }

    /**
     * Brings every chunk the piece will touch to {@code FULL} before a single block is written.
     *
     * <p>
     * This is the gate, and it is a blocking fetch rather than a forced-chunk ticket on purpose. A
     * ticket is not a load: {@code setChunkForced} queues one for the chunk source to act on, and a
     * command runs to completion inside a single tick, so a ticket added and released within one
     * gallery run is never processed at all. The synchronous {@code getChunk} is what actually
     * guarantees the chunk exists.
     *
     * <p>
     * {@code ServerLevel.setBlock} would load the chunk itself, which is why an unloaded chunk here
     * is a performance problem rather than the silent data loss it would be on a
     * {@code WorldGenLevel}. Doing it once per chunk instead of once per block is the point.
     */
    private static void loadChunks(ServerLevel level, BoundingBox bounds) {
        for (int x = SectionPos.blockToSectionCoord(bounds.minX());
             x <= SectionPos.blockToSectionCoord(bounds.maxX()); x++) {
            for (int z = SectionPos.blockToSectionCoord(bounds.minZ());
                 z <= SectionPos.blockToSectionCoord(bounds.maxZ()); z++) {
                level.getChunk(x, z);
            }
        }
    }

    // -----------------------------------------------------------------------------------------------
    // Labels
    // -----------------------------------------------------------------------------------------------

    /**
     * Floats the piece's id over its cell.
     *
     * <p>
     * A gallery without labels is a room of unattributed rooms: the one question an author asks in
     * front of a cell is "which file is this", and there is nothing in a stamped piece that answers
     * it. Built from NBT rather than from setters because every setter on {@code Display.TextDisplay}
     * is private; {@code readAdditionalSaveData} is the only door in, and it is the same one
     * {@code /summon} uses.
     *
     * @param y one height for the whole run, so the labels form a plane rather than a skyline
     */
    private static void label(ServerLevel level, ResourceLocation id, BlockPos corner, Vec3i size, double y) {
        double x = corner.getX() + size.getX() / 2.0;
        double z = corner.getZ() + size.getZ() / 2.0;

        CompoundTag tag = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(x));
        pos.add(DoubleTag.valueOf(y));
        pos.add(DoubleTag.valueOf(z));
        tag.put("Pos", pos);
        tag.putString("text", Component.Serializer.toJson(Component.literal(id.toString()), level.registryAccess()));
        // CENTER so the label faces the reader from anywhere in the aisle, and a view range four
        // times the default so a row is legible from the end of it rather than only up close.
        tag.putString("billboard", "center");
        tag.putFloat("view_range", 4.0F);
        tag.putString("alignment", "center");
        tag.putBoolean("see_through", false);
        tag.putBoolean("shadow", false);
        tag.putBoolean("default_background", false);
        tag.putInt("background", 0xC0101010); // near-opaque black: readable over any theme's palette
        tag.put("transformation", scale(LABEL_SCALE));

        Entity entity = EntityType.TEXT_DISPLAY.create(level);
        if (entity == null) {
            return;
        }
        entity.load(tag);
        level.addFreshEntity(entity);
    }

    /**
     * A pure scale, in the four-part form {@code Transformation.EXTENDED_CODEC} expects.
     *
     * <p>
     * Every one of the four fields is required by that codec, so an identity rotation cannot simply
     * be left out. Text-display glyphs are authored tiny &mdash; at native size an id read from the
     * far end of a 12-cell row is a grey smudge &mdash; and a label that has to be walked up to
     * answers the question the gallery already answers by being walked round.
     */
    private static CompoundTag scale(float scale) {
        CompoundTag transformation = new CompoundTag();
        transformation.put("translation", floats(0.0F, 0.0F, 0.0F));
        transformation.put("left_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        transformation.put("scale", floats(scale, scale, scale));
        transformation.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        return transformation;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }

    /**
     * Removes the labels a previous run left in one cell.
     *
     * <p>
     * Only the labels. The blocks of a previous run are overwritten as the new piece goes down, but
     * its text display is an entity and would otherwise sit on top of the new one, two ids in the
     * same place, until nobody could read either.
     *
     * <p>
     * The cell's whole column, floor to build limit, rather than a box around where the label is
     * about to go. Labels hang above the tallest piece in their run, so a run of short pieces
     * following a run of tall ones would otherwise leave the old labels stranded overhead.
     *
     * <p>
     * Per cell rather than once over the whole run, because the two ways of getting a chunk are not
     * equivalent and only one of them works here. Sweeping the run's footprint up front finds
     * entities only in chunks that happen to be loaded already, and a forceload does not make that
     * true &mdash; it queues a ticket the chunk source acts on over following ticks. That sweep
     * therefore cleared whichever cells were loaded and quietly skipped the rest.
     */
    private static void clearLabels(ServerLevel level, BlockPos corner, int cell) {
        AABB box = new AABB(corner.getX(), level.getMinBuildHeight(), corner.getZ(),
                corner.getX() + cell, level.getMaxBuildHeight(), corner.getZ() + cell);
        List<Display.TextDisplay> stale = new ArrayList<>(
                level.getEntitiesOfClass(Display.TextDisplay.class, box));
        stale.forEach(Entity::discard);
    }

    // -----------------------------------------------------------------------------------------------
    // Argument plumbing
    // -----------------------------------------------------------------------------------------------

    /**
     * The theme argument, resolved and checked against the registry.
     *
     * <p>
     * Checked here rather than left to the processor, which falls back to {@code riftfall:default}
     * for a theme it cannot find. That is right for worldgen &mdash; a missing theme must not become
     * a missing dungeon &mdash; and wrong for a command, where a mistyped theme silently rendering
     * the default is how someone concludes a theme is broken when they simply never applied it.
     */
    private static ResourceKey<RiftTheme> theme(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ResourceKey<?> raw = context.getArgument("theme", ResourceKey.class);
        ResourceKey<RiftTheme> key = raw.cast(RiftfallRegistries.RIFT_THEMES)
                .orElseThrow(() -> NO_SUCH_THEME.create(raw.location()));
        if (!context.getSource().registryAccess().registryOrThrow(RiftfallRegistries.RIFT_THEMES)
                .containsKey(key)) {
            throw NO_SUCH_THEME.create(key.location());
        }
        return key;
    }

    /**
     * The filter, as a path.
     *
     * <p>
     * A {@code ResourceLocation} argument rather than a string because brigadier's unquoted string
     * reader stops at a slash, and every useful filter has one. The namespace is thrown away, so
     * both {@code rift/room} (which parses as {@code minecraft:rift/room}) and
     * {@code riftfall:rift/room} mean the same thing, and neither is a trap.
     */
    private static String filter(CommandContext<CommandSourceStack> context) {
        return ResourceLocationArgument.getId(context, "filter").getPath();
    }

    private static TripleMirror orientationOf(CommandContext<CommandSourceStack> context) {
        return TripleMirror.byIndex(IntegerArgumentType.getInteger(context, "orientation"));
    }

    private static String describe(@Nullable ResourceKey<RiftTheme> theme) {
        return theme == null ? "raw placeholders" : "theme " + theme.location();
    }
}
