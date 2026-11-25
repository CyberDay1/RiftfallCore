package com.cyberday1.riftfall.data;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.MarkerKind;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.block.marker.NpcSpawnMarkerBlock;
import com.cyberday1.riftfall.init.RiftfallBlocks;
import com.cyberday1.riftfall.block.marker.PoiAnchorKind;
import com.cyberday1.riftfall.block.marker.PoiAnchors;
import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.EnumMap;
import java.util.Map;

/**
 * Blockstate + model generation for the nine rift marker blocks.
 *
 * <p>
 * One of the nine wears art carried over from Unbound Ascension: {@code rift_chest} takes that
 * mod's dungeon chest. <strong>The other eight are still vanilla placeholders</strong>, borrowed
 * purely so the marker is distinguishable at a glance until the art is done. Nothing downstream
 * keys off any of them.
 *
 * <p>
 * The variants are emitted with {@code forAllStates} rather than {@code simpleBlock}, which means
 * the generated JSON enumerates every blockstate the block has instead of collapsing to one
 * catch-all entry. That is intentional and doubles as the checkable artifact: the file for
 * {@code rift_chest} must contain 4 x 3 x 2 = 24 entries, {@code spring_block} 10 x 2 = 20,
 * {@code rift_mob_spawner} 2 x 6 = 12, {@code objective_block} 2, and 1 each for {@code quest_hub},
 * {@code exit_portal}, {@code boss_arena}, {@code boss_entry} and {@code boss_gate}. A short file
 * means a property went missing.
 */
public class MarkerBlockStateProvider extends BlockStateProvider {

    /** Vanilla stand-ins, for the seven markers no art exists for yet — see the class comment. */
    private static final Map<MarkerKind, String> PLACEHOLDER_TEXTURES = placeholderTextures();

    /**
     * Vanilla stand-ins for the four POI anchors, picked to be told apart across a room rather than
     * to be pretty. Nothing commissions art for these: a builder is the only one who ever sees one.
     */
    private static final Map<PoiAnchorKind, String> ANCHOR_TEXTURES = anchorTextures();

    public MarkerBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, RiftfallCore.MODID, existingFileHelper);
    }

    private static Map<MarkerKind, String> placeholderTextures() {
        EnumMap<MarkerKind, String> textures = new EnumMap<>(MarkerKind.class);
        textures.put(MarkerKind.SPRING_BLOCK, "block/slime_block");
        textures.put(MarkerKind.QUEST_HUB, "block/lodestone_side");
        textures.put(MarkerKind.OBJECTIVE_BLOCK, "block/target_side");
        textures.put(MarkerKind.RIFT_MOB_SPAWNER, "block/chiseled_deepslate");
        textures.put(MarkerKind.EXIT_PORTAL, "block/crying_obsidian");
        textures.put(MarkerKind.BOSS_ARENA, "block/chiseled_polished_blackstone");
        textures.put(MarkerKind.BOSS_ENTRY, "block/gilded_blackstone");
        // A grate, because the gate it stands in for is bars: the shape is the readable part.
        textures.put(MarkerKind.BOSS_GATE, "block/copper_grate");
        return textures;
    }

    private static Map<PoiAnchorKind, String> anchorTextures() {
        EnumMap<PoiAnchorKind, String> textures = new EnumMap<>(PoiAnchorKind.class);
        textures.put(PoiAnchorKind.FREE, "block/amethyst_block");
        textures.put(PoiAnchorKind.CEILING, "block/purpur_block");
        textures.put(PoiAnchorKind.INWALL, "block/quartz_bricks");
        textures.put(PoiAnchorKind.HALFWAY, "block/prismarine_bricks");
        return textures;
    }

    /**
     * The generator keys providers by name and rejects duplicates, and
     * {@code BlockStateProvider}'s default name is just {@code "Block States: <modid>"} — so a
     * second one in the same mod has to say which it is.
     */
    @Override
    public String getName() {
        return "Marker Block States: " + RiftfallCore.MODID;
    }

    @Override
    protected void registerStatesAndModels() {
        for (MarkerKind kind : MarkerKind.ALL) {
            marker(kind);
        }
        for (PoiAnchorKind kind : PoiAnchorKind.ALL) {
            anchor(kind);
        }
        for (NpcRole role : NpcRole.values()) {
            Block block = RiftfallBlocks.NPC_SPAWN_MARKERS.get(role).get();
            ModelFile model = models().withExistingParent(role.blockId(), modLoc("block/npc_spawn_marker"))
                    .texture("role", mcLoc("block/" + role.color() + "_concrete"));
            getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder().modelFile(model)
                    .rotationY(((int) state.getValue(NpcSpawnMarkerBlock.FACING).toYRot() + 180) % 360).build());
            itemModels().withExistingParent(role.blockId(), modLoc("block/" + role.blockId()));
        }
    }

    private void marker(MarkerKind kind) {
        Block block = RiftfallMarkers.block(kind);
        ModelFile model = model(kind);

        // One model for every state: the markers are inert, so no state changes how they look yet.
        getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder().modelFile(model).build());
        itemModels().withExistingParent(kind.blockId(), modLoc("block/" + kind.blockId()));
    }

    /**
     * One model per category, and not one per size or facing. Twenty models saying a number would be
     * twenty pieces of art for a block that is deleted at generation and that no player ever stands
     * in front of; the size is reported on the action bar when it is cycled instead. So the file for
     * each of the two facing anchors carries 5 &times; 4 = 20 identical entries, and the two without
     * a facing carry 5.
     */
    private void anchor(PoiAnchorKind kind) {
        Block block = PoiAnchors.block(kind);
        ModelFile model = models().cubeAll(kind.blockId(), mcLoc(ANCHOR_TEXTURES.get(kind)));

        getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder().modelFile(model).build());
        itemModels().withExistingParent(kind.blockId(), modLoc("block/" + kind.blockId()));
    }

    /** Wrap our own texture in a cube, or a vanilla one where no art exists yet. */
    private ModelFile model(MarkerKind kind) {
        ResourceLocation self = modLoc("block/" + kind.blockId());
        String placeholder = PLACEHOLDER_TEXTURES.get(kind);
        return models().cubeAll(kind.blockId(), placeholder != null ? mcLoc(placeholder) : self);
    }
}
