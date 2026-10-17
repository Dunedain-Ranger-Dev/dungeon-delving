package io.github.ranger3462.dungeondelving.gametest;

import java.util.ArrayList;
import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonLoot;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.NoneBiomeModifier;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Checks dungeon-only items: anything in {@code dungeondelving:dungeon_only} is stripped from loot generated outside
 * dungeons, but not from dungeon loot. The tag is filled by the pack, so the Iron's Spells checks only run when the
 * pack's data (synced from the pack repository) and Iron's Spells are present; otherwise they pass without checking.
 */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class DungeonOnlyTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";
    private static final ResourceLocation ARCANE_ESSENCE = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "arcane_essence");
    private static final ResourceLocation MITHRIL_ORE = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "mithril_ore");

    @GameTest(template = ANY_TEMPLATE)
    public static void dungeonOnlyItemsAreStrippedOutsideDungeons(GameTestHelper helper) {
        if (!packIsLoaded()) {
            helper.succeed();
            return;
        }
        // Iron's Spells adds its loot to vanilla dungeon chests, including arcane essence.
        List<ItemStack> overworld = roll(helper.getLevel(), BuiltInLootTables.SIMPLE_DUNGEON, 300);
        helper.assertTrue(overworld.stream().anyMatch(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("irons_spellbooks")),
                "Iron's Spells should still add its other loot (e.g. scrolls and ink) to overworld chests");
        helper.assertFalse(overworld.stream().anyMatch(stack -> stack.is(DungeonLoot.DUNGEON_ONLY)),
                "dungeon-only items shouldn't come out of overworld chests");

        // The same items are kept when the loot is generated in a dungeon. (The test server has no dungeon dimension,
        // so this checks the rule the loot modifier applies.)
        List<ItemStack> found = new ArrayList<>(List.of(new ItemStack(arcaneEssence(), 3)));
        DungeonLoot.removeDungeonOnly(found, DungeonDimension.DUNGEON);
        helper.assertTrue(found.size() == 1, "dungeon-only items generated in a dungeon should be kept");
        DungeonLoot.removeDungeonOnly(found, Level.OVERWORLD);
        helper.assertTrue(found.isEmpty(), "dungeon-only items generated outside dungeons should be removed");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void mithrilMovesFromTheOverworldToDungeons(GameTestHelper helper) {
        if (!packIsLoaded()) {
            helper.succeed();
            return;
        }
        var biomeModifiers = helper.getLevel().registryAccess().registryOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS);
        helper.assertTrue(biomeModifiers.get(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "add_mithril_ore")) instanceof NoneBiomeModifier,
                "mithril ore shouldn't generate in the overworld");
        Block mithril = BuiltInRegistries.BLOCK.get(MITHRIL_ORE);
        var blocks = helper.getLevel().registryAccess().registryOrThrow(Registries.BLOCK);
        helper.assertFalse(blocks.wrapAsHolder(mithril).is(DungeonMarkers.tierResources(1)), "tier 1 dungeons shouldn't have mithril");
        for (int tier = 2; tier <= 4; tier++) {
            helper.assertTrue(blocks.wrapAsHolder(mithril).is(DungeonMarkers.tierResources(tier)), "tier " + tier + " dungeons should have mithril ore");
        }
        helper.succeed();
    }

    /** Whether Iron's Spells is installed and the pack has filled the dungeon-only tag. */
    private static boolean packIsLoaded() {
        boolean loaded = BuiltInRegistries.ITEM.containsKey(ARCANE_ESSENCE) && new ItemStack(arcaneEssence()).is(DungeonLoot.DUNGEON_ONLY);
        if (!loaded) {
            DungeonDelving.LOGGER.info("Skipping dungeon-only checks: Iron's Spells or the pack's data isn't loaded");
        }
        return loaded;
    }

    private static Item arcaneEssence() {
        return BuiltInRegistries.ITEM.get(ARCANE_ESSENCE);
    }

    private static List<ItemStack> roll(ServerLevel level, ResourceKey<LootTable> key, int times) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(BlockPos.ZERO))
                .create(LootContextParamSets.CHEST);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            items.addAll(table.getRandomItems(params));
        }
        return items;
    }
}
