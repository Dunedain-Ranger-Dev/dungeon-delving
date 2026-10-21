package io.github.ranger3462.dungeondelving.workshop;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * A Runic Workshop recipe: an optional key, an optional shard, and a stack of some material (e.g. 12 Barrow Iron
 * ingots), made into a result. Recipe files use the type {@code dungeondelving:runic_workshop}:
 * <pre>{"type": "dungeondelving:runic_workshop", "key": {...}, "shard": {...}, "material": {...}, "count": 12, "result": {...}}</pre>
 */
public record RunicWorkshopRecipe(Optional<Ingredient> key, Optional<Ingredient> shard, Ingredient material, int count, ItemStack result)
        implements Recipe<RunicWorkshopRecipe.Input> {

    /** The workshop's three input slots: key, shard and material. */
    public record Input(ItemStack key, ItemStack shard, ItemStack material) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return switch (index) {
                case 0 -> key;
                case 1 -> shard;
                case 2 -> material;
                default -> throw new IllegalArgumentException("No workshop slot " + index);
            };
        }

        @Override
        public int size() {
            return 3;
        }
    }

    public static final MapCodec<RunicWorkshopRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.optionalFieldOf("key").forGetter(RunicWorkshopRecipe::key),
            Ingredient.CODEC_NONEMPTY.optionalFieldOf("shard").forGetter(RunicWorkshopRecipe::shard),
            Ingredient.CODEC_NONEMPTY.fieldOf("material").forGetter(RunicWorkshopRecipe::material),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(RunicWorkshopRecipe::count),
            ItemStack.CODEC.fieldOf("result").forGetter(RunicWorkshopRecipe::result)).apply(instance, RunicWorkshopRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RunicWorkshopRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC), RunicWorkshopRecipe::key,
            ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC), RunicWorkshopRecipe::shard,
            Ingredient.CONTENTS_STREAM_CODEC, RunicWorkshopRecipe::material,
            ByteBufCodecs.VAR_INT, RunicWorkshopRecipe::count,
            ItemStack.STREAM_CODEC, RunicWorkshopRecipe::result,
            RunicWorkshopRecipe::new);

    @Override
    public boolean matches(Input input, Level level) {
        return fits(key, input.key()) && fits(shard, input.shard()) && material.test(input.material()) && input.material().getCount() >= count;
    }

    /** A slot fits if it holds the ingredient, or is empty when the recipe doesn't use it. */
    private static boolean fits(Optional<Ingredient> ingredient, ItemStack stack) {
        return ingredient.map(i -> i.test(stack)).orElse(stack.isEmpty());
    }

    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        key.ifPresent(ingredients::add);
        shard.ifPresent(ingredients::add);
        ingredients.add(material);
        return ingredients;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RunicWorkshop.SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return RunicWorkshop.RECIPE_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<RunicWorkshopRecipe> {
        @Override
        public MapCodec<RunicWorkshopRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, RunicWorkshopRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
