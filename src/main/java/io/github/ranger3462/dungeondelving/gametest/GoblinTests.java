package io.github.ranger3462.dungeondelving.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonGear;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.SpawnerMobs;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.entity.Goblin;
import io.github.ranger3462.dungeondelving.entity.GoblinArcher;
import io.github.ranger3462.dungeondelving.entity.GoblinBosses;
import io.github.ranger3462.dungeondelving.entity.GoblinSwarm;
import io.github.ranger3462.dungeondelving.entity.HobgoblinHunter;
import io.github.ranger3462.dungeondelving.entity.HobgoblinSoldier;
import io.github.ranger3462.dungeondelving.entity.HobgoblinSpellbreaker;
import io.github.ranger3462.dungeondelving.entity.HobgoblinVanguard;
import io.github.ranger3462.dungeondelving.entity.Spellcasters;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the spear goblin: fast, fragile, armed, and the most common dungeon mob. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class GoblinTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";
    /** An iron sword's damage. */
    private static final float IRON_SWORD = 6.0f;

    @GameTest(template = ANY_TEMPLATE)
    public static void goblinsAreFastFragileAndArmed(GameTestHelper helper) {
        Goblin goblin = spawn(helper);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        try {
            helper.assertTrue(goblin.getMaxHealth() == 14.0f, "goblins should have 14 health, not " + goblin.getMaxHealth());
            helper.assertTrue(goblin.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) > zombie.getAttributeBaseValue(Attributes.MOVEMENT_SPEED),
                    "goblins should be faster than zombies");
            helper.assertTrue(goblin.getMainHandItem().is(ModEntities.GOBLIN_SPEAR.get()), "goblins should carry a spear");
            helper.assertTrue(goblin.getBbHeight() < 1.6f, "goblins should be shorter than a player");

            int hits = 0;
            while (goblin.isAlive() && hits < 10) {
                goblin.invulnerableTime = 0;
                goblin.hurt(helper.getLevel().damageSources().generic(), IRON_SWORD);
                hits++;
            }
            helper.assertTrue(hits == 3, "an iron sword should kill a goblin in 3 hits, took " + hits);
        } finally {
            goblin.discard();
            zombie.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void spawnersFollowTheMobWeights(GameTestHelper helper) {
        RandomSource random = RandomSource.create(3);
        Map<EntityType<?>, Integer> counts = new HashMap<>();
        int rolls = 4000;
        for (int i = 0; i < rolls; i++) {
            counts.merge(SpawnerMobs.pick(helper.getLevel(), random, DungeonFactions.GOBLIN_DEFAULTS, 1).orElseThrow().value(), 1, Integer::sum);
        }
        // Tier 1 goblin dungeons are warriors and archers only.
        expectShare(helper, counts, ModEntities.GOBLIN.get(), 60 / 90.0, rolls);
        expectShare(helper, counts, ModEntities.GOBLIN_ARCHER.get(), 30 / 90.0, rolls);
        helper.assertTrue(counts.keySet().stream().allMatch(type -> type.is(GoblinSwarm.GOBLINS)), "goblin dungeon spawners should only spawn goblins");

        helper.assertTrue(SpawnerMobs.pick(helper.getLevel(), random, List.of("dungeondelving:not_a_mob=5", "minecraft:zombie=1"))
                .orElseThrow().value() == EntityType.ZOMBIE, "mobs that don't exist should be skipped");
        helper.assertTrue(SpawnerMobs.pick(helper.getLevel(), random, List.of("#dungeondelving:dungeon_mobs=1")).orElseThrow().value()
                .is(DungeonMarkers.DUNGEON_MOBS), "a tag entry should pick a mob from the tag");
        helper.assertTrue(SpawnerMobs.pick(helper.getLevel(), random, List.of("minecraft:zombie=1@2-4", "minecraft:skeleton=1@1"), 1)
                .orElseThrow().value() == EntityType.SKELETON, "entries for other tiers shouldn't spawn");
        for (String bad : new String[] { "", "goblin=abc", "dungeondelving:goblin=-2", "a=b=c", "minecraft:zombie=1@3-2", "minecraft:zombie=1@x" }) {
            helper.assertTrue(SpawnerMobs.parse(bad).isEmpty(), "\"" + bad + "\" shouldn't be a valid entry");
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void archersKeepTheirDistanceWithAWeakBow(GameTestHelper helper) {
        GoblinArcher archer = spawn(helper, ModEntities.GOBLIN_ARCHER.get());
        try {
            helper.assertTrue(archer.getMaxHealth() == 10.0f, "archers should have 10 health, not " + archer.getMaxHealth());
            helper.assertTrue(archer.getMainHandItem().is(ModEntities.GOBLIN_BOW.get()), "archers should carry a Goblin Bow");
            helper.assertTrue(archer.getType().is(GoblinSwarm.GOBLINS), "archers are goblins, so they join swarms");
            Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
            double normal = arrow.getBaseDamage();
            ModEntities.GOBLIN_BOW.get().customArrow(arrow, new ItemStack(Items.ARROW), new ItemStack(ModEntities.GOBLIN_BOW.get()));
            helper.assertTrue(arrow.getBaseDamage() < normal, "Goblin Bow arrows should hit softer than normal");
            helper.assertTrue(new ItemStack(ModEntities.GOBLIN_BOW.get()).getMaxDamage() < new ItemStack(Items.BOW).getMaxDamage(),
                    "the Goblin Bow should wear out faster than a normal bow");
        } finally {
            archer.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void shamansExistWithIronsSpells(GameTestHelper helper) {
        boolean irons = ModList.get().isLoaded("irons_spellbooks");
        boolean shamans = BuiltInRegistries.ENTITY_TYPE.containsKey(DungeonDelving.id("goblin_shaman"));
        helper.assertTrue(irons == shamans, "goblin shamans should exist exactly when Iron's Spells is installed");
        if (shamans) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(DungeonDelving.id("goblin_shaman"));
            Mob shaman = (Mob) type.create(helper.getLevel());
            BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0)).above(100);
            shaman.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            helper.getLevel().addFreshEntity(shaman);
            try {
                helper.assertTrue(shaman.getMaxHealth() == 12.0f, "shamans should have 12 health, not " + shaman.getMaxHealth());
                helper.assertTrue(type.is(GoblinSwarm.GOBLINS), "shamans are goblins, so they join swarms");
            } finally {
                shaman.discard();
            }
        }
        helper.succeed();
    }

    private static void expectShare(GameTestHelper helper, Map<EntityType<?>, Integer> counts, EntityType<?> type, double share, int rolls) {
        double got = counts.getOrDefault(type, 0) / (double) rolls;
        helper.assertTrue(Math.abs(got - share) < 0.04, type.getDescriptionId() + " should be about " + Math.round(share * 100) + "% of spawns, got "
                + Math.round(got * 100) + "%");
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void theSpearIsAWeapon(GameTestHelper helper) {
        helper.assertTrue(ModEntities.GOBLIN_SPEAR.get().getDefaultInstance().is(ItemTags.SWORDS), "the spear should count as a sword");
        helper.assertFalse(ModEntities.GOBLIN_SPEAR.get().getDefaultInstance().is(Items.WOODEN_SWORD), "the spear is its own item");
        helper.succeed();
    }

    /** Each tier of goblin dungeon adds tougher goblins on top of the warriors and archers. */
    @GameTest(template = ANY_TEMPLATE)
    public static void goblinDungeonsGrowTougherByTier(GameTestHelper helper) {
        RandomSource random = RandomSource.create(9);
        List<Set<EntityType<?>>> tiers = new ArrayList<>();
        for (int tier = 1; tier <= 4; tier++) {
            Set<EntityType<?>> mobs = new HashSet<>();
            for (int i = 0; i < 600; i++) {
                mobs.add(SpawnerMobs.pick(helper.getLevel(), random, DungeonFactions.GOBLIN_DEFAULTS, tier).orElseThrow().value());
            }
            tiers.add(mobs);
        }
        Set<EntityType<?>> casters = new HashSet<>();
        for (String id : List.of("goblin_shaman", "goblin_pyro")) {
            BuiltInRegistries.ENTITY_TYPE.getOptional(DungeonDelving.id(id)).ifPresent(casters::add);
        }
        Set<EntityType<?>> expected = new HashSet<>(Set.of(ModEntities.GOBLIN.get(), ModEntities.GOBLIN_ARCHER.get()));
        helper.assertTrue(tiers.get(0).equals(expected), "tier 1: warriors and archers, got " + tiers.get(0));
        expected.addAll(casters);
        helper.assertTrue(tiers.get(1).equals(expected), "tier 2: plus shamans and pyros (with Iron's Spells), got " + tiers.get(1));
        expected.addAll(Set.of(ModEntities.HOBGOBLIN_SOLDIER.get(), ModEntities.HOBGOBLIN_HUNTER.get()));
        helper.assertTrue(tiers.get(2).equals(expected), "tier 3: plus hobgoblin soldiers and hunters, got " + tiers.get(2));
        expected.addAll(Set.of(ModEntities.HOBGOBLIN_SPELLBREAKER.get(), ModEntities.HOBGOBLIN_VANGUARD.get()));
        helper.assertTrue(tiers.get(3).equals(expected), "tier 4: plus spellbreakers and vanguards, got " + tiers.get(3));
        helper.assertTrue(expected.stream().allMatch(type -> type.is(GoblinSwarm.GOBLINS)), "every goblin dungeon mob is a goblin");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void goblinsComeInBiggerPacksAtHigherTiers(GameTestHelper helper) {
        short low = DungeonMarkers.spawnerTag(ModEntities.GOBLIN.get(), 1).getShort("SpawnCount");
        short high = DungeonMarkers.spawnerTag(ModEntities.GOBLIN.get(), 4).getShort("SpawnCount");
        short soldier = DungeonMarkers.spawnerTag(ModEntities.HOBGOBLIN_SOLDIER.get(), 4).getShort("SpawnCount");
        helper.assertTrue(high == low + DungeonMarkers.SPAWN_COUNT[3] - DungeonMarkers.SPAWN_COUNT[0] + 3,
                "goblin spawners should put out 3 more at tier 4 than the usual growth, got " + low + " then " + high);
        helper.assertTrue(soldier == DungeonMarkers.SPAWN_COUNT[3], "hobgoblins don't come in packs, got " + soldier);
        helper.assertFalse(ModEntities.GOBLIN.get().is(DungeonGear.GEARED), "goblins wear no dungeon armor");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void hobgoblinsCarryTheirOwnGear(GameTestHelper helper) {
        HobgoblinSoldier soldier = spawn(helper, ModEntities.HOBGOBLIN_SOLDIER.get());
        HobgoblinHunter hunter = spawn(helper, ModEntities.HOBGOBLIN_HUNTER.get());
        HobgoblinVanguard vanguard = spawn(helper, ModEntities.HOBGOBLIN_VANGUARD.get());
        HobgoblinSpellbreaker breaker = spawn(helper, ModEntities.HOBGOBLIN_SPELLBREAKER.get());
        try {
            helper.assertTrue(soldier.getMainHandItem().is(Items.IRON_SWORD) && soldier.getOffhandItem().is(Items.SHIELD)
                    && soldier.getItemBySlot(EquipmentSlot.CHEST).is(Items.CHAINMAIL_CHESTPLATE), "soldiers: sword, shield and chainmail");
            helper.assertTrue(hunter.getMainHandItem().is(Items.CROSSBOW), "hunters carry crossbows");
            helper.assertTrue(vanguard.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE) && vanguard.getOffhandItem().is(Items.SHIELD),
                    "vanguards: heavy armor, sword and board");
            helper.assertTrue(breaker.getMainHandItem().is(Items.IRON_AXE) && breaker.getOffhandItem().is(Items.IRON_SWORD)
                    && breaker.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE), "spellbreakers: two weapons, light armor");
            helper.assertTrue(soldier.getMaxHealth() > ModEntities.GOBLIN.get().create(helper.getLevel()).getMaxHealth(), "hobgoblins are tougher than goblins");
        } finally {
            soldier.discard();
            hunter.discard();
            vanguard.discard();
            breaker.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void spellbreakersKnowWhoHasBeenCasting(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "mage"));
        helper.assertFalse(Spellcasters.isCaster(player), "nobody counts as a caster before casting");
        Spellcasters.markCast(player);
        helper.assertTrue(Spellcasters.isCaster(player), "a player who just cast is a caster");
        Spellcasters.forget(player);
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void goblinBossesGuardEachTier(GameTestHelper helper) {
        RandomSource random = RandomSource.create(2);
        helper.assertTrue(DungeonBosses.pick(1, DungeonFactions.GOBLINS, random).equals(DungeonBosses.GOBLIN_GLADIATOR), "tier 1: the Gladiator");
        helper.assertTrue(DungeonBosses.pick(3, DungeonFactions.GOBLINS, random).equals(DungeonBosses.HOBGOBLIN_VETERAN), "tier 3: the Veteran");
        helper.assertTrue(DungeonBosses.pick(4, DungeonFactions.GOBLINS, random).equals(DungeonBosses.HOBGOBLIN_GENERAL), "tier 4: the General");
        if (ModList.get().isLoaded("irons_spellbooks")) {
            helper.assertTrue(DungeonBosses.pick(2, DungeonFactions.GOBLINS, random).equals("goblin_chief"), "tier 2: the Goblin Shaman Chief");
        }
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0)).above(100);
        LivingEntity gladiator = DungeonBosses.spawn(helper.getLevel(), pos, 1, -1, DungeonBosses.GOBLIN_GLADIATOR);
        GoblinBosses.General general = (GoblinBosses.General) DungeonBosses.spawn(helper.getLevel(), pos, 4, -1, DungeonBosses.HOBGOBLIN_GENERAL);
        try {
            helper.assertTrue(gladiator.getMainHandItem().is(Items.IRON_AXE) && gladiator.getOffhandItem().is(Items.IRON_SWORD), "the Gladiator dual-wields");
            helper.assertTrue(general.getMainHandItem().is(Items.CROSSBOW), "the General starts with his crossbow");
            general.switchWeapons(3.0);
            helper.assertTrue(general.isSwordDrawn(), "up close, the General draws his sword");
            general.switchWeapons(12.0);
            helper.assertTrue(general.getMainHandItem().is(Items.CROSSBOW), "at range, he goes back to the crossbow");
        } finally {
            helper.getLevel().getEntitiesOfClass(LivingEntity.class, gladiator.getBoundingBox().inflate(16),
                    entity -> !(entity instanceof net.minecraft.world.entity.player.Player)).forEach(net.minecraft.world.entity.Entity::discard);
        }
        helper.succeed();
    }

    private static Goblin spawn(GameTestHelper helper) {
        return spawn(helper, ModEntities.GOBLIN.get());
    }

    private static <T extends Goblin> T spawn(GameTestHelper helper, EntityType<T> type) {
        T goblin = type.create(helper.getLevel());
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0)).above(100);
        goblin.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        EventHooks.finalizeMobSpawn(goblin, helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        helper.getLevel().addFreshEntity(goblin);
        return goblin;
    }
}
