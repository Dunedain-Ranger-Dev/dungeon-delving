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
 * A vat for the crafts' timed step, their furnace: a prepared piece goes in, and comes out a while later with the ★ of
 * the liquid it soaked in ({@link LiquorItem}). Better liquids take longer, {@link #STEP_TICKS} more for each ★ (as
 * heating a better ingot does). Each craft has a cauldron vat, made by pouring its liquid into an empty cauldron:
 * <ul>
 * <li>Leatherworking: a {@link Type#TANNING_VAT} of tanning liquor tans scraped hides into tanned leather.</li>
 * <li>Weaving: a {@link Type#SIZING_VAT} of sizing stiffens yarn (or string) into sized yarn.</li>
 * <li>Woodworking: a {@link Type#FINISHING_VAT} of finish cures seasoned billets into cured billets.</li>
 * <li>Goldsmithing: a {@link Type#CRUCIBLE} (a block of its own) of refining salt refines gold ingots into fine gold.</li>
 * </ul>
 * The Tanning Barrel and the Dye Works are the bigger, faster vats of a level 2 tannery and a level 3 weaving room. Each
 * batch uses a level of liquid. Right-click with an empty hand to take the batch out once it's done (or to see how long
 * it has left).
 */
public class SoakingVatBlock extends BaseEntityBlock {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 3);
    public static final BooleanProperty SOAKING = BooleanProperty.create("soaking");
    private static final VoxelShape CAULDRON_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    /** How long a soak takes: this much, plus as much again for each ★ of the liquid. ★3 takes 2:00, ★5 3:00, ★7 4:00. */
    public static final int STEP_TICKS = 600;

    /** What a soak turns into what, for which craft, and the XP each piece gives (per ★). */
    public record Soak(Supplier<? extends Item> input, Supplier<? extends Item> output, Profession profession, int xp) {}

    public static final List<Soak> SOAKS = List.of(
            new Soak(Crafts.SCRAPED_HIDE, Forging.REFINED_LEATHER, Profession.LEATHERWORKING, 2),
            new Soak(Crafts.YARN, Crafts.SIZED_YARN, Profession.WEAVING, 1),
            new Soak(() -> Items.STRING, Crafts.SIZED_YARN, Profession.WEAVING, 1),
            new Soak(Crafts.SEASONED_BILLET, Crafts.CURED_BILLET, Profession.WOODWORKING, 2),
            new Soak(() -> Items.GOLD_INGOT, Crafts.FINE_GOLD, Profession.GOLDSMITHING, 2));

    public enum Type {
        TANNING_VAT(Profession.LEATHERWORKING, 4, 1.0f, true),
        TANNING_BARREL(Profession.LEATHERWORKING, 8, 0.75f, false),
        SIZING_VAT(Profession.WEAVING, 4, 1.0f, true),
        DYE_WORKS(Profession.WEAVING, 8, 0.75f, false),
        FINISHING_VAT(Profession.WOODWORKING, 4, 1.0f, true),
        /** Goldsmithing's: gold refined with a refining salt. Not a cauldron: pour the salt straight in. */
        CRUCIBLE(Profession.GOLDSMITHING, 4, 1.0f, false),
        GREAT_CRUCIBLE(Profession.GOLDSMITHING, 8, 0.75f, false);

        public final Profession profession;
        public final int capacity;
        public final float timeScale;
        /** A cauldron vat: made from a cauldron, and a cauldron again once its liquid is used up. */
        public final boolean cauldron;

        Type(Profession profession, int capacity, float timeScale, boolean cauldron) {
            this.profession = profession;
            this.capacity = capacity;
            this.timeScale = timeScale;
            this.cauldron = cauldron;
        }
    }

    public final Type type;

    public SoakingVatBlock(Properties properties, Type type) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(LEVEL, type.cauldron ? 1 : 0).setValue(SOAKING, false));
    }

    /** The cauldron vat a craft's liquid makes. */
    public static SoakingVatBlock cauldronFor(Profession profession) {
        return switch (profession) {
            case LEATHERWORKING -> Crafts.TANNING_VAT.get();
            case WEAVING -> Crafts.SIZING_VAT.get();
            case WOODWORKING -> Crafts.FINISHING_VAT.get();
            case GOLDSMITHING -> Crafts.CRUCIBLE.get();
        };
    }

    /** How long a batch soaks in a liquid of this ★ (before a bigger vat's speed-up). */
    public static int soakTicks(int stars) {
        return STEP_TICKS * (stars + 1);
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

    /** The soak that takes this piece in a vat of this craft, if any. */
    public static Optional<Soak> soakFor(ItemStack stack, Profession profession) {
        return SOAKS.stream().filter(soak -> soak.profession == profession && stack.is(soak.input.get())).findFirst();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SoakingVatBlockEntity vat)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof LiquorItem liquor && liquor.profession == type.profession) {
            return pour(state, level, pos, player, hand, stack, vat, liquor);
        }
        Optional<Soak> soak = soakFor(stack, type.profession);
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
        if (fill >= 3 || fill > 0 && vat.liquorStars() != liquor.stars) {
            if (!level.isClientSide() && fill > 0 && vat.liquorStars() != liquor.stars) {
                tell(player, Component.translatable("message.dungeondelving.vat.other_liquor").withStyle(ChatFormatting.YELLOW));
            }
            return ItemInteractionResult.CONSUME;
        }
        if (!level.isClientSide()) {
            // Liquids hand back their bottle; salts are just used up.
            ItemStack container = stack.getCraftingRemainingItem();
            if (container.isEmpty()) {
                stack.consume(1, player);
            } else {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, container));
            }
            vat.setLiquorStars(liquor.stars);
            level.setBlockAndUpdate(pos, state.setValue(LEVEL, fill + 1));
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 0.8f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /**
     * Puts as many of the held pieces as fit into the vat as a batch, using a level of its liquid. Refused for a liquid
     * better than the workshop can work (its pieces couldn't be used there).
     */
    public static void insert(Level level, BlockPos pos, BlockState state, SoakingVatBlockEntity vat, Player player, ItemStack stack, Soak soak) {
        SoakingVatBlock block = (SoakingVatBlock) state.getBlock();
        if (vat.soaking()) {
            tell(player, Component.translatable("message.dungeondelving.vat.busy").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (state.getValue(LEVEL) <= 0) {
            tell(player, Component.translatable("message.dungeondelving.vat.no_liquor").withStyle(ChatFormatting.RED));
            return;
        }
        Optional<CraftWorkshop> workshop = CraftWorkshop.around(level, pos, soak.profession());
        if (workshop.isEmpty()) {
            tell(player, Component.translatable("message.dungeondelving.craft.no_workshop", soak.profession().displayName()).withStyle(ChatFormatting.RED));
            return;
        }
        if (vat.liquorStars() > workshop.get().maxStars()) {
            tell(player, Component.translatable("message.dungeondelving.craft.workshop_too_low", StarredItem.starsText(workshop.get().maxStars()))
                    .withStyle(ChatFormatting.RED));
            return;
        }
        int count = Math.min(stack.getCount(), block.type.capacity);
        List<ItemStack> batch = List.of(stack.copyWithCount(count));
        stack.shrink(count);
        long ready = level.getGameTime() + Math.round(soakTicks(vat.liquorStars()) * block.type.timeScale);
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

    /** Takes the batch out if it's done, with the liquid's ★, or says how long it has left. */
    public static void collect(Level level, BlockPos pos, BlockState state, SoakingVatBlockEntity vat, Player player) {
        long left = vat.readyAt() - level.getGameTime();
        if (left > 0) {
            tell(player, Component.translatable("message.dungeondelving.vat.waiting", minutes(left)).withStyle(ChatFormatting.YELLOW));
            return;
        }
        SoakingVatBlock block = (SoakingVatBlock) state.getBlock();
        int stars = Math.clamp(vat.liquorStars(), 1, Forging.MAX_STARS);
        for (ItemStack piece : vat.takeAll()) {
            Optional<Soak> soak = soakFor(piece, block.type.profession);
            if (soak.isEmpty()) {
                Block.popResource(level, pos.above(), piece);
                continue;
            }
            ItemStack out = new ItemStack(soak.get().output().get(), piece.getCount());
            out.set(Forging.STARS.get(), stars);
            if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
            CraftingSkills.addXp(player, soak.get().profession().skill, soak.get().xp() * stars * piece.getCount());
        }
        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.6f, 1.2f);
        if (state.getValue(LEVEL) == 0 && block.type.cauldron) {
            level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
        } else {
            level.setBlockAndUpdate(pos, state.setValue(SOAKING, false));
        }
    }

    /** Time as "m:ss". */
    public static String minutes(long ticks) {
        long seconds = Math.max(0, (ticks + 19) / 20);
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    private static void tell(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(SOAKING) && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.FALLING_DRIPSTONE_WATER, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.9,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.02, 0.0);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SoakingVatBlockEntity vat) {
            vat.takeAll().forEach(piece -> Block.popResource(level, pos, piece));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Sets up the vanilla cauldron: pouring a craft's liquid into an empty one makes that craft's vat. Call once during common setup. */
    public static void registerInteractions() {
        for (Supplier<LiquorItem> liquor : List.<Supplier<LiquorItem>>of(Crafts.BARK_LIQUOR, Crafts.TANNIN_LIQUOR, Crafts.ELDER_LIQUOR,
                Crafts.WHEAT_SIZING, Crafts.SILK_SIZING, Crafts.ARCANE_SIZING, Crafts.LINSEED_OIL, Crafts.HEARTWOOD_RESIN, Crafts.SYLVAN_SAP)) {
            CauldronInteraction.EMPTY.map().put(liquor.get(), (state, level, pos, player, hand, stack) -> {
                if (!level.isClientSide()) {
                    LiquorItem poured = (LiquorItem) stack.getItem();
                    level.setBlockAndUpdate(pos, cauldronFor(poured.profession).defaultBlockState().setValue(LEVEL, 1));
                    if (level.getBlockEntity(pos) instanceof SoakingVatBlockEntity vat) {
                        vat.setLiquorStars(poured.stars);
                    }
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                    level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 0.8f);
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
