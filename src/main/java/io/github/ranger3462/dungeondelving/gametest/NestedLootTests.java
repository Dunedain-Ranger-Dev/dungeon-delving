package io.github.ranger3462.dungeondelving.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonLoot;
import io.github.ranger3462.dungeondelving.registry.DungeonOre;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Checks that dungeon loot can't escape a failed run by being stored inside something: shulker boxes, bundles, and
 * (when installed) Sophisticated Backpacks, including one worn in a Curios slot. Also checks a successful exit turns
 * stored loot into ordinary items.
 */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class NestedLootTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";
    private static final ResourceLocation BACKPACK = ResourceLocation.fromNamespaceAndPath("sophisticatedbackpacks", "backpack");
    private static final ResourceLocation STORAGE_UUID = ResourceLocation.fromNamespaceAndPath("sophisticatedcore", "storage_uuid");

    @GameTest(template = ANY_TEMPLATE)
    public static void failureReachesIntoShulkerBoxesAndBundles(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(ore(8), new ItemStack(Items.TORCH, 16))));
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(ore(4), new ItemStack(Items.ARROW, 8))));
        player.getInventory().setItem(0, shulker);
        player.getInventory().setItem(1, bundle);

        int lost = DungeonLoot.applyFailurePenalty(player, 0);

        helper.assertTrue(lost == 2, "both stored ore stacks should be lost, but " + lost + " were");
        ItemContainerContents left = player.getInventory().getItem(0).get(DataComponents.CONTAINER);
        helper.assertTrue(left != null && left.nonEmptyStream().noneMatch(DungeonLoot::isLoot), "ore in a shulker box should be lost");
        helper.assertTrue(left.nonEmptyStream().anyMatch(stack -> stack.is(Items.TORCH)), "the shulker box's other items should be kept");
        BundleContents bundled = player.getInventory().getItem(1).get(DataComponents.BUNDLE_CONTENTS);
        helper.assertTrue(bundled != null && bundled.size() == 1 && bundled.getItemUnsafe(0).is(Items.ARROW), "only the ore should leave the bundle");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void failureReachesIntoBackpacks(GameTestHelper helper) {
        if (!ModList.get().isLoaded("sophisticatedbackpacks")) {
            helper.succeed();
            return;
        }
        ServerPlayer player = player(helper);
        ItemStack backpack = backpack(ore(16), new ItemStack(Items.COBBLESTONE, 32));
        // A backpack inside a shulker box inside the inventory.
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(backpack(ore(2)))));
        player.getInventory().setItem(0, backpack);
        player.getInventory().setItem(1, shulker);

        int lost = DungeonLoot.applyFailurePenalty(player, 0);

        helper.assertTrue(lost == 2, "both backpacks' ore should be lost, but " + lost + " stacks were");
        IItemHandler contents = contents(player.getInventory().getItem(0));
        helper.assertTrue(count(contents, true) == 0, "ore in a backpack should be lost");
        helper.assertTrue(count(contents, false) == 32, "the backpack's other items should be kept");
        ItemStack nested = player.getInventory().getItem(1).get(DataComponents.CONTAINER).nonEmptyStream().findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(nested.is(backpack.getItem()), "the backpack in the shulker box should still be there");
        helper.assertTrue(count(contents(nested), true) == 0, "ore in a backpack inside a shulker box should be lost");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void exitClaimsLootInBackpacks(GameTestHelper helper) {
        if (!ModList.get().isLoaded("sophisticatedbackpacks")) {
            helper.succeed();
            return;
        }
        ServerPlayer player = player(helper);
        player.getInventory().setItem(0, backpack(ore(16)));

        int claimed = DungeonLoot.claim(player);

        IItemHandler contents = contents(player.getInventory().getItem(0));
        helper.assertTrue(claimed == 1, "the backpack's ore should be claimed, but " + claimed + " stacks were");
        helper.assertTrue(count(contents, true) == 0 && count(contents, false) == 16, "claimed ore should stay in the backpack as ordinary items");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void failureReachesIntoWornBackpacks(GameTestHelper helper) {
        if (!ModList.get().isLoaded("sophisticatedbackpacks") || !ModList.get().isLoaded("curios")) {
            helper.succeed();
            return;
        }
        ServerPlayer player = player(helper);
        ItemStack backpack = backpack(ore(16), new ItemStack(Items.COBBLESTONE, 32));
        helper.assertTrue(CuriosTestSupport.wearOnBack(player, backpack), "the test player should have a Curios back slot");

        int lost = DungeonLoot.applyFailurePenalty(player, 0);

        IItemHandler contents = contents(CuriosTestSupport.back(player));
        helper.assertTrue(lost == 1, "the worn backpack's ore should be lost, but " + lost + " stacks were");
        helper.assertTrue(count(contents, true) == 0 && count(contents, false) == 32, "only the ore should leave a worn backpack");
        helper.succeed();
    }

    /**
     * A Sophisticated Backpack holding the given items. Backpacks only expose their storage once they have a storage
     * id, which real ones get the first time they're used, so the test gives it one.
     */
    @SuppressWarnings("unchecked")
    private static ItemStack backpack(ItemStack... items) {
        ItemStack backpack = new ItemStack(BuiltInRegistries.ITEM.get(BACKPACK));
        backpack.set((DataComponentType<UUID>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(STORAGE_UUID), UUID.randomUUID());
        IItemHandler contents = contents(backpack);
        for (ItemStack item : items) {
            ItemStack left = contents.insertItem(0, item, false);
            for (int slot = 1; slot < contents.getSlots() && !left.isEmpty(); slot++) {
                left = contents.insertItem(slot, left, false);
            }
        }
        return backpack;
    }

    private static IItemHandler contents(ItemStack stack) {
        IItemHandler handler = stack.getCapability(Capabilities.ItemHandler.ITEM);
        if (handler == null) {
            throw new IllegalStateException(stack + " has no item handler");
        }
        return handler;
    }

    /** How many items in a handler are (or aren't) dungeon loot. */
    private static int count(IItemHandler handler, boolean loot) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && DungeonLoot.isLoot(stack) == loot) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static ItemStack ore(int count) {
        ItemStack stack = new ItemStack(DungeonOre.TIER_1.raw.get(), count);
        DungeonLoot.mark(stack);
        return stack;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "dungeon_tester"));
    }
}
