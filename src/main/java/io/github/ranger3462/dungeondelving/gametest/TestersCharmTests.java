package io.github.ranger3462.dungeondelving.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.integration.ars.ArsMana;
import io.github.ranger3462.dungeondelving.integration.irons.IronsMana;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import io.github.ranger3462.dungeondelving.testing.TestersCharm;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Checks the Tester's Charm is noticed anywhere in the inventory, and that refilling mana works with the magic mods
 * installed. (Its damage, death and flight handling need a real player, so they're checked in game.)
 */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class TestersCharmTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void theCharmWorksFromAnyInventorySlot(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "dungeon_tester"));
        helper.assertFalse(TestersCharm.has(player), "a player without the charm shouldn't count as carrying it");
        player.getInventory().setItem(30, new ItemStack(ModRegistries.TESTERS_CHARM.get()));
        helper.assertTrue(TestersCharm.has(player), "the charm should work from the backpack rows, not just the hotbar");
        player.getInventory().setItem(30, ItemStack.EMPTY);
        player.getInventory().offhand.set(0, new ItemStack(ModRegistries.TESTERS_CHARM.get()));
        helper.assertTrue(TestersCharm.has(player), "the charm should work from the offhand");
        if (ModList.get().isLoaded("irons_spellbooks")) {
            IronsMana.refill(player);
        }
        if (ModList.get().isLoaded("ars_nouveau")) {
            ArsMana.refill(player);
        }
        helper.succeed();
    }
}
