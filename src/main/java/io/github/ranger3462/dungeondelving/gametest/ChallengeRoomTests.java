package io.github.ranger3462.dungeondelving.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.block.PuzzleLockBlock;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonKeyItem;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonProgress;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonWaves;
import io.github.ranger3462.dungeondelving.dungeon.challenge.WaveRoom;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the puzzle, collection and wave defense challenge rooms. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class ChallengeRoomTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void aPuzzleLockIsSolvedOnceWhenPowered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lock = helper.absolutePos(new BlockPos(1, 1, 1)).above(120);
        try {
            level.setBlock(lock, ModRegistries.PUZZLE_LOCK.get().defaultBlockState(), Block.UPDATE_ALL);
            helper.assertFalse(level.getBlockState(lock).getValue(PuzzleLockBlock.SOLVED), "a new lock should be unsolved");
            level.setBlock(lock.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(level.getBlockState(lock).getValue(PuzzleLockBlock.SOLVED), "powering the lock should solve it");
            level.setBlock(lock.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(level.getBlockState(lock).getValue(PuzzleLockBlock.SOLVED), "a solved lock should stay solved when the power goes");
        } finally {
            level.setBlock(lock.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(lock, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void anAltarTakesOnlyTheRelicsItNeeds(GameTestHelper helper) {
        DungeonProgress progress = new DungeonProgress(1, 2, List.of(), BlockPos.ZERO);
        long room = BlockPos.ZERO.asLong();
        helper.assertTrue(progress.relicsLeft(room) == 0, "a room without an altar needs nothing");
        progress.addAltar(room);
        progress.addAltar(room);
        helper.assertTrue(progress.relicsLeft(room) == DungeonProgress.relicsFor(1) && DungeonProgress.relicsFor(1) == 4,
                "a tier 1 altar needs 4 shards, even with two altars in the room");
        helper.assertTrue(progress.offerRelics(room, 3) == 3 && progress.relicsLeft(room) == 1, "three shards should leave one");
        helper.assertTrue(progress.offerRelics(room, 5) == 1 && progress.relicsLeft(room) == 0, "the altar should only take the last one it needs");
        helper.assertTrue(progress.offerRelics(room, 2) == 0, "a finished altar takes nothing");
        helper.assertTrue(DungeonProgress.relicsFor(4) == 7, "a tier 4 altar needs 7 shards");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void altarProgressIsSaved(GameTestHelper helper) {
        DungeonRegions regions = new DungeonRegions();
        DungeonProgress progress = new DungeonProgress(2, 4, List.of(), BlockPos.ZERO);
        progress.addAltar(42L);
        progress.offerRelics(42L, 2);
        regions.startProgress(5, progress);
        DungeonRegions loaded = DungeonRegions.load(regions.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(loaded.progress(5).orElseThrow().relicsLeft(42L) == DungeonProgress.relicsFor(2) - 2, "relics still needed should be saved");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void relicShardsBelongToTheirDungeonAndDontLeaveIt(GameTestHelper helper) {
        helper.assertTrue(DungeonKeyItem.isRelicFrom(DungeonKeyItem.relicFor(3), 3), "a shard belongs to its own dungeon");
        helper.assertFalse(DungeonKeyItem.isRelicFrom(DungeonKeyItem.relicFor(3), 4), "a shard doesn't count in another dungeon");
        helper.assertFalse(DungeonKeyItem.opens(DungeonKeyItem.relicFor(3), 3), "a shard isn't a key");
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "relic_tester"));
        player.getInventory().setItem(0, DungeonKeyItem.relicFor(3).copyWithCount(5));
        player.getInventory().setItem(1, DungeonKeyItem.forRegion(3));
        helper.assertTrue(DungeonKeyItem.removeAll(player) == 2 && player.getInventory().isEmpty(), "shards should be taken away with the keys");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void placeholderChallengeRoomsHaveTheirBlocks(GameTestHelper helper) {
        var structures = helper.getLevel().getStructureManager();
        StructureTemplate puzzle = structures.get(DungeonPieces.id("challenge_puzzle_placeholder")).orElseThrow();
        helper.assertTrue(count(puzzle, ModRegistries.PUZZLE_LOCK.get()) == 1 && count(puzzle, Blocks.LEVER) == 1,
                "the puzzle room should have one lock and one lever");
        StructureTemplate relics = structures.get(DungeonPieces.id("challenge_relics_placeholder")).orElseThrow();
        helper.assertTrue(count(relics, ModRegistries.RELIC_ALTAR.get()) == 1 && count(relics, ModRegistries.MOB_MARKER.get()) == 6,
                "the relic room should have an altar and six mob markers");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void waveRoomsGrowWithTier(GameTestHelper helper) {
        helper.assertTrue(WaveRoom.wavesFor(1) == 3 && WaveRoom.wavesFor(2) == 3 && WaveRoom.wavesFor(3) == 4 && WaveRoom.wavesFor(4) == 4,
                "tiers 1-2 should have 3 waves, tiers 3-4 four");
        helper.assertTrue(WaveRoom.mobsFor(1, 1) == 3 && WaveRoom.mobsFor(4, 4) == 9, "waves should grow by wave and tier");
        WaveRoom room = new WaveRoom(BlockPos.ZERO, 3);
        helper.assertFalse(room.started(), "a new wave room hasn't started");
        helper.assertTrue(room.advance() && room.advance() && room.advance() && room.lastWave(), "three waves, then the last");
        helper.assertFalse(room.advance(), "there's no fourth wave");
        room.reset();
        helper.assertTrue(!room.started() && room.advance() && room.wave() == 1, "a reset room starts again from wave 1");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void waveRoomsAreSaved(GameTestHelper helper) {
        DungeonRegions regions = new DungeonRegions();
        DungeonProgress progress = new DungeonProgress(3, 6, List.of(), BlockPos.ZERO);
        progress.addWaveRoom(42L, new BlockPos(1, 2, 3));
        WaveRoom room = progress.waveRoom(42L).orElseThrow();
        room.advance();
        room.seal().add(new BlockPos(4, 5, 6));
        regions.startProgress(5, progress);
        DungeonRegions loaded = DungeonRegions.load(regions.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        WaveRoom back = loaded.progress(5).orElseThrow().waveRoom(42L).orElseThrow();
        helper.assertTrue(back.wave() == 1 && back.waves() == 4 && back.drum().equals(new BlockPos(1, 2, 3)) && back.seal().size() == 1,
                "a running wave room should be saved");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void aWaveRoomSealsOnlyItsOpenDoorways(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = helper.absolutePos(BlockPos.ZERO).above(140);
        List<BlockPos> doorways = DungeonWaves.doorways(min);
        helper.assertTrue(doorways.size() == 4 * 30, "four 5x6 doorways, got " + doorways.size());
        try {
            // A cap fills the north doorway; the other three are open.
            doorways.stream().filter(pos -> pos.getZ() == min.getZ()).forEach(pos -> level.setBlock(pos, Blocks.RED_CONCRETE.defaultBlockState(), Block.UPDATE_CLIENTS));
            WaveRoom room = new WaveRoom(min, 3);
            DungeonWaves.seal(level, min, room);
            long sealed = doorways.stream().filter(pos -> level.getBlockState(pos).is(ModRegistries.WAVE_SEAL.get())).count();
            helper.assertTrue(sealed == 90 && room.seal().size() == 90, "the three open doorways should be sealed, sealed " + sealed);
            DungeonWaves.unseal(level, room);
            helper.assertTrue(doorways.stream().noneMatch(pos -> level.getBlockState(pos).is(ModRegistries.WAVE_SEAL.get())), "the seal should come down");
            helper.assertTrue(doorways.stream().filter(pos -> pos.getZ() == min.getZ()).allMatch(pos -> level.getBlockState(pos).is(Blocks.RED_CONCRETE)),
                    "the cap should be left alone");
        } finally {
            doorways.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void placeholderWaveRoomHasADrumAndNoSpawners(GameTestHelper helper) {
        StructureTemplate waves = helper.getLevel().getStructureManager().get(DungeonPieces.id("challenge_waves_placeholder")).orElseThrow();
        helper.assertTrue(count(waves, ModRegistries.WAR_DRUM.get()) == 1 && count(waves, ModRegistries.MOB_MARKER.get()) == 0
                && count(waves, ModRegistries.HUNT_MARKER.get()) == 0, "the wave room should have one drum and no spawners");
        helper.succeed();
    }

    private static int count(StructureTemplate template, Block block) {
        return template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).size();
    }
}
