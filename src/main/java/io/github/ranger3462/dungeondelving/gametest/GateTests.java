package io.github.ranger3462.dungeondelving.gametest;

import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces.Kind;
import io.github.ranger3462.dungeondelving.gate.DungeonGates;
import io.github.ranger3462.dungeondelving.gate.DungeonStone;
import io.github.ranger3462.dungeondelving.gate.GateArea;
import io.github.ranger3462.dungeondelving.gate.GateTunerBlock;
import io.github.ranger3462.dungeondelving.gate.GateTunerBlockEntity;
import io.github.ranger3462.dungeondelving.gate.RiftPortalBlock;
import io.github.ranger3462.dungeondelving.gate.RunTuning;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the Dungeon Gate (DD-75): its Riftstone frame, the rift a key opens, Gate Tuners and dungeon stones. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class GateTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    /** A gate with a 2x3 Riftstone frame running along x, three blocks east of it. Returns the gate's position. */
    private static BlockPos buildGate(ServerLevel level, BlockPos gate) {
        level.setBlock(gate, ModRegistries.DUNGEON_GATE.get().defaultBlockState(), Block.UPDATE_ALL);
        BlockPos corner = gate.east(3);
        BlockState riftstone = ModRegistries.RIFTSTONE.get().defaultBlockState();
        for (int i = -1; i <= 2; i++) {
            level.setBlock(corner.east(i).below(), riftstone, Block.UPDATE_ALL);
            level.setBlock(corner.east(i).above(3), riftstone, Block.UPDATE_ALL);
        }
        for (int y = 0; y < 3; y++) {
            level.setBlock(corner.west().above(y), riftstone, Block.UPDATE_ALL);
            level.setBlock(corner.east(2).above(y), riftstone, Block.UPDATE_ALL);
        }
        return gate;
    }

    private static void clear(ServerLevel level, BlockPos gate) {
        for (BlockPos pos : BlockPos.betweenClosed(gate.offset(-GateArea.RADIUS, -2, -GateArea.RADIUS), gate.offset(GateArea.RADIUS, 6, GateArea.RADIUS))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static BlockPos spot(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(1, 1, 1)).above(130);
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void theGateFindsItsRiftstoneFrame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos gate = buildGate(level, spot(helper));
        try {
            GateArea area = GateArea.scan(level, gate);
            helper.assertTrue(area.frame().isPresent(), "the gate should find its frame");
            helper.assertTrue(area.frame().get().width() == 2 && area.frame().get().height() == 3 && area.frame().get().opening().size() == 6,
                    "the opening should be 2x3, got " + area.frame().get());
            level.setBlock(gate.east(3).above(3), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(GateArea.scan(level, gate).frame().isEmpty(), "a frame with a gap in its top isn't a frame");
        } finally {
            clear(level, gate);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void aKeyOpensTheRiftAndBreakingTheFrameClosesIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos gate = buildGate(level, spot(helper));
        try {
            helper.assertTrue(DungeonGates.light(level, gate, 3) == DungeonGates.Light.LIT, "a key should light the frame");
            List<BlockPos> opening = GateArea.scan(level, gate).frame().orElseThrow().opening();
            helper.assertTrue(opening.stream().allMatch(pos -> level.getBlockState(pos).is(DungeonGates.RIFT_PORTAL.get())
                    && level.getBlockState(pos).getValue(RiftPortalBlock.TIER) == 3), "the whole opening should be a tier 3 rift");
            helper.assertTrue(DungeonGates.light(level, gate, 3) == DungeonGates.Light.ALREADY_OPEN, "an open rift shouldn't take another key");
            level.setBlock(gate.east(2).above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); // Break the frame's left side.
            helper.assertTrue(opening.stream().noneMatch(pos -> level.getBlockState(pos).is(DungeonGates.RIFT_PORTAL.get())),
                    "the rift should fall apart when its frame breaks");
            helper.assertTrue(DungeonGates.light(clearAndRebuild(level, gate), gate, 1) == DungeonGates.Light.LIT, "a rebuilt frame can be lit again");
        } finally {
            clear(level, gate);
        }
        helper.succeed();
    }

    private static ServerLevel clearAndRebuild(ServerLevel level, BlockPos gate) {
        clear(level, gate);
        buildGate(level, gate);
        return level;
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void tunersHandOverTheirStonesUpToTheGatesSlots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos gate = buildGate(level, spot(helper));
        BlockPos near = gate.west(2);
        BlockPos far = gate.west(4);
        try {
            for (BlockPos pos : List.of(near, far)) {
                level.setBlock(pos, DungeonGates.GATE_TUNER.get().defaultBlockState(), Block.UPDATE_ALL);
                ((GateTunerBlockEntity) level.getBlockEntity(pos)).setStone(new ItemStack(DungeonGates.BLOODSHED_STONE.get()));
            }
            helper.assertTrue(level.getBlockState(near).getValue(GateTunerBlock.TUNED), "a tuner with a stone shows it");
            RunTuning tuning = DungeonGates.takeStones(level, gate);
            helper.assertTrue(tuning.stones().equals(List.of(DungeonStone.BLOODSHED)),
                    "a gate with " + GateArea.BASE_TUNER_SLOTS + " slot should use one stone, got " + tuning.stones());
            helper.assertTrue(((GateTunerBlockEntity) level.getBlockEntity(near)).stone().isEmpty() && !level.getBlockState(near).getValue(GateTunerBlock.TUNED),
                    "the nearest tuner's stone should be used up");
            helper.assertFalse(((GateTunerBlockEntity) level.getBlockEntity(far)).stone().isEmpty(), "a tuner past the gate's slots keeps its stone");
        } finally {
            clear(level, gate);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void bloodshedFavorsFightingRooms(GameTestHelper helper) {
        DungeonPieces pieces = DungeonPieces.load(helper.getLevel().getServer());
        RunTuning bloodshed = new RunTuning(List.of(DungeonStone.BLOODSHED));
        RandomSource random = RandomSource.create(5);
        int plain = 0;
        int tuned = 0;
        int rolls = 2000;
        for (int i = 0; i < rolls; i++) {
            plain += fighting(pieces.pick(Kind.CHALLENGE, random)) ? 1 : 0;
            tuned += fighting(pieces.pick(Kind.CHALLENGE, random, piece -> bloodshed.pieceWeight(Kind.CHALLENGE, piece))) ? 1 : 0;
        }
        helper.assertTrue(tuned > rolls * 0.7 && tuned > plain + rolls / 5,
                "Bloodshed should make hunts and waves far likelier: " + plain + " without, " + tuned + " with, of " + rolls);
        helper.succeed();
    }

    private static boolean fighting(ResourceLocation piece) {
        return piece.getPath().contains("hunt") || piece.getPath().contains("wave");
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void tunersAndStonesAreCraftable(GameTestHelper helper) {
        ItemStack riftstone = new ItemStack(ModRegistries.RIFTSTONE_ITEM.get());
        ItemStack tuner = craft(helper, ItemStack.EMPTY, new ItemStack(Items.AMETHYST_SHARD), ItemStack.EMPTY, riftstone, new ItemStack(Items.IRON_INGOT),
                riftstone, riftstone, riftstone, riftstone);
        helper.assertTrue(tuner.is(DungeonGates.GATE_TUNER_ITEM.get()), "Riftstone, iron and amethyst should make a Gate Tuner, got " + tuner);
        if (ModList.get().isLoaded("irons_spellbooks")) {
            ItemStack vial = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "blood_vial")));
            ItemStack stone = craft(helper, ItemStack.EMPTY, riftstone, ItemStack.EMPTY, riftstone, vial, riftstone, ItemStack.EMPTY, riftstone, ItemStack.EMPTY);
            helper.assertTrue(stone.is(DungeonGates.BLOODSHED_STONE.get()), "Riftstone around a Blood Vial should make a Bloodshed Stone, got " + stone);
        }
        helper.succeed();
    }

    private static ItemStack craft(GameTestHelper helper, ItemStack... items) {
        CraftingInput input = CraftingInput.of(3, 3, List.of(items));
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(recipe -> recipe.value().assemble(input, helper.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
    }
}
