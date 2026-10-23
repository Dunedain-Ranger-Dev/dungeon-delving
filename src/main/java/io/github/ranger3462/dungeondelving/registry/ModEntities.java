package io.github.ranger3462.dungeondelving.registry;

import io.github.ranger3462.dungeondelving.entity.GoblinBosses;
import io.github.ranger3462.dungeondelving.entity.Hobgoblin;
import io.github.ranger3462.dungeondelving.entity.HobgoblinHunter;
import io.github.ranger3462.dungeondelving.entity.HobgoblinSoldier;
import io.github.ranger3462.dungeondelving.entity.HobgoblinSpellbreaker;
import io.github.ranger3462.dungeondelving.entity.HobgoblinVanguard;
import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.entity.Goblin;
import io.github.ranger3462.dungeondelving.entity.GoblinArcher;
import io.github.ranger3462.dungeondelving.entity.BoggedSwordsman;
import io.github.ranger3462.dungeondelving.entity.GoblinBowItem;
import io.github.ranger3462.dungeondelving.entity.Orc;
import io.github.ranger3462.dungeondelving.entity.OrcBosses;
import io.github.ranger3462.dungeondelving.entity.OrcRaider;
import io.github.ranger3462.dungeondelving.entity.OrcVagabond;
import io.github.ranger3462.dungeondelving.entity.SkeletonKnight;
import io.github.ranger3462.dungeondelving.entity.SkeletonLord;
import io.github.ranger3462.dungeondelving.entity.SkeletonSwordsman;
import io.github.ranger3462.dungeondelving.entity.StraySwordsman;
import io.github.ranger3462.dungeondelving.entity.ThrownAxe;
import io.github.ranger3462.dungeondelving.entity.WitherSkeletonArcher;
import io.github.ranger3462.dungeondelving.entity.ZombieLord;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
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

    /** Hobgoblins (tiers 3-4 goblin dungeons): full-sized goblins. */
    public static final DeferredHolder<EntityType<?>, EntityType<HobgoblinSoldier>> HOBGOBLIN_SOLDIER = hobgoblin("hobgoblin_soldier", HobgoblinSoldier::new);
    public static final DeferredHolder<EntityType<?>, EntityType<HobgoblinHunter>> HOBGOBLIN_HUNTER = hobgoblin("hobgoblin_hunter", HobgoblinHunter::new);
    public static final DeferredHolder<EntityType<?>, EntityType<HobgoblinVanguard>> HOBGOBLIN_VANGUARD = hobgoblin("hobgoblin_vanguard",
            HobgoblinVanguard::new);
    public static final DeferredHolder<EntityType<?>, EntityType<HobgoblinSpellbreaker>> HOBGOBLIN_SPELLBREAKER = hobgoblin("hobgoblin_spellbreaker",
            HobgoblinSpellbreaker::new);
    /** The goblin bosses that don't need Iron's Spells (see GoblinBosses). */
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinBosses.Gladiator>> GOBLIN_GLADIATOR = ENTITIES.register("goblin_gladiator",
            () -> EntityType.Builder.of(GoblinBosses.Gladiator::new, MobCategory.MONSTER).sized(0.5f, 1.45f).eyeHeight(1.25f).clientTrackingRange(10)
                    .build(DungeonDelving.id("goblin_gladiator").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinBosses.Veteran>> HOBGOBLIN_VETERAN = hobgoblin("hobgoblin_veteran",
            GoblinBosses.Veteran::new);
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinBosses.General>> HOBGOBLIN_GENERAL = hobgoblin("hobgoblin_general",
            GoblinBosses.General::new);

    public static final DeferredItem<DeferredSpawnEggItem> HOBGOBLIN_SOLDIER_SPAWN_EGG = egg("hobgoblin_soldier", HOBGOBLIN_SOLDIER, 0xB8562E);
    public static final DeferredItem<DeferredSpawnEggItem> HOBGOBLIN_HUNTER_SPAWN_EGG = egg("hobgoblin_hunter", HOBGOBLIN_HUNTER, 0x6B4A2B);
    public static final DeferredItem<DeferredSpawnEggItem> HOBGOBLIN_VANGUARD_SPAWN_EGG = egg("hobgoblin_vanguard", HOBGOBLIN_VANGUARD, 0x8A8F9A);
    public static final DeferredItem<DeferredSpawnEggItem> HOBGOBLIN_SPELLBREAKER_SPAWN_EGG = egg("hobgoblin_spellbreaker", HOBGOBLIN_SPELLBREAKER, 0x4A2E6A);
    public static final DeferredItem<DeferredSpawnEggItem> GOBLIN_GLADIATOR_SPAWN_EGG = egg("goblin_gladiator", GOBLIN_GLADIATOR, 0xC8A040);
    public static final DeferredItem<DeferredSpawnEggItem> HOBGOBLIN_VETERAN_SPAWN_EGG = egg("hobgoblin_veteran", HOBGOBLIN_VETERAN, 0xC8A040);
    public static final DeferredItem<DeferredSpawnEggItem> HOBGOBLIN_GENERAL_SPAWN_EGG = egg("hobgoblin_general", HOBGOBLIN_GENERAL, 0xC8A040);

    /** Orcs (DD-74): bigger than men, in warbands; the casters come with Iron's Spells. */
    public static final DeferredHolder<EntityType<?>, EntityType<Orc.Scrapper>> ORC_SCRAPPER = orc("orc_scrapper", Orc.Scrapper::new);
    public static final DeferredHolder<EntityType<?>, EntityType<OrcRaider>> ORC_RAIDER = orc("orc_raider", OrcRaider::new);
    public static final DeferredHolder<EntityType<?>, EntityType<Orc.Veteran>> ORC_VETERAN = orc("orc_veteran", Orc.Veteran::new);
    public static final DeferredHolder<EntityType<?>, EntityType<OrcVagabond>> ORC_VAGABOND = orc("orc_vagabond", OrcVagabond::new);
    public static final DeferredHolder<EntityType<?>, EntityType<Orc.Rampager>> ORC_RAMPAGER = orc("orc_rampager", Orc.Rampager::new);
    public static final DeferredHolder<EntityType<?>, EntityType<OrcBosses.Commander>> ORC_COMMANDER = orc("orc_commander", OrcBosses.Commander::new);
    public static final DeferredHolder<EntityType<?>, EntityType<OrcBosses.Chieftain>> ORC_CHIEFTAIN = orc("orc_chieftain", OrcBosses.Chieftain::new);
    public static final DeferredHolder<EntityType<?>, EntityType<OrcBosses.Warchief>> ORC_WARCHIEF = orc("orc_warchief", OrcBosses.Warchief::new);
    /** The hand axe orc raiders throw. */
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownAxe>> THROWN_AXE = ENTITIES.register("thrown_axe",
            () -> EntityType.Builder.<ThrownAxe>of(ThrownAxe::new, MobCategory.MISC).sized(0.4f, 0.4f).clientTrackingRange(4).updateInterval(10)
                    .build(DungeonDelving.id("thrown_axe").toString()));

    public static final DeferredItem<DeferredSpawnEggItem> ORC_SCRAPPER_SPAWN_EGG = orcEgg("orc_scrapper", ORC_SCRAPPER, 0x6B4A2B);
    public static final DeferredItem<DeferredSpawnEggItem> ORC_RAIDER_SPAWN_EGG = orcEgg("orc_raider", ORC_RAIDER, 0x9A9A9A);
    public static final DeferredItem<DeferredSpawnEggItem> ORC_VETERAN_SPAWN_EGG = orcEgg("orc_veteran", ORC_VETERAN, 0x5A5F6A);
    public static final DeferredItem<DeferredSpawnEggItem> ORC_VAGABOND_SPAWN_EGG = orcEgg("orc_vagabond", ORC_VAGABOND, 0x4A3A2A);
    public static final DeferredItem<DeferredSpawnEggItem> ORC_RAMPAGER_SPAWN_EGG = orcEgg("orc_rampager", ORC_RAMPAGER, 0xA02020);
    public static final DeferredItem<DeferredSpawnEggItem> ORC_COMMANDER_SPAWN_EGG = orcEgg("orc_commander", ORC_COMMANDER, 0xC8A040);
    public static final DeferredItem<DeferredSpawnEggItem> ORC_CHIEFTAIN_SPAWN_EGG = orcEgg("orc_chieftain", ORC_CHIEFTAIN, 0xC8A040);
    public static final DeferredItem<DeferredSpawnEggItem> ORC_WARCHIEF_SPAWN_EGG = orcEgg("orc_warchief", ORC_WARCHIEF, 0xC8A040);

    private static <T extends Orc> DeferredHolder<EntityType<?>, EntityType<T>> orc(String name, EntityType.EntityFactory<T> factory) {
        return ENTITIES.register(name, () -> EntityType.Builder.of(factory, MobCategory.MONSTER).sized(0.7f, 2.0f).eyeHeight(1.8f)
                .clientTrackingRange(8).build(DungeonDelving.id(name).toString()));
    }

    private static DeferredItem<DeferredSpawnEggItem> orcEgg(String name, DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> type, int spots) {
        return ModRegistries.ITEMS.registerItem(name + "_spawn_egg", properties -> new DeferredSpawnEggItem(type, 0x6E7F5A, spots, properties));
    }

    private static <T extends Hobgoblin> DeferredHolder<EntityType<?>, EntityType<T>> hobgoblin(String name, EntityType.EntityFactory<T> factory) {
        return ENTITIES.register(name, () -> EntityType.Builder.of(factory, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.74f)
                .clientTrackingRange(8).build(DungeonDelving.id(name).toString()));
    }

    private static DeferredItem<DeferredSpawnEggItem> egg(String name, DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> type, int spots) {
        return ModRegistries.ITEMS.registerItem(name + "_spawn_egg", properties -> new DeferredSpawnEggItem(type, 0xB8562E, spots, properties));
    }

    /** The skeleton swordsman: sword and shield, blocks when you get close. */
    public static final DeferredHolder<EntityType<?>, EntityType<SkeletonSwordsman>> SKELETON_SWORDSMAN = ENTITIES.register("skeleton_swordsman",
            () -> EntityType.Builder.of(SkeletonSwordsman::new, MobCategory.MONSTER).sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(8)
                    .build(DungeonDelving.id("skeleton_swordsman").toString()));
    /** The skeleton knight: an armored swordsman in the Skeleton Lord's honor guard. */
    public static final DeferredHolder<EntityType<?>, EntityType<SkeletonKnight>> SKELETON_KNIGHT = ENTITIES.register("skeleton_knight",
            () -> EntityType.Builder.of(SkeletonKnight::new, MobCategory.MONSTER).sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(8)
                    .build(DungeonDelving.id("skeleton_knight").toString()));
    /** The stray swordsman (tier 2): a skeleton swordsman whose blows slow. */
    public static final DeferredHolder<EntityType<?>, EntityType<StraySwordsman>> STRAY_SWORDSMAN = ENTITIES.register("stray_swordsman",
            () -> EntityType.Builder.of(StraySwordsman::new, MobCategory.MONSTER).sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(8)
                    .build(DungeonDelving.id("stray_swordsman").toString()));
    /** The bogged swordsman (tier 3): a skeleton swordsman whose blows poison. */
    public static final DeferredHolder<EntityType<?>, EntityType<BoggedSwordsman>> BOGGED_SWORDSMAN = ENTITIES.register("bogged_swordsman",
            () -> EntityType.Builder.of(BoggedSwordsman::new, MobCategory.MONSTER).sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(8)
                    .build(DungeonDelving.id("bogged_swordsman").toString()));
    /** The wither skeleton archer (tier 4): burning arrows that wither. */
    public static final DeferredHolder<EntityType<?>, EntityType<WitherSkeletonArcher>> WITHER_SKELETON_ARCHER = ENTITIES.register(
            "wither_skeleton_archer", () -> EntityType.Builder.of(WitherSkeletonArcher::new, MobCategory.MONSTER).fireImmune().sized(0.7f, 2.4f)
                    .eyeHeight(2.1f).clientTrackingRange(8).build(DungeonDelving.id("wither_skeleton_archer").toString()));
    /** The Zombie Lord, the tier 1 undead boss. */
    public static final DeferredHolder<EntityType<?>, EntityType<ZombieLord>> ZOMBIE_LORD = ENTITIES.register("zombie_lord",
            () -> EntityType.Builder.of(ZombieLord::new, MobCategory.MONSTER).sized(0.6f, 1.95f).eyeHeight(1.74f).clientTrackingRange(10)
                    .build(DungeonDelving.id("zombie_lord").toString()));
    /** The Skeleton Lord, the tier 2 undead boss. */
    public static final DeferredHolder<EntityType<?>, EntityType<SkeletonLord>> SKELETON_LORD = ENTITIES.register("skeleton_lord",
            () -> EntityType.Builder.of(SkeletonLord::new, MobCategory.MONSTER).sized(0.6f, 1.99f).eyeHeight(1.74f).clientTrackingRange(10)
                    .build(DungeonDelving.id("skeleton_lord").toString()));

    public static final DeferredItem<DeferredSpawnEggItem> SKELETON_SWORDSMAN_SPAWN_EGG = ModRegistries.ITEMS.registerItem("skeleton_swordsman_spawn_egg",
            properties -> new DeferredSpawnEggItem(SKELETON_SWORDSMAN, 0xC1C1C1, 0x8A8A8A, properties));
    public static final DeferredItem<DeferredSpawnEggItem> SKELETON_KNIGHT_SPAWN_EGG = ModRegistries.ITEMS.registerItem("skeleton_knight_spawn_egg",
            properties -> new DeferredSpawnEggItem(SKELETON_KNIGHT, 0xC1C1C1, 0x5A6A7A, properties));
    public static final DeferredItem<DeferredSpawnEggItem> SKELETON_LORD_SPAWN_EGG = ModRegistries.ITEMS.registerItem("skeleton_lord_spawn_egg",
            properties -> new DeferredSpawnEggItem(SKELETON_LORD, 0xC1C1C1, 0xC8A040, properties));

    public static final DeferredItem<DeferredSpawnEggItem> STRAY_SWORDSMAN_SPAWN_EGG = ModRegistries.ITEMS.registerItem("stray_swordsman_spawn_egg",
            properties -> new DeferredSpawnEggItem(STRAY_SWORDSMAN, 0x617677, 0xDDEAEA, properties));
    public static final DeferredItem<DeferredSpawnEggItem> BOGGED_SWORDSMAN_SPAWN_EGG = ModRegistries.ITEMS.registerItem("bogged_swordsman_spawn_egg",
            properties -> new DeferredSpawnEggItem(BOGGED_SWORDSMAN, 0x8A9A5B, 0x314D1B, properties));
    public static final DeferredItem<DeferredSpawnEggItem> WITHER_SKELETON_ARCHER_SPAWN_EGG = ModRegistries.ITEMS.registerItem(
            "wither_skeleton_archer_spawn_egg", properties -> new DeferredSpawnEggItem(WITHER_SKELETON_ARCHER, 0x141414, 0x8A6A3A, properties));
    public static final DeferredItem<DeferredSpawnEggItem> ZOMBIE_LORD_SPAWN_EGG = ModRegistries.ITEMS.registerItem("zombie_lord_spawn_egg",
            properties -> new DeferredSpawnEggItem(ZOMBIE_LORD, 0x00AFAF, 0xC8A040, properties));

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
        event.put(STRAY_SWORDSMAN.get(), StraySwordsman.createAttributes().build());
        event.put(BOGGED_SWORDSMAN.get(), BoggedSwordsman.createAttributes().build());
        event.put(WITHER_SKELETON_ARCHER.get(), AbstractSkeleton.createAttributes().build());
        event.put(ZOMBIE_LORD.get(), ZombieLord.createAttributes().build());
        event.put(HOBGOBLIN_SOLDIER.get(), HobgoblinSoldier.createAttributes().build());
        event.put(HOBGOBLIN_HUNTER.get(), HobgoblinHunter.createAttributes().build());
        event.put(HOBGOBLIN_VANGUARD.get(), HobgoblinVanguard.createAttributes().build());
        event.put(HOBGOBLIN_SPELLBREAKER.get(), HobgoblinSpellbreaker.createAttributes().build());
        event.put(GOBLIN_GLADIATOR.get(), GoblinBosses.Gladiator.createAttributes().build());
        event.put(HOBGOBLIN_VETERAN.get(), GoblinBosses.Veteran.createAttributes().build());
        event.put(HOBGOBLIN_GENERAL.get(), GoblinBosses.General.createAttributes().build());
        event.put(ORC_SCRAPPER.get(), Orc.Scrapper.createAttributes().build());
        event.put(ORC_RAIDER.get(), OrcRaider.createAttributes().build());
        event.put(ORC_VETERAN.get(), Orc.Veteran.createAttributes().build());
        event.put(ORC_VAGABOND.get(), OrcVagabond.createAttributes().build());
        event.put(ORC_RAMPAGER.get(), Orc.Rampager.createAttributes().build());
        event.put(ORC_COMMANDER.get(), OrcBosses.Commander.createAttributes().build());
        event.put(ORC_CHIEFTAIN.get(), OrcBosses.Chieftain.createAttributes().build());
        event.put(ORC_WARCHIEF.get(), OrcBosses.Warchief.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        for (EntityType<? extends Monster> goblin : List.of(GOBLIN.get(), GOBLIN_ARCHER.get(), SKELETON_SWORDSMAN.get(), SKELETON_KNIGHT.get(),
                SKELETON_LORD.get(), STRAY_SWORDSMAN.get(), BOGGED_SWORDSMAN.get(), WITHER_SKELETON_ARCHER.get(), ZOMBIE_LORD.get(),
                HOBGOBLIN_SOLDIER.get(), HOBGOBLIN_HUNTER.get(), HOBGOBLIN_VANGUARD.get(), HOBGOBLIN_SPELLBREAKER.get(), GOBLIN_GLADIATOR.get(),
                HOBGOBLIN_VETERAN.get(), HOBGOBLIN_GENERAL.get(), ORC_SCRAPPER.get(), ORC_RAIDER.get(), ORC_VETERAN.get(), ORC_VAGABOND.get(),
                ORC_RAMPAGER.get(), ORC_COMMANDER.get(), ORC_CHIEFTAIN.get(), ORC_WARCHIEF.get())) {
            event.register(goblin, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }

    /** Makes sure the items above are registered; call once while setting up registries. */
    static void init() {}
}
