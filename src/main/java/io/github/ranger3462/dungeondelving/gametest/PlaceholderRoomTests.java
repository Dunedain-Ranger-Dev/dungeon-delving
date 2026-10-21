package io.github.ranger3462.dungeondelving.gametest;

import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.*;

import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces.Kind;
import io.github.ranger3462.dungeondelving.dungeon.layout.PieceValidator;
import io.github.ranger3462.dungeondelving.dungeon.layout.PieceValidator.Problem;
import io.github.ranger3462.dungeondelving.registry.DungeonOre;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Places each placeholder piece in a real world and checks it against {@link io.github.ranger3462.dungeondelving.dungeon.RoomSpec}.
 * Run with {@code ./gradlew runGameTestServer}, or in-game with {@code /test runall}.
 */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class PlaceholderRoomTests {

    @GameTest(template = "dungeon/start_placeholder")
    public static void startRoom(GameTestHelper helper) {
        BlockPos corner = BlockPos.containing(helper.getBounds().minX, helper.getBounds().minY, helper.getBounds().minZ);
        for (DungeonOre ore : DungeonOre.values()) {
            int count = 0;
            for (BlockPos pos : BlockPos.betweenClosed(corner, corner.offset(ROOM_SIZE - 1, ROOM_HEIGHT - 1, ROOM_SIZE - 1))) {
                if (helper.getLevel().getBlockState(pos).is(ore.ore.get())) {
                    count++;
                }
            }
            helper.assertTrue(count >= 3 * 10, "the start room should have three mounds of " + ore.key + " ore, but has " + count + " blocks");
        }
        checkRoom(helper);
    }

    @GameTest(template = "dungeon/common_pillars_placeholder")
    public static void pillarsRoom(GameTestHelper helper) {
        checkRoom(helper);
    }

    @GameTest(template = "dungeon/common_steps_placeholder")
    public static void stepsRoom(GameTestHelper helper) {
        checkRoom(helper);
    }

    @GameTest(template = "dungeon/common_cover_placeholder")
    public static void coverRoom(GameTestHelper helper) {
        checkRoom(helper);
    }

    @GameTest(template = "dungeon/common_walls_placeholder")
    public static void wallsRoom(GameTestHelper helper) {
        checkRoom(helper);
    }

    @GameTest(template = "dungeon/quarry_placeholder")
    public static void quarryRoom(GameTestHelper helper) {
        checkRoom(helper);
    }

    @GameTest(template = "dungeon/treasure_placeholder")
    public static void treasureRoom(GameTestHelper helper) {
        checkRoom(helper);
    }

    @GameTest(template = "dungeon/challenge_hunt_placeholder")
    public static void huntRoom(GameTestHelper helper) {
        checkRoom(helper);
    }

    @GameTest(template = "dungeon/boss_placeholder")
    public static void bossRoom(GameTestHelper helper) {
        checkRoom(helper);
    }

    @GameTest(template = "dungeon/boss_skeleton_lord_placeholder")
    public static void skeletonLordHall(GameTestHelper helper) {
        checkRoom(helper);
    }

    @GameTest(template = "dungeon/boss_goblin_chief_placeholder")
    public static void goblinChiefArena(GameTestHelper helper) {
        checkRoom(helper);
    }

    @GameTest(template = "dungeon/tunnel_placeholder")
    public static void tunnel(GameTestHelper helper) {
        BlockPos corner = check(helper, Kind.TUNNEL, TUNNEL_WIDTH, TUNNEL_HEIGHT, TUNNEL_LENGTH);
        // Both ends are open across the doorway span, so the corridor meets each room's doorway.
        for (int z : new int[] { 0, TUNNEL_LENGTH - 1 }) {
            helper.assertTrue(helper.getLevel().getBlockState(corner.offset(TUNNEL_WIDTH / 2, DOOR_BOTTOM_Y + 2, z)).isAir(),
                    "tunnel ends should be open");
        }
        helper.succeed();
    }

    @GameTest(template = "dungeon/cap_placeholder")
    public static void cap(GameTestHelper helper) {
        check(helper, Kind.CAP, CAP_WIDTH, CAP_HEIGHT, CAP_DEPTH);
        helper.succeed();
    }

    /** Rooms meet the spec, and have walls: a block in the middle of each wall section away from the doorway. */
    private static void checkRoom(GameTestHelper helper) {
        BlockPos corner = check(helper, Kind.COMMON, ROOM_SIZE, ROOM_HEIGHT, ROOM_SIZE);
        int max = ROOM_SIZE - 1;
        for (BlockPos wall : new BlockPos[] { new BlockPos(4, 6, 0), new BlockPos(4, 6, max), new BlockPos(0, 6, 4), new BlockPos(max, 6, 4) }) {
            helper.assertFalse(helper.getLevel().getBlockState(corner.offset(wall)).isAir(), "placeholder rooms should have walls at " + wall.toShortString());
        }
        helper.succeed();
    }

    /** Checks a placed piece's size and runs it through the spec checker. Returns its minimum corner. */
    private static BlockPos check(GameTestHelper helper, Kind kind, int x, int y, int z) {
        AABB bounds = helper.getBounds();
        int sx = (int) Math.round(bounds.getXsize());
        int sy = (int) Math.round(bounds.getYsize());
        int sz = (int) Math.round(bounds.getZsize());
        helper.assertTrue(sx == x && sy == y && sz == z, "Expected size " + x + "x" + y + "x" + z + " but was " + sx + "x" + sy + "x" + sz);
        BlockPos corner = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        List<Problem> problems = PieceValidator.validate(kind, helper.getLevel(), corner);
        helper.assertTrue(problems.isEmpty(), kind + " placeholder doesn't meet the spec: "
                + (problems.isEmpty() ? "" : problems.getFirst().message() + " at local " + problems.getFirst().local().toShortString()));
        return corner;
    }
}
