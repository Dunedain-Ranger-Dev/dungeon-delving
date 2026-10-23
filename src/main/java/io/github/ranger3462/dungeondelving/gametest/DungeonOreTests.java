package io.github.ranger3462.dungeondelving.gametest;

import java.util.List;
import java.util.UUID;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRun;
import io.github.ranger3462.dungeondelving.registry.DungeonOre;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the dungeon ores drop, smelt, and need the right tools, and that runs keep their tier. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class DungeonOreTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void oresDropRawOreAndSmeltIntoIngots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        for (DungeonOre ore : DungeonOre.values()) {
            BlockState state = ore.ore.get().defaultBlockState();
            List<ItemStack> drops = Block.getDrops(state, level, pos, null, null, new ItemStack(Items.NETHERITE_PICKAXE));
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(ore.raw.get()), ore + " should drop its raw ore, dropped " + drops);

            ItemStack smelted = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(ore.raw.get())), level)
                    .map(recipe -> recipe.value().getResultItem(level.registryAccess())).orElse(ItemStack.EMPTY);
            helper.assertTrue(smelted.is(ore.ingot.get()), ore + " raw ore should smelt into its ingot");
            ItemStack blasted = level.getRecipeManager().getRecipeFor(RecipeType.BLASTING, new SingleRecipeInput(new ItemStack(ore.raw.get())), level)
                    .map(recipe -> recipe.value().getResultItem(level.registryAccess())).orElse(ItemStack.EMPTY);
            helper.assertTrue(blasted.is(ore.ingot.get()), ore + " raw ore should also work in a blast furnace");
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void higherTierOresNeedBetterTools(GameTestHelper helper) {
        // Gold ranks above iron for dungeon ores: stone, iron, gold, diamond, netherite.
        helper.assertTrue(needs(DungeonOre.COMMON, Items.STONE_PICKAXE) && !needs(DungeonOre.COMMON, Items.WOODEN_PICKAXE), "Barrow Iron needs stone");
        helper.assertTrue(needs(DungeonOre.TIER_1, Items.IRON_PICKAXE) && needs(DungeonOre.TIER_1, Items.GOLDEN_PICKAXE)
                && !needs(DungeonOre.TIER_1, Items.STONE_PICKAXE), "Thraggite needs iron or better");
        helper.assertTrue(needs(DungeonOre.TIER_2, Items.GOLDEN_PICKAXE) && needs(DungeonOre.TIER_2, Items.DIAMOND_PICKAXE)
                && !needs(DungeonOre.TIER_2, Items.IRON_PICKAXE), "Wardsilver needs gold or better");
        helper.assertTrue(needs(DungeonOre.TIER_3, Items.DIAMOND_PICKAXE) && !needs(DungeonOre.TIER_3, Items.GOLDEN_PICKAXE), "Emberstone needs diamond");
        helper.assertTrue(needs(DungeonOre.TIER_4, Items.NETHERITE_PICKAXE) && !needs(DungeonOre.TIER_4, Items.DIAMOND_PICKAXE), "Thailite needs netherite");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void runsKeepTheirTier(GameTestHelper helper) {
        DungeonRun run = new DungeonRun(UUID.randomUUID(), 3, 4, Level.OVERWORLD, 0, 64, 0, 0, 0);
        helper.assertTrue(DungeonRun.load(run.save()).tier() == 4, "a run's tier should be saved");
        CompoundTag old = run.save();
        old.remove("tier");
        helper.assertTrue(DungeonRun.load(old).tier() == DungeonRun.MIN_TIER, "runs saved before tiers existed should load as tier 1");
        helper.assertTrue(DungeonOre.forTier(0).isEmpty() && DungeonOre.forTier(2).orElseThrow() == DungeonOre.TIER_2, "each tier has its ore");
        helper.succeed();
    }

    /** Whether a pickaxe can harvest the ore (i.e. it would drop anything). */
    private static boolean needs(DungeonOre ore, Item pickaxe) {
        return new ItemStack(pickaxe).isCorrectToolForDrops(ore.ore.get().defaultBlockState());
    }
}
