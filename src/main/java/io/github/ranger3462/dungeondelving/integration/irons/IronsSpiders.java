package io.github.ranger3462.dungeondelving.integration.irons;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import io.redspace.ironsspellbooks.entity.mobs.ice_spider.IceSpiderEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The spiders' bosses, which need Iron's Spells (DD-58): the Spider Queen (tier 1), the Cave Spider Queen (tier 2) and
 * the Ice Spider Queen (tier 3), all built on its ice spider ({@link SpiderQueen}). Only loaded when Iron's Spells is
 * installed, so nothing outside this package may refer to these classes directly.
 */
public final class IronsSpiders {
    public static final String SPIDER_QUEEN_BOSS = "spider_queen";
    public static final String CAVE_SPIDER_QUEEN_BOSS = "cave_spider_queen";
    public static final String ICE_SPIDER_QUEEN_BOSS = "ice_spider_queen";
    public static DeferredHolder<EntityType<?>, EntityType<SpiderQueen.Common>> SPIDER_QUEEN;
    public static DeferredHolder<EntityType<?>, EntityType<SpiderQueen.Cave>> CAVE_SPIDER_QUEEN;
    public static DeferredHolder<EntityType<?>, EntityType<SpiderQueen.Ice>> ICE_SPIDER_QUEEN;
    public static DeferredItem<DeferredSpawnEggItem> SPIDER_QUEEN_SPAWN_EGG;
    public static DeferredItem<DeferredSpawnEggItem> CAVE_SPIDER_QUEEN_SPAWN_EGG;
    public static DeferredItem<DeferredSpawnEggItem> ICE_SPIDER_QUEEN_SPAWN_EGG;

    private IronsSpiders() {}

    /** Called from the mod constructor when Iron's Spells is loaded. */
    public static void register(IEventBus modEventBus) {
        SPIDER_QUEEN = ModEntities.ENTITIES.register("spider_queen", () -> EntityType.Builder.<SpiderQueen.Common>of(SpiderQueen.Common::new, MobCategory.MONSTER)
                .sized(1.75f, 1.9f).clientTrackingRange(10).build(DungeonDelving.id("spider_queen").toString()));
        CAVE_SPIDER_QUEEN = ModEntities.ENTITIES.register("cave_spider_queen", () -> EntityType.Builder.<SpiderQueen.Cave>of(SpiderQueen.Cave::new, MobCategory.MONSTER)
                .sized(1.75f, 1.9f).clientTrackingRange(10).build(DungeonDelving.id("cave_spider_queen").toString()));
        ICE_SPIDER_QUEEN = ModEntities.ENTITIES.register("ice_spider_queen", () -> EntityType.Builder.<SpiderQueen.Ice>of(SpiderQueen.Ice::new, MobCategory.MONSTER)
                .sized(1.75f, 1.9f).clientTrackingRange(10).build(DungeonDelving.id("ice_spider_queen").toString()));
        SPIDER_QUEEN_SPAWN_EGG = ModRegistries.ITEMS.registerItem("spider_queen_spawn_egg",
                properties -> new DeferredSpawnEggItem(SPIDER_QUEEN, 0x342A24, 0xC81414, properties));
        CAVE_SPIDER_QUEEN_SPAWN_EGG = ModRegistries.ITEMS.registerItem("cave_spider_queen_spawn_egg",
                properties -> new DeferredSpawnEggItem(CAVE_SPIDER_QUEEN, 0x0C3A42, 0xC81414, properties));
        ICE_SPIDER_QUEEN_SPAWN_EGG = ModRegistries.ITEMS.registerItem("ice_spider_queen_spawn_egg",
                properties -> new DeferredSpawnEggItem(ICE_SPIDER_QUEEN, 0xE4EBE4, 0x13A5FF, properties));
        DungeonBosses.register(new DungeonBosses.BossType(SPIDER_QUEEN_BOSS, DungeonFactions.SPIDERS, tier -> tier == 1,
                (level, pos, tier) -> create(SPIDER_QUEEN.get(), level, pos, SPIDER_QUEEN_BOSS, 60.0, 6.0, 1.0)));
        DungeonBosses.register(new DungeonBosses.BossType(CAVE_SPIDER_QUEEN_BOSS, DungeonFactions.SPIDERS, tier -> tier == 2,
                (level, pos, tier) -> create(CAVE_SPIDER_QUEEN.get(), level, pos, CAVE_SPIDER_QUEEN_BOSS, 110.0, 8.0, 1.1)));
        DungeonBosses.register(new DungeonBosses.BossType(ICE_SPIDER_QUEEN_BOSS, DungeonFactions.SPIDERS, tier -> tier == 3,
                (level, pos, tier) -> create(ICE_SPIDER_QUEEN.get(), level, pos, ICE_SPIDER_QUEEN_BOSS, 200.0, 12.0, 1.4)));
        modEventBus.addListener(IronsSpiders::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SPIDER_QUEEN.get(), IceSpiderEntity.prepareAttributes().build());
        event.put(CAVE_SPIDER_QUEEN.get(), IceSpiderEntity.prepareAttributes().build());
        event.put(ICE_SPIDER_QUEEN.get(), IceSpiderEntity.prepareAttributes().build());
    }

    /** A spider queen with a boss's health, damage and size, named, ready to be added to the world. */
    private static Mob create(EntityType<? extends SpiderQueen> type, ServerLevel level, BlockPos pos, String name, double health, double damage, double scale) {
        SpiderQueen queen = type.create(level);
        queen.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180.0f, 0.0f);
        EventHooks.finalizeMobSpawn(queen, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        DungeonBosses.setBase(queen, Attributes.MAX_HEALTH, health);
        DungeonBosses.setBase(queen, Attributes.ATTACK_DAMAGE, damage);
        DungeonBosses.setBase(queen, Attributes.SCALE, scale);
        DungeonBosses.setBase(queen, Attributes.FOLLOW_RANGE, 48.0);
        queen.setHealth(queen.getMaxHealth());
        queen.setCustomName(Component.translatable("entity.dungeondelving." + name).withStyle(ChatFormatting.GOLD));
        queen.setCustomNameVisible(true);
        return queen;
    }
}
