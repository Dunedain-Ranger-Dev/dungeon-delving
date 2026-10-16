package io.github.ranger3462.dungeondelving.gametest;

import java.util.ArrayList;
import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers;
import io.github.ranger3462.dungeondelving.registry.DungeonOre;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Checks the per-tier chest loot tables: each tier has a room and an objective table, objective chests always pay out
 * the tier's ingots, and a tier's chests only ever hold that tier's ore (plus the common ore).
 */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class TierLootTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";
    private static final int ROLLS = 100;

    @GameTest(template = ANY_TEMPLATE)
    public static void everyTierHasItsOwnLoot(GameTestHelper helper) {
        for (int tier = 1; tier <= 4; tier++) {
            DungeonOre ore = DungeonOre.forTier(tier).orElseThrow();
            List<ItemStack> rooms = roll(helper, DungeonMarkers.roomLoot(tier), ROLLS);
            List<ItemStack> objectives = roll(helper, DungeonMarkers.objectiveLoot(tier), ROLLS);

            helper.assertFalse(rooms.isEmpty(), "tier " + tier + " room chests should have loot");
            helper.assertTrue(rooms.stream().anyMatch(stack -> stack.is(ore.raw.get())), "tier " + tier + " room chests should sometimes hold raw " + ore.key);
            helper.assertTrue(rooms.stream().anyMatch(stack -> stack.is(DungeonOre.COMMON.raw.get())), "room chests should sometimes hold raw barrow iron");
            for (int other = 1; other <= 4; other++) {
                if (other == tier) {
                    continue;
                }
                DungeonOre wrong = DungeonOre.forTier(other).orElseThrow();
                helper.assertFalse(rooms.stream().anyMatch(stack -> stack.is(wrong.raw.get()) || stack.is(wrong.ingot.get()))
                        || objectives.stream().anyMatch(stack -> stack.is(wrong.raw.get()) || stack.is(wrong.ingot.get())),
                        "tier " + tier + " chests shouldn't hold tier " + other + " ore");
            }
            for (int i = 0; i < 20; i++) {
                List<ItemStack> chest = roll(helper, DungeonMarkers.objectiveLoot(tier), 1);
                helper.assertTrue(chest.stream().anyMatch(stack -> stack.is(ore.ingot.get())), "every tier " + tier + " objective chest should pay out " + ore.key + " ingots");
            }
        }
        helper.succeed();
    }

    /** Rolls a chest loot table some number of times and returns everything it produced. */
    private static List<ItemStack> roll(GameTestHelper helper, ResourceKey<LootTable> key, int times) {
        LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(key);
        if (table == LootTable.EMPTY) {
            throw new IllegalStateException("Missing loot table " + key.location());
        }
        LootParams params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
                .create(LootContextParamSets.CHEST);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            items.addAll(table.getRandomItems(params));
        }
        return items;
    }
}
