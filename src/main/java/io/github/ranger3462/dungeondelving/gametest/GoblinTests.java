package io.github.ranger3462.dungeondelving.gametest;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers;
import io.github.ranger3462.dungeondelving.entity.Goblin;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
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
    public static void goblinsAreTheCommonDungeonMob(GameTestHelper helper) {
        RandomSource random = RandomSource.create(3);
        for (int i = 0; i < 50; i++) {
            helper.assertTrue(DungeonMarkers.pickMob(helper.getLevel(), random, 1.0).orElseThrow().value() == ModEntities.GOBLIN.get(),
                    "the common pick should be a goblin");
            helper.assertTrue(DungeonMarkers.pickMob(helper.getLevel(), random, 0.0).orElseThrow().value() != ModEntities.GOBLIN.get(),
                    "the other pick should come from the general dungeon mob list");
        }
        int goblins = 0;
        for (int i = 0; i < 1000; i++) {
            if (DungeonMarkers.pickMob(helper.getLevel(), random, 0.6).orElseThrow().value() == ModEntities.GOBLIN.get()) {
                goblins++;
            }
        }
        helper.assertTrue(goblins > 520 && goblins < 680, "about 60% of dungeon mobs should be goblins, got " + goblins / 10 + "%");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void theSpearIsAWeapon(GameTestHelper helper) {
        helper.assertTrue(ModEntities.GOBLIN_SPEAR.get().getDefaultInstance().is(ItemTags.SWORDS), "the spear should count as a sword");
        helper.assertFalse(ModEntities.GOBLIN_SPEAR.get().getDefaultInstance().is(Items.WOODEN_SWORD), "the spear is its own item");
        helper.succeed();
    }

    private static Goblin spawn(GameTestHelper helper) {
        Goblin goblin = ModEntities.GOBLIN.get().create(helper.getLevel());
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0)).above(100);
        goblin.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        EventHooks.finalizeMobSpawn(goblin, helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        helper.getLevel().addFreshEntity(goblin);
        return goblin;
    }
}
