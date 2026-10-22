package io.github.ranger3462.dungeondelving.integration.irons;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
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
 * Undead that need Iron's Spells: the skeleton mage (in the Barrow King's court), the wither skeleton mage (a tier 4
 * undead), and the Lich Lord (the tier 4 undead boss). Only loaded when Iron's Spells is installed, so nothing outside
 * this package may refer to these classes directly.
 */
public final class IronsSkeletons {
    public static DeferredHolder<EntityType<?>, EntityType<SkeletonMage>> SKELETON_MAGE;
    public static DeferredItem<DeferredSpawnEggItem> SKELETON_MAGE_SPAWN_EGG;
    public static DeferredHolder<EntityType<?>, EntityType<WitherSkeletonMage>> WITHER_SKELETON_MAGE;
    public static DeferredItem<DeferredSpawnEggItem> WITHER_SKELETON_MAGE_SPAWN_EGG;
    public static DeferredHolder<EntityType<?>, EntityType<LichLord>> LICH_LORD;
    public static DeferredItem<DeferredSpawnEggItem> LICH_LORD_SPAWN_EGG;
    public static final String LICH_LORD_ID = "lich_lord";
    /** Skeleton mages in the Barrow King's court. */
    public static final int BARROW_KING_MAGES = 1;

    private IronsSkeletons() {}

    /** Called from the mod constructor when Iron's Spells is loaded. */
    public static void register(IEventBus modEventBus) {
        SKELETON_MAGE = ModEntities.ENTITIES.register("skeleton_mage",
                () -> EntityType.Builder.of(SkeletonMage::new, MobCategory.MONSTER).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(8)
                        .build(DungeonDelving.id("skeleton_mage").toString()));
        SKELETON_MAGE_SPAWN_EGG = ModRegistries.ITEMS.registerItem("skeleton_mage_spawn_egg",
                properties -> new DeferredSpawnEggItem(SKELETON_MAGE, 0xC1C1C1, 0x4A2E6A, properties));
        WITHER_SKELETON_MAGE = ModEntities.ENTITIES.register("wither_skeleton_mage",
                () -> EntityType.Builder.of(WitherSkeletonMage::new, MobCategory.MONSTER).fireImmune().sized(0.6f, 1.8f).eyeHeight(1.62f)
                        .clientTrackingRange(8).build(DungeonDelving.id("wither_skeleton_mage").toString()));
        WITHER_SKELETON_MAGE_SPAWN_EGG = ModRegistries.ITEMS.registerItem("wither_skeleton_mage_spawn_egg",
                properties -> new DeferredSpawnEggItem(WITHER_SKELETON_MAGE, 0x141414, 0x6A2E8A, properties));
        LICH_LORD = ModEntities.ENTITIES.register(LICH_LORD_ID,
                () -> EntityType.Builder.of(LichLord::new, MobCategory.MONSTER).fireImmune().sized(0.6f, 1.95f).eyeHeight(1.74f)
                        .clientTrackingRange(10).build(DungeonDelving.id(LICH_LORD_ID).toString()));
        LICH_LORD_SPAWN_EGG = ModRegistries.ITEMS.registerItem("lich_lord_spawn_egg",
                properties -> new DeferredSpawnEggItem(LICH_LORD, 0x8A7A55, 0x4A1E6A, properties));
        DungeonBosses.register(new DungeonBosses.BossType(LICH_LORD_ID, DungeonFactions.UNDEAD, tier -> tier == 4, LichLord::create));
        // A mage behind the King, out past his knights.
        DungeonBosses.addRetinue(DungeonBosses.BARROW_KING,
                (level, pos, tier) -> DungeonBosses.ring(level, pos, SKELETON_MAGE.get(), BARROW_KING_MAGES, 6.0, 0.5));
        modEventBus.addListener(IronsSkeletons::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SKELETON_MAGE.get(), SkeletonMage.createAttributes().build());
        event.put(WITHER_SKELETON_MAGE.get(), WitherSkeletonMage.createAttributes().build());
        event.put(LICH_LORD.get(), LichLord.createAttributes().build());
    }
}
