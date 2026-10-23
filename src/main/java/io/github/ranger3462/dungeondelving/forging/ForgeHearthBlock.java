package io.github.ranger3462.dungeondelving.forging;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The heart of a forge. It checks the stations around it every couple of seconds and lights up once the workshop is
 * complete. Right-click it to see what the workshop has, what it's missing, its forge level and its workshop bonus.
 */
public class ForgeHearthBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ForgeHearthBlock> CODEC = simpleCodec(ForgeHearthBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** How often the hearth checks its workshop, in ticks. */
    private static final int CHECK_INTERVAL = 40;
    /**
     * The hearth's outline, for each way it can face: its two-tier base, the firebox under the lintel, and the fire tray
     * at the front. Matches HEARTH_SHAPE in tools/forging_resources.py (the model), given there facing north.
     */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            SHAPES.put(facing, Shapes.or(
                    turned(facing, 0, 0, 0, 16, 2, 16),
                    turned(facing, 1, 2, 2, 15, 4, 15),
                    turned(facing, 4.5, 4, 2.5, 11.5, 5, 6),
                    turned(facing, 2, 4, 6, 14, 16, 15)));
        }
    }

    /** A box given facing north (in pixels), turned to face another way, as the block's model is. */
    private static VoxelShape turned(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }

    public ForgeHearthBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean complete = ForgeWorkshop.scan(level, pos).complete();
        if (complete != state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, complete), Block.UPDATE_ALL);
            if (complete) {
                level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8f, 0.8f);
            }
        }
        level.scheduleTick(pos, this, CHECK_INTERVAL);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            report(ForgeWorkshop.scan(level, pos)).forEach(player::sendSystemMessage);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** The lines a player sees when they right-click the hearth. */
    static List<Component> report(ForgeWorkshop workshop) {
        List<Component> lines = new java.util.ArrayList<>();
        if (workshop.complete()) {
            lines.add(Component.translatable("forge.dungeondelving.report.complete", workshop.forgeLevel(), StarredItem.starsText(workshop.maxStars()))
                    .withStyle(ChatFormatting.GOLD));
        } else {
            MutableComponent missing = Component.empty();
            for (int i = 0; i < workshop.missing().size(); i++) {
                missing.append(i == 0 ? Component.empty() : Component.literal(", ")).append(workshop.missing().get(i));
            }
            lines.add(Component.translatable("forge.dungeondelving.report.missing", missing).withStyle(ChatFormatting.RED));
        }
        lines.add(station("blast_furnace", workshop.blastFurnace()));
        lines.add(station("master_anvil", workshop.masterAnvil()));
        lines.add(station("bellows", workshop.bellows()));
        lines.add(station("oil_cauldron", workshop.oilCauldron()));
        lines.add(station("grinding_wheel", workshop.grindingWheel()));
        lines.add(Component.translatable("forge.dungeondelving.report.decor", workshop.decor(), Math.round(workshop.decorBonus() * 100))
                .withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private static Component station(String id, boolean present) {
        return Component.literal(present ? "  ✔ " : "  ✘ ").append(Component.translatable("forge.dungeondelving.upgrade." + id))
                .withStyle(present ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        // The flames are part of the model; only a little smoke rises from the top.
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x, y + 0.2, z, 0.0, 0.05, 0.0);
        }
        if (random.nextInt(40) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8f, 1.0f, false);
        }
    }
}
