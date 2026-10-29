package io.github.ranger3462.dungeondelving.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.SpawnerMobs;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.entity.Kobold;
import io.github.ranger3462.dungeondelving.entity.KoboldPackmaster;
import io.github.ranger3462.dungeondelving.entity.KoboldScavenger;
import io.github.ranger3462.dungeondelving.entity.ThrownSpear;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the kobolds (DD-54): their gear, their pack tactics, the scavenger's spears, and the realm's kobolds using them. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class KoboldTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    private static <T extends Mob> T spawn(GameTestHelper helper, EntityType<T> type, int x) {
        T mob = type.create(helper.getLevel());
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0)).above(100);
        mob.moveTo(pos.getX() + 0.5 + x, pos.getY(), pos.getZ() + 0.5);
        mob.setNoGravity(true);
        EventHooks.finalizeMobSpawn(mob, helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void koboldsAreSmallAndArmed(GameTestHelper helper) {
        Kobold looter = spawn(helper, ModEntities.KOBOLD_LOOTER.get(), 0);
        Kobold scavenger = spawn(helper, ModEntities.KOBOLD_SCAVENGER.get(), 1);
        try {
            helper.assertTrue(looter.getBbHeight() < 1.45f, "kobolds should be shorter than goblins");
            helper.assertTrue(looter.getMainHandItem().is(Items.STONE_PICKAXE), "looters should carry a stolen pickaxe");
            helper.assertTrue(scavenger.getMainHandItem().is(ModEntities.GOBLIN_SPEAR.get()), "scavengers should carry a spear");
            helper.assertTrue(looter.getType().is(Kobold.KOBOLDS) && scavenger.getType().is(Kobold.KOBOLDS), "every kobold should be tagged");
        } finally {
            looter.discard();
            scavenger.discard();
        }
        helper.succeed();
    }

    /** Alone, a kobold hits normally; with two more nearby, half as hard again, and so do its spears. */
    @GameTest(template = ANY_TEMPLATE)
    public static void koboldsHitHarderInAPack(GameTestHelper helper) {
        Kobold looter = spawn(helper, ModEntities.KOBOLD_LOOTER.get(), 0);
        List<Kobold> pack = List.of();
        try {
            double alone = looter.getAttributeValue(Attributes.ATTACK_DAMAGE);
            looter.updatePack();
            helper.assertTrue(!looter.inPack() && looter.getAttributeValue(Attributes.ATTACK_DAMAGE) == alone, "a lone kobold gets no bonus");
            pack = List.of(spawn(helper, ModEntities.KOBOLD_LOOTER.get(), 2), spawn(helper, ModEntities.KOBOLD_SCAVENGER.get(), -2));
            looter.updatePack();
            helper.assertTrue(looter.inPack() && Math.abs(looter.getAttributeValue(Attributes.ATTACK_DAMAGE) - alone * Kobold.PACK_BONUS) < 0.01,
                    "in a pack it hits " + Kobold.PACK_BONUS + "x as hard: " + looter.getAttributeValue(Attributes.ATTACK_DAMAGE));
            pack.get(0).discard();
            looter.updatePack();
            helper.assertTrue(looter.getAttributeValue(Attributes.ATTACK_DAMAGE) == alone, "and loses it when the pack breaks up");

            KoboldScavenger scavenger = (KoboldScavenger) pack.get(1);
            Pig pig = spawn(helper, EntityType.PIG, 6);
            try {
                int before = helper.getLevel().getEntitiesOfClass(ThrownSpear.class, scavenger.getBoundingBox().inflate(4)).size();
                scavenger.performRangedAttack(pig, 1.0f);
                int after = helper.getLevel().getEntitiesOfClass(ThrownSpear.class, scavenger.getBoundingBox().inflate(4)).size();
                helper.assertTrue(after == before + 1, "a scavenger should throw a spear");
                ThrownSpear spear = new ThrownSpear(helper.getLevel(), scavenger, ThrownSpear.DAMAGE);
                float health = pig.getHealth();
                pig.invulnerableTime = 0;
                spear.strike(pig);
                helper.assertTrue(pig.getHealth() <= health - ThrownSpear.DAMAGE + 0.01f, "a spear should hit for " + ThrownSpear.DAMAGE);
                helper.assertTrue(new ThrownSpear(helper.getLevel(), scavenger, (float) (ThrownSpear.DAMAGE * Kobold.PACK_BONUS)).damage() > ThrownSpear.DAMAGE,
                        "a pack's spears hit harder");
            } finally {
                pig.discard();
                helper.getLevel().getEntitiesOfClass(ThrownSpear.class, scavenger.getBoundingBox().inflate(16)).forEach(ThrownSpear::discard);
            }
        } finally {
            looter.discard();
            pack.forEach(Kobold::discard);
        }
        helper.succeed();
    }

    /** Kobolds hold tier 1 dungeons only, led by the Packmaster, and he comes with his pack. */
    @GameTest(template = ANY_TEMPLATE)
    public static void koboldDungeonsAreLedByThePackmaster(GameTestHelper helper) {
        RandomSource random = RandomSource.create(541);
        List<String> withKobolds = List.of(DungeonFactions.UNDEAD, DungeonFactions.KOBOLDS);
        Set<String> tierOne = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            tierOne.add(DungeonFactions.pick(1, random, withKobolds));
            helper.assertTrue(DungeonFactions.pick(2, random, withKobolds).equals(DungeonFactions.UNDEAD), "kobolds hold only tier 1 dungeons");
        }
        helper.assertTrue(tierOne.contains(DungeonFactions.KOBOLDS), "tier 1 dungeons should roll kobolds when they're on");
        helper.assertTrue(DungeonBosses.pick(1, DungeonFactions.KOBOLDS, random).equals(DungeonBosses.KOBOLD_PACKMASTER), "the kobolds' boss is the Packmaster");
        Set<String> mobs = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            SpawnerMobs.pick(helper.getLevel(), random, DungeonFactions.mobs(DungeonFactions.KOBOLDS), 1)
                    .ifPresent(type -> mobs.add(BuiltInRegistries.ENTITY_TYPE.getKey(type.value()).getPath()));
        }
        helper.assertTrue(mobs.equals(Set.of("kobold_looter", "kobold_scavenger")), "kobold spawners spawn looters and scavengers, got " + mobs);

        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1)).above(100);
        LivingEntity boss = DungeonBosses.spawn(helper.getLevel(), pos, 1, 0, DungeonBosses.KOBOLD_PACKMASTER);
        List<Kobold> pack = helper.getLevel().getEntitiesOfClass(Kobold.class, boss.getBoundingBox().inflate(8), kobold -> kobold != boss);
        try {
            helper.assertTrue(boss instanceof KoboldPackmaster && boss.getMaxHealth() == KoboldPackmaster.MAX_HEALTH, "the Packmaster should be a big kobold");
            helper.assertTrue(DungeonBosses.isBoss(boss) && boss.getMainHandItem().is(Items.IRON_PICKAXE), "he's a boss with an iron pickaxe");
            helper.assertTrue(pack.size() == 4, "he comes with two looters and two scavengers, got " + pack.size());
            KoboldPackmaster packmaster = (KoboldPackmaster) boss;
            helper.assertTrue(packmaster.inPack(), "with his pack around him, he fights as a pack");
            packmaster.rally();
            List<Kobold> after = helper.getLevel().getEntitiesOfClass(Kobold.class, boss.getBoundingBox().inflate(8), kobold -> kobold != boss);
            helper.assertTrue(packmaster.hasRallied() && after.size() == pack.size() + KoboldPackmaster.RALLY, "his call brings " + KoboldPackmaster.RALLY + " more looters");
            pack = after;
        } finally {
            boss.discard();
            pack.forEach(Kobold::discard);
        }
        helper.succeed();
    }
}
