package io.github.ranger3462.dungeondelving.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.registry.DungeonOre;
import io.github.ranger3462.dungeondelving.workshop.RunicWorkshop;
import io.github.ranger3462.dungeondelving.workshop.RunicWorkshopMenu;
import io.github.ranger3462.dungeondelving.workshop.RunicWorkshopRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the key ladder: each Runic Key's recipe, the workshop crafting it, and bosses dropping the shards. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class RunicKeyTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";
    /** The material and amount for each tier's key, index 0 for tier 1. */
    private static final List<Item> MATERIALS = List.of(Items.IRON_INGOT, DungeonOre.COMMON.ingot.get(), DungeonOre.TIER_1.ingot.get(),
            DungeonOre.TIER_2.ingot.get());
    private static final int[] COUNTS = { 8, 12, 16, 20 };

    @GameTest(template = ANY_TEMPLATE)
    public static void eachKeyIsMadeFromTheOneBelow(GameTestHelper helper) {
        for (int tier = 1; tier <= 4; tier++) {
            ItemStack key = tier > 1 ? new ItemStack(RunicWorkshop.key(tier - 1)) : ItemStack.EMPTY;
            ItemStack shard = tier > 1 ? new ItemStack(RunicWorkshop.shard(tier - 1)) : ItemStack.EMPTY;
            ItemStack material = new ItemStack(MATERIALS.get(tier - 1), COUNTS[tier - 1]);
            var recipe = find(helper, key, shard, material);
            helper.assertTrue(recipe.isPresent() && recipe.get().result().is(RunicWorkshop.key(tier)),
                    "Runic Key " + tier + " should be made from " + COUNTS[tier - 1] + " " + material.getHoverName().getString()
                            + (tier > 1 ? ", Runic Key " + (tier - 1) + " and Runic Shard " + (tier - 1) : ""));
            helper.assertTrue(find(helper, key, shard, material.copyWithCount(COUNTS[tier - 1] - 1)).isEmpty(), "one ingot short shouldn't work");
            if (tier > 1) {
                helper.assertTrue(find(helper, ItemStack.EMPTY, shard, material).isEmpty(), "a key needs the key below it");
                helper.assertTrue(find(helper, key, ItemStack.EMPTY, material).isEmpty(), "a key needs its boss shard");
            }
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void theWorkshopUsesUpItsInputs(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "dungeon_tester"));
        RunicWorkshopMenu menu = new RunicWorkshopMenu(1, player.getInventory());
        menu.getSlot(RunicWorkshopMenu.KEY_SLOT).set(new ItemStack(RunicWorkshop.key(1)));
        menu.getSlot(RunicWorkshopMenu.SHARD_SLOT).set(new ItemStack(RunicWorkshop.shard(1)));
        menu.getSlot(RunicWorkshopMenu.MATERIAL_SLOT).set(new ItemStack(DungeonOre.COMMON.ingot.get(), 20));
        menu.createResult();
        ItemStack result = menu.getSlot(RunicWorkshopMenu.RESULT_SLOT).getItem();
        helper.assertTrue(result.is(RunicWorkshop.key(2)), "the workshop should offer Runic Key II, offered " + result);

        menu.getSlot(RunicWorkshopMenu.RESULT_SLOT).onTake(player, result);

        helper.assertTrue(menu.getSlot(RunicWorkshopMenu.KEY_SLOT).getItem().isEmpty(), "the old key should be used up");
        helper.assertTrue(menu.getSlot(RunicWorkshopMenu.SHARD_SLOT).getItem().isEmpty(), "the shard should be used up");
        helper.assertTrue(menu.getSlot(RunicWorkshopMenu.MATERIAL_SLOT).getItem().getCount() == 8, "12 of the 20 ingots should be used");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void bossesDropTheShardForTheNextTier(GameTestHelper helper) {
        LootParams params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        for (int tier = 1; tier <= 4; tier++) {
            LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(DungeonBosses.lootTable(DungeonBosses.BARROW_KING, tier));
            for (int i = 0; i < 20; i++) {
                List<ItemStack> drops = table.getRandomItems(params);
                boolean shard = drops.stream().anyMatch(stack -> stack.is(RunicWorkshop.RUNIC_SHARDS));
                if (tier < 4) {
                    final int t = tier;
                    helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(RunicWorkshop.shard(t))), "the tier " + tier + " boss should always drop Runic Shard " + tier);
                } else {
                    helper.assertFalse(shard, "the tier 4 boss has no next tier to drop a shard for");
                }
            }
        }
        helper.succeed();
    }

    private static Optional<RunicWorkshopRecipe> find(GameTestHelper helper, ItemStack key, ItemStack shard, ItemStack material) {
        return helper.getLevel().getRecipeManager()
                .getRecipeFor(RunicWorkshop.RECIPE_TYPE.get(), new RunicWorkshopRecipe.Input(key, shard, material), helper.getLevel())
                .map(holder -> holder.value());
    }
}
