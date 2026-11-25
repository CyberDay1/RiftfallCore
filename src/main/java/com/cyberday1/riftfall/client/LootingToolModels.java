package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.tool.LootingToolItem;
import com.cyberday1.riftfall.tool.ToolAppearances;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Reuses installed item models, including their transforms and emissive quads, without copying assets. */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class LootingToolModels {
    private LootingToolModels() {}

    @SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    var minecraft = Minecraft.getInstance();
                    renderer = new LootingToolRenderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
                }
                return renderer;
            }
        }, RiftfallItems.LOOTING_PICK.get());
    }

    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        var key = ModelResourceLocation.inventory(RiftfallCore.id("looting_pick"));
        BakedModel fallback = models.get(key);
        if (fallback == null) return;
        Map<ResourceLocation, BakedModel> appearances = new HashMap<>();
        for (var skin : ToolAppearances.ALL) {
            BakedModel model = models.get(ModelResourceLocation.inventory(skin.item()));
            if (model != null) appearances.put(skin.item(), model);
        }
        BakedModel base = appearances.getOrDefault(ToolAppearances.DEFAULT.item(), fallback);
        models.put(key, new AppearanceModel(base, Map.copyOf(appearances)));
    }

    static final class AppearanceModel extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;
        AppearanceModel(BakedModel base, Map<ResourceLocation, BakedModel> models) {
            super(base);
            overrides = new ItemOverrides() {
                @Override public BakedModel resolve(BakedModel original, ItemStack stack,
                        @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                    return models.getOrDefault(LootingToolItem.appearance(stack).item(), base);
                }
            };
        }
        @Override public ItemOverrides getOverrides() { return overrides; }
    }
}
