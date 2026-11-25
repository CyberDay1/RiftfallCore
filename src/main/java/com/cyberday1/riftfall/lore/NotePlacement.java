package com.cyberday1.riftfall.lore;

import com.cyberday1.riftfall.home.ShatteredHomeProgress;
import com.cyberday1.riftfall.home.ShatteredHomeTiles;
import com.cyberday1.riftfall.init.RiftfallBlocks;
import com.cyberday1.riftfall.lore.NoteMarkerBlock.Carrier;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Turns note markers into their carriers when a tile is placed, and drops wilderness notes. */
public final class NotePlacement {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Chance a wilderness tile carries one note from the pool (no repeats per home). */
    public static final double WILD_CHANCE = 0.2;
    private static final long WILD_SALT = 0x6C6F72655F6E6F74L;

    /** A marker read from a template, in world coordinates; {@code note} is empty for an unknown slot. */
    public record Pending(BlockPos pos, Carrier carrier, Direction facing, Optional<LoreNotes.Note> note) {}

    private NotePlacement() {}

    /** The tile a template belongs to: its file name, the same in layout_v1 and layout_v2. */
    public static String tileOf(ResourceLocation template) {
        var path = template.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public static List<Pending> read(StructureTemplate template, ResourceLocation templateId, BlockPos origin,
                                     StructurePlaceSettings settings) {
        var tile = tileOf(templateId);
        var out = new ArrayList<Pending>();
        for (var info : template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), RiftfallBlocks.NOTE_MARKER.get())) {
            var pos = StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(origin);
            var state = info.state().mirror(settings.getMirror()).rotate(settings.getRotation());
            out.add(new Pending(pos, state.getValue(NoteMarkerBlock.CARRIER), state.getValue(NoteMarkerBlock.FACING),
                    LoreNotes.note(tile, state.getValue(NoteMarkerBlock.NOTE))));
        }
        return List.copyOf(out);
    }

    /** After the template is in the world; an unknown slot leaves air, never a marker. */
    public static void resolve(ServerLevel level, List<Pending> pending, ShatteredHomeTiles.GridPos grid) {
        if (pending.isEmpty()) return;
        var progress = ShatteredHomeProgress.of(level);
        for (var p : pending) {
            if (p.note().isEmpty()) {
                level.setBlock(p.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                LOGGER.warn("Note marker at {} names no note", p.pos());
                continue;
            }
            if (!place(level, p.pos(), p.carrier(), p.facing(), p.note().get()))
                LOGGER.warn("Could not place note {} at {}", p.note().get().id(), p.pos());
            progress.markNotePlaced(key(grid, p.note().get()));
        }
    }

    /** The saved-progress key for one note of one placed tile. */
    public static String key(ShatteredHomeTiles.GridPos grid, LoreNotes.Note note) {
        return grid.x() + "," + grid.z() + ":" + note.id();
    }

    public static boolean place(ServerLevel level, BlockPos pos, Carrier carrier, Direction facing, LoreNotes.Note note) {
        if (retired(note)) {
            if (level.getBlockState(pos).is(RiftfallBlocks.NOTE_MARKER.get()))
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        var horizontal = facing.getAxis().isHorizontal() ? facing : Direction.SOUTH;
        if (note.sign()) return sign(level, pos, carrier, facing, note);
        var stack = LostNoteItem.of(note);
        switch (carrier) {
            case LECTERN -> {
                level.setBlock(pos, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, horizontal)
                        .setValue(LecternBlock.HAS_BOOK, true), Block.UPDATE_ALL);
                if (!(level.getBlockEntity(pos) instanceof LecternBlockEntity lectern)) return false;
                lectern.setBook(stack);
            }
            case CHEST -> {
                level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, horizontal), Block.UPDATE_ALL);
                if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return false;
                chest.setItem(13, stack);
            }
            case BARREL -> {
                level.setBlock(pos, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), Block.UPDATE_ALL);
                if (!(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)) return false;
                barrel.setItem(13, stack);
            }
            case SHELF -> {
                level.setBlock(pos, Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, horizontal),
                        Block.UPDATE_ALL);
                if (!(level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf)) return false;
                shelf.setItem(1, stack);
            }
            case FRAME, SIGN, BIRCH_SIGN, STANDING_SIGN -> {
                if (level.getBlockState(pos).is(RiftfallBlocks.NOTE_MARKER.get()))
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                if (!level.getEntitiesOfClass(HangingEntity.class, new AABB(pos)).isEmpty()) return false;
                var frame = new ItemFrame(level, pos, facing);
                if (!frame.survives()) return false;
                frame.setItem(stack, false);
                var tag = new net.minecraft.nbt.CompoundTag();
                frame.saveWithoutId(tag);
                tag.putBoolean("Fixed", true);
                frame.load(tag);
                level.addFreshEntity(frame);
            }
        }
        return true;
    }

    private static boolean sign(ServerLevel level, BlockPos pos, Carrier carrier, Direction facing, LoreNotes.Note note) {
        var state = switch (carrier) {
            case BIRCH_SIGN -> Blocks.BIRCH_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing);
            case SIGN -> Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing);
            default -> Blocks.SPRUCE_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION,
                    facing.getAxis().isHorizontal() ? facing.get2DDataValue() * 4 : 0);
        };
        if (facing == Direction.UP && state.hasProperty(WallSignBlock.FACING))
            state = Blocks.SPRUCE_SIGN.defaultBlockState();
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return false;
        var text = new SignText();
        for (int i = 0; i < note.lines(); i++) text = text.setMessage(i, Component.translatable(note.lineKey(i)));
        sign.setText(text, true);
        sign.setWaxed(true);
        sign.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        return true;
    }

    /** The note a sign carries, from its translatable first line. */
    public static Optional<LoreNotes.Note> signNote(SignBlockEntity sign) {
        for (var side : List.of(sign.getFrontText(), sign.getBackText())) {
            if (side.getMessage(0, false).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().startsWith("lore.riftfall.note.") && t.getKey().endsWith(".line.0")) {
                var key = t.getKey();
                return LoreNotes.note(key.substring("lore.riftfall.note.".length(), key.length() - ".line.0".length()));
            }
        }
        return Optional.empty();
    }

    /** Keep the historical text for already discovered lore, but retire its physical sign. */
    public static boolean retired(LoreNotes.Note note) { return note.retired(); }

    /** Only the unchanged generated sign at its recipe location may be removed. */
    static void removeRetiredSign(ServerLevel level, BlockPos pos, LoreNotes.Note note) {
        if (!retired(note) || !(level.getBlockEntity(pos) instanceof SignBlockEntity sign) || !sign.isWaxed()) return;
        for (int i = 0; i < 4; i++) {
            var expected = i < note.lines() ? Component.translatable(note.lineKey(i)) : Component.empty();
            if (!sign.getFrontText().getMessage(i, false).equals(expected)
                    || !sign.getBackText().getMessage(i, false).equals(Component.empty())) return;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** At most one pool note on a new wilderness tile, seeded by the tile. Returns its id when one was placed. */
    public static Optional<String> wild(ServerLevel level, ShatteredHomeTiles.GridPos grid, long seed) {
        var random = new Random(seed ^ WILD_SALT);
        if (random.nextDouble() >= WILD_CHANCE) return Optional.empty();
        var progress = ShatteredHomeProgress.of(level);
        var left = LoreNotes.wilderness().stream().filter(n -> !progress.wildNotes().contains(n.id())).toList();
        if (left.isEmpty()) return Optional.empty();
        var note = left.get(random.nextInt(left.size()));
        var spot = spot(level, grid, random);
        if (spot == null) return Optional.empty();
        var carrier = note.sign() ? Carrier.STANDING_SIGN : note.look() == LoreNotes.Look.CLOCK ? Carrier.FRAME : Carrier.CHEST;
        var facing = carrier == Carrier.FRAME ? Direction.UP : Direction.from2DDataValue(random.nextInt(4));
        if (!place(level, spot, carrier, facing, note)) return Optional.empty();
        progress.recordWildNote(note.id());
        return Optional.of(note.id());
    }

    /** Dry, sturdy ground with headroom, preferring a spot beside a trunk or a stone. */
    private static @Nullable BlockPos spot(ServerLevel level, ShatteredHomeTiles.GridPos grid, Random random) {
        var origin = grid.blockOrigin();
        BlockPos open = null;
        for (int attempt = 0; attempt < 400; attempt++) {
            int x = origin.getX() + 8 + random.nextInt(84), z = origin.getZ() + 8 + random.nextInt(84);
            var pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            var below = pos.below();
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
                    || !level.getFluidState(below).isEmpty()
                    || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue;
            for (var side : Direction.Plane.HORIZONTAL) {
                var beside = level.getBlockState(pos.relative(side));
                if (beside.isSolid() && beside.getFluidState().isEmpty()) return pos;
            }
            if (open == null) open = pos;
        }
        return open;
    }
}
