package io.github.ranger3462.dungeondelving.gametest;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/** Curios helpers for game tests. Only call these when Curios is loaded. */
final class CuriosTestSupport {
    private CuriosTestSupport() {}

    /** Puts an item in the player's Curios back slot. Returns false if they don't have one. */
    static boolean wearOnBack(ServerPlayer player, ItemStack stack) {
        return CuriosApi.getCuriosInventory(player).map(curios -> {
            if (curios.getStacksHandler("back").isEmpty()) {
                return false;
            }
            curios.setEquippedCurio("back", 0, stack);
            return true;
        }).orElse(false);
    }

    /** What's in the player's Curios back slot. */
    static ItemStack back(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(curios -> curios.getStacksHandler("back"))
                .map(slots -> slots.getStacks().getStackInSlot(0))
                .orElse(ItemStack.EMPTY);
    }
}
