package io.github.ranger3462.dungeondelving.forging;

import io.github.ranger3462.dungeondelving.skill.CraftSkill;
import io.github.ranger3462.dungeondelving.skill.CraftingSkills;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A crucible's screen: the furnace's layout, with gold ingots on top, the salt as fuel and fine gold out (DD-91). */
public class CrucibleMenu extends AbstractContainerMenu {
    private final Container crucible;
    private final ContainerData data;

    /** On the client, with blank contents the server fills in. */
    public CrucibleMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(3), new SimpleContainerData(4));
    }

    public CrucibleMenu(int id, Inventory inventory, Container crucible, ContainerData data) {
        super(Crafts.CRUCIBLE_MENU.get(), id);
        this.crucible = crucible;
        this.data = data;
        addSlot(new Slot(crucible, CrucibleBlockEntity.INPUT, 56, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CrucibleBlockEntity.fits(CrucibleBlockEntity.INPUT, stack);
            }
        });
        addSlot(new Slot(crucible, CrucibleBlockEntity.SALT, 56, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CrucibleBlockEntity.fits(CrucibleBlockEntity.SALT, stack);
            }
        });
        addSlot(new Slot(crucible, CrucibleBlockEntity.OUTPUT, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                // The XP comes when the refined gold is taken: 2 a piece, a ★.
                CraftingSkills.addXp(player, CraftSkill.GOLDSMITHING, 2 * Forging.stars(stack) * stack.getCount());
                super.onTake(player, stack);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
    }

    /** Progress through the current ingot, from 0 to 1. */
    public float progress() {
        return data.get(1) <= 0 ? 0.0f : data.get(0) / (float) data.get(1);
    }

    /** What's left of the current salt, from 0 to 1. */
    public float saltLeft() {
        return data.get(2) / (float) CrucibleBlockEntity.INGOTS_PER_SALT;
    }

    /** The current salt's ★ (0 with none). */
    public int saltStars() {
        return data.get(2) > 0 ? data.get(3) : 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < 3) {
            if (!moveItemStackTo(stack, 3, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            if (index == CrucibleBlockEntity.OUTPUT) {
                slot.onTake(player, original);
            }
        } else if (CrucibleBlockEntity.fits(CrucibleBlockEntity.INPUT, stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (CrucibleBlockEntity.fits(CrucibleBlockEntity.SALT, stack)) {
            if (!moveItemStackTo(stack, 1, 2, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return crucible.stillValid(player);
    }
}
