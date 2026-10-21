package io.github.ranger3462.dungeondelving.integration.irons;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Skeletons that need Iron's Spells: the skeleton mage, which joins the Barrow King's court. Only loaded when Iron's
 * Spells is installed, so nothing outside this package may refer to these classes directly.
 */
public final class IronsSkeletons {
    public static DeferredHolder<EntityType<?>, EntityType<SkeletonMage>> SKELETON_MAGE;
    public static DeferredItem<DeferredSpawnEggItem> SKELETON_MAGE_SPAWN_EGG;
    /** Skeleton mages in the Barrow King's court. */
    public static final int BARROW_KING_MAGES = 2;

    private IronsSkeletons() {}

    /** Called from the mod constructor when Iron's Spells is loaded. */
    public static void register(IEventBus modEventBus) {
        SKELETON_MAGE = ModEntities.ENTITIES.register("skeleton_mage",
                () -> EntityType.Builder.of(SkeletonMage::new, MobCategory.MONSTER).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(8)
                        .build(DungeonDelving.id("skeleton_mage").toString()));
        SKELETON_MAGE_SPAWN_EGG = ModRegistries.ITEMS.registerItem("skeleton_mage_spawn_egg",
                properties -> new DeferredSpawnEggItem(SKELETON_MAGE, 0xC1C1C1, 0x4A2E6A, properties));
        // Two mages behind the King, out past his knights.
        DungeonBosses.addRetinue(DungeonBosses.BARROW_KING,
                (level, pos, tier) -> DungeonBosses.ring(level, pos, SKELETON_MAGE.get(), BARROW_KING_MAGES, 6.0, 0.5));
        modEventBus.addListener(IronsSkeletons::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SKELETON_MAGE.get(), SkeletonMage.createAttributes().build());
    }
}
