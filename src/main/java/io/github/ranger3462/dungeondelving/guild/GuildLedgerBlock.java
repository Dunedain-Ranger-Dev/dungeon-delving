package io.github.ranger3462.dungeondelving.guild;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;
import io.github.ranger3462.dungeondelving.forging.ForgeWorkshop;
import io.github.ranger3462.dungeondelving.forging.StarredItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Guild Ledger (DD-88): marks a guild workshop for every crafting profession, in place of the old Forge Hearth.
 * Stations within {@link ForgeWorkshop#RADIUS} blocks across and {@link ForgeWorkshop#HEIGHT} up or down count
 * towards it. Right-click it to see each profession's stations, what's missing and its workshop level. Shaped like a
 * lectern (and drawn as one, for now).
 */
public class GuildLedgerBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<GuildLedgerBlock> CODEC = simpleCodec(GuildLedgerBlock::new);

    public GuildLedgerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** The lectern's outline, facing the same way. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, state.getValue(FACING)).getShape(level, pos, context);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, state.getValue(FACING)).getCollisionShape(level, pos, context);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            report(level, pos).forEach(player::sendSystemMessage);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** What a player sees when they read the ledger: each profession's workshop. */
    public static List<Component> report(Level level, BlockPos pos) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("guild.dungeondelving.report.header").withStyle(ChatFormatting.YELLOW));
        lines.addAll(forging(ForgeWorkshop.scan(level, pos)));
        lines.add(Component.translatable("guild.dungeondelving.report.more").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** The forge's part of the report. */
    static List<Component> forging(ForgeWorkshop workshop) {
        List<Component> lines = new ArrayList<>();
        MutableComponent title = Component.translatable("guild.dungeondelving.profession.forging").withStyle(ChatFormatting.WHITE);
        if (workshop.complete()) {
            lines.add(title.append(Component.literal(": ")).append(Component.translatable("forge.dungeondelving.report.complete", workshop.forgeLevel(),
                    StarredItem.starsText(workshop.maxStars())).withStyle(ChatFormatting.GOLD)));
        } else {
            MutableComponent missing = Component.empty();
            for (int i = 0; i < workshop.missing().size(); i++) {
                missing.append(i == 0 ? Component.empty() : Component.literal(", ")).append(workshop.missing().get(i));
            }
            lines.add(title.append(Component.literal(": ")).append(Component.translatable("forge.dungeondelving.report.missing", missing)
                    .withStyle(ChatFormatting.RED)));
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
}
