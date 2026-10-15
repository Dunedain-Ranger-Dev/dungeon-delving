package io.github.ranger3462.dungeondelving.datagen;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Registers this mod's data generators. Run them with {@code ./gradlew runData}. */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class DataGenerators {
    private DataGenerators() {}

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer(), new PlaceholderRoomProvider(event.getGenerator().getPackOutput()));
    }
}
