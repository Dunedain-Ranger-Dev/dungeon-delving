package io.github.ranger3462.dungeondelving.gametest;

import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.*;

import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers.Rolls;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuilder;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuilder.Placement;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces;
import io.github.ranger3462.dungeondelving.registry.DungeonOre;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks marker blocks turn into the right content, and that the placeholder rooms use them. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class DungeonMarkerTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";
    // Resource markers here always become a vanilla ore (no tier or common ore chance), to test the vanilla list.
    private static final Rolls ALWAYS = new Rolls(1, 1.0, 1.0, 0.0, 0.0, 3);
    private static final Rolls NEVER = new Rolls(1, 0.0, 0.0, 0.0, 0.0, 3);

    @GameTest(template = ANY_TEMPLATE)
    public static void markersBecomeContent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO).above(120);
        RandomSource random = RandomSource.create(5);
        try {
            resolve(level, pos, ModRegistries.LOOT_MARKER.get(), random, ALWAYS);
            chestWith(helper, pos, DungeonMarkers.ROOM_LOOT, "a loot marker");
            resolve(level, pos, ModRegistries.LOOT_MARKER.get(), random, NEVER);
            helper.assertTrue(level.getBlockState(pos).isAir(), "a loot marker that doesn't roll a chest should leave nothing");

            resolve(level, pos, ModRegistries.OBJECTIVE_MARKER.get(), random, NEVER);
            chestWith(helper, pos, DungeonMarkers.OBJECTIVE_LOOT, "an objective marker");

            resolve(level, pos, ModRegistries.RESOURCE_MARKER.get(), random, ALWAYS);
            helper.assertTrue(level.getBlockState(pos).is(DungeonMarkers.DUNGEON_RESOURCES), "a resource marker should become a dungeon ore");
            resolve(level, pos, ModRegistries.RESOURCE_MARKER.get(), random, NEVER);
            helper.assertTrue(level.getBlockState(pos).isAir(), "a resource marker that doesn't roll an ore should leave nothing");

            level.setBlock(pos, ModRegistries.MOB_MARKER.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            List<Entity> mobs = DungeonMarkers.resolveAt(level, pos, ModRegistries.MOB_MARKER.get(), random, ALWAYS);
            mobs.forEach(Entity::discard);
            helper.assertTrue(mobs.size() >= 1 && mobs.size() <= 3, "a mob marker should spawn 1 to 3 mobs, spawned " + mobs.size());
            helper.assertTrue(mobs.stream().allMatch(mob -> mob.getType().is(DungeonMarkers.DUNGEON_MOBS)), "mobs should come from the dungeon mob list");
            helper.assertTrue(mobs.stream().allMatch(mob -> mob instanceof Mob m && m.isPersistenceRequired()), "dungeon mobs shouldn't despawn");
            helper.assertTrue(level.getBlockState(pos).isAir(), "the mob marker itself should be removed");
        } finally {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void resourceMarkersRollDungeonOres(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO).above(120);
        RandomSource random = RandomSource.create(9);
        try {
            for (int tier = 1; tier <= 4; tier++) {
                resolve(level, pos, ModRegistries.RESOURCE_MARKER.get(), random, new Rolls(tier, 0, 1.0, 1.0, 0.0, 1));
                Block expected = DungeonOre.forTier(tier).orElseThrow().ore.get();
                helper.assertTrue(level.getBlockState(pos).is(expected), "a tier " + tier + " dungeon should roll its own tier ore");
            }
            resolve(level, pos, ModRegistries.RESOURCE_MARKER.get(), random, new Rolls(3, 0, 1.0, 0.0, 1.0, 1));
            helper.assertTrue(level.getBlockState(pos).is(DungeonOre.COMMON.ore.get()), "the common ore can roll in any dungeon");
        } finally {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void placeholderRoomsUseMarkers(GameTestHelper helper) {
        var structures = helper.getLevel().getStructureManager();
        for (String room : List.of("common_pillars_placeholder", "common_steps_placeholder", "common_cover_placeholder",
                "common_walls_placeholder", "objective_placeholder")) {
            StructureTemplate template = structures.get(DungeonPieces.id(room)).orElseThrow();
            helper.assertTrue(count(template, ModRegistries.MOB_MARKER.get()) > 0, room + " should have mob markers");
            helper.assertTrue(count(template, Blocks.GOLD_BLOCK) == 0, room + " should no longer use gold blocks as stand-ins");
        }
        StructureTemplate objective = structures.get(DungeonPieces.id("objective_placeholder")).orElseThrow();
        helper.assertTrue(count(objective, ModRegistries.OBJECTIVE_MARKER.get()) == 1, "the objective room should have one objective marker");
        StructureTemplate pillars = structures.get(DungeonPieces.id("common_pillars_placeholder")).orElseThrow();
        helper.assertTrue(count(pillars, ModRegistries.LOOT_MARKER.get()) > 0 && count(pillars, ModRegistries.RESOURCE_MARKER.get()) > 0,
                "the pillars room should have loot and resource markers");
        helper.succeed();
    }

    /** Placing a room for real resolves every marker in it: the objective chest appears and no markers are left. */
    @GameTest(template = ANY_TEMPLATE)
    public static void placedRoomsResolveTheirMarkers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(BlockPos.ZERO).above(120);
        BlockPos far = corner.offset(ROOM_SIZE - 1, ROOM_HEIGHT - 1, ROOM_SIZE - 1);
        try {
            DungeonBuilder.place(level, new Placement(DungeonPieces.id("objective_placeholder"), corner, Rotation.CLOCKWISE_90, Mirror.NONE));
            int markersLeft = 0;
            int chests = 0;
            for (BlockPos pos : BlockPos.betweenClosed(corner, far)) {
                Block block = level.getBlockState(pos).getBlock();
                if (block == ModRegistries.LOOT_MARKER.get() || block == ModRegistries.MOB_MARKER.get()
                        || block == ModRegistries.RESOURCE_MARKER.get() || block == ModRegistries.OBJECTIVE_MARKER.get()) {
                    markersLeft++;
                }
                if (block == Blocks.CHEST) {
                    chests++;
                }
            }
            helper.assertTrue(markersLeft == 0, markersLeft + " marker blocks were left in the placed room");
            helper.assertTrue(chests >= 1, "the objective room should have its reward chest");
        } finally {
            level.getEntitiesOfClass(Mob.class, new AABB(corner.getCenter(), far.getCenter()).inflate(4)).forEach(Entity::discard);
            for (BlockPos pos : BlockPos.betweenClosed(corner, far)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        helper.succeed();
    }

    private static void resolve(ServerLevel level, BlockPos pos, Block marker, RandomSource random, Rolls rolls) {
        level.setBlock(pos, marker.defaultBlockState(), Block.UPDATE_CLIENTS);
        DungeonMarkers.resolveAt(level, pos, marker, random, rolls);
    }

    private static void chestWith(GameTestHelper helper, BlockPos pos, Object table, String what) {
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.CHEST), what + " should become a chest");
        helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest && table.equals(chest.getLootTable()),
                what + " should use the " + table + " loot table");
        RandomizableContainerBlockEntity chest = (RandomizableContainerBlockEntity) helper.getLevel().getBlockEntity(pos);
        chest.unpackLootTable(null);
        helper.assertFalse(chest.isEmpty(), what + "'s chest should contain loot once opened");
    }

    private static int count(StructureTemplate template, Block block) {
        return template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).size();
    }
}
