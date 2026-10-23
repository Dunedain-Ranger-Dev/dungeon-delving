package io.github.ranger3462.dungeondelving.integration.bettercombat;

import java.util.Optional;

import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.world.item.ItemStack;

/** Better Combat's weapon types. Only called when Better Combat is installed. */
public final class BetterCombatWeapons {
    private BetterCombatWeapons() {}

    /** A weapon's Better Combat category (e.g. "sword", "claymore"), if it has Better Combat attributes. */
    public static Optional<String> category(ItemStack stack) {
        var attributes = WeaponRegistry.getAttributes(stack);
        if (attributes == null || attributes.category() == null || attributes.category().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(attributes.category());
    }
}
