package io.github.ranger3462.dungeondelving.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonLoot;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks how dungeon loot is marked, claimed on exit, and taken away (with gear damage) when a run fails. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class DungeonLootTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void onlyDungeonLootIsMarked(GameTestHelper helper) {
        List<ItemStack> overworld = List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.BONE));
        List<ItemStack> dungeon = List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.BONE));
        DungeonLoot.markIfInDungeon(overworld, Level.OVERWORLD);
        DungeonLoot.markIfInDungeon(dungeon, DungeonDimension.DUNGEON);
        helper.assertTrue(overworld.stream().noneMatch(DungeonLoot::isLoot), "items generated outside dungeons shouldn't be marked");
        helper.assertTrue(dungeon.stream().allMatch(DungeonLoot::isLoot), "items generated in a dungeon should be marked");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void failureTakesLootAndDamagesGear(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ItemStack loot = loot(new ItemStack(Items.DIAMOND, 5));
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemStack nearlyBroken = new ItemStack(Items.IRON_PICKAXE);
        nearlyBroken.setDamageValue(nearlyBroken.getMaxDamage() - 3);
        player.getInventory().setItem(0, loot);
        player.getInventory().setItem(1, sword);
        player.getInventory().setItem(2, new ItemStack(Items.COBBLESTONE, 10));
        player.getInventory().setItem(3, nearlyBroken);
        player.getEnderChestInventory().setItem(0, loot(new ItemStack(Items.EMERALD)));

        int lost = DungeonLoot.applyFailurePenalty(player, 0.25);

        helper.assertTrue(lost == 2, "two loot stacks should be lost, but " + lost + " were");
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "dungeon loot should be removed from the inventory");
        helper.assertTrue(player.getEnderChestInventory().getItem(0).isEmpty(), "dungeon loot stashed in the ender chest should be removed");
        helper.assertTrue(player.getInventory().getItem(2).getCount() == 10, "brought-in items should be kept");
        int expected = Mth.ceil(sword.getMaxDamage() * 0.25);
        helper.assertTrue(player.getInventory().getItem(1).getDamageValue() == expected,
                "gear should lose 25% durability: expected " + expected + ", got " + player.getInventory().getItem(1).getDamageValue());
        ItemStack pick = player.getInventory().getItem(3);
        helper.assertTrue(!pick.isEmpty() && pick.getDamageValue() == pick.getMaxDamage() - 1, "gear damage should never break an item");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void exitKeepsLootAsNormalItems(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.getInventory().setItem(0, loot(new ItemStack(Items.DIAMOND, 5)));
        player.getEnderChestInventory().setItem(0, loot(new ItemStack(Items.EMERALD)));

        DungeonLoot.claim(player);

        ItemStack kept = player.getInventory().getItem(0);
        helper.assertTrue(kept.is(Items.DIAMOND) && kept.getCount() == 5, "loot should be kept on exit");
        helper.assertFalse(DungeonLoot.isLoot(kept), "kept loot should become a normal item");
        helper.assertFalse(DungeonLoot.isLoot(player.getEnderChestInventory().getItem(0)), "ender chest loot should become normal too");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void heldInventorySurvivesSaveAndLoad(GameTestHelper helper) {
        ServerPlayer before = player(helper);
        before.getInventory().setItem(4, new ItemStack(Items.IRON_SWORD));
        before.getInventory().setItem(10, new ItemStack(Items.TORCH, 32));

        DungeonRegions regions = new DungeonRegions();
        regions.holdInventory(before.getUUID(), before.getInventory().save(new ListTag()));
        DungeonRegions loaded = DungeonRegions.load(regions.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());

        ServerPlayer after = player(helper);
        loaded.takeHeldInventory(before.getUUID()).ifPresent(held -> after.getInventory().load(held));
        helper.assertTrue(after.getInventory().getItem(4).is(Items.IRON_SWORD), "held items should come back in the same slot");
        helper.assertTrue(after.getInventory().getItem(10).getCount() == 32, "held stack sizes should be kept");
        helper.assertTrue(loaded.takeHeldInventory(before.getUUID()).isEmpty(), "a held inventory should only be given back once");
        helper.succeed();
    }

    /**
     * A fake player: a real server player with no network connection, so other mods (e.g. KubeJS) don't try to
     * send it data. Each call gets a new identity.
     */
    private static ServerPlayer player(GameTestHelper helper) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "dungeon_tester"));
    }

    private static ItemStack loot(ItemStack stack) {
        DungeonLoot.mark(stack);
        return stack;
    }
}
