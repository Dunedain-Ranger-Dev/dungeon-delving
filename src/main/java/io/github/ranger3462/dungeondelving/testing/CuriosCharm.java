package io.github.ranger3462.dungeondelving.testing;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * The Tester's Charm in a Curios ring slot. Curios is optional, so nothing outside this class may refer to Curios
 * classes; check that Curios is loaded before calling these.
 */
final class CuriosCharm {
    /** The Curios slot the charm is worn in. */
    static final String SLOT = "ring";

    private CuriosCharm() {}

    /** Whether the player is wearing the charm in any Curios slot. */
    static boolean wearing(Player player) {
        return CuriosApi.getCuriosInventory(player).flatMap(curios -> curios.findFirstCurio(stack -> stack.getItem() instanceof TestersCharm.CharmItem))
                .isPresent();
    }

    /** Puts the charm in the player's first empty ring slot. False if they have none free. */
    static boolean wear(Player player, ItemStack charm) {
        return CuriosApi.getCuriosInventory(player).flatMap(curios -> curios.getStacksHandler(SLOT)).map(slots -> {
            for (int i = 0; i < slots.getSlots(); i++) {
                if (slots.getStacks().getStackInSlot(i).isEmpty()) {
                    slots.getStacks().setStackInSlot(i, charm);
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    /** Empties every Curios slot the player has. */
    static void clear(Player player) {
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> curios.getCurios().values().forEach(slots -> {
            for (int i = 0; i < slots.getSlots(); i++) {
                slots.getStacks().setStackInSlot(i, ItemStack.EMPTY);
                slots.getCosmeticStacks().setStackInSlot(i, ItemStack.EMPTY);
            }
        }));
    }
}
