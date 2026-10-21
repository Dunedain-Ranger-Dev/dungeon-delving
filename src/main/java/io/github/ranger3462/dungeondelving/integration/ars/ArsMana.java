package io.github.ranger3462.dungeondelving.integration.ars;

import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;

import net.minecraft.server.level.ServerPlayer;

/** Ars Nouveau mana, for the Tester's Charm. Only used when Ars Nouveau is installed. */
public final class ArsMana {
    private ArsMana() {}

    /** Fills the player's Ars Nouveau mana. */
    public static void refill(ServerPlayer player) {
        var mana = CapabilityRegistry.getMana(player);
        if (mana != null) {
            mana.setMana(mana.getMaxMana());
        }
    }
}
