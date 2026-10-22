package io.github.ranger3462.dungeondelving.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonGear;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers.Rolls;
import io.github.ranger3462.dungeondelving.dungeon.RoomSpec;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.entity.SkeletonKnight;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the boss-room exit, the Barrow King's court, tier gear for dungeon mobs, and tier-scaled spawners. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class BossCourtAndTierTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";
    private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    /** The exit goes on the floor two blocks inside the boss door, whichever wall the door is in. */
    @GameTest(template = ANY_TEMPLATE)
    public static void theExitAppearsJustInsideTheBossDoor(GameTestHelper helper) {
        BlockPos min = DungeonDimension.roomMinAt(DungeonDimension.regionCenter(3));
        int max = RoomSpec.ROOM_SIZE - 1;
        int middle = RoomSpec.ROOM_SIZE / 2;
        helper.assertTrue(DungeonBosses.exitPos(door(min, 0, false)).equals(Optional.of(min.offset(middle, 1, 2))), "north door");
        helper.assertTrue(DungeonBosses.exitPos(door(min, max, false)).equals(Optional.of(min.offset(middle, 1, max - 2))), "south door");
        helper.assertTrue(DungeonBosses.exitPos(door(min, 0, true)).equals(Optional.of(min.offset(2, 1, middle))), "west door");
        helper.assertTrue(DungeonBosses.exitPos(door(min, max, true)).equals(Optional.of(min.offset(max - 2, 1, middle))), "east door");
        helper.assertTrue(DungeonBosses.exitPos(List.of()).isEmpty(), "no door, no exit");
        helper.succeed();
    }

    /** A boss door's blocks, in the order {@code DungeonBuilder} lists them, in the wall at {@code wall}. */
    private static List<BlockPos> door(BlockPos min, int wall, boolean alongX) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int across = RoomSpec.DOOR_START; across < RoomSpec.DOOR_START + RoomSpec.DOOR_WIDTH; across++) {
            for (int y = RoomSpec.DOOR_BOTTOM_Y; y < RoomSpec.DOOR_BOTTOM_Y + RoomSpec.DOOR_HEIGHT; y++) {
                blocks.add(alongX ? min.offset(wall, y, across) : min.offset(across, y, wall));
            }
        }
        return blocks;
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void theBarrowKingHoldsCourt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0)).above(100);
        LivingEntity king = DungeonBosses.spawn(level, pos, 2, -1, DungeonBosses.BARROW_KING);
        List<Mob> court = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(10), mob -> mob != king);
        try {
            long knights = court.stream().filter(mob -> mob instanceof SkeletonKnight).count();
            helper.assertTrue(knights == DungeonBosses.BARROW_KING_GUARDS, "the King should have " + DungeonBosses.BARROW_KING_GUARDS + " knights, has " + knights);
            if (ModList.get().isLoaded("irons_spellbooks")) {
                EntityType<?> mageType = BuiltInRegistries.ENTITY_TYPE.get(DungeonDelving.id("skeleton_mage"));
                List<Mob> mages = court.stream().filter(mob -> mob.getType() == mageType).toList();
                helper.assertTrue(mages.size() == 2, "with Iron's Spells, the King should have 2 skeleton mages, has " + mages.size());
                helper.assertTrue(mages.stream().allMatch(mage -> mage.isAlliedTo(king)), "the mages should count the King as an ally, to heal him");
            }
        } finally {
            king.discard();
            court.forEach(Mob::discard);
        }
        helper.succeed();
    }

    /** With Iron's Spells, the King's mages heal him when he's hurt, even with nobody to fight. */
    @GameTest(template = ANY_TEMPLATE, timeoutTicks = 300)
    public static void theMagesHealTheBarrowKing(GameTestHelper helper) {
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            helper.succeed();
            return;
        }
        BlockPos pos = platform(helper);
        LivingEntity king = DungeonBosses.spawn(helper.getLevel(), pos, 2, -1, DungeonBosses.BARROW_KING);
        king.setHealth(king.getMaxHealth() / 2);
        float hurt = king.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(king.getHealth() > hurt, "the mages should have healed the King by now");
            cleanUp(helper, pos);
        });
    }

    /** With Iron's Spells, a hurt Goblin Shaman Chief heals himself. */
    @GameTest(template = ANY_TEMPLATE, timeoutTicks = 300)
    public static void theGoblinChiefHealsHimself(GameTestHelper helper) {
        if (!ModList.get().isLoaded("irons_spellbooks")) {
            helper.succeed();
            return;
        }
        BlockPos pos = platform(helper);
        LivingEntity chief = DungeonBosses.spawn(helper.getLevel(), pos, 1, -1, "goblin_chief");
        chief.setHealth(chief.getMaxHealth() / 2);
        float hurt = chief.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(chief.getHealth() > hurt, "the chief should have healed himself by now");
            cleanUp(helper, pos);
        });
    }

    /** A stone floor high above the test, for mobs that need to stand around for a while. Returns the spot above its middle. */
    private static BlockPos platform(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0)).above(100);
        for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-8, -1, -8), pos.offset(8, -1, 8))) {
            helper.getLevel().setBlock(floor, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return pos;
    }

    private static void cleanUp(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().getEntitiesOfClass(Mob.class, new AABB(pos).inflate(16)).forEach(Mob::discard);
        for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-8, -1, -8), pos.offset(8, -1, 8))) {
            helper.getLevel().setBlock(floor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Tier 1 mobs get a little leather; tier 4 mobs get plenty of diamond, some of it enchanted. None of it drops. */
    @GameTest(template = ANY_TEMPLATE)
    public static void dungeonMobsGearUpByTier(GameTestHelper helper) {
        RandomSource random = RandomSource.create(7);
        int tierOnePieces = 0;
        int tierFourPieces = 0;
        boolean enchanted = false;
        for (int i = 0; i < 40; i++) {
            Zombie low = EntityType.ZOMBIE.create(helper.getLevel());
            DungeonGear.gear(low, 1, random);
            Zombie high = EntityType.ZOMBIE.create(helper.getLevel());
            high.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            DungeonGear.gear(high, 4, random);
            for (EquipmentSlot slot : ARMOR) {
                ItemStack lowPiece = low.getItemBySlot(slot);
                if (!lowPiece.isEmpty()) {
                    tierOnePieces++;
                    helper.assertTrue(lowPiece.getItem() instanceof ArmorItem armor && armor.getMaterial().is(ArmorMaterials.LEATHER),
                            "tier 1 mobs should only wear leather, not " + lowPiece);
                    helper.assertFalse(lowPiece.isEnchanted(), "tier 1 gear shouldn't be enchanted");
                }
                ItemStack highPiece = high.getItemBySlot(slot);
                if (!highPiece.isEmpty()) {
                    tierFourPieces++;
                    helper.assertTrue(highPiece.getItem() instanceof ArmorItem armor && armor.getMaterial().is(ArmorMaterials.DIAMOND),
                            "tier 4 mobs should wear diamond, not " + highPiece);
                    helper.assertTrue(high.saveWithoutId(new CompoundTag()).getList("ArmorDropChances", Tag.TAG_FLOAT).getFloat(slot.getIndex()) == 0.0f,
                            "dungeon gear should never drop");
                    enchanted |= highPiece.isEnchanted();
                }
            }
            enchanted |= high.getMainHandItem().isEnchanted();
            low.discard();
            high.discard();
        }
        helper.assertTrue(tierOnePieces > 0 && tierOnePieces < tierFourPieces,
                "deeper mobs should wear more armor: tier 1 had " + tierOnePieces + " pieces, tier 4 " + tierFourPieces);
        helper.assertTrue(enchanted, "some tier 4 gear should be enchanted");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void spawnersAreFewerAndGentlerAtLowTiers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        RandomSource random = RandomSource.create(3);
        try {
            resolve(level, pos, random, new Rolls(1, 0, 0, 0, 0, -1, null, 0.0));
            helper.assertTrue(level.getBlockState(pos).isAir(), "a mob marker that doesn't roll a spawner should leave nothing");
            resolve(level, pos, random, new Rolls(1, 0, 0, 0, 0, -1, null, 1.0));
            helper.assertTrue(spawnCount(level, pos) == DungeonMarkers.SPAWN_COUNT[0], "tier 1 spawners should spawn " + DungeonMarkers.SPAWN_COUNT[0]);
            resolve(level, pos, random, new Rolls(4, 0, 0, 0, 0, -1, null, 1.0));
            helper.assertTrue(spawnCount(level, pos) == DungeonMarkers.SPAWN_COUNT[3], "tier 4 spawners should spawn " + DungeonMarkers.SPAWN_COUNT[3]);
            helper.assertTrue(DungeonMarkers.SPAWN_COUNT[0] < DungeonMarkers.SPAWN_COUNT[3], "low-tier spawners should spawn fewer mobs");
        } finally {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        helper.succeed();
    }

    private static void resolve(ServerLevel level, BlockPos pos, RandomSource random, Rolls rolls) {
        level.setBlock(pos, ModRegistries.MOB_MARKER.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        DungeonMarkers.resolveAt(level, pos, ModRegistries.MOB_MARKER.get(), random, rolls);
    }

    private static int spawnCount(ServerLevel level, BlockPos pos) {
        CompoundTag spawner = level.getBlockEntity(pos).saveWithoutMetadata(level.registryAccess());
        return spawner.getShort("SpawnCount");
    }
}
