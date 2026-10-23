package io.github.ranger3462.dungeondelving.gate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonTravel;
import io.github.ranger3462.dungeondelving.dungeon.RoomCounts;
import io.github.ranger3462.dungeondelving.dungeon.layout.LayoutType;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The way into dungeons (DD-75). Place a Dungeon Gate, build a Riftstone frame near it ({@link GateArea}), and use a
 * Runic Key on the gate: the frame fills with a rift of the key's tier. Stepping in starts the run, tuned by the dungeon
 * stones in the gate's Gate Tuners (which are used up), and closes the rift.
 */
public final class DungeonGates {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DungeonDelving.MODID);

    public static final DeferredBlock<GateTunerBlock> GATE_TUNER = ModRegistries.BLOCKS.registerBlock("gate_tuner", GateTunerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(3.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.LODESTONE)
                    .lightLevel(state -> state.getValue(GateTunerBlock.TUNED) ? 7 : 0));
    public static final DeferredItem<BlockItem> GATE_TUNER_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(GATE_TUNER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GateTunerBlockEntity>> TUNER_ENTITY = BLOCK_ENTITIES.register("gate_tuner",
            () -> BlockEntityType.Builder.of(GateTunerBlockEntity::new, GATE_TUNER.get()).build(null));

    public static final DeferredBlock<RiftPortalBlock> RIFT_PORTAL = ModRegistries.BLOCKS.registerBlock("rift_portal", RiftPortalBlock::new,
            BlockBehaviour.Properties.of().noCollission().strength(-1.0f).sound(SoundType.GLASS).lightLevel(state -> 11).noLootTable()
                    .pushReaction(PushReaction.BLOCK));

    public static final DeferredItem<DungeonStoneItem> BLOODSHED_STONE = ModRegistries.ITEMS.registerItem("bloodshed_stone",
            properties -> new DungeonStoneItem(DungeonStone.BLOODSHED, properties), new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

    private DungeonGates() {}

    /** Makes sure the blocks and items above are registered; call once while setting up registries. */
    public static void init() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }

    // --- Opening a rift ---

    /** The result of using a Runic Key on a gate. */
    public enum Light { LIT, NO_FRAME, ALREADY_OPEN }

    /** Fills the gate's Riftstone frame with a rift of this tier, if it has one and it isn't already open. */
    public static Light light(Level level, BlockPos gate, int tier) {
        Optional<GateArea.Frame> frame = GateArea.scan(level, gate).frame();
        if (frame.isEmpty()) {
            return Light.NO_FRAME;
        }
        if (level.getBlockState(frame.get().opening().getFirst()).is(RIFT_PORTAL.get())) {
            return Light.ALREADY_OPEN;
        }
        BlockState rift = RIFT_PORTAL.get().defaultBlockState().setValue(RiftPortalBlock.AXIS, frame.get().axis()).setValue(RiftPortalBlock.TIER, tier);
        for (BlockPos pos : frame.get().opening()) {
            // Without neighbor updates, so the half-built rift doesn't fall apart before it's whole.
            level.setBlock(pos, rift, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        level.playSound(null, gate, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.6f, 1.4f);
        return Light.LIT;
    }

    // --- Stepping in ---

    /** A player stepped into a rift: start the run, tuned by the gate's tuners, and close the rift. */
    public static void enterRift(ServerPlayer player, ServerLevel level, BlockPos riftPos) {
        BlockState rift = level.getBlockState(riftPos);
        if (!rift.is(RIFT_PORTAL.get()) || player.level() != level || !canEnter(player)) {
            return;
        }
        int tier = rift.getValue(RiftPortalBlock.TIER);
        RunTuning tuning = nearestGate(level, riftPos).map(gate -> takeStones(level, gate)).orElse(RunTuning.NONE);
        close(level, riftPos);
        DungeonTravel.Entry entry = DungeonTravel.enter(player, RoomCounts.roll(tier, level.getRandom()), tier, LayoutType.roll(player.getRandom()),
                DungeonFactions.pick(tier, player.getRandom()), tuning);
        announce(player, entry, tier, tuning);
    }

    /** Whether a player can start a run: not already in one. Tells them why not. */
    public static boolean canEnter(ServerPlayer player) {
        if (player.level().dimension() == DungeonDimension.DUNGEON || DungeonRegions.get().run(player.getUUID()).isPresent()) {
            player.displayClientMessage(Component.translatable("message.dungeondelving.gate_already_inside").withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    /** Uses up the stones in a gate's tuners (as many tuners as it has slots), returning what they do. */
    public static RunTuning takeStones(Level level, BlockPos gate) {
        List<DungeonStone> stones = new ArrayList<>();
        for (BlockPos pos : GateArea.scan(level, gate).usedTuners()) {
            if (level.getBlockEntity(pos) instanceof GateTunerBlockEntity tuner && tuner.tunedStone() != null) {
                stones.add(tuner.tunedStone());
                tuner.take();
            }
        }
        return new RunTuning(stones);
    }

    /** The Dungeon Gate nearest a rift, within reach of its frame. */
    static Optional<BlockPos> nearestGate(Level level, BlockPos rift) {
        int reach = GateArea.RADIUS + GateArea.MAX_WIDTH;
        BlockPos nearest = null;
        for (BlockPos pos : BlockPos.betweenClosed(rift.offset(-reach, -GateArea.ABOVE - GateArea.MAX_HEIGHT, -reach),
                rift.offset(reach, GateArea.BELOW + GateArea.MAX_HEIGHT, reach))) {
            if (level.getBlockState(pos).is(ModRegistries.DUNGEON_GATE.get()) && (nearest == null || pos.distSqr(rift) < nearest.distSqr(rift))) {
                nearest = pos.immutable();
            }
        }
        return Optional.ofNullable(nearest);
    }

    /** Removes a whole rift, from any block of it. */
    public static void close(Level level, BlockPos start) {
        Deque<BlockPos> queue = new ArrayDeque<>(List.of(start));
        Set<BlockPos> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            BlockPos pos = queue.pop();
            if (!seen.add(pos) || !level.getBlockState(pos).is(RIFT_PORTAL.get())) {
                continue;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            for (Direction direction : Direction.values()) {
                queue.add(pos.relative(direction));
            }
        }
    }

    private static void announce(ServerPlayer player, DungeonTravel.Entry entry, int tier, RunTuning tuning) {
        int keys = DungeonRegions.get().progress(entry.run().region()).map(progress -> progress.keysNeeded()).orElse(0);
        player.sendSystemMessage(Component.translatable("message.dungeondelving.gate_entered", tier, entry.layout().rooms().size(), keys)
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable("message.dungeondelving.faction." + entry.faction()).withStyle(ChatFormatting.DARK_RED));
        if (entry.type() != LayoutType.NORMAL) {
            player.sendSystemMessage(Component.translatable("message.dungeondelving.layout." + entry.type().id()).withStyle(ChatFormatting.YELLOW));
        }
        if (!tuning.isEmpty()) {
            String stones = tuning.stones().stream().map(stone -> Component.translatable("item.dungeondelving." + stone.id() + "_stone").getString())
                    .collect(Collectors.joining(", "));
            player.sendSystemMessage(Component.translatable("message.dungeondelving.gate_tuned", stones).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** What a gate has around it, for players checking on it. */
    public static Component status(Level level, BlockPos gate) {
        GateArea area = GateArea.scan(level, gate);
        Component frame = area.frame().map(f -> Component.translatable("message.dungeondelving.gate_frame", f.width(), f.height()))
                .orElse(Component.translatable("message.dungeondelving.gate_no_frame_short"));
        long tuned = area.usedTuners().stream().filter(pos -> level.getBlockEntity(pos) instanceof GateTunerBlockEntity tuner && tuner.tunedStone() != null)
                .count();
        return Component.translatable("message.dungeondelving.gate_status", frame, area.tuners().size(), area.tunerSlots(), tuned);
    }
}
