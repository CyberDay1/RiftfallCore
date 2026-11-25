package com.cyberday1.riftfall.home.npc.conversion;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.init.RiftfallRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * {@code riftfall:resident_conversion}, from {@code data/<ns>/recipe/resident/**.json}: inputs plus Rift Shards
 * become a result at one resident. A path recipe ({@code specialisation}) shows only for that path and hides the
 * recipe it {@code replaces}; {@code work_order} starts an order instead of filling the output; {@code requires.research}
 * keeps a recipe closed until research exists.
 */
public record ResidentConversionRecipe(NpcRole role, ResidentRank minRank, Optional<String> specialisation,
                                       List<SizedIngredient> inputs, Optional<Ingredient> catalyst, int shards,
                                       ItemStack result, Optional<Requirements> requires, Optional<WorkOrder> workOrder,
                                       Optional<ResourceLocation> replaces)
        implements Recipe<ResidentConversionRecipe.Input> {
    public static final int MAX_INPUTS = 6;

    public record Requirements(Optional<ResourceLocation> discovery, int minTier, Optional<ResourceLocation> research) {
        static final Codec<Requirements> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("discovery").forGetter(Requirements::discovery),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("min_tier", 0).forGetter(Requirements::minTier),
                ResourceLocation.CODEC.optionalFieldOf("research").forGetter(Requirements::research)
        ).apply(i, Requirements::new));
    }
    public record WorkOrder(int runs, int minutes) {
        static final Codec<WorkOrder> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("runs", 0).forGetter(WorkOrder::runs),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("minutes", 0).forGetter(WorkOrder::minutes)
        ).apply(i, WorkOrder::new));
    }

    /** The service's input slots, in order. */
    public record Input(List<ItemStack> stacks) implements RecipeInput {
        @Override public ItemStack getItem(int index) { return stacks.get(index); }
        @Override public int size() { return stacks.size(); }
    }

    static final Codec<NpcRole> ROLE_CODEC = Codec.STRING.comapFlatMap(id -> Arrays.stream(NpcRole.values())
            .filter(r -> r.id().equals(id)).findFirst().map(DataResult::success)
            .orElseGet(() -> DataResult.error(() -> "Unknown resident role " + id)), NpcRole::id);

    public static final MapCodec<ResidentConversionRecipe> CODEC = RecordCodecBuilder.<ResidentConversionRecipe>mapCodec(i -> i.group(
            ROLE_CODEC.fieldOf("role").forGetter(ResidentConversionRecipe::role),
            ResidentRank.CODEC.optionalFieldOf("min_rank", ResidentRank.APPRENTICE).forGetter(ResidentConversionRecipe::minRank),
            Codec.STRING.optionalFieldOf("specialisation").forGetter(ResidentConversionRecipe::specialisation),
            SizedIngredient.FLAT_CODEC.listOf().fieldOf("inputs").forGetter(ResidentConversionRecipe::inputs),
            Ingredient.CODEC_NONEMPTY.optionalFieldOf("catalyst").forGetter(ResidentConversionRecipe::catalyst),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("shards", 0).forGetter(ResidentConversionRecipe::shards),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ResidentConversionRecipe::result),
            Requirements.CODEC.optionalFieldOf("requires").forGetter(ResidentConversionRecipe::requires),
            WorkOrder.CODEC.optionalFieldOf("work_order").forGetter(ResidentConversionRecipe::workOrder),
            ResourceLocation.CODEC.optionalFieldOf("replaces").forGetter(ResidentConversionRecipe::replaces)
    ).apply(i, ResidentConversionRecipe::new)).validate(ResidentConversionRecipe::validate);

    public static final StreamCodec<RegistryFriendlyByteBuf, ResidentConversionRecipe> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    private static DataResult<ResidentConversionRecipe> validate(ResidentConversionRecipe recipe) {
        if (recipe.inputs.isEmpty() || recipe.inputs.size() > MAX_INPUTS)
            return DataResult.error(() -> "A resident conversion needs 1 to " + MAX_INPUTS + " inputs");
        if (recipe.result.isEmpty()) return DataResult.error(() -> "A resident conversion needs a result");
        return DataResult.success(recipe);
    }

    public ResidentConversionRecipe {
        inputs = List.copyOf(inputs);
        result = result.copy();
    }

    public ResidentConversionRecipe(NpcRole role, ResidentRank minRank, Optional<String> specialisation, List<SizedIngredient> inputs,
                                    Optional<Ingredient> catalyst, int shards, ItemStack result, Optional<Requirements> requires,
                                    Optional<WorkOrder> workOrder) {
        this(role, minRank, specialisation, inputs, catalyst, shards, result, requires, workOrder, Optional.empty());
    }

    /** Units taken from each input stack, or null if the inputs do not cover the recipe. */
    public int[] allocate(List<ItemStack> stacks) {
        int[] left = new int[stacks.size()], take = new int[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) left[i] = stacks.get(i).getCount();
        for (var input : inputs) {
            int need = input.count();
            for (int i = 0; need > 0 && i < stacks.size(); i++) {
                if (left[i] <= 0 || !input.ingredient().test(stacks.get(i))) continue;
                int n = Math.min(need, left[i]);
                left[i] -= n; take[i] += n; need -= n;
            }
            if (need > 0) return null;
        }
        if (catalyst.isPresent()) {
            boolean found = false;
            for (int i = 0; i < stacks.size() && !found; i++) found = left[i] > 0 && catalyst.get().test(stacks.get(i));
            if (!found) return null;
        }
        return take;
    }

    /** Whether the recipe needs a layer that is not built yet (research); such recipes stay closed. */
    public boolean deferred() {
        return requires.flatMap(Requirements::research).isPresent();
    }

    /** Started as a work order rather than made on the spot. */
    public boolean ordered() {
        return workOrder.map(o -> o.runs() > 0 || o.minutes() > 0).orElse(false);
    }

    @Override public boolean matches(Input input, Level level) { return allocate(input.stacks()) != null; }
    @Override public ItemStack assemble(Input input, HolderLookup.Provider registries) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return result; }
    @Override public NonNullList<Ingredient> getIngredients() {
        var list = NonNullList.<Ingredient>create();
        for (var input : inputs) list.add(input.ingredient());
        return list;
    }
    @Override public boolean isSpecial() { return true; } // No recipe book category; JEI reads the type directly.
    @Override public RecipeSerializer<?> getSerializer() { return RiftfallRecipes.RESIDENT_CONVERSION_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return RiftfallRecipes.RESIDENT_CONVERSION.get(); }

    public static final class Serializer implements RecipeSerializer<ResidentConversionRecipe> {
        @Override public MapCodec<ResidentConversionRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ResidentConversionRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
