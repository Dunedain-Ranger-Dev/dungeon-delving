package io.github.ranger3462.dungeondelving.dungeon.bag;

import io.github.ranger3462.dungeondelving.dungeon.DungeonLoot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The screen for a container inside a dungeon. It looks like a normal chest, but taking an item sends it to the
 * player's dungeon bag instead of their inventory, and nothing can be put into the container. If the bag is full,
 * the item simply stays where it is.
 * <p>
 * With {@code readOnly}, nothing in the container can be taken either; used to show the bag itself. Items can still be
 * thrown out of the bag (Q for one, Ctrl+Q for the stack) to make room. They stay marked as dungeon loot, so picking
 * them back up puts them back in the bag.
 */
public class DungeonChestMenu extends ChestMenu {
    private final DungeonBag bag;
    private final int containerSlots;
    private final boolean readOnly;

    private DungeonChestMenu(MenuType<?> type, int containerId, Inventory playerInventory, Container container, int rows, DungeonBag bag,
            boolean readOnly) {
        super(type, containerId, playerInventory, container, rows);
        this.bag = bag;
        this.containerSlots = rows * 9;
        this.readOnly = readOnly;
    }

    /** A dungeon container: taking an item moves it into the bag. */
    public static DungeonChestMenu looting(int containerId, Inventory playerInventory, Container container, DungeonBag bag) {
        int rows = container.getContainerSize() / 9;
        return new DungeonChestMenu(menuType(rows), containerId, playerInventory, container, rows, bag, false);
    }

    /** A read-only view of the bag. */
    public static DungeonChestMenu viewing(int containerId, Inventory playerInventory, DungeonBag bag) {
        int rows = bag.size() / 9;
        return new DungeonChestMenu(menuType(rows), containerId, playerInventory, bag.container(), rows, bag, true);
    }

    /** Whether a container can be shown in this menu: whole rows of 9, up to a double chest. */
    public static boolean fits(Container container) {
        int size = container.getContainerSize();
        return size > 0 && size % 9 == 0 && size <= 54;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Collecting matching items and drag-spreading could move items into or out of the container in bulk.
        if (clickType == ClickType.PICKUP_ALL || clickType == ClickType.QUICK_CRAFT) {
            return;
        }
        if (slotId >= 0 && slotId < containerSlots) {
            if (readOnly && clickType == ClickType.THROW) {
                throwOut(slots.get(slotId), player, button == 1);
                return;
            }
            if (!readOnly && getCarried().isEmpty() && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
                takeIntoBag(slots.get(slotId));
            }
            return;
        }
        if (clickType == ClickType.QUICK_MOVE) {
            return; // Shift-clicking from the inventory would put things into the container.
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** Moves as much of a slot's stack into the bag as fits; the rest stays in the slot. */
    public void takeIntoBag(Slot slot) {
        if (!slot.hasItem()) {
            return;
        }
        ItemStack left = bag.insert(slot.getItem());
        slot.set(left);
        slot.setChanged();
        broadcastChanges();
    }

    /** Drops one item (or the whole stack) from a bag slot at the player's feet, still marked as dungeon loot. */
    public void throwOut(Slot slot, Player player, boolean wholeStack) {
        if (!(player instanceof ServerPlayer) || !slot.hasItem()) {
            return;
        }
        ItemStack thrown = slot.remove(wholeStack ? slot.getItem().getCount() : 1);
        DungeonLoot.mark(thrown);
        player.drop(thrown, true);
        slot.setChanged();
        broadcastChanges();
    }

    private static MenuType<?> menuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }
}
