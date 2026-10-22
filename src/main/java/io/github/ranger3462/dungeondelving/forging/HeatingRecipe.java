package io.github.ranger3462.dungeondelving.forging;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCookingSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;

/**
 * Heats metal in a furnace or blast furnace. The result keeps the ★ being worked towards: a Tempered Ingot ★n heats
 * into a Heated Ingot aiming for ★n+1, plain iron aims for ★1, and cold hot metal (a heated or struck ingot that cooled
 * off) just heats back up as it was. The result starts cooling once it leaves the furnace.
 */
public final class HeatingRecipe {
    private HeatingRecipe() {}

    /** The result of heating {@code input}, given the recipe's own result item. */
    static ItemStack heat(ItemStack input, ItemStack result) {
        ItemStack heated = result.copy();
        int stars = input.has(Forging.STARS.get()) ? Forging.stars(input) : 0;
        if (input.is(Forging.TEMPERED_IRON_INGOT.get()) || stars == 0) {
            stars = Math.min(stars + 1, Forging.MAX_STARS);
        }
        heated.set(Forging.STARS.get(), stars);
        heated.remove(Forging.HEATED_AT.get());
        return heated;
    }

    static <T extends AbstractCookingRecipe> RecipeSerializer<T> serializer(AbstractCookingRecipe.Factory<T> factory, int cookTime) {
        return new SimpleCookingSerializer<>(factory, cookTime);
    }

    public static class Smelting extends SmeltingRecipe {
        public Smelting(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime) {
            super(group, category, ingredient, result, experience, cookingTime);
        }

        @Override
        public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
            return heat(input.item(), result);
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return Forging.HEATING_SMELTING.get();
        }
    }

    public static class Blasting extends BlastingRecipe {
        public Blasting(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime) {
            super(group, category, ingredient, result, experience, cookingTime);
        }

        @Override
        public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
            return heat(input.item(), result);
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return Forging.HEATING_BLASTING.get();
        }
    }
}
