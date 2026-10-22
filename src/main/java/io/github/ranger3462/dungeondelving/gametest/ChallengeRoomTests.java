package io.github.ranger3462.dungeondelving.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.block.PuzzleLockBlock;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonKeyItem;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonProgress;
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

/** Checks the puzzle and collection challenge rooms: the Puzzle Lock, the Relic Altar and its Relic Shards. */
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

    private static int count(StructureTemplate template, Block block) {
        return template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).size();
    }
}
