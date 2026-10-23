package io.github.ranger3462.dungeondelving.integration.irons;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Orcs that need Iron's Spells: the doomsayer and shaman, and the Orc Lorekeeper (the tier 3 orc boss). Only loaded when
 * Iron's Spells is installed, so nothing outside this package may refer to these classes directly.
 */
public final class IronsOrcs {
    public static DeferredHolder<EntityType<?>, EntityType<OrcCaster.Doomsayer>> ORC_DOOMSAYER;
    public static DeferredHolder<EntityType<?>, EntityType<OrcCaster.Shaman>> ORC_SHAMAN;
    public static DeferredHolder<EntityType<?>, EntityType<OrcCaster.Lorekeeper>> ORC_LOREKEEPER;
    public static DeferredItem<DeferredSpawnEggItem> ORC_DOOMSAYER_SPAWN_EGG;
    public static DeferredItem<DeferredSpawnEggItem> ORC_SHAMAN_SPAWN_EGG;
    public static DeferredItem<DeferredSpawnEggItem> ORC_LOREKEEPER_SPAWN_EGG;
    public static final String ORC_LOREKEEPER_BOSS = "orc_lorekeeper";

    private IronsOrcs() {}

    /** Called from the mod constructor when Iron's Spells is loaded. */
    public static void register(IEventBus modEventBus) {
        ORC_DOOMSAYER = caster("orc_doomsayer", OrcCaster.Doomsayer::new);
        ORC_SHAMAN = caster("orc_shaman", OrcCaster.Shaman::new);
        ORC_LOREKEEPER = caster(ORC_LOREKEEPER_BOSS, OrcCaster.Lorekeeper::new);
        ORC_DOOMSAYER_SPAWN_EGG = egg("orc_doomsayer", ORC_DOOMSAYER, 0x5A1E1E);
        ORC_SHAMAN_SPAWN_EGG = egg("orc_shaman", ORC_SHAMAN, 0x3A6AA0);
        ORC_LOREKEEPER_SPAWN_EGG = egg(ORC_LOREKEEPER_BOSS, ORC_LOREKEEPER, 0xC8A040);
        DungeonBosses.register(new DungeonBosses.BossType(ORC_LOREKEEPER_BOSS, DungeonFactions.ORCS, tier -> tier == 3, OrcCaster.Lorekeeper::create));
        modEventBus.addListener(IronsOrcs::onAttributes);
        modEventBus.addListener(IronsOrcs::onSpawnPlacements);
    }

    private static <T extends OrcCaster> DeferredHolder<EntityType<?>, EntityType<T>> caster(String name, EntityType.EntityFactory<T> factory) {
        return ModEntities.ENTITIES.register(name, () -> EntityType.Builder.of(factory, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.74f)
                .clientTrackingRange(8).build(DungeonDelving.id(name).toString()));
    }

    private static DeferredItem<DeferredSpawnEggItem> egg(String name, DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> type, int spots) {
        return ModRegistries.ITEMS.registerItem(name + "_spawn_egg", properties -> new DeferredSpawnEggItem(type, 0x6E7F5A, spots, properties));
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ORC_DOOMSAYER.get(), OrcCaster.createAttributes().build());
        event.put(ORC_SHAMAN.get(), OrcCaster.createAttributes().build());
        event.put(ORC_LOREKEEPER.get(), OrcCaster.Lorekeeper.createAttributes().build());
    }

    private static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        for (EntityType<? extends Mob> type : java.util.List.of(ORC_DOOMSAYER.get(), ORC_SHAMAN.get(), ORC_LOREKEEPER.get())) {
            event.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }
}
