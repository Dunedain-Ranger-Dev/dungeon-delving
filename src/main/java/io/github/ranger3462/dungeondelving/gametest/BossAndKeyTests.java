package io.github.ranger3462.dungeondelving.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.RoomSpec;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonChallenges;
import io.github.ranger3462.dungeondelving.dungeon.RoomCounts;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonKeyItem;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonProgress;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuilder;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout.Room;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout.RoomKind;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayoutGenerator;
import io.github.ranger3462.dungeondelving.registry.DungeonOre;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the path to the boss: challenge rooms, run-bound keys, the boss door, hunts, the boss and its loot. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class BossAndKeyTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void everyTierHasEnoughKeyRooms(GameTestHelper helper) {
        RandomSource random = RandomSource.create(7);
        for (int tier = 1; tier <= 4; tier++) {
            int keys = DungeonProgress.keysFor(tier);
            helper.assertTrue(keys == tier * 2, "tier " + tier + " should need " + tier * 2 + " keys");
            for (int i = 0; i < 50; i++) {
                int rooms = RoomCounts.forTier(RoomCounts.DEFAULTS, tier).roll(random);
                DungeonLayout layout = DungeonLayoutGenerator.generate(random, rooms, keys);
                long challenges = layout.rooms().values().stream().filter(room -> room.kind() == RoomKind.CHALLENGE).count();
                long bosses = layout.rooms().values().stream().filter(room -> room.kind() == RoomKind.BOSS).count();
                helper.assertTrue(bosses == 1, "a tier " + tier + " dungeon should have one boss room");
                helper.assertTrue(challenges == keys, "a " + rooms + "-room tier " + tier + " dungeon should have " + keys + " key rooms, has " + challenges);
            }
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void bossDoorFillsEveryWayIn(GameTestHelper helper) {
        RandomSource random = RandomSource.create(11);
        for (int i = 0; i < 20; i++) {
            DungeonLayout layout = DungeonLayoutGenerator.generate(random, 10, 4);
            Room boss = layout.rooms().values().stream().filter(room -> room.kind() == RoomKind.BOSS).findFirst().orElseThrow();
            long ways = Direction.Plane.HORIZONTAL.stream().filter(direction -> layout.connected(boss.cell(), direction)).count();
            List<BlockPos> door = DungeonBuilder.bossDoor(layout, BlockPos.ZERO);
            helper.assertTrue(ways >= 1 && door.size() == ways * 30, "the door should fill each of the " + ways + " doorways (5x6), has " + door.size());
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void theDoorOpensWithTheLastKey(GameTestHelper helper) {
        DungeonProgress progress = new DungeonProgress(2, 4, List.of(), BlockPos.ZERO);
        for (int key = 1; key <= 3; key++) {
            helper.assertTrue(progress.useKey() && !progress.doorOpen(), "key " + key + " of 4 shouldn't open the door");
        }
        helper.assertTrue(progress.useKey() && progress.doorOpen(), "the fourth key should open the door");
        helper.assertFalse(progress.useKey(), "an open door shouldn't take more keys");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void aHuntEndsWithTheLastTarget(GameTestHelper helper) {
        DungeonProgress progress = new DungeonProgress(1, 2, List.of(), BlockPos.ZERO);
        long room = BlockPos.ZERO.asLong();
        for (int i = 0; i < 3; i++) {
            progress.addHunter(room);
        }
        helper.assertTrue(progress.hunterKilled(room).equals(Optional.of(2)), "two targets should be left");
        helper.assertTrue(progress.hunterKilled(room).equals(Optional.of(1)), "one target should be left");
        helper.assertTrue(progress.hunterKilled(room).equals(Optional.of(0)), "the last kill should finish the hunt");
        helper.assertTrue(progress.hunterKilled(room).isEmpty(), "a finished hunt shouldn't drop another key");
        helper.assertTrue(progress.hunterKilled(BlockPos.ZERO.above().asLong()).isEmpty(), "untracked rooms give nothing");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void huntTargetsComeOutOfSpawnersOneAtATime(GameTestHelper helper) {
        DungeonProgress progress = new DungeonProgress(1, 2, List.of(), BlockPos.ZERO);
        long room = BlockPos.ZERO.asLong();
        progress.addHunter(room);
        progress.addHunter(room);
        helper.assertTrue(progress.unspawnedHunters(room) == 2 && progress.huntersLeft(room) == 2, "both targets should be waiting to spawn");
        helper.assertTrue(progress.hunterSpawned(room) && progress.hunterSpawned(room), "both targets should be able to spawn");
        helper.assertFalse(progress.hunterSpawned(room), "a room shouldn't spawn more targets than it has");
        helper.assertTrue(progress.huntersLeft(room) == 2, "spawning a target doesn't count as killing it");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void aClearedHuntRoomTradesItsSpawnersForAChest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = helper.absolutePos(BlockPos.ZERO).above(140);
        BlockPos max = min.offset(RoomSpec.ROOM_SIZE - 1, RoomSpec.ROOM_HEIGHT - 1, RoomSpec.ROOM_SIZE - 1);
        try {
            for (BlockPos pos : List.of(min.offset(5, 1, 5), min.offset(20, 1, 9), min.offset(28, 2, 28))) {
                level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            int removed = DungeonChallenges.clearSpawners(level, min, 1);
            int spawners = 0;
            int chests = 0;
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                spawners += level.getBlockState(pos).is(Blocks.SPAWNER) ? 1 : 0;
                chests += level.getBlockState(pos).is(Blocks.CHEST) ? 1 : 0;
            }
            helper.assertTrue(removed == 3 && spawners == 0, "every spawner in the room should be removed");
            helper.assertTrue(chests == 1, "one reward chest should take a spawner's place, found " + chests);
        } finally {
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void progressSurvivesSaveAndLoad(GameTestHelper helper) {
        DungeonRegions regions = new DungeonRegions();
        DungeonProgress progress = new DungeonProgress(3, 6, List.of(new BlockPos(1, 2, 3), new BlockPos(4, 5, 6)), new BlockPos(7, 8, 9));
        progress.useKey();
        progress.useKey();
        progress.addHunter(42L);
        regions.startProgress(5, progress);
        DungeonRegions loaded = DungeonRegions.load(regions.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        DungeonProgress back = loaded.progress(5).orElseThrow();
        helper.assertTrue(back.tier() == 3 && back.keysNeeded() == 6 && back.keysUsed() == 2, "keys should be saved");
        helper.assertTrue(back.door().size() == 2 && new BlockPos(7, 8, 9).equals(back.bossSpawn()), "the door and boss spot should be saved");
        helper.assertTrue(back.huntersLeft(42L) == 1, "hunts in progress should be saved");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void keysOnlyWorkInTheirDungeonAndDontLeaveIt(GameTestHelper helper) {
        ItemStack key = DungeonKeyItem.forRegion(3);
        helper.assertTrue(DungeonKeyItem.opens(key, 3), "a key should open its own dungeon's door");
        helper.assertFalse(DungeonKeyItem.opens(key, 4), "a key shouldn't open another dungeon's door");

        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "dungeon_tester"));
        player.getInventory().setItem(0, DungeonKeyItem.forRegion(3));
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(DungeonKeyItem.forRegion(3), new ItemStack(Items.TORCH))));
        player.getInventory().setItem(1, shulker);
        player.getEnderChestInventory().setItem(0, DungeonKeyItem.forRegion(3));

        int removed = DungeonKeyItem.removeAll(player);

        helper.assertTrue(removed == 3, "every key should be taken away when the run ends, took " + removed);
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "keys in the inventory should be gone");
        ItemContainerContents left = player.getInventory().getItem(1).get(DataComponents.CONTAINER);
        helper.assertTrue(left.nonEmptyStream().noneMatch(stack -> stack.is(ModRegistries.DUNGEON_KEY.get()))
                && left.nonEmptyStream().anyMatch(stack -> stack.is(Items.TORCH)), "keys hidden in a shulker box should be gone, other items kept");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void theBarrowKingScalesWithTier(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0)).above(100);
        LivingEntity low = DungeonBosses.spawn(helper.getLevel(), pos, 1, -1);
        LivingEntity high = DungeonBosses.spawn(helper.getLevel(), pos, 4, -1);
        try {
            helper.assertTrue(DungeonBosses.isBoss(low) && DungeonBosses.isBoss(high), "spawned bosses should be marked as bosses");
            helper.assertTrue(low.getMaxHealth() == 100.0f && high.getMaxHealth() == 250.0f,
                    "boss health should grow with tier, got " + low.getMaxHealth() + " and " + high.getMaxHealth());
        } finally {
            low.discard();
            high.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void bossLootIsBossSpecific(GameTestHelper helper) {
        for (int tier = 1; tier <= 4; tier++) {
            DungeonOre ore = DungeonOre.forTier(tier).orElseThrow();
            boolean crown = false;
            for (int i = 0; i < 60; i++) {
                List<ItemStack> drops = roll(helper, DungeonBosses.lootTable(DungeonBosses.BARROW_KING, tier));
                helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(ore.ingot.get())), "the tier " + tier + " boss should always drop " + ore.key + " ingots");
                crown |= drops.stream().anyMatch(stack -> stack.is(Items.GOLDEN_HELMET) && stack.has(DataComponents.ITEM_NAME));
            }
            helper.assertTrue(crown, "the tier " + tier + " Barrow King should sometimes drop its crown");
        }
        helper.succeed();
    }

    private static List<ItemStack> roll(GameTestHelper helper, ResourceKey<LootTable> key) {
        LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(key);
        if (table == LootTable.EMPTY) {
            throw new IllegalStateException("Missing loot table " + key.location());
        }
        LootParams params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        return new ArrayList<>(table.getRandomItems(params));
    }
}
