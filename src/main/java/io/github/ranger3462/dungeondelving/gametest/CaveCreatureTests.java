package io.github.ranger3462.dungeondelving.gametest;

import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.integration.iceandfire.IafDungeons;
import io.github.ranger3462.dungeondelving.integration.irons.SpiderQueen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The cave creatures (spiders, trolls, mythical beasts) and the kobolds' dragons (DD-58): each faction's boss at each
 * tier, and how the bosses come out. The spiders' bosses need Iron's Spells, the rest Ice and Fire; without them, the
 * checks that need them are skipped.
 */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class CaveCreatureTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    private static boolean irons() {
        return ModList.get().isLoaded("irons_spellbooks");
    }

    private static boolean iceAndFire() {
        return ModList.get().isLoaded("iceandfire");
    }

    private static String id(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    /** A spot in this test's own arena, far from the test grid ({@link TestArena}), loaded until {@link #clear}. */
    private static BlockPos arena(GameTestHelper helper, int slot) {
        BlockPos pos = TestArena.away(helper, slot, 100);
        TestArena.load(helper.getLevel(), pos);
        return pos;
    }

    /** Removes the boss and anything that came with it, and lets the arena unload. */
    private static void clear(ServerLevel level, BlockPos pos) {
        level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(24)).forEach(Entity::discard);
        TestArena.unload(level, pos);
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void eachCaveFactionHasItsBossByTier(GameTestHelper helper) {
        RandomSource random = RandomSource.create(3);
        if (irons()) {
            helper.assertTrue(DungeonBosses.pick(1, DungeonFactions.SPIDERS, random).equals("spider_queen"), "spiders, tier 1: the Spider Queen");
            helper.assertTrue(DungeonBosses.pick(2, DungeonFactions.SPIDERS, random).equals("cave_spider_queen"), "spiders, tier 2: the Cave Spider Queen");
            helper.assertTrue(DungeonBosses.pick(3, DungeonFactions.SPIDERS, random).equals("ice_spider_queen"), "spiders, tier 3: the Ice Spider Queen");
            helper.assertFalse(DungeonBosses.guards(DungeonFactions.SPIDERS, 4), "spiders don't hold tier 4");
        }
        if (iceAndFire()) {
            helper.assertTrue(DungeonBosses.pick(2, DungeonFactions.FOREST_TROLLS, random).equals("forest_troll_king"), "forest trolls, tier 2");
            helper.assertTrue(DungeonBosses.pick(3, DungeonFactions.FROST_TROLLS, random).equals("frost_troll_king"), "frost trolls, tier 3");
            helper.assertTrue(DungeonBosses.pick(3, DungeonFactions.MOUNTAIN_TROLLS, random).equals("mountain_troll_king"), "mountain trolls, tier 3");
            helper.assertTrue(DungeonBosses.pick(3, DungeonFactions.MYTHICAL_BEASTS, random).equals("hydra"), "mythical beasts, tier 3: the Hydra");
            helper.assertTrue(DungeonBosses.pick(3, DungeonFactions.KOBOLDS, random).equals("wyrmling_nest")
                    && DungeonBosses.pick(4, DungeonFactions.KOBOLDS, random).equals("wyrmling_nest"), "kobolds, tiers 3 and 4: the Wyrmling Nest");
            helper.assertTrue(DungeonBosses.pick(1, DungeonFactions.KOBOLDS, random).equals(DungeonBosses.KOBOLD_PACKMASTER), "kobolds, tier 1: still the Packmaster");
            helper.assertFalse(DungeonBosses.guards(DungeonFactions.KOBOLDS, 2), "kobolds don't hold tier 2");
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void tierThreeAndFourKoboldsKeepHatchlings(GameTestHelper helper) {
        for (String dragon : List.of("fire_dragon", "ice_dragon", "lightning_dragon")) {
            helper.assertTrue(DungeonFactions.KOBOLD_DEFAULTS.stream().anyMatch(entry -> entry.startsWith("iceandfire:" + dragon + "=") && entry.endsWith("@3-4")),
                    "tier 3 and 4 kobolds should keep " + dragon + "s");
        }
        helper.assertTrue(DungeonFactions.KOBOLD_DEFAULTS.contains("dungeondelving:kobold_looter=60@1"), "tier 1 kobolds are unchanged");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE, timeoutTicks = 100)
    public static void theNestGuardianSendsHatchlingsInWaves(GameTestHelper helper) {
        if (!iceAndFire()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos pos = arena(helper, 3);
        LivingEntity guardian = DungeonBosses.spawn(level, pos, 3, -1, "wyrmling_nest");
        helper.assertTrue(id(guardian).endsWith("_dragon") && IafDungeons.dragonAge(guardian) == IafDungeons.GUARDIAN_DAYS_T3,
                "the guardian should be a young dragon, not " + id(guardian) + " aged " + IafDungeons.dragonAge(guardian));
        helper.assertTrue(guardian.getMaxHealth() >= 120.0f, "the guardian should have a boss's health, not " + guardian.getMaxHealth());
        helper.assertTrue(IafDungeons.broodSize(guardian.getUUID()) == 0, "the nest starts with no wave out");
        IafDungeons.sendWave(level, guardian.getUUID());
        helper.assertTrue(IafDungeons.broodSize(guardian.getUUID()) == IafDungeons.WAVE_SIZE, "a wave should send " + IafDungeons.WAVE_SIZE + " hatchlings");
        // The arena's entities show up in searches once its chunk has loaded them, a few ticks on.
        helper.succeedWhen(() -> {
            List<LivingEntity> brood = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(16),
                    mob -> mob != guardian && IafDungeons.dragonAge(mob) >= 0);
            helper.assertTrue(brood.size() >= IafDungeons.WAVE_SIZE + 3, "the retinue and the wave should be about, found " + brood.size());
            helper.assertTrue(brood.stream().allMatch(mob -> IafDungeons.dragonAge(mob) < IafDungeons.GUARDIAN_DAYS_T3),
                    "the brood should be younger than the guardian");
            clear(level, pos);
        });
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void trollKingsAreTheirFactionsKind(GameTestHelper helper) {
        if (!iceAndFire()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos pos = arena(helper, 4);
        try {
            for (String[] king : new String[][] { { "forest_troll_king", "forest", "2" }, { "frost_troll_king", "frost", "3" }, { "mountain_troll_king", "mountain", "3" } }) {
                LivingEntity troll = DungeonBosses.spawn(level, pos, Integer.parseInt(king[2]), -1, king[0]);
                helper.assertTrue(id(troll).equals("iceandfire:troll") && king[1].equals(IafDungeons.trollKindName(troll)),
                        king[0] + " should be a " + king[1] + " troll, not " + IafDungeons.trollKindName(troll));
                helper.assertTrue(troll.getMaxHealth() >= 200.0f && troll.getScale() > 1.2f, king[0] + " should be bigger and tougher than a troll");
                helper.assertTrue(troll.hasCustomName(), king[0] + " should be named");
                troll.discard();
            }
            helper.assertTrue("frost".equals(IafDungeons.trollKind(DungeonFactions.FROST_TROLLS).getName()) && IafDungeons.trollKind(DungeonFactions.KOBOLDS) == null,
                    "dungeon trolls take their faction's kind");
            LivingEntity hydra = DungeonBosses.spawn(level, pos, 3, -1, "hydra");
            helper.assertTrue(id(hydra).equals("iceandfire:hydra") && hydra.getMaxHealth() == 250.0f, "the Hydra should be Ice and Fire's, with 250 health");
        } finally {
            clear(level, pos);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void spiderQueensCastTheirOwnSpells(GameTestHelper helper) {
        if (!irons()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos pos = arena(helper, 5);
        try {
            LivingEntity spider = DungeonBosses.spawn(level, pos, 1, -1, "spider_queen");
            LivingEntity cave = DungeonBosses.spawn(level, pos, 2, -1, "cave_spider_queen");
            LivingEntity ice = DungeonBosses.spawn(level, pos, 3, -1, "ice_spider_queen");
            helper.assertTrue(spider instanceof SpiderQueen queen && queen.attackSpells().isEmpty(), "the Spider Queen casts nothing");
            helper.assertTrue(cave instanceof SpiderQueen queen && queen.attackSpells().size() == 2
                    && queen.attackSpells().stream().allMatch(spell -> spell.getSpellId().contains("poison") || spell.getSpellId().contains("acid")),
                    "the Cave Spider Queen casts poison");
            helper.assertTrue(ice instanceof SpiderQueen queen && !queen.attackSpells().isEmpty()
                    && queen.attackSpells().stream().noneMatch(spell -> spell.getSpellId().contains("poison")), "the Ice Spider Queen keeps the ice spider's spells");
            helper.assertTrue(spider.getMaxHealth() == 60.0f && cave.getMaxHealth() == 110.0f && ice.getMaxHealth() == 200.0f,
                    "each queen should be tougher than the last");
            helper.assertTrue(ice.getScale() > 1.3f, "the Ice Spider Queen should be bigger than an ice spider");
        } finally {
            clear(level, pos);
        }
        helper.succeed();
    }
}
