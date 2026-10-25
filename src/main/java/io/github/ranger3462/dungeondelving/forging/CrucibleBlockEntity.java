package io.github.ranger3462.dungeondelving.forging;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A crucible's contents and its refining, like a furnace's (DD-91): gold ingots in the top slot, a refining salt in the
 * fuel slot, fine gold out. Each salt refines {@link #INGOTS_PER_SALT} ingots, at the salt's ★; the better the salt, the
 * longer each ingot takes ({@link #ticksFor}). A complete goldsmithy must be around it, and good enough for the salt.
 */
public class CrucibleBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int INPUT = 0;
    public static final int SALT = 1;
    public static final int OUTPUT = 2;
    /** Ingots one salt refines. */
    public static final int INGOTS_PER_SALT = 4;

    private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    /** The salt being used: its ★, and how many more ingots it will refine. */
    private int saltStars;
    private int saltLeft;
    private int progress;
    private int total;

    /** What the menu shows: progress, the ingot's total time, the salt left and its ★. */
    final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> total;
                case 2 -> saltLeft;
                default -> saltStars;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 1 -> total = value;
                case 2 -> saltLeft = value;
                default -> saltStars = value;
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(Crafts.CRUCIBLE_ENTITY.get(), pos, state);
    }

    /** How long one ingot takes with a salt of this ★: 30 seconds more for each tier of salt (★3, ★5, ★7). */
    public static int ticksFor(int stars, boolean great) {
        return Math.max(100, Math.round(300 * (stars - 1) * (great ? 0.75f : 1.0f)));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrucibleBlockEntity crucible) {
        boolean lit = crucible.tick(level, pos, state);
        if (state.getValue(CrucibleBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(CrucibleBlock.LIT, lit), 3);
        }
    }

    /** One tick of refining. Whether it's working. */
    private boolean tick(Level level, BlockPos pos, BlockState state) {
        ItemStack input = items.get(INPUT);
        if (!input.is(Items.GOLD_INGOT)) {
            progress = 0;
            return false;
        }
        if (progress == 0) {
            // Starting an ingot: take a new salt if this one's spent, and check the goldsmithy can work it.
            if (saltLeft <= 0 && !takeSalt()) {
                return false;
            }
            Optional<CraftWorkshop> workshop = CraftWorkshop.around(level, pos, Profession.GOLDSMITHING);
            if (workshop.isEmpty() || saltStars > workshop.get().maxStars() || !outputFits()) {
                return false;
            }
            total = ticksFor(saltStars, state.getBlock() == Crafts.GREAT_CRUCIBLE.get());
        }
        if (!outputFits()) {
            return false;
        }
        if (++progress >= total) {
            progress = 0;
            input.shrink(1);
            saltLeft--;
            ItemStack output = items.get(OUTPUT);
            if (output.isEmpty()) {
                ItemStack fine = new ItemStack(Crafts.FINE_GOLD.get());
                fine.set(Forging.STARS.get(), saltStars);
                items.set(OUTPUT, fine);
            } else {
                output.grow(1);
            }
        }
        setChanged();
        return true;
    }

    private boolean takeSalt() {
        ItemStack salt = items.get(SALT);
        if (!(salt.getItem() instanceof LiquorItem liquor) || liquor.profession != Profession.GOLDSMITHING) {
            return false;
        }
        saltStars = liquor.stars;
        saltLeft = INGOTS_PER_SALT;
        salt.shrink(1);
        setChanged();
        return true;
    }

    /** Whether a fine gold of the salt's ★ fits in the output. */
    private boolean outputFits() {
        ItemStack output = items.get(OUTPUT);
        return output.isEmpty() || output.is(Crafts.FINE_GOLD.get()) && Forging.stars(output) == saltStars && output.getCount() < output.getMaxStackSize();
    }

    /** Whether a stack belongs in a slot: gold ingots in, goldsmithing salts as fuel, nothing into the output. */
    public static boolean fits(int slot, ItemStack stack) {
        return switch (slot) {
            case INPUT -> stack.is(Items.GOLD_INGOT);
            case SALT -> stack.getItem() instanceof LiquorItem liquor && liquor.profession == Profession.GOLDSMITHING;
            default -> false;
        };
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return fits(slot, stack);
    }

    // --- Container ---

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack taken = ContainerHelper.removeItem(items, slot, amount);
        setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // --- Menu ---

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CrucibleMenu(id, inventory, this, data);
    }

    // --- Saving ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("salt_stars", saltStars);
        tag.putInt("salt_left", saltLeft);
        tag.putInt("progress", progress);
        tag.putInt("total", total);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        saltStars = tag.getInt("salt_stars");
        saltLeft = tag.getInt("salt_left");
        progress = tag.getInt("progress");
        total = tag.getInt("total");
    }

    /** Runs the crucible until it stops working. For tests. */
    public void runUntilIdle(Level level) {
        for (int i = 0; i < 100_000 && tick(level, worldPosition, getBlockState()); i++) {
        }
    }
}
