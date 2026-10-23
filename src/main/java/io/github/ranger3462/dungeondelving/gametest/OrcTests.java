package io.github.ranger3462.dungeondelving.gametest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.SpawnerMobs;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.entity.Orc;
import io.github.ranger3462.dungeondelving.entity.OrcBosses;
import io.github.ranger3462.dungeondelving.entity.ThrownAxe;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the orcs (DD-74): their tiers, their gear, the raider's thrown axe, the rampager's rage and the bosses. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class OrcTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void eachTierHasItsOwnOrcs(GameTestHelper helper) {
        boolean irons = ModList.get().isLoaded("irons_spellbooks");
        Map<Integer, Set<String>> expected = Map.of(
                1, Set.of("orc_scrapper", "orc_raider"),
                2, irons ? Set.of("orc_raider", "orc_veteran", "orc_doomsayer") : Set.of("orc_raider", "orc_veteran"),
                3, irons ? Set.of("orc_veteran", "orc_vagabond", "orc_doomsayer") : Set.of("orc_veteran", "orc_vagabond"),
                4, irons ? Set.of("orc_vagabond", "orc_rampager", "orc_shaman") : Set.of("orc_vagabond", "orc_rampager"));
        RandomSource random = RandomSource.create(74);
        for (int tier = 1; tier <= 4; tier++) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 2000; i++) {
                EntityType<?> type = SpawnerMobs.pick(helper.getLevel(), random, DungeonFactions.ORC_DEFAULTS, tier).orElseThrow().value();
                helper.assertTrue(type.is(OrcBosses.ORCS), "orc dungeons should only spawn orcs, got " + type.getDescriptionId());
                seen.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());
            }
            helper.assertTrue(seen.equals(expected.get(tier)), "tier " + tier + " orcs should be " + expected.get(tier) + ", got " + seen);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void orcsAreBigAndGeared(GameTestHelper helper) {
        Orc scrapper = spawn(helper, ModEntities.ORC_SCRAPPER.get());
        Orc veteran = spawn(helper, ModEntities.ORC_VETERAN.get());
        Orc vagabond = spawn(helper, ModEntities.ORC_VAGABOND.get());
        Orc raider = spawn(helper, ModEntities.ORC_RAIDER.get());
        try {
            helper.assertTrue(scrapper.getBbHeight() > 1.8f, "orcs should be taller than a player");
            helper.assertTrue(scrapper.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) == 20.0,
                    "scrappers should have 20 base health");
            helper.assertTrue(scrapper.getMainHandItem().is(Items.STONE_AXE), "scrappers should carry a stone axe");
            helper.assertTrue(veteran.getMainHandItem().is(Items.IRON_AXE), "veterans should carry an iron axe");
            helper.assertTrue(veteran.getItemBySlot(EquipmentSlot.CHEST).is(Items.CHAINMAIL_CHESTPLATE), "veterans should wear chainmail");
            helper.assertTrue(vagabond.getMainHandItem().is(Items.CROSSBOW), "vagabonds should carry a crossbow");
            helper.assertTrue(raider.getType().is(OrcBosses.ORCS) && vagabond.getType().is(OrcBosses.ORCS), "every orc should be tagged");
        } finally {
            scrapper.discard();
            veteran.discard();
            vagabond.discard();
            raider.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void raidersThrowAxes(GameTestHelper helper) {
        Orc raider = spawn(helper, ModEntities.ORC_RAIDER.get());
        Pig pig = EntityType.PIG.create(helper.getLevel());
        BlockPos pos = raider.blockPosition().east(6);
        pig.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        helper.getLevel().addFreshEntity(pig);
        try {
            int before = helper.getLevel().getEntitiesOfClass(ThrownAxe.class, raider.getBoundingBox().inflate(4)).size();
            ((net.minecraft.world.entity.monster.RangedAttackMob) raider).performRangedAttack(pig, 1.0f);
            int after = helper.getLevel().getEntitiesOfClass(ThrownAxe.class, raider.getBoundingBox().inflate(4)).size();
            helper.assertTrue(after == before + 1, "a raider should throw an axe");

            ThrownAxe axe = new ThrownAxe(helper.getLevel(), raider);
            float health = pig.getHealth();
            pig.invulnerableTime = 0;
            axe.strike(pig);
            helper.assertTrue(pig.getHealth() <= health - ThrownAxe.DAMAGE + 0.01f, "a thrown axe should hit for " + ThrownAxe.DAMAGE);
        } finally {
            raider.discard();
            pig.discard();
            helper.getLevel().getEntitiesOfClass(ThrownAxe.class, raider.getBoundingBox().inflate(16)).forEach(ThrownAxe::discard);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void rampagersRageBelowHalfHealth(GameTestHelper helper) {
        Orc.Rampager rampager = spawn(helper, ModEntities.ORC_RAMPAGER.get());
        try {
            rampager.aiStep();
            helper.assertFalse(rampager.isRaging(), "a healthy rampager shouldn't rage");
            rampager.setHealth(rampager.getMaxHealth() * 0.4f);
            rampager.aiStep();
            helper.assertTrue(rampager.isRaging(), "a rampager below half health should rage");
            helper.assertTrue(rampager.hasEffect(MobEffects.DAMAGE_BOOST) && rampager.hasEffect(MobEffects.MOVEMENT_SPEED),
                    "a raging rampager should have Strength and Speed");
        } finally {
            rampager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void theWarchiefRalliesAndSlams(GameTestHelper helper) {
        OrcBosses.Warchief warchief = (OrcBosses.Warchief) OrcBosses.warchief(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)).above(100), 4);
        helper.getLevel().addFreshEntity(warchief);
        Orc scrapper = spawn(helper, ModEntities.ORC_SCRAPPER.get());
        Pig pig = EntityType.PIG.create(helper.getLevel());
        pig.moveTo(warchief.getX() + 2, warchief.getY(), warchief.getZ());
        helper.getLevel().addFreshEntity(pig);
        try {
            helper.assertTrue(warchief.getMaxHealth() >= OrcBosses.Warchief.MAX_HEALTH, "the Warchief should have at least 260 health");
            int rallied = warchief.warCry(helper.getLevel());
            helper.assertTrue(rallied >= 1 && scrapper.hasEffect(MobEffects.DAMAGE_BOOST), "the war cry should give nearby orcs Strength");
            helper.assertTrue(warchief.hasEffect(MobEffects.DAMAGE_RESISTANCE), "the war cry should harden the Warchief");
            float health = pig.getHealth();
            int hit = warchief.slam(helper.getLevel());
            helper.assertTrue(hit >= 1 && pig.getHealth() < health, "the slam should hurt what's next to him");
            helper.assertFalse(scrapper.getHealth() < scrapper.getMaxHealth(), "the slam shouldn't hurt orcs");
        } finally {
            warchief.discard();
            scrapper.discard();
            pig.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void orcsHaveABossForEveryTier(GameTestHelper helper) {
        boolean irons = ModList.get().isLoaded("irons_spellbooks");
        RandomSource random = RandomSource.create(4);
        Map<Integer, String> expected = new HashMap<>(Map.of(1, DungeonBosses.ORC_COMMANDER, 2, DungeonBosses.ORC_CHIEFTAIN, 4, DungeonBosses.ORC_WARCHIEF));
        if (irons) {
            expected.put(3, "orc_lorekeeper");
        }
        for (var entry : expected.entrySet()) {
            String boss = DungeonBosses.pick(entry.getKey(), DungeonFactions.ORCS, random);
            helper.assertTrue(boss.equals(entry.getValue()), "tier " + entry.getKey() + " orc dungeons should be guarded by " + entry.getValue()
                    + ", got " + boss);
            helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(DungeonBosses.lootTable(boss, entry.getKey()))
                    != net.minecraft.world.level.storage.loot.LootTable.EMPTY, boss + " should have a tier " + entry.getKey() + " loot table");
        }
        helper.assertTrue(DungeonBosses.guards(DungeonFactions.ORCS, 3) == irons, "orcs should only hold tier 3 dungeons with Iron's Spells (the Lorekeeper)");
        for (var entry : expected.entrySet()) {
            Mob boss = (Mob) DungeonBosses.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)).above(100), entry.getKey(), 0, entry.getValue());
            try {
                helper.assertTrue(boss.getType().is(OrcBosses.ORCS), entry.getValue() + " should be an orc");
                helper.assertTrue(boss.hasCustomName(), entry.getValue() + " should be named");
            } finally {
                boss.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void thrownAxesLodgeInBlocks(GameTestHelper helper) {
        Orc raider = spawn(helper, ModEntities.ORC_RAIDER.get());
        ThrownAxe axe = new ThrownAxe(helper.getLevel(), raider);
        helper.getLevel().addFreshEntity(axe);
        try {
            axe.setDeltaMovement(0.5, 0.0, 0.0);
            axe.lodge(raider.position().add(2, 0, 0));
            helper.assertTrue(axe.isLodged() && axe.isNoGravity(), "an axe that hits a block should stick in it");
            for (int i = 0; i < ThrownAxe.LODGED_TICKS - 1; i++) {
                axe.tick();
            }
            helper.assertFalse(axe.isRemoved(), "a lodged axe should stay a while");
            helper.assertTrue(axe.getDeltaMovement().lengthSqr() == 0, "a lodged axe shouldn't move");
            axe.tick();
            helper.assertTrue(axe.isRemoved(), "a lodged axe should be gone after " + ThrownAxe.LODGED_TICKS + " ticks");
        } finally {
            axe.discard();
            raider.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void orcBossesBringTheirWarband(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0)).above(100);
        Mob warchief = (Mob) DungeonBosses.spawn(helper.getLevel(), pos, 4, 0, DungeonBosses.ORC_WARCHIEF);
        var near = helper.getLevel().getEntitiesOfClass(Mob.class, warchief.getBoundingBox().inflate(10), mob -> mob != warchief);
        try {
            helper.assertTrue(near.stream().filter(mob -> mob.getType() == ModEntities.ORC_RAMPAGER.get()).count() == 2,
                    "the Warchief should bring 2 rampagers");
            helper.assertTrue(near.stream().filter(mob -> mob.getType() == ModEntities.ORC_VAGABOND.get()).count() == 2,
                    "the Warchief should bring 2 vagabonds");
        } finally {
            warchief.discard();
            near.forEach(Mob::discard);
        }
        helper.succeed();
    }

    private static <T extends Orc> T spawn(GameTestHelper helper, EntityType<T> type) {
        T orc = type.create(helper.getLevel());
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0)).above(100);
        orc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        EventHooks.finalizeMobSpawn(orc, helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        helper.getLevel().addFreshEntity(orc);
        return orc;
    }
}
