package io.github.ranger3462.dungeondelving.gametest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers;
import io.github.ranger3462.dungeondelving.dungeon.SpawnerMobs;
import io.github.ranger3462.dungeondelving.entity.Goblin;
import io.github.ranger3462.dungeondelving.entity.GoblinArcher;
import io.github.ranger3462.dungeondelving.entity.GoblinSwarm;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
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
            counts.merge(SpawnerMobs.pick(helper.getLevel(), random, SpawnerMobs.DEFAULTS).orElseThrow().value(), 1, Integer::sum);
        }
        boolean shamans = BuiltInRegistries.ENTITY_TYPE.containsKey(DungeonDelving.id("goblin_shaman"));
        double total = shamans ? 100 : 85;
        expectShare(helper, counts, ModEntities.GOBLIN.get(), 60 / total, rolls);
        expectShare(helper, counts, ModEntities.GOBLIN_ARCHER.get(), 25 / total, rolls);
        if (shamans) {
            expectShare(helper, counts, BuiltInRegistries.ENTITY_TYPE.get(DungeonDelving.id("goblin_shaman")), 15 / total, rolls);
        }
        helper.assertTrue(counts.keySet().stream().allMatch(type -> type.is(GoblinSwarm.GOBLINS)), "the default spawner mobs should all be goblins");

        helper.assertTrue(SpawnerMobs.pick(helper.getLevel(), random, List.of("dungeondelving:not_a_mob=5", "minecraft:zombie=1"))
                .orElseThrow().value() == EntityType.ZOMBIE, "mobs that don't exist should be skipped");
        helper.assertTrue(SpawnerMobs.pick(helper.getLevel(), random, List.of("#dungeondelving:dungeon_mobs=1")).orElseThrow().value()
                .is(DungeonMarkers.DUNGEON_MOBS), "a tag entry should pick a mob from the tag");
        for (String bad : new String[] { "", "goblin=abc", "dungeondelving:goblin=-2", "a=b=c" }) {
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
