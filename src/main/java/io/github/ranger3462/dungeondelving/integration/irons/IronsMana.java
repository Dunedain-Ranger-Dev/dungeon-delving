package io.github.ranger3462.dungeondelving.integration.irons;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.server.level.ServerPlayer;

/** Iron's Spells mana, for the Tester's Charm. Only used when Iron's Spells is installed. */
public final class IronsMana {
    private IronsMana() {}

    /** Fills the player's Iron's Spells mana. */
    public static void refill(ServerPlayer player) {
        MagicData.getPlayerMagicData(player).setMana((float) player.getAttributeValue(AttributeRegistry.MAX_MANA));
    }
}
