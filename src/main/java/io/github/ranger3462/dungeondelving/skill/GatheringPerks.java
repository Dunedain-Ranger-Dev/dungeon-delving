package io.github.ranger3462.dungeondelving.skill;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The gathering skills' perks (DD-56), from their trees ({@link Perk}).
 * <ul>
 *   <li>Vein Miner (Mining): sneak and mine an ore to mine the rest of the vein, up to 8, 16 or 32 more blocks.</li>
 *   <li>Tree Feller (Woodcutting): sneak and chop a log to fell the tree, up to 32, 64 or 160 more logs. Only trees: the logs
 *   must touch natural leaves, so log builds are safe.</li>
 *   <li>Prospector (Mining): a chance of a second drop from ore (not with Silk Touch).</li>
 *   <li>Forester (Woodcutting): chopping a tree's bottom log sometimes plants its sapling, or four for a 2×2 trunk (giant
 *   spruce, dark oak, giant jungle) wherever the trunk's been cleared. Only trees, like Tree Feller.</li>
 *   <li>Bountiful (Farming): a chance of an extra crop from a ripe harvest.</li>
 *   <li>Replanter (Farming): a ripe crop you harvest plants itself again, using one of its seeds.</li>
 * </ul>
 * The extra blocks are broken as if the player broke them, so the tool wears, drops and skill XP come as usual, and the
 * chain stops before the tool would break.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class GatheringPerks {
    static final int[] VEIN_LIMIT = { 8, 16, 32 };
    static final int[] TREE_LIMIT = { 32, 64, 160 };
    public static final float PROSPECTOR_CHANCE = 0.10f;
    public static final float FORESTER_CHANCE = 0.5f;
    /** Bountiful's chance of an extra crop, a rank. */
    public static final float BOUNTIFUL_CHANCE = 0.15f;

    /** Blocks to set on the next server tick: replanted crops and saplings (the broken block isn't gone yet when drops are made). */
    private record Planting(ServerLevel level, BlockPos pos, BlockState state) {}

    private static final List<Planting> PLANTINGS = new ArrayList<>();
    /**
     * Logs just broken by a Forester that are part of a tree, checked when the log breaks: by the time its drops are
     * made, Tree Feller has already taken the rest of the tree, and its leaves with it as far as the check can see.
     */
    private static final Map<BlockPos, List<BlockPos>> FORESTER_TREES = new HashMap<>();
    /** True while we're breaking a chain's extra blocks, so they don't start chains of their own. */
    private static boolean chaining;

    private GatheringPerks() {}

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (chaining || !(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        if (Perk.FORESTER.has(player) && isTreeBase(level, event.getPos(), state)) {
            FORESTER_TREES.put(event.getPos().immutable(), trunkBase(level, event.getPos(), state));
        }
        if (!player.isShiftKeyDown()) {
            return;
        }
        int limit = 0;
        boolean tree = false;
        if (state.is(Tags.Blocks.ORES) && Perk.VEIN_MINER.has(player) && player.hasCorrectToolForDrops(state)) {
            limit = VEIN_LIMIT[Perk.VEIN_MINER.rank(player) - 1];
        } else if (state.is(BlockTags.LOGS) && Perk.TREE_FELLER.has(player)) {
            limit = TREE_LIMIT[Perk.TREE_FELLER.rank(player) - 1];
            tree = true;
        }
        if (limit == 0) {
            return;
        }
        Set<BlockPos> chain = connected(level, event.getPos(), state.getBlock(), limit);
        if (tree && !touchesNaturalLeaves(level, chain, event.getPos())) {
            return;
        }
        chain.remove(event.getPos());
        chaining = true;
        try {
            for (BlockPos pos : chain) {
                ItemStack tool = player.getMainHandItem();
                if (tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() <= 1) {
                    break; // Stop before the tool breaks.
                }
                player.gameMode.destroyBlock(pos);
            }
        } finally {
            chaining = false;
        }
    }

    /** Whether this log is the bottom of a tree: on dirt, and part of logs that touch natural leaves. */
    public static boolean isTreeBase(ServerLevel level, BlockPos pos, BlockState state) {
        return state.is(BlockTags.LOGS) && level.getBlockState(pos.below()).is(BlockTags.DIRT)
                && touchesNaturalLeaves(level, connected(level, pos, state.getBlock(), TREE_LIMIT[TREE_LIMIT.length - 1]), pos);
    }

    /**
     * Where a tree's saplings go: the bottom log's spot, or all four of a 2×2 trunk's bottom logs if it's one of those
     * (the same log, side by side, each on dirt), since those trees only grow back from four saplings in a square.
     */
    public static List<BlockPos> trunkBase(ServerLevel level, BlockPos pos, BlockState state) {
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                List<BlockPos> square = List.of(pos.offset(dx, 0, dz), pos.offset(dx + 1, 0, dz), pos.offset(dx, 0, dz + 1), pos.offset(dx + 1, 0, dz + 1));
                if (square.stream().allMatch(spot -> level.getBlockState(spot).is(state.getBlock()) && level.getBlockState(spot.below()).is(BlockTags.DIRT))) {
                    return square.stream().map(BlockPos::immutable).toList();
                }
            }
        }
        return List.of(pos.immutable());
    }

    /** The blocks of this kind connected to start (corners count), nearest first, up to limit beyond the start. */
    static Set<BlockPos> connected(ServerLevel level, BlockPos start, Block block, int limit) {
        Set<BlockPos> found = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        found.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty() && found.size() <= limit) {
            BlockPos here = queue.poll();
            for (BlockPos next : BlockPos.betweenClosed(here.offset(-1, -1, -1), here.offset(1, 1, 1))) {
                if (found.size() > limit) {
                    break;
                }
                if (!found.contains(next) && level.getBlockState(next).is(block)) {
                    BlockPos kept = next.immutable();
                    found.add(kept);
                    queue.add(kept);
                }
            }
        }
        return found;
    }

    /** Whether any of these logs (besides the one being chopped's own spot) has natural leaves next to it. */
    static boolean touchesNaturalLeaves(ServerLevel level, Set<BlockPos> logs, BlockPos chopped) {
        Set<BlockPos> checked = new HashSet<>();
        for (BlockPos log : logs) {
            for (BlockPos next : BlockPos.betweenClosed(log.offset(-1, -1, -1), log.offset(1, 1, 1))) {
                if (checked.add(next.immutable())) {
                    BlockState state = level.getBlockState(next);
                    if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || event.getDrops().isEmpty()) {
            return;
        }
        ServerLevel level = event.getLevel();
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        List<ItemEntity> drops = event.getDrops();
        if (state.is(Tags.Blocks.ORES) && Perk.PROSPECTOR.has(player) && !droppedItself(drops, state)
                && level.getRandom().nextFloat() < PROSPECTOR_CHANCE) {
            extra(level, drops, drops.getFirst().getItem());
        }
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            int bountiful = Perk.BOUNTIFUL.rank(player);
            if (bountiful > 0 && level.getRandom().nextFloat() < BOUNTIFUL_CHANCE * bountiful) {
                extra(level, drops, drops.getFirst().getItem());
            }
            if (Perk.REPLANTER.has(player) && takeSeed(drops, crop.getCloneItemStack(level, pos, state))) {
                PLANTINGS.add(new Planting(level, pos.immutable(), crop.getStateForAge(0)));
            }
        }
        List<BlockPos> base = state.is(BlockTags.LOGS) ? FORESTER_TREES.remove(pos) : null;
        if (base != null && level.getRandom().nextFloat() < FORESTER_CHANCE) {
            // Any of a 2x2 trunk's spots still holding a log (it wasn't felled) get skipped when planting.
            saplingFor(state.getBlock()).ifPresent(sapling -> base.forEach(spot -> PLANTINGS.add(new Planting(level, spot, sapling.defaultBlockState()))));
        }
    }

    /** Silk Touch: the ore dropped as itself, so it gets no extra. */
    private static boolean droppedItself(List<ItemEntity> drops, BlockState state) {
        return drops.stream().anyMatch(drop -> drop.getItem().is(state.getBlock().asItem()));
    }

    private static void extra(ServerLevel level, List<ItemEntity> drops, ItemStack like) {
        ItemEntity first = drops.getFirst();
        drops.add(new ItemEntity(level, first.getX(), first.getY(), first.getZ(), like.copyWithCount(1)));
    }

    /** Takes one seed from the drops for replanting; false if there was none. */
    private static boolean takeSeed(List<ItemEntity> drops, ItemStack seed) {
        for (ItemEntity drop : drops) {
            if (!seed.isEmpty() && drop.getItem().is(seed.getItem())) {
                drop.getItem().shrink(1);
                if (drop.getItem().isEmpty()) {
                    drops.remove(drop);
                }
                return true;
            }
        }
        return false;
    }

    /** A log's sapling, by name: oak_log to oak_sapling, and so on. Empty for logs without one (stems, stripped logs). */
    static Optional<Block> saplingFor(Block log) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(log);
        if (!id.getPath().endsWith("_log") || id.getPath().startsWith("stripped_")) {
            return Optional.empty();
        }
        ResourceLocation sapling = id.withPath(id.getPath().replace("_log", "_sapling"));
        return BuiltInRegistries.BLOCK.getOptional(sapling).filter(block -> block != Blocks.AIR);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        FORESTER_TREES.clear(); // Any whose drops never came (a break that was cancelled).
        if (PLANTINGS.isEmpty()) {
            return;
        }
        for (Planting planting : List.copyOf(PLANTINGS)) {
            if (planting.level.getBlockState(planting.pos).isAir() && planting.state.canSurvive(planting.level, planting.pos)) {
                planting.level.setBlockAndUpdate(planting.pos, planting.state);
            }
        }
        PLANTINGS.clear();
    }
}
