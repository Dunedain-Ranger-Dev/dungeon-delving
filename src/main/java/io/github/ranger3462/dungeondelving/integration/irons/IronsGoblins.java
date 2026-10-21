package io.github.ranger3462.dungeondelving.integration.irons;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Goblins that need Iron's Spells: the goblin shaman. Only loaded when Iron's Spells is installed, so nothing outside
 * this package may refer to these classes directly.
 */
public final class IronsGoblins {
    public static DeferredHolder<EntityType<?>, EntityType<GoblinShaman>> GOBLIN_SHAMAN;
    public static DeferredItem<DeferredSpawnEggItem> GOBLIN_SHAMAN_SPAWN_EGG;

    private IronsGoblins() {}

    /** Called from the mod constructor when Iron's Spells is loaded. */
    public static void register(IEventBus modEventBus) {
        GOBLIN_SHAMAN = ModEntities.ENTITIES.register("goblin_shaman",
                () -> EntityType.Builder.of(GoblinShaman::new, MobCategory.MONSTER).sized(0.5f, 1.45f).eyeHeight(1.25f).clientTrackingRange(8)
                        .build(DungeonDelving.id("goblin_shaman").toString()));
        GOBLIN_SHAMAN_SPAWN_EGG = ModRegistries.ITEMS.registerItem("goblin_shaman_spawn_egg",
                properties -> new DeferredSpawnEggItem(GOBLIN_SHAMAN, 0x5E8C3A, 0x6A3D8F, properties));
        modEventBus.addListener(IronsGoblins::onAttributes);
        modEventBus.addListener(IronsGoblins::onSpawnPlacements);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(GOBLIN_SHAMAN.get(), GoblinShaman.createAttributes().build());
    }

    private static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(GOBLIN_SHAMAN.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
