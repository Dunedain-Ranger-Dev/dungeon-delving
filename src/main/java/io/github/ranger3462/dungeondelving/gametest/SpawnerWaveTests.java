package io.github.ranger3462.dungeondelving.gametest;

import java.util.ArrayList;
import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers.Rolls;
import io.github.ranger3462.dungeondelving.dungeon.SpawnerWaves;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks that common rooms' spawners send one wave and break (DD-110), and challenge rooms' keep spawning. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class SpawnerWaveTests {
    private static final Rolls TIER_4_SPAWNERS = new Rolls(4, 0, 0, 0, 0, -1, null, 1.0);

    @GameTest(template = "dungeon/common_pillars_placeholder")
    public static void commonRoomSpawnersSendOneWaveAndBreak(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> spawners = resolve(helper, "common_pillars_placeholder");
        helper.assertTrue(!spawners.isEmpty(), "the common room should get spawners");
        for (BlockPos pos : spawners) {
            helper.assertTrue(SpawnerWaves.isWaveSpawner(level, pos), "a common room's spawners are wave spawners");
        }
        BlockPos first = spawners.get(0);
        List<Mob> wave = SpawnerWaves.release(level, first);
        helper.assertTrue(level.getBlockState(first).isAir(), "a wave spawner breaks once it's sent its wave");
        // Pack mobs bring up to three more at tier 4.
        helper.assertTrue(wave.size() >= SpawnerWaves.WAVE_MIN[3] && wave.size() <= SpawnerWaves.WAVE_MAX[3] + 3,
                "a tier 4 wave is " + SpawnerWaves.WAVE_MIN[3] + " to " + SpawnerWaves.WAVE_MAX[3] + " mobs, got " + wave.size());
        helper.assertTrue(SpawnerWaves.release(level, first).isEmpty(), "a broken spawner sends nothing more");
        wave.forEach(Mob::discard);
        helper.succeed();
    }

    @GameTest(template = "dungeon/challenge_hunt_placeholder")
    public static void challengeRoomSpawnersKeepSpawning(GameTestHelper helper) {
        List<BlockPos> spawners = resolve(helper, "challenge_hunt_placeholder");
        helper.assertTrue(!spawners.isEmpty(), "the hunt room should get spawners");
        for (BlockPos pos : spawners) {
            helper.assertFalse(SpawnerWaves.isWaveSpawner(helper.getLevel(), pos), "a challenge room's spawners keep spawning");
        }
        helper.succeed();
    }

    @GameTest(template = "dungeon/common_walls_placeholder", timeoutTicks = 200)
    public static void aPlayerComingNearSetsAWaveOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> spawners = resolve(helper, "common_walls_placeholder");
        helper.assertTrue(!spawners.isEmpty(), "the common room should get spawners");
        BlockPos spawner = spawners.get(0);
        // The spawn event a vanilla spawner fires when a player comes in range (a mock player can't join with the pack's mods).
        SpawnerBlockEntity blockEntity = (SpawnerBlockEntity) level.getBlockEntity(spawner);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.moveTo(spawner.getX() + 1.5, spawner.getY(), spawner.getZ() + 0.5);
        FinalizeSpawnEvent event = EventHooks.finalizeMobSpawnSpawner(zombie, level, level.getCurrentDifficultyAt(spawner), MobSpawnType.SPAWNER,
                null, blockEntity.getSpawner(), true);
        helper.assertTrue(event.isSpawnCancelled(), "the spawner's own spawning is called off");
        zombie.discard();
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(level.getBlockState(spawner).isAir(), "the spawner breaks by the end of the tick");
            List<Mob> wave = level.getEntitiesOfClass(Mob.class, new AABB(spawner).inflate(6));
            helper.assertTrue(!wave.isEmpty(), "its wave is out");
            wave.forEach(Mob::discard);
            helper.succeed();
        });
    }

    /** Resolves a placeholder room's markers where the test placed it, and returns where spawners ended up. */
    private static List<BlockPos> resolve(GameTestHelper helper, String room) {
        ServerLevel level = helper.getLevel();
        StructureTemplate template = level.getStructureManager().get(DungeonPieces.id(room)).orElseThrow();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        StructurePlaceSettings settings = new StructurePlaceSettings();
        List<BlockPos> markers = new ArrayList<>();
        template.filterBlocks(origin, settings, ModRegistries.MOB_MARKER.get()).forEach(info -> markers.add(info.pos()));
        template.filterBlocks(origin, settings, ModRegistries.HUNT_MARKER.get()).forEach(info -> markers.add(info.pos()));
        DungeonMarkers.resolve(level, template, origin, settings, RandomSource.create(7), TIER_4_SPAWNERS, origin);
        return markers.stream().filter(pos -> level.getBlockState(pos).is(Blocks.SPAWNER)).toList();
    }
}
