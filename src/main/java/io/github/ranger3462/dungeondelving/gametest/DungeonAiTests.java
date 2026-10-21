package io.github.ranger3462.dungeondelving.gametest;

import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.*;

import java.util.ArrayList;
import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuilder;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonLayout.Cell;
import io.github.ranger3462.dungeondelving.entity.Goblin;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks dungeon mob behavior: goblins swarming, and finding which room a position belongs to (for room leashes). */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class DungeonAiTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void hittingOneGoblinSendsTheNearbyOnes(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(0, 1, 0)).above(100);
        Goblin hit = goblin(helper, base);
        Goblin near = goblin(helper, base.east(10));
        Goblin far = goblin(helper, base.east(30));
        Zombie attacker = EntityType.ZOMBIE.create(helper.getLevel());
        attacker.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 2.5);
        helper.getLevel().addFreshEntity(attacker);
        try {
            hit.hurt(helper.getLevel().damageSources().mobAttack(attacker), 1.0f);
            helper.assertTrue(near.getTarget() == attacker, "a goblin 10 blocks away should join the fight");
            helper.assertTrue(far.getTarget() != attacker, "a goblin 30 blocks away is out of earshot");
        } finally {
            for (var entity : List.of(hit, near, far, attacker)) {
                entity.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void positionsMapToTheirRoom(GameTestHelper helper) {
        for (int region : new int[] { 0, 3 }) {
            BlockPos center = DungeonDimension.regionCenter(region);
            for (Cell cell : List.of(Cell.ORIGIN, new Cell(2, -1), new Cell(-3, 4))) {
                BlockPos min = DungeonBuilder.roomMin(center, cell);
                List<BlockPos> inside = new ArrayList<>(List.of(min.offset(1, 1, 1), min.offset(ROOM_SIZE - 2, 5, ROOM_SIZE - 2),
                        min.offset(ROOM_SIZE / 2, 1, ROOM_SIZE / 2)));
                for (BlockPos pos : inside) {
                    helper.assertTrue(DungeonDimension.regionAt(pos) == region, pos + " should be in region " + region);
                    helper.assertTrue(DungeonDimension.roomMinAt(pos).equals(min), pos + " should be in the room at " + min);
                }
                helper.assertTrue(DungeonDimension.roomCenterAt(min).equals(min.offset(ROOM_SIZE / 2, 0, ROOM_SIZE / 2)),
                        "a room's center should be in its middle");
            }
        }
        helper.succeed();
    }

    private static Goblin goblin(GameTestHelper helper, BlockPos pos) {
        Goblin goblin = ModEntities.GOBLIN.get().create(helper.getLevel());
        goblin.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        helper.getLevel().addFreshEntity(goblin);
        return goblin;
    }
}
