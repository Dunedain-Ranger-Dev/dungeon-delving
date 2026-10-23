package io.github.ranger3462.dungeondelving.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.RoomMobCap;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonProgress;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import io.github.ranger3462.dungeondelving.testing.BossDoorSpot;
import io.github.ranger3462.dungeondelving.testing.DevGear;
import io.github.ranger3462.dungeondelving.testing.TestersCharm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the dev tools for survival testing: the tier gear kit, the Tester's Charm in a ring slot, and the boss door spot. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class DevToolTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void gearMatchesTheTier(GameTestHelper helper) {
        ServerPlayer player = player(helper, "gear_tester");
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        DevGear.equip(player, 1);
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "tier 1 should be iron armor");
        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.LEATHER_HELMET)), "armor already worn should go to the inventory");
        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_SWORD)) && player.getOffhandItem().is(Items.SHIELD),
                "tier 1 should come with an iron sword and a shield");

        ServerPlayer top = player(helper, "gear_tester_4");
        DevGear.equip(top, 4);
        ItemStack helmet = top.getItemBySlot(EquipmentSlot.HEAD);
        var protection = top.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
        helper.assertTrue(helmet.is(Items.NETHERITE_HELMET) && helmet.getEnchantmentLevel(protection) == 4,
                "tier 4 should be netherite with Protection IV, got " + helmet);
        helper.assertTrue(top.getInventory().hasAnyMatching(stack -> stack.is(Items.NETHERITE_SWORD)), "tier 4 should come with a netherite sword");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void theCharmIsWornAsARing(GameTestHelper helper) {
        ServerPlayer player = player(helper, "charm_tester");
        player.getInventory().add(new ItemStack(Items.DIRT, 10));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        TestersCharm.clearEverything(player);
        helper.assertTrue(player.getInventory().isEmpty(), "clearing should empty the inventory and armor");
        helper.assertFalse(TestersCharm.has(player), "a cleared player has no charm");
        boolean worn = TestersCharm.give(player);
        helper.assertTrue(TestersCharm.has(player), "a player given the charm should have it");
        if (ModList.get().isLoaded("curios")) {
            helper.assertTrue(worn, "with Curios the charm should go in a ring slot");
            helper.assertTrue(player.getInventory().items.stream().noneMatch(stack -> stack.is(ModRegistries.TESTERS_CHARM.get())),
                    "a worn charm isn't in the inventory");
            TestersCharm.clearEverything(player);
            helper.assertFalse(TestersCharm.has(player), "clearing should take the worn charm off too");
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void theBossDoorSpotIsOutsideTheDoor(GameTestHelper helper) {
        // A doorway in the boss room's north wall at z = 100, five wide and six high; the room is to the south.
        List<BlockPos> door = new ArrayList<>();
        for (int x = 10; x < 15; x++) {
            for (int y = 65; y < 71; y++) {
                door.add(new BlockPos(x, y, 100));
            }
        }
        DungeonProgress progress = new DungeonProgress(1, 1, door, new BlockPos(12, 65, 116));
        BossDoorSpot.Spot spot = BossDoorSpot.find(progress).orElseThrow();
        helper.assertTrue(spot.pos().equals(new BlockPos(12, 65, 100 - BossDoorSpot.DISTANCE)), "the spot should be just north of the door, got " + spot.pos());
        helper.assertTrue(spot.facing() == Direction.SOUTH, "the spot should face the door, got " + spot.facing());

        // And one in the east wall, with the room to the west.
        List<BlockPos> east = door.stream().map(pos -> new BlockPos(200, pos.getY(), pos.getX() + 40)).toList();
        BossDoorSpot.Spot eastSpot = BossDoorSpot.find(new DungeonProgress(1, 1, east, new BlockPos(184, 65, 52))).orElseThrow();
        helper.assertTrue(eastSpot.pos().equals(new BlockPos(200 + BossDoorSpot.DISTANCE, 65, 52)) && eastSpot.facing() == Direction.WEST,
                "the spot should be just east of an east door, facing west, got " + eastSpot);
        helper.assertTrue(BossDoorSpot.find(new DungeonProgress(1, 1, List.of(), null)).isEmpty(), "no door, no spot");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void roomsCapTheirMobsByTier(GameTestHelper helper) {
        helper.assertTrue(RoomMobCap.cap(1) == 7 && RoomMobCap.cap(4) == 15, "room caps should run from 7 at tier 1 to 15 at tier 4");
        helper.assertTrue(RoomMobCap.cap(2) < RoomMobCap.cap(3), "caps should grow with tier");
        ServerLevel level = helper.getLevel();
        BlockPos center = DungeonDimension.roomCenterAt(helper.absolutePos(BlockPos.ZERO)).above(2);
        level.getChunkAt(center);
        List<Zombie> zombies = new ArrayList<>();
        try {
            for (int i = 0; i < RoomMobCap.cap(1); i++) {
                Zombie zombie = EntityType.ZOMBIE.create(level);
                zombie.moveTo(center.getX() + 0.5 + i % 3, center.getY(), center.getZ() + 0.5 + i / 3);
                zombie.setNoAi(true);
                level.addFreshEntity(zombie);
                zombies.add(zombie);
                if (i == RoomMobCap.cap(1) - 2) {
                    helper.assertFalse(RoomMobCap.full(level, center, 1), "a room one short of its cap isn't full");
                }
            }
            helper.assertTrue(RoomMobCap.count(level, center) >= RoomMobCap.cap(1), "the room should count its mobs");
            helper.assertTrue(RoomMobCap.full(level, center, 1), "a tier 1 room with 7 mobs is full");
            helper.assertFalse(RoomMobCap.full(level, center, 4), "a tier 4 room holds more");
        } finally {
            zombies.forEach(Zombie::discard);
        }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }
}
