package io.github.ranger3462.dungeondelving.workshop;

import java.util.Optional;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Runic Workshop's screen: a key slot, a shard slot and a material slot, laid out like the smithing table, and the
 * result. Taking the result uses up the key, the shard, and as much material as the recipe asks for.
 */
public class RunicWorkshopMenu extends ItemCombinerMenu {
    public static final int KEY_SLOT = 0;
    public static final int SHARD_SLOT = 1;
    public static final int MATERIAL_SLOT = 2;
    public static final int RESULT_SLOT = 3;

    private final Level level;
    private RecipeHolder<RunicWorkshopRecipe> selected;

    /** The client side, opened from the server. */
    public RunicWorkshopMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public RunicWorkshopMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(RunicWorkshop.MENU.get(), containerId, inventory, access);
        this.level = inventory.player.level();
    }

    @Override
    protected ItemCombinerMenuSlotDefinition createInputSlotDefinitions() {
        return ItemCombinerMenuSlotDefinition.create()
                .withSlot(KEY_SLOT, 8, 48, stack -> stack.getItem() instanceof RunicKeyItem)
                .withSlot(SHARD_SLOT, 26, 48, stack -> stack.is(RunicWorkshop.RUNIC_SHARDS))
                .withSlot(MATERIAL_SLOT, 44, 48, stack -> !(stack.getItem() instanceof RunicKeyItem) && !stack.is(RunicWorkshop.RUNIC_SHARDS))
                .withResultSlot(RESULT_SLOT, 98, 48)
                .build();
    }

    private RunicWorkshopRecipe.Input input() {
        return new RunicWorkshopRecipe.Input(inputSlots.getItem(KEY_SLOT), inputSlots.getItem(SHARD_SLOT), inputSlots.getItem(MATERIAL_SLOT));
    }

    /** The recipe the current inputs make, if any. */
    public Optional<RecipeHolder<RunicWorkshopRecipe>> recipe() {
        return level.getRecipeManager().getRecipeFor(RunicWorkshop.RECIPE_TYPE.get(), input(), level);
    }

    @Override
    public void createResult() {
        Optional<RecipeHolder<RunicWorkshopRecipe>> recipe = recipe();
        selected = recipe.orElse(null);
        resultSlots.setRecipeUsed(selected);
        resultSlots.setItem(0, recipe.map(holder -> holder.value().assemble(input(), level.registryAccess())).orElse(ItemStack.EMPTY));
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasStack) {
        return selected != null && selected.value().matches(input(), level);
    }

    @Override
    protected void onTake(Player player, ItemStack stack) {
        stack.onCraftedBy(player.level(), player, stack.getCount());
        RunicWorkshopRecipe recipe = selected.value();
        if (recipe.key().isPresent()) {
            shrink(KEY_SLOT, 1);
        }
        if (recipe.shard().isPresent()) {
            shrink(SHARD_SLOT, 1);
        }
        shrink(MATERIAL_SLOT, recipe.count());
        access.execute((world, pos) -> world.levelEvent(1044, pos, 0)); // The smithing table's sound.
    }

    private void shrink(int slot, int amount) {
        ItemStack stack = inputSlots.getItem(slot);
        stack.shrink(amount);
        inputSlots.setItem(slot, stack);
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(RunicWorkshop.BLOCK.get());
    }
}
