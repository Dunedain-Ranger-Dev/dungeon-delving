package io.github.ranger3462.dungeondelving.gametest;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Checks that Riftstone turns up in normal play, as veins deep underground. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class RiftstoneTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void riftstoneVeinsGrowInStone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ConfiguredFeature<?, ?> vein = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(DungeonDelving.id("riftstone_vein"));
        helper.assertTrue(vein != null, "the riftstone_vein feature should load");
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1)).above(120);
        int r = 5;
        try {
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            int riftstone = 0;
            RandomSource random = RandomSource.create(3);
            for (int attempt = 0; attempt < 5 && riftstone == 0; attempt++) {
                vein.place(level, level.getChunkSource().getGenerator(), random, center);
                for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
                    riftstone += level.getBlockState(pos).is(ModRegistries.RIFTSTONE.get()) ? 1 : 0;
                }
            }
            helper.assertTrue(riftstone > 5, "a vein should turn stone into Riftstone, made " + riftstone);
        } finally {
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r - 3, -r - 3, -r - 3), center.offset(r + 3, r + 3, r + 3))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void riftstoneVeinsAreAddedToTheOverworld(GameTestHelper helper) {
        var modifiers = helper.getLevel().registryAccess().registryOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS);
        helper.assertTrue(modifiers.containsKey(DungeonDelving.id("riftstone_vein")), "the biome modifier adding Riftstone veins should load");
        helper.succeed();
    }
}
