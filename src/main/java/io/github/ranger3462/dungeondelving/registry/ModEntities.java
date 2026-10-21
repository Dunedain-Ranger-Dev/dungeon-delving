package io.github.ranger3462.dungeondelving.registry;

import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.entity.Goblin;
import io.github.ranger3462.dungeondelving.entity.GoblinArcher;
import io.github.ranger3462.dungeondelving.entity.GoblinBowItem;
import io.github.ranger3462.dungeondelving.entity.SkeletonKnight;
import io.github.ranger3462.dungeondelving.entity.SkeletonLord;
import io.github.ranger3462.dungeondelving.entity.SkeletonSwordsman;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's mobs, their spawn eggs, and the gear they carry. */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, DungeonDelving.MODID);

    /** The spear goblin. About three quarters of a player's height. */
    public static final DeferredHolder<EntityType<?>, EntityType<Goblin>> GOBLIN = ENTITIES.register("goblin",
            () -> EntityType.Builder.of(Goblin::new, MobCategory.MONSTER).sized(0.5f, 1.45f).eyeHeight(1.25f).clientTrackingRange(8)
                    .build(DungeonDelving.id("goblin").toString()));

    /** The goblin archer: keeps its distance and shoots a Goblin Bow. */
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinArcher>> GOBLIN_ARCHER = ENTITIES.register("goblin_archer",
            () -> EntityType.Builder.of(GoblinArcher::new, MobCategory.MONSTER).sized(0.5f, 1.45f).eyeHeight(1.25f).clientTrackingRange(8)
                    .build(DungeonDelving.id("goblin_archer").toString()));

    public static final DeferredItem<DeferredSpawnEggItem> GOBLIN_ARCHER_SPAWN_EGG = ModRegistries.ITEMS.registerItem("goblin_archer_spawn_egg",
            properties -> new DeferredSpawnEggItem(GOBLIN_ARCHER, 0x5E8C3A, 0x3F5A2A, properties));

    /** The bow goblin archers carry: low durability, and its arrows hit softer. */
    public static final DeferredItem<GoblinBowItem> GOBLIN_BOW = ModRegistries.ITEMS.registerItem("goblin_bow", GoblinBowItem::new,
            new Item.Properties().durability(96));

    /** The skeleton swordsman: sword and shield, blocks when you get close. */
    public static final DeferredHolder<EntityType<?>, EntityType<SkeletonSwordsman>> SKELETON_SWORDSMAN = ENTITIES.register("skeleton_swordsman",
            () -> EntityType.Builder.of(SkeletonSwordsman::new, MobCategory.MONSTER).sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(8)
                    .build(DungeonDelving.id("skeleton_swordsman").toString()));
    /** The skeleton knight: an armored swordsman in the Skeleton Lord's honor guard. */
    public static final DeferredHolder<EntityType<?>, EntityType<SkeletonKnight>> SKELETON_KNIGHT = ENTITIES.register("skeleton_knight",
            () -> EntityType.Builder.of(SkeletonKnight::new, MobCategory.MONSTER).sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(8)
                    .build(DungeonDelving.id("skeleton_knight").toString()));
    /** The Skeleton Lord, a tier 1 boss. */
    public static final DeferredHolder<EntityType<?>, EntityType<SkeletonLord>> SKELETON_LORD = ENTITIES.register("skeleton_lord",
            () -> EntityType.Builder.of(SkeletonLord::new, MobCategory.MONSTER).sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(10)
                    .build(DungeonDelving.id("skeleton_lord").toString()));

    public static final DeferredItem<DeferredSpawnEggItem> SKELETON_SWORDSMAN_SPAWN_EGG = ModRegistries.ITEMS.registerItem("skeleton_swordsman_spawn_egg",
            properties -> new DeferredSpawnEggItem(SKELETON_SWORDSMAN, 0xC1C1C1, 0x8A8A8A, properties));
    public static final DeferredItem<DeferredSpawnEggItem> SKELETON_KNIGHT_SPAWN_EGG = ModRegistries.ITEMS.registerItem("skeleton_knight_spawn_egg",
            properties -> new DeferredSpawnEggItem(SKELETON_KNIGHT, 0xC1C1C1, 0x5A6A7A, properties));
    public static final DeferredItem<DeferredSpawnEggItem> SKELETON_LORD_SPAWN_EGG = ModRegistries.ITEMS.registerItem("skeleton_lord_spawn_egg",
            properties -> new DeferredSpawnEggItem(SKELETON_LORD, 0xC1C1C1, 0xC8A040, properties));

    public static final DeferredItem<DeferredSpawnEggItem> GOBLIN_SPAWN_EGG = ModRegistries.ITEMS.registerItem("goblin_spawn_egg",
            properties -> new DeferredSpawnEggItem(GOBLIN, 0x5E8C3A, 0x6B4A2B, properties));

    /** The spear goblins carry: a crude, quick weapon about as strong as a stone sword. */
    public static final DeferredItem<SwordItem> GOBLIN_SPEAR = ModRegistries.ITEMS.registerItem("goblin_spear",
            properties -> new SwordItem(Tiers.WOOD, properties.attributes(SwordItem.createAttributes(Tiers.WOOD, 4, -2.6f))));

    private ModEntities() {}

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(GOBLIN.get(), Goblin.createAttributes().build());
        event.put(GOBLIN_ARCHER.get(), GoblinArcher.createAttributes().build());
        event.put(SKELETON_SWORDSMAN.get(), SkeletonSwordsman.createAttributes().build());
        event.put(SKELETON_KNIGHT.get(), SkeletonKnight.createAttributes().build());
        event.put(SKELETON_LORD.get(), SkeletonLord.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        for (EntityType<? extends Monster> goblin : List.of(GOBLIN.get(), GOBLIN_ARCHER.get(), SKELETON_SWORDSMAN.get(), SKELETON_KNIGHT.get(),
                SKELETON_LORD.get())) {
            event.register(goblin, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }

    /** Makes sure the items above are registered; call once while setting up registries. */
    static void init() {}
}
