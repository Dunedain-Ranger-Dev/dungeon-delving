package io.github.ranger3462.dungeondelving.forging;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

/**
 * Crafting recipes that carry the forging ★ through: the result is as good as the worst ★ among its forged inputs, and
 * no better than the weakest flux used ({@link FluxItem#maxStars}). Results that are forged gear get their stats for
 * that ★ ({@link ForgedGear#applyStats}). Written like vanilla shaped and shapeless recipes, with types
 * {@code dungeondelving:starred_shaped} and {@code dungeondelving:starred_shapeless}; smithing upgrades written like
 * vanilla's as {@code dungeondelving:starred_smithing} keep the base's ★ (or the addition's, if lower).
 */
public final class StarredRecipes {
    private StarredRecipes() {}

    /** The result for these inputs: the recipe's result, with the inputs' ★ (and stats, for gear). */
    public static ItemStack starred(ItemStack result, CraftingInput input) {
        int stars = Integer.MAX_VALUE;
        int fluxCap = Integer.MAX_VALUE;
        for (ItemStack stack : input.items()) {
            if (stack.has(Forging.STARS.get())) {
                stars = Math.min(stars, Forging.stars(stack));
            }
            if (stack.getItem() instanceof FluxItem flux) {
                fluxCap = Math.min(fluxCap, flux.maxStars);
            }
        }
        if (stars == Integer.MAX_VALUE) {
            return result;
        }
        int earned = Math.max(1, Math.min(stars, fluxCap));
        if (Quality.isGear(result)) {
            // A forged iron piece is ★1 to ★3, whatever its parts.
            int parts = earned;
            earned = Quality.band(result).map(band -> band.clamp(parts)).orElse(parts);
        }
        result.set(Forging.STARS.get(), earned);
        ForgedGear.applyStats(result);
        return result;
    }

    public static class Shaped extends ShapedRecipe {
        public Shaped(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification) {
            super(group, category, pattern, result, showNotification);
        }

        static Shaped of(ShapedRecipe recipe) {
            return new Shaped(recipe.getGroup(), recipe.category(), recipe.pattern, recipe.getResultItem(null), recipe.showNotification());
        }

        @Override
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
            return starred(super.assemble(input, registries), input);
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return Forging.STARRED_SHAPED.get();
        }
    }

    public static class Shapeless extends ShapelessRecipe {
        public Shapeless(String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients) {
            super(group, category, result, ingredients);
        }

        static Shapeless of(ShapelessRecipe recipe) {
            return new Shapeless(recipe.getGroup(), recipe.category(), recipe.getResultItem(null), recipe.getIngredients());
        }

        @Override
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
            return starred(super.assemble(input, registries), input);
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return Forging.STARRED_SHAPELESS.get();
        }
    }

    /**
     * A smithing upgrade that carries the ★ through. Within a band it keeps the base's ★ (or the addition's, if that's
     * starred and lower). Up into a better material's band (a forged iron piece smithed with a diamond, a forged
     * diamond one with netherite) it starts that band: at its first ★, or its second from the top of the old band. A
     * plain piece comes out plain. Its durability is worked out again for the upgraded item.
     * <p>
     * {@code dungeondelving:forged_smithing} recipes only take a forged (starred) base: they're the forged way up, and
     * would otherwise be a cheap way to plain gear.
     */
    public static class Smithing extends SmithingTransformRecipe {
        final boolean forgedOnly;

        public Smithing(Ingredient template, Ingredient base, Ingredient addition, ItemStack result) {
            this(template, base, addition, result, false);
        }

        public Smithing(Ingredient template, Ingredient base, Ingredient addition, ItemStack result, boolean forgedOnly) {
            super(template, base, addition, result);
            this.forgedOnly = forgedOnly;
        }

        static Smithing of(SmithingTransformRecipe recipe) {
            return new Smithing(recipe.template, recipe.base, recipe.addition, recipe.result, false);
        }

        static Smithing forgedOf(SmithingTransformRecipe recipe) {
            return new Smithing(recipe.template, recipe.base, recipe.addition, recipe.result, true);
        }

        @Override
        public boolean matches(SmithingRecipeInput input, net.minecraft.world.level.Level level) {
            return super.matches(input, level) && (!forgedOnly || input.base().has(Forging.STARS.get()));
        }

        @Override
        public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
            ItemStack result = super.assemble(input, registries);
            if (!result.has(Forging.STARS.get())) {
                return result;
            }
            int stars = Forging.stars(result);
            var from = Quality.band(input.base());
            var to = Quality.band(result);
            boolean gear = Quality.isGear(result);
            if (gear && from.isPresent() && to.isPresent() && to.get().low > from.get().high) {
                stars = to.get().low + (Forging.stars(input.base()) >= from.get().high ? 1 : 0);
            }
            if (input.addition().has(Forging.STARS.get())) {
                stars = Math.min(stars, Forging.stars(input.addition()));
            }
            if (gear && to.isPresent()) {
                stars = to.get().clamp(stars);
            }
            result.set(Forging.STARS.get(), stars);
            result.set(DataComponents.MAX_DAMAGE, result.getPrototype().getOrDefault(DataComponents.MAX_DAMAGE, 0));
            ForgedGear.applyStats(result);
            return result;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return forgedOnly ? Forging.FORGED_SMITHING.get() : Forging.STARRED_SMITHING.get();
        }
    }

    /** Reads and writes starred shaped recipes exactly like vanilla shaped ones. */
    public static final class ShapedSerializer implements RecipeSerializer<Shaped> {
        private static final MapCodec<Shaped> CODEC = ((MapCodec<ShapedRecipe>) RecipeSerializer.SHAPED_RECIPE.codec()).xmap(Shaped::of, recipe -> recipe);
        private static final StreamCodec<RegistryFriendlyByteBuf, Shaped> STREAM_CODEC = RecipeSerializer.SHAPED_RECIPE.streamCodec()
                .map(Shaped::of, recipe -> recipe);

        @Override
        public MapCodec<Shaped> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Shaped> streamCodec() {
            return STREAM_CODEC;
        }
    }

    /** Reads and writes starred shapeless recipes exactly like vanilla shapeless ones. */
    public static final class ShapelessSerializer implements RecipeSerializer<Shapeless> {
        private static final MapCodec<Shapeless> CODEC = ((MapCodec<ShapelessRecipe>) RecipeSerializer.SHAPELESS_RECIPE.codec())
                .xmap(Shapeless::of, recipe -> recipe);
        private static final StreamCodec<RegistryFriendlyByteBuf, Shapeless> STREAM_CODEC = RecipeSerializer.SHAPELESS_RECIPE.streamCodec()
                .map(Shapeless::of, recipe -> recipe);

        @Override
        public MapCodec<Shapeless> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Shapeless> streamCodec() {
            return STREAM_CODEC;
        }
    }

    /** Reads and writes forged-only smithing upgrades exactly like vanilla ones. */
    public static final class ForgedSmithingSerializer implements RecipeSerializer<Smithing> {
        private static final MapCodec<Smithing> CODEC = ((MapCodec<SmithingTransformRecipe>) RecipeSerializer.SMITHING_TRANSFORM.codec())
                .xmap(Smithing::forgedOf, recipe -> recipe);
        private static final StreamCodec<RegistryFriendlyByteBuf, Smithing> STREAM_CODEC = RecipeSerializer.SMITHING_TRANSFORM.streamCodec()
                .map(Smithing::forgedOf, recipe -> recipe);

        @Override
        public MapCodec<Smithing> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Smithing> streamCodec() {
            return STREAM_CODEC;
        }
    }

    /** Reads and writes starred smithing upgrades exactly like vanilla ones. */
    public static final class SmithingSerializer implements RecipeSerializer<Smithing> {
        private static final MapCodec<Smithing> CODEC = ((MapCodec<SmithingTransformRecipe>) RecipeSerializer.SMITHING_TRANSFORM.codec())
                .xmap(Smithing::of, recipe -> recipe);
        private static final StreamCodec<RegistryFriendlyByteBuf, Smithing> STREAM_CODEC = RecipeSerializer.SMITHING_TRANSFORM.streamCodec()
                .map(Smithing::of, recipe -> recipe);

        @Override
        public MapCodec<Smithing> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Smithing> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
