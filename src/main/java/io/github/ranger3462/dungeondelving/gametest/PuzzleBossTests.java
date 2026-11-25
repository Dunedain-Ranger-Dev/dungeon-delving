package io.github.ranger3462.dungeondelving.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonProgress;
import io.github.ranger3462.dungeondelving.dungeon.challenge.PuzzleBosses;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The puzzle bosses (DD-58): the Treasure Vault (any faction, tier 2) and the Undead Dwarf Archivist (undead, tier 1,
 * with Iron's Spells), and the stock puzzle they get without an arena of their own.
 */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class PuzzleBossTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    private static boolean irons() {
        return ModList.get().isLoaded("irons_spellbooks");
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void puzzleBossesTurnUpInPlaceOfOthers(GameTestHelper helper) {
        RandomSource random = RandomSource.create(4);
        Set<String> orcTierTwo = new HashSet<>();
        Set<String> undeadTierOne = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            orcTierTwo.add(DungeonBosses.pick(2, DungeonFactions.ORCS, random));
            undeadTierOne.add(DungeonBosses.pick(1, DungeonFactions.UNDEAD, random));
        }
        helper.assertTrue(orcTierTwo.contains(PuzzleBosses.TREASURE_VAULT), "any faction's tier 2 dungeons can get the Treasure Vault, got " + orcTierTwo);
        helper.assertFalse(DungeonBosses.guards(DungeonFactions.KOBOLDS, 2), "the vault doesn't give kobolds a tier 2 of their own");
        if (irons()) {
            helper.assertTrue(undeadTierOne.contains(PuzzleBosses.ARCHIVIST) && undeadTierOne.contains(DungeonBosses.ZOMBIE_LORD),
                    "undead tier 1 dungeons get the Zombie Lord or the Archivist, got " + undeadTierOne);
        }
        helper.succeed();
    }

    /** A run in a region no real run uses, with its boss spot in this test's own arena. */
    private static DungeonProgress run(GameTestHelper helper, int region, int tier, String boss, BlockPos spawn) {
        DungeonProgress progress = new DungeonProgress(tier, boss, 0, List.of(), spawn).withFaction(DungeonFactions.UNDEAD);
        DungeonRegions.get().startProgress(region, progress);
        return progress;
    }

    /** Sets the stock puzzle's levers to these, one bit each. */
    private static void setLevers(ServerLevel level, CompoundTag puzzle, int up) {
        long[] levers = puzzle.getLongArray("levers");
        for (int i = 0; i < levers.length; i++) {
            BlockPos lever = BlockPos.of(levers[i]);
            level.setBlock(lever, level.getBlockState(lever).setValue(BlockStateProperties.POWERED, (up & (1 << i)) != 0), Block.UPDATE_ALL);
        }
    }

    @GameTest(template = ANY_TEMPLATE, timeoutTicks = 60)
    public static void theVaultOpensForTheRightAnswerAndCallsGuardsForAWrongOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = TestArena.away(helper, 8, 100);
        TestArena.load(level, spawn);
        int region = 9801;
        DungeonProgress progress = run(helper, region, 2, PuzzleBosses.TREASURE_VAULT, spawn);
        helper.assertTrue(DungeonBosses.spawn(level, spawn, 2, region, PuzzleBosses.TREASURE_VAULT) == null, "the vault isn't a mob");
        CompoundTag puzzle = progress.bossPuzzle();
        helper.assertTrue(puzzle.getLongArray("levers").length == PuzzleBosses.levers(2), "a tier 2 stock puzzle has " + PuzzleBosses.levers(2) + " levers");
        BlockPos lectern = BlockPos.of(puzzle.getLongArray("levers")[0]).below().offset(-2, 0, 0);
        helper.assertTrue(level.getBlockEntity(lectern) instanceof LecternBlockEntity book
                && book.getBook().has(DataComponents.WRITTEN_BOOK_CONTENT), "the lectern should hold the clue");
        int answer = puzzle.getInt("answer");
        setLevers(level, puzzle, ~answer & ((1 << PuzzleBosses.levers(2)) - 1));
        PuzzleBosses.answer(level, region, puzzle);
        helper.assertFalse(puzzle.getBoolean("solved"), "a wrong answer shouldn't open the vault");
        setLevers(level, puzzle, answer);
        PuzzleBosses.answer(level, region, puzzle);
        helper.assertTrue(puzzle.getBoolean("solved"), "the right answer should open the vault");
        helper.assertTrue(level.getBlockEntity(spawn) instanceof ChestBlockEntity, "the vault's chest should be where the boss would stand");
        // The guards the wrong answer called show up in searches once the arena's chunk has loaded them, a few ticks on.
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getEntitiesOfClass(Mob.class, new AABB(spawn).inflate(8)).size() >= 1, "a wrong answer should call the vault's guards");
            level.getEntitiesOfClass(Mob.class, new AABB(spawn).inflate(16)).forEach(Entity::discard);
            TestArena.unload(level, spawn);
        });
    }

    @GameTest(template = ANY_TEMPLATE, timeoutTicks = 60)
    public static void theArchivistWaitsThenFightsWeakenedOrAngered(GameTestHelper helper) {
        if (!irons()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos spawn = TestArena.away(helper, 9, 100);
        TestArena.load(level, spawn);
        helper.runAfterDelay(5, () -> {
        try {
            // Solved: he fights on, weakened, with his better loot.
            DungeonProgress solved = run(helper, 9802, 1, PuzzleBosses.ARCHIVIST, spawn);
            Mob humbled = (Mob) DungeonBosses.spawn(level, spawn, 1, 9802, PuzzleBosses.ARCHIVIST);
            helper.assertTrue(humbled.isNoAi() && PuzzleBosses.WAITING.equals(humbled.getPersistentData().getString(PuzzleBosses.STATE)),
                    "the Archivist should wait at first");
            float full = humbled.getMaxHealth();
            setLevers(level, solved.bossPuzzle(), solved.bossPuzzle().getInt("answer"));
            PuzzleBosses.answer(level, 9802, solved.bossPuzzle());
            helper.assertTrue(!humbled.isNoAi() && PuzzleBosses.HUMBLED.equals(humbled.getPersistentData().getString(PuzzleBosses.STATE)),
                    "solving the puzzle should humble him into fighting");
            helper.assertTrue(humbled.getMaxHealth() < full, "humbled, he should be weaker");
            helper.assertTrue((PuzzleBosses.ARCHIVIST + "_solved").equals(humbled.getPersistentData().getCompound(DungeonBosses.BOSS_TAG)
                    .getString(DungeonBosses.LOOT_OVERRIDE)), "humbled, he should drop his better loot");
            humbled.discard();
            level.getEntitiesOfClass(Mob.class, new AABB(spawn).inflate(16)).forEach(Entity::discard);

            // Failed: he fights at full strength.
            DungeonProgress failed = run(helper, 9803, 1, PuzzleBosses.ARCHIVIST, spawn);
            Mob angered = (Mob) DungeonBosses.spawn(level, spawn, 1, 9803, PuzzleBosses.ARCHIVIST);
            setLevers(level, failed.bossPuzzle(), ~failed.bossPuzzle().getInt("answer") & ((1 << PuzzleBosses.levers(1)) - 1));
            PuzzleBosses.answer(level, 9803, failed.bossPuzzle());
            helper.assertTrue(!angered.isNoAi() && PuzzleBosses.ANGERED.equals(angered.getPersistentData().getString(PuzzleBosses.STATE))
                    && angered.getMaxHealth() == full, "a wrong answer should wake him at full strength");
            angered.discard();

            // Attacked: he fights at full strength too.
            run(helper, 9804, 1, PuzzleBosses.ARCHIVIST, spawn);
            LivingEntity attacked = DungeonBosses.spawn(level, spawn, 1, 9804, PuzzleBosses.ARCHIVIST);
            FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "impatient"));
            attacked.hurt(level.damageSources().playerAttack(player), 1.0f);
            helper.assertTrue(PuzzleBosses.ANGERED.equals(attacked.getPersistentData().getString(PuzzleBosses.STATE)), "attacking him should wake him");
            attacked.discard();
        } finally {
            level.getEntitiesOfClass(Mob.class, new AABB(spawn).inflate(16)).forEach(Entity::discard);
            TestArena.unload(level, spawn);
        }
        helper.succeed();
        });
    }
}
