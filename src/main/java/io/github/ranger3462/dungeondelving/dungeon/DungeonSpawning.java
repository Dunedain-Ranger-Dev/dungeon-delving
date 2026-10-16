package io.github.ranger3462.dungeondelving.dungeon;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * Keeps dungeon mobs under the dungeon's control: mobs don't spawn naturally in the dungeon dimension, so they only
 * come from mob markers (and later, configured spawners). Controlled by {@code blockNaturalSpawns} in the config.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class DungeonSpawning {
    private DungeonSpawning() {}

    /** Whether a spawn of this kind in this dimension should be stopped. */
    public static boolean blocks(ResourceKey<Level> dimension, MobSpawnType type, boolean enabled) {
        if (!enabled || dimension != DungeonDimension.DUNGEON) {
            return false;
        }
        return type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION || type == MobSpawnType.PATROL;
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (blocks(event.getLevel().getLevel().dimension(), event.getSpawnType(), Config.BLOCK_NATURAL_SPAWNS.get())) {
            event.setSpawnCancelled(true);
        }
    }
}
