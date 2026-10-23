package io.github.ranger3462.dungeondelving.integration.irons;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.entity.Spellcasters;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.item.weapons.ExtendedSwordItem;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import io.redspace.ironsspellbooks.item.weapons.StaffTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Goblins that need Iron's Spells: the goblin shaman and pyro, the Goblin Shaman Chief (the tier 2 boss), and their staves.
 * Only loaded when Iron's Spells is installed, so nothing outside this package may refer to these classes directly.
 */
public final class IronsGoblins {
    public static DeferredHolder<EntityType<?>, EntityType<GoblinShaman>> GOBLIN_SHAMAN;
    public static DeferredItem<DeferredSpawnEggItem> GOBLIN_SHAMAN_SPAWN_EGG;
    public static DeferredHolder<EntityType<?>, EntityType<GoblinPyro>> GOBLIN_PYRO;
    public static DeferredItem<DeferredSpawnEggItem> GOBLIN_PYRO_SPAWN_EGG;
    public static DeferredHolder<EntityType<?>, EntityType<GoblinChief>> GOBLIN_CHIEF;
    public static DeferredItem<DeferredSpawnEggItem> GOBLIN_CHIEF_SPAWN_EGG;
    /** The crude staff goblin shamans sometimes drop: a little better than nothing, weaker than Iron's early staves. */
    public static DeferredItem<GoblinStaffItem> GOBLIN_TOTEM_STAFF;
    /** The Goblin Shaman Chief's signature drop: better than the early Iron's staves, for tier 1. */
    public static DeferredItem<GoblinStaffItem> GOBLIN_CHIEF_STAFF;
    public static final String GOBLIN_CHIEF_BOSS = "goblin_chief";

    private IronsGoblins() {}

    /** Called from the mod constructor when Iron's Spells is loaded. */
    public static void register(IEventBus modEventBus) {
        GOBLIN_SHAMAN = ModEntities.ENTITIES.register("goblin_shaman",
                () -> EntityType.Builder.of(GoblinShaman::new, MobCategory.MONSTER).sized(0.5f, 1.45f).eyeHeight(1.25f).clientTrackingRange(8)
                        .build(DungeonDelving.id("goblin_shaman").toString()));
        GOBLIN_SHAMAN_SPAWN_EGG = ModRegistries.ITEMS.registerItem("goblin_shaman_spawn_egg",
                properties -> new DeferredSpawnEggItem(GOBLIN_SHAMAN, 0x5E8C3A, 0x6A3D8F, properties));
        GOBLIN_PYRO = ModEntities.ENTITIES.register("goblin_pyro",
                () -> EntityType.Builder.of(GoblinPyro::new, MobCategory.MONSTER).fireImmune().sized(0.5f, 1.45f).eyeHeight(1.25f).clientTrackingRange(8)
                        .build(DungeonDelving.id("goblin_pyro").toString()));
        GOBLIN_PYRO_SPAWN_EGG = ModRegistries.ITEMS.registerItem("goblin_pyro_spawn_egg",
                properties -> new DeferredSpawnEggItem(GOBLIN_PYRO, 0x5E8C3A, 0xD8601C, properties));
        GOBLIN_CHIEF = ModEntities.ENTITIES.register("goblin_chief",
                () -> EntityType.Builder.of(GoblinChief::new, MobCategory.MONSTER).sized(0.6f, 1.75f).eyeHeight(1.5f).clientTrackingRange(10)
                        .build(DungeonDelving.id("goblin_chief").toString()));
        GOBLIN_CHIEF_SPAWN_EGG = ModRegistries.ITEMS.registerItem("goblin_chief_spawn_egg",
                properties -> new DeferredSpawnEggItem(GOBLIN_CHIEF, 0x5E8C3A, 0xC8A040, properties));
        GOBLIN_TOTEM_STAFF = ModRegistries.ITEMS.registerItem("goblin_totem_staff", properties -> new GoblinStaffItem(properties.attributes(
                ExtendedSwordItem.createAttributes(new StaffTier(2.0f, -3.0f,
                        new AttributeContainer(AttributeRegistry.SPELL_POWER, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                        new AttributeContainer(AttributeRegistry.MANA_REGEN, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                        new AttributeContainer(AttributeRegistry.NATURE_SPELL_POWER, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_BASE))))),
                new Item.Properties().stacksTo(1));
        GOBLIN_CHIEF_STAFF = ModRegistries.ITEMS.registerItem("goblin_chief_staff", properties -> new GoblinStaffItem(properties.attributes(
                ExtendedSwordItem.createAttributes(new StaffTier(4.0f, -3.0f,
                        new AttributeContainer(AttributeRegistry.SPELL_POWER, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                        new AttributeContainer(AttributeRegistry.MANA_REGEN, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                        new AttributeContainer(AttributeRegistry.COOLDOWN_REDUCTION, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                        new AttributeContainer(AttributeRegistry.FIRE_SPELL_POWER, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                        new AttributeContainer(AttributeRegistry.NATURE_SPELL_POWER, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE))))),
                new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant());
        DungeonBosses.register(new DungeonBosses.BossType(GOBLIN_CHIEF_BOSS, DungeonFactions.GOBLINS, tier -> tier == 2, IronsGoblins::createChief));
        modEventBus.addListener(IronsGoblins::onAttributes);
        // Spellbreakers go after whoever has been casting.
        NeoForge.EVENT_BUS.addListener((SpellOnCastEvent event) -> Spellcasters.markCast(event.getEntity()));
        // Casting trains the spell's magic school tree (DD-81): a base, plus a little for higher-level spells.
        NeoForge.EVENT_BUS.addListener((SpellOnCastEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                io.github.ranger3462.dungeondelving.skill.MagicSchool.byId(event.getSchoolType().getId().toString()).ifPresent(school ->
                        io.github.ranger3462.dungeondelving.skill.ActivitySkills.award(player, school.skill,
                                io.github.ranger3462.dungeondelving.skill.MagicSchool.CAST_XP + event.getSpellLevel() / 2));
            }
        });
        modEventBus.addListener(IronsGoblins::onSpawnPlacements);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(GOBLIN_SHAMAN.get(), GoblinShaman.createAttributes().build());
        event.put(GOBLIN_PYRO.get(), GoblinShaman.createAttributes().build());
        event.put(GOBLIN_CHIEF.get(), GoblinChief.createAttributes().build());
    }

    /** The Goblin Shaman Chief, holding his staff, named, ready to be added to the world. */
    private static Mob createChief(ServerLevel level, BlockPos pos, int tier) {
        GoblinChief chief = GOBLIN_CHIEF.get().create(level);
        chief.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
        EventHooks.finalizeMobSpawn(chief, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        chief.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(GOBLIN_CHIEF_STAFF.get()));
        chief.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        chief.setCustomName(Component.translatable("entity.dungeondelving.goblin_chief").withStyle(ChatFormatting.GOLD));
        chief.setCustomNameVisible(true);
        return chief;
    }

    private static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(GOBLIN_SHAMAN.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(GOBLIN_PYRO.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
