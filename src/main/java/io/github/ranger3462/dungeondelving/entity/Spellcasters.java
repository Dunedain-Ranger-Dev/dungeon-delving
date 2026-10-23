package io.github.ranger3462.dungeondelving.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.entity.player.Player;

/**
 * Remembers who has been casting spells lately, so mobs like the hobgoblin spellbreaker can go after casters first.
 * Magic mod integrations report casts (e.g. Iron's Spells, from its cast event).
 */
public final class Spellcasters {
    /** How long a player counts as a caster after casting, in ticks (30 seconds). */
    public static final long MEMORY = 30 * 20;
    private static final Map<UUID, Long> LAST_CAST = new HashMap<>();

    private Spellcasters() {}

    /** A player just cast a spell. */
    public static void markCast(Player player) {
        LAST_CAST.put(player.getUUID(), player.level().getGameTime());
    }

    /** Whether a player has cast a spell recently. */
    public static boolean isCaster(Player player) {
        Long last = LAST_CAST.get(player.getUUID());
        return last != null && player.level().getGameTime() - last <= MEMORY;
    }

    /** Forgets a player's casting (e.g. in tests). */
    public static void forget(Player player) {
        LAST_CAST.remove(player.getUUID());
    }
}
