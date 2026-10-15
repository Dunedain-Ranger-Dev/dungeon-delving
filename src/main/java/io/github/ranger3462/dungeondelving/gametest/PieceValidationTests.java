package io.github.ranger3462.dungeondelving.gametest;

import static io.github.ranger3462.dungeondelving.dungeon.RoomSpec.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuilder;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonBuilder.Placement;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces.Kind;
import io.github.ranger3462.dungeondelving.dungeon.layout.PieceTemplates;
import io.github.ranger3462.dungeondelving.dungeon.layout.PieceValidator;
import io.github.ranger3462.dungeondelving.dungeon.layout.PieceValidator.Problem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Checks every dungeon piece in the mod against the spec, so a delivered room that doesn't fit fails the build, and
 * checks the validator really catches problems.
 */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class PieceValidationTests {
    // These tests don't use the placed structure; the cap is just the smallest available piece.
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void everyPieceMeetsSpec(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonPieces pieces = DungeonPieces.load(level.getServer());
        BlockPos corner = helper.absolutePos(BlockPos.ZERO).above(120);
        List<String> failures = new ArrayList<>();

        for (Map.Entry<ResourceLocation, Kind> entry : pieces.all().entrySet()) {
            ResourceLocation id = entry.getKey();
            Kind kind = entry.getValue();
            Vec3i size = level.getStructureManager().get(id).orElseThrow().getSize();
            if (!size.equals(kind.size())) {
                failures.add(id.getPath() + " is " + sizeText(size) + " but a " + kind + " piece must be " + sizeText(kind.size()));
                continue;
            }
            DungeonBuilder.place(level, new Placement(id, corner, Rotation.NONE, Mirror.NONE), false);
            try {
                List<Problem> problems = PieceValidator.validate(kind, level, corner);
                if (!problems.isEmpty()) {
                    failures.add(id.getPath() + ": " + problems.getFirst().message() + " at local " + problems.getFirst().local().toShortString()
                            + (problems.size() > 1 ? " (+" + (problems.size() - 1) + " more)" : ""));
                }
            } finally {
                clear(level, corner, corner.offset(size).offset(-1, -1, -1));
            }
        }
        helper.assertTrue(failures.isEmpty(), "Pieces that don't meet the spec: " + String.join("; ", failures));
        helper.assertTrue(pieces.skipped().isEmpty(), "Pieces skipped for the wrong size: " + pieces.skipped());
        helper.assertTrue(pieces.all().size() >= Kind.values().length, "every kind of piece should exist");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void validatorCatchesBrokenPieces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(BlockPos.ZERO).above(120);
        int doorMid = DOOR_START + DOOR_WIDTH / 2;
        try {
            // A room with a block in its north doorway spot and a hole in its floor.
            DungeonBuilder.place(level, new Placement(DungeonPieces.id("start_placeholder"), corner, Rotation.NONE, Mirror.NONE), false);
            level.setBlock(corner.offset(doorMid, DOOR_BOTTOM_Y, 0), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(corner.offset(5, 0, 5), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            List<Problem> problems = PieceValidator.validate(Kind.START, level, corner);
            helper.assertTrue(problems.size() == 2, "expected 2 problems, found " + problems.size());
            helper.assertTrue(problems.stream().anyMatch(p -> p.message().startsWith("North doorway")), "blocked doorway should be reported");
            helper.assertTrue(problems.stream().anyMatch(p -> p.message().startsWith("Floor")), "floor hole should be reported");
        } finally {
            clear(level, corner, corner.offset(ROOM_SIZE - 1, ROOM_HEIGHT - 1, ROOM_SIZE - 1));
        }
        try {
            // A cap with a gap where the doorway would be.
            DungeonBuilder.place(level, new Placement(DungeonPieces.id("cap_placeholder"), corner, Rotation.NONE, Mirror.NONE), false);
            level.setBlock(corner.offset(doorMid, DOOR_BOTTOM_Y, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.assertTrue(PieceValidator.validate(Kind.CAP, level, corner).size() == 1, "a cap gap should be reported");
        } finally {
            clear(level, corner, corner.offset(WALL_LENGTH - 1, WALL_HEIGHT - 1, 0));
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void templatesMeetSpec(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(BlockPos.ZERO).above(120);
        for (Kind kind : Kind.values()) {
            Vec3i size = kind.size();
            try {
                helper.assertTrue(PieceTemplates.firstBlockInTheWay(kind, level, corner).isEmpty(), "test space should start empty");
                PieceTemplates.build(kind, level, corner);
                List<Problem> problems = PieceValidator.validate(kind, level, corner);
                helper.assertTrue(problems.isEmpty(), "a fresh " + kind + " template should meet the spec, but: "
                        + (problems.isEmpty() ? "" : problems.getFirst().message()));
                helper.assertTrue(PieceTemplates.firstBlockInTheWay(kind, level, corner).isPresent(), "a template shouldn't be built over existing blocks");
            } finally {
                // Include the one-block ring outside the piece, where the red wool doorway outlines go.
                clear(level, corner.offset(-1, -1, -1), corner.offset(size));
            }
        }
        helper.succeed();
    }

    private static String sizeText(Vec3i size) {
        return size.getX() + "x" + size.getY() + "x" + size.getZ();
    }

    private static void clear(ServerLevel level, BlockPos min, BlockPos max) {
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
