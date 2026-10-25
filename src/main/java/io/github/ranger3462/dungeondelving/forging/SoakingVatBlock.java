package io.github.ranger3462.dungeondelving.forging;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import io.github.ranger3462.dungeondelving.skill.CraftingSkills;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A vat for the crafts' timed step: pieces go in, and come out changed a while later. Three kinds:
 * <ul>
 * <li>{@link Type#TANNING_VAT} (Leatherworking): a cauldron of tanning liquor. Pour liquor into a cauldron to make one;
 * scraped hides tan into tanned leather. No heat.</li>
 * <li>{@link Type#TANNING_BARREL} (a level 2 tannery): the same, but holds twice as many hides and tans faster.</li>
 * <li>{@link Type#SOAKING_CAULDRON} (Weaving, Woodworking): a water cauldron over heat. Woven cloth fulls into fulled
 * cloth, bow limbs bend. Putting a piece into a heated water cauldron makes one; emptied, it's a water cauldron again.</li>
 * </ul>
 * Each batch uses a level of liquid. Right-click with an empty hand to take the batch out once it's done (or to see how
 * long it has left). A piece comes out with its own ★, no better than the liquor's (water doesn't limit it).
 */
public class SoakingVatBlock extends BaseEntityBlock {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 3);
    public static final BooleanProperty SOAKING = BooleanProperty.create("soaking");
    private static final VoxelShape CAULDRON_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    /** What a soak turns into what, for which craft, in which liquid, how long it takes and the XP each piece gives. */
    public record Soak(Supplier<? extends Item> input, Supplier<? extends Item> output, Profession profession, boolean liquor, int ticks, int xp) {}

    public static final List<Soak> SOAKS = List.of(
            new Soak(Crafts.SCRAPED_HIDE, Forging.REFINED_LEATHER, Profession.LEATHERWORKING, true, 2400, 4),
            new Soak(Forging.WOVEN_CLOTH, Forging.PADDED_CLOTH, Profession.WEAVING, false, 1200, 2),
            new Soak(Crafts.BOW_LIMBS, Crafts.BENT_LIMBS, Profession.WOODWORKING, false, 1200, 2));

    public enum Type {
        TANNING_VAT(true, false, 4, 1.0f, true),
        TANNING_BARREL(true, false, 8, 0.75f, false),
        SOAKING_CAULDRON(false, true, 4, 1.0f, true);

        public final boolean liquor;
        public final boolean heated;
        public final int capacity;
        public final float timeScale;
        public final boolean cauldron;

        Type(boolean liquor, boolean heated, int capacity, float timeScale, boolean cauldron) {
            this.liquor = liquor;
            this.heated = heated;
            this.capacity = capacity;
            this.timeScale = timeScale;
            this.cauldron = cauldron;
        }
    }

    public final Type type;

    public SoakingVatBlock(Properties properties, Type type) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(LEVEL, type == Type.TANNING_BARREL ? 0 : 1).setValue(SOAKING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new SoakingVatBlock(properties, type));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL, SOAKING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return type.cauldron ? Blocks.CAULDRON.defaultBlockState().getShape(level, pos, context) : Shapes.block();
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return CAULDRON_SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SoakingVatBlockEntity(pos, state);
    }

    /** The soak that takes this piece in this kind of vat, if any. */
    public static Optional<Soak> soakFor(ItemStack stack, boolean liquor) {
        return SOAKS.stream().filter(soak -> soak.liquor == liquor && stack.is(soak.input.get())).findFirst();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SoakingVatBlockEntity vat)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (type.liquor && stack.getItem() instanceof LiquorItem liquor) {
            return pour(state, level, pos, player, hand, stack, vat, liquor);
        }
        if (!type.liquor && stack.is(Items.WATER_BUCKET) && state.getValue(LEVEL) < 3) {
            if (!level.isClientSide()) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
                level.setBlockAndUpdate(pos, state.setValue(LEVEL, 3));
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        Optional<Soak> soak = soakFor(stack, type.liquor);
        if (soak.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            insert(level, pos, state, vat, player, stack, soak.get());
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    private ItemInteractionResult pour(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack,
            SoakingVatBlockEntity vat, LiquorItem liquor) {
        int fill = state.getValue(LEVEL);
        if (fill >= 3 || fill > 0 && vat.liquorStars() != liquor.maxStars) {
            if (!level.isClientSide() && fill > 0 && vat.liquorStars() != liquor.maxStars) {
                tell(player, Component.translatable("message.dungeondelving.vat.other_liquor").withStyle(ChatFormatting.YELLOW));
            }
            return ItemInteractionResult.CONSUME;
        }
        if (!level.isClientSide()) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
            vat.setLiquorStars(liquor.maxStars);
            level.setBlockAndUpdate(pos, state.setValue(LEVEL, fill + 1));
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 0.8f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Puts as many of the held pieces as fit into the vat as a batch, using a level of its liquid. */
    public static void insert(Level level, BlockPos pos, BlockState state, SoakingVatBlockEntity vat, Player player, ItemStack stack, Soak soak) {
        SoakingVatBlock block = (SoakingVatBlock) state.getBlock();
        if (vat.soaking()) {
            tell(player, Component.translatable("message.dungeondelving.vat.busy").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (state.getValue(LEVEL) <= 0) {
            tell(player, Component.translatable(block.type.liquor ? "message.dungeondelving.vat.no_liquor" : "message.dungeondelving.vat.no_water")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (block.type.heated && !ForgeActions.heatedFromBelow(level, pos)) {
            tell(player, Component.translatable("message.dungeondelving.boil.no_heat").withStyle(ChatFormatting.RED));
            return;
        }
        if (CraftWorkshop.around(level, pos, soak.profession()).isEmpty()) {
            tell(player, Component.translatable("message.dungeondelving.craft.no_workshop", soak.profession().displayName()).withStyle(ChatFormatting.RED));
            return;
        }
        int count = Math.min(stack.getCount(), block.type.capacity);
        List<ItemStack> batch = List.of(stack.copyWithCount(count));
        stack.shrink(count);
        long ready = level.getGameTime() + Math.round(soak.ticks() * block.type.timeScale);
        vat.start(batch, ready);
        level.setBlockAndUpdate(pos, state.setValue(LEVEL, state.getValue(LEVEL) - 1).setValue(SOAKING, true));
        level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.6f, 1.0f);
        tell(player, Component.translatable("message.dungeondelving.vat.started", count, minutes(ready - level.getGameTime())).withStyle(ChatFormatting.GOLD));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SoakingVatBlockEntity vat) || !vat.soaking()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            collect(level, pos, state, vat, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Takes the batch out if it's done, or says how long it has left. */
    public static void collect(Level level, BlockPos pos, BlockState state, SoakingVatBlockEntity vat, Player player) {
        long left = vat.readyAt() - level.getGameTime();
        if (left > 0) {
            tell(player, Component.translatable("message.dungeondelving.vat.waiting", minutes(left)).withStyle(ChatFormatting.YELLOW));
            return;
        }
        SoakingVatBlock block = (SoakingVatBlock) state.getBlock();
        int cap = block.type.liquor ? vat.liquorStars() : Forging.MAX_STARS;
        for (ItemStack piece : vat.takeAll()) {
            Optional<Soak> soak = soakFor(piece, block.type.liquor);
            if (soak.isEmpty()) {
                Block.popResource(level, pos.above(), piece);
                continue;
            }
            ItemStack out = new ItemStack(soak.get().output().get(), piece.getCount());
            out.set(Forging.STARS.get(), Math.max(1, Math.min(Forging.stars(piece), cap)));
            if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
            CraftingSkills.addXp(player, soak.get().profession().skill, soak.get().xp() * piece.getCount());
        }
        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.6f, 1.2f);
        int fill = state.getValue(LEVEL);
        if (fill == 0 && block.type == Type.TANNING_VAT) {
            level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
        } else if (block.type == Type.SOAKING_CAULDRON) {
            level.setBlockAndUpdate(pos, fill == 0 ? Blocks.CAULDRON.defaultBlockState()
                    : Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, fill));
        } else {
            level.setBlockAndUpdate(pos, state.setValue(SOAKING, false));
        }
    }

    /** Time left as "m:ss". */
    static String minutes(long ticks) {
        long seconds = Math.max(0, (ticks + 19) / 20);
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    private static void tell(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(SOAKING) && random.nextInt(3) == 0) {
            level.addParticle(type.heated ? ParticleTypes.BUBBLE_POP : ParticleTypes.FALLING_DRIPSTONE_WATER, pos.getX() + 0.3 + random.nextDouble() * 0.4,
                    pos.getY() + 0.9, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.02, 0.0);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SoakingVatBlockEntity vat) {
            vat.takeAll().forEach(piece -> Block.popResource(level, pos, piece));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Sets up the vanilla cauldrons: pouring liquor into an empty cauldron makes a Tanning Vat, and putting a piece to
     * soak into a water cauldron makes a Soaking Cauldron (it checks for heat). Call once during common setup.
     */
    public static void registerInteractions() {
        for (Supplier<? extends Item> liquor : List.of(Crafts.BARK_LIQUOR, Crafts.TANNIN_LIQUOR, Crafts.ELDER_LIQUOR)) {
            CauldronInteraction.EMPTY.map().put(liquor.get(), (state, level, pos, player, hand, stack) -> {
                if (!level.isClientSide()) {
                    level.setBlockAndUpdate(pos, Crafts.TANNING_VAT.get().defaultBlockState().setValue(LEVEL, 1));
                    if (level.getBlockEntity(pos) instanceof SoakingVatBlockEntity vat) {
                        vat.setLiquorStars(((LiquorItem) stack.getItem()).maxStars);
                    }
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                    level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 0.8f);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            });
        }
        for (Soak soak : SOAKS) {
            if (soak.liquor()) {
                continue;
            }
            CauldronInteraction.WATER.map().put(soak.input().get(), (state, level, pos, player, hand, stack) -> {
                if (!level.isClientSide()) {
                    if (!ForgeActions.heatedFromBelow(level, pos)) {
                        tell(player, Component.translatable("message.dungeondelving.boil.no_heat").withStyle(ChatFormatting.RED));
                        return ItemInteractionResult.CONSUME;
                    }
                    if (CraftWorkshop.around(level, pos, soak.profession()).isEmpty()) {
                        tell(player, Component.translatable("message.dungeondelving.craft.no_workshop", soak.profession().displayName())
                                .withStyle(ChatFormatting.RED));
                        return ItemInteractionResult.CONSUME;
                    }
                    BlockState soaking = Crafts.SOAKING_CAULDRON.get().defaultBlockState().setValue(LEVEL, state.getValue(LayeredCauldronBlock.LEVEL));
                    level.setBlockAndUpdate(pos, soaking);
                    if (level.getBlockEntity(pos) instanceof SoakingVatBlockEntity vat) {
                        insert(level, pos, soaking, vat, player, stack, soak);
                    }
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            });
        }
    }

    /** The pieces a batch would soak, for tests: everything in the vat. */
    public static List<ItemStack> contents(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SoakingVatBlockEntity vat ? new ArrayList<>(vat.contents()) : List.of();
    }

    /** Marks a vat's batch done, for tests. */
    public static void finishNow(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SoakingVatBlockEntity vat) {
            vat.finishNow();
        }
    }
}
