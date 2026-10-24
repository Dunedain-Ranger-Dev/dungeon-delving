package io.github.ranger3462.dungeondelving.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.skill.GatheringPerks;
import io.github.ranger3462.dungeondelving.skill.Perk;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the skill tree perks (DD-70, DD-56): the trees give the tags, and the gathering perks do their jobs. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class PerkTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    /** Every rank of every perk is some tree node's reward, so the code and tools/skill_trees.py agree. */
    @GameTest(template = ANY_TEMPLATE)
    public static void everyPerkIsInATree(GameTestHelper helper) {
        var resources = helper.getLevel().getServer().getResourceManager();
        StringBuilder trees = new StringBuilder();
        resources.listResources("puffish_skills/categories", id -> id.getNamespace().equals(DungeonDelving.MODID) && id.getPath().endsWith("definitions.json"))
                .forEach((id, resource) -> {
                    try (var in = resource.open()) {
                        trees.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        for (Perk perk : Perk.values()) {
            for (int rank = 1; rank <= perk.ranks; rank++) {
                helper.assertTrue(trees.indexOf("\"" + perk.tag(rank) + "\"") >= 0, perk.tag(rank) + " is in a tree");
            }
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void veinMinerTakesTheWholeVein(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = spot(helper);
        ServerPlayer player = player(helper, Items.IRON_PICKAXE);
        try {
            for (int x = 0; x < 5; x++) {
                level.setBlockAndUpdate(start.east(x), Blocks.IRON_ORE.defaultBlockState());
            }
            level.setBlockAndUpdate(start.east(8), Blocks.IRON_ORE.defaultBlockState()); // Not touching the vein.
            player.gameMode.destroyBlock(start);
            helper.assertTrue(level.getBlockState(start.east(4)).is(Blocks.IRON_ORE), "without Vein Miner, the rest of the vein stays");

            player.addTag(Perk.VEIN_MINER.tag(1));
            player.setShiftKeyDown(true);
            player.gameMode.destroyBlock(start.east(1));
            for (int x = 1; x < 5; x++) {
                helper.assertTrue(level.getBlockState(start.east(x)).isAir(), "sneaking with Vein Miner mines the vein, block " + x);
            }
            helper.assertTrue(level.getBlockState(start.east(8)).is(Blocks.IRON_ORE), "but not ore it doesn't touch");
            helper.assertTrue(player.getMainHandItem().getDamageValue() > 1, "each block wears the pickaxe");
        } finally {
            clear(level, start);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void treeFellerFellsTreesNotBuilds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tree = spot(helper);
        BlockPos build = tree.east(6);
        ServerPlayer player = player(helper, Items.IRON_AXE);
        player.addTag(Perk.TREE_FELLER.tag(1));
        player.setShiftKeyDown(true);
        try {
            for (int y = 0; y < 5; y++) {
                level.setBlockAndUpdate(tree.above(y), Blocks.OAK_LOG.defaultBlockState());
                level.setBlockAndUpdate(build.above(y), Blocks.OAK_LOG.defaultBlockState());
            }
            level.setBlockAndUpdate(tree.above(5), Blocks.OAK_LEAVES.defaultBlockState()); // Natural leaves: not persistent.
            player.gameMode.destroyBlock(tree);
            for (int y = 1; y < 5; y++) {
                helper.assertTrue(level.getBlockState(tree.above(y)).isAir(), "the tree's log " + y + " is felled");
            }
            player.gameMode.destroyBlock(build);
            helper.assertTrue(level.getBlockState(build.above(1)).is(Blocks.OAK_LOG), "a pillar of logs with no leaves isn't a tree");
        } finally {
            clear(level, tree);
        }
        helper.succeed();
    }

    /** Forester only replants trees: a log on dirt that's part of a tree with natural leaves, not a pillar. */
    @GameTest(template = ANY_TEMPLATE)
    public static void foresterOnlyReplantsTrees(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tree = spot(helper);
        BlockPos pillar = tree.east(6);
        try {
            for (BlockPos base : new BlockPos[] { tree, pillar }) {
                level.setBlockAndUpdate(base.below(), Blocks.DIRT.defaultBlockState());
                for (int y = 0; y < 5; y++) {
                    level.setBlockAndUpdate(base.above(y), Blocks.OAK_LOG.defaultBlockState());
                }
            }
            level.setBlockAndUpdate(tree.above(5), Blocks.OAK_LEAVES.defaultBlockState());
            helper.assertTrue(GatheringPerks.isTreeBase(level, tree, level.getBlockState(tree)), "a tree's bottom log is a tree base");
            helper.assertFalse(GatheringPerks.isTreeBase(level, pillar, level.getBlockState(pillar)), "a pillar of logs isn't");
            helper.assertFalse(GatheringPerks.isTreeBase(level, tree.above(), level.getBlockState(tree.above())), "nor is a log higher up the tree");
        } finally {
            clear(level, tree);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void replanterPlantsTheCropAgain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos crop = spot(helper);
        ServerPlayer player = player(helper, Items.AIR);
        player.addTag(Perk.REPLANTER.tag(1));
        level.setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState());
        level.setBlockAndUpdate(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        player.gameMode.destroyBlock(crop);
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getBlockState(crop).is(Blocks.WHEAT) && ((CropBlock) Blocks.WHEAT).getAge(level.getBlockState(crop)) == 0,
                    "the harvested wheat is planted again");
            clear(level, crop);
        });
    }

    /** A spot high above the test, in open air (crops need the light). */
    private static BlockPos spot(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(1, 2, 1)).above(80);
    }

    private static ServerPlayer player(GameTestHelper helper, net.minecraft.world.item.Item tool) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "perk_tester"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        return player;
    }

    /** Clears the blocks and drops around a spot. */
    private static void clear(ServerLevel level, BlockPos spot) {
        for (BlockPos pos : BlockPos.betweenClosed(spot.offset(-1, -2, -1), spot.offset(9, 6, 1))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(spot).inflate(12)).forEach(ItemEntity::discard);
    }
}
