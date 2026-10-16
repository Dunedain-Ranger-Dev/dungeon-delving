package io.github.ranger3462.dungeondelving.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonLoot;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonTravel;
import io.github.ranger3462.dungeondelving.dungeon.bag.BagEvents;
import io.github.ranger3462.dungeondelving.dungeon.bag.BagEvents.PickupResult;
import io.github.ranger3462.dungeondelving.dungeon.bag.DungeonBag;
import io.github.ranger3462.dungeondelving.dungeon.bag.DungeonChestMenu;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the dungeon bag: capacity, pickups, looting containers, and emptying it on exit. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class DungeonBagTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void bagHoldsNineStacksAndMergesMatches(GameTestHelper helper) {
        DungeonBag bag = new DungeonBag(DungeonBag.BASE_SLOTS);
        helper.assertTrue(bag.size() == 9, "a new bag should have 9 slots");
        helper.assertTrue(bag.insert(new ItemStack(Items.DIAMOND, 10)).isEmpty(), "loot should fit in an empty bag");
        helper.assertTrue(bag.insert(new ItemStack(Items.DIAMOND, 20)).isEmpty() && bag.usedSlots() == 1, "matching items should stack together");
        for (int i = 0; i < 8; i++) {
            bag.insert(new ItemStack(Items.IRON_SWORD));
        }
        ItemStack left = bag.insert(new ItemStack(Items.EMERALD, 3));
        helper.assertTrue(left.getCount() == 3, "a full bag should take nothing more");
        helper.assertTrue(bag.insert(new ItemStack(ModRegistries.DUNGEON_BAG.get())).getCount() == 1, "a bag can't go inside a bag");
        helper.assertTrue(DungeonBag.slotsForLevel(1) == 18 && DungeonBag.slotsForLevel(99) == DungeonBag.MAX_SLOTS,
                "upgrades should add a row of 9 each, up to a double chest");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void pickupsGoIntoTheBag(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        DungeonBag bag = new DungeonBag(9);

        ItemEntity loot = item(helper, new ItemStack(Items.DIAMOND, 5));
        helper.assertTrue(BagEvents.pickUpIntoBag(player, loot, bag) == PickupResult.BAGGED, "loot on the ground should go into the bag");
        helper.assertTrue(loot.isRemoved() && bag.container().countItem(Items.DIAMOND) == 5, "the whole stack should move into the bag");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0, "bagged loot shouldn't reach the inventory");

        ItemEntity thrown = item(helper, new ItemStack(Items.IRON_SWORD));
        thrown.setThrower(player);
        helper.assertTrue(BagEvents.pickUpIntoBag(player, thrown, bag) == PickupResult.NOT_LOOT, "items the player threw are picked up normally");

        for (int i = 0; i < 8; i++) {
            bag.insert(new ItemStack(Items.SHIELD));
        }
        ItemEntity tooMuch = item(helper, new ItemStack(Items.EMERALD, 2));
        helper.assertTrue(BagEvents.pickUpIntoBag(player, tooMuch, bag) == PickupResult.BAG_FULL, "a full bag leaves loot on the ground");
        helper.assertTrue(!tooMuch.isRemoved() && tooMuch.getItem().getCount() == 2, "loot that doesn't fit should stay on the ground");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void takingFromDungeonChestsFillsTheBag(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        DungeonBag bag = new DungeonBag(9);
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.GOLD_INGOT, 4));
        player.getInventory().setItem(0, new ItemStack(Items.TORCH, 16));
        DungeonChestMenu menu = DungeonChestMenu.looting(1, player.getInventory(), chest, bag);

        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(chest.getItem(0).isEmpty() && bag.container().countItem(Items.GOLD_INGOT) == 4, "clicking chest loot should bag it");
        helper.assertTrue(menu.getCarried().isEmpty() && player.getInventory().countItem(Items.GOLD_INGOT) == 0, "chest loot shouldn't reach the inventory");

        int torchSlot = 27 + 27; // Container slots, then the main inventory, then the hotbar (where the torches are).
        menu.clicked(torchSlot, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(chest.isEmpty() && player.getInventory().countItem(Items.TORCH) == 16, "nothing can be shift-clicked into a dungeon chest");

        for (int i = 0; i < 8; i++) {
            bag.insert(new ItemStack(Items.SHIELD));
        }
        chest.setItem(1, new ItemStack(Items.EMERALD, 3));
        menu.clicked(1, 0, ClickType.PICKUP, player);
        helper.assertTrue(chest.getItem(1).getCount() == 3, "with a full bag, loot stays in the chest");

        DungeonChestMenu view = DungeonChestMenu.viewing(2, player.getInventory(), bag);
        view.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(view.getCarried().isEmpty() && bag.usedSlots() == 9, "the bag view is read-only");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void itemsCanBeThrownOutOfTheBag(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setPos(helper.absolutePos(BlockPos.ZERO).getCenter());
        DungeonBag bag = new DungeonBag(9);
        bag.insert(new ItemStack(Items.ROTTEN_FLESH, 5));
        DungeonChestMenu view = DungeonChestMenu.viewing(3, player.getInventory(), bag);

        view.clicked(0, 0, ClickType.THROW, player);
        helper.assertTrue(bag.container().countItem(Items.ROTTEN_FLESH) == 4, "Q should throw one item out of the bag");
        view.clicked(0, 1, ClickType.THROW, player);
        helper.assertTrue(bag.usedSlots() == 0, "Ctrl+Q should throw the whole stack out of the bag");

        List<ItemEntity> thrown = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4));
        helper.assertTrue(thrown.stream().mapToInt(e -> e.getItem().getCount()).sum() == 5, "thrown items should land near the player");
        ItemEntity flesh = thrown.getFirst();
        helper.assertTrue(DungeonLoot.isLoot(flesh.getItem()), "items thrown from the bag stay dungeon loot");
        flesh.setNoPickUpDelay();
        helper.assertTrue(BagEvents.pickUpIntoBag(player, flesh, bag) == PickupResult.BAGGED,
                "picking a thrown-out item back up should put it back in the bag, not the inventory");
        thrown.forEach(ItemEntity::discard);
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void exitEmptiesTheBagIntoTheInventory(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        DungeonBag bag = new DungeonBag(9);
        bag.insert(new ItemStack(Items.DIAMOND, 7));
        bag.insert(new ItemStack(Items.IRON_SWORD));

        int stacks = DungeonTravel.emptyIntoInventory(player, bag);

        helper.assertTrue(stacks == 2 && bag.usedSlots() == 0, "the bag should be emptied");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 7 && player.getInventory().countItem(Items.IRON_SWORD) == 1,
                "bag contents should land in the inventory");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void bagsSurviveSaveAndLoad(GameTestHelper helper) {
        UUID player = UUID.randomUUID();
        DungeonRegions regions = new DungeonRegions();
        DungeonBag bag = new DungeonBag(18);
        bag.insert(new ItemStack(Items.GOLDEN_APPLE, 2));
        regions.startBag(player, bag);

        var registries = helper.getLevel().registryAccess();
        DungeonRegions loaded = DungeonRegions.load(regions.save(new CompoundTag(), registries), registries);
        DungeonBag back = loaded.bag(player).orElseThrow();
        helper.assertTrue(back.size() == 18 && back.container().countItem(Items.GOLDEN_APPLE) == 2, "a saved bag should come back with its contents");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "dungeon_tester"));
    }

    private static ItemEntity item(GameTestHelper helper, ItemStack stack) {
        ItemEntity entity = new ItemEntity(helper.getLevel(), 0, 0, 0, stack);
        entity.setNoPickUpDelay();
        return entity;
    }
}
