package io.github.ranger3462.dungeondelving.dungeon.bag;

import java.util.Optional;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonLoot;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Routes loot into the dungeon bag: items picked up off the ground, and items taken from containers, while the
 * player is in their dungeon. Also keeps the bag item from being thrown away.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class BagEvents {
    private BagEvents() {}

    /** The player's bag, if they're in the dungeon dimension with an active run. */
    private static Optional<DungeonBag> activeBag(Player player) {
        if (!(player instanceof ServerPlayer) || player.level().dimension() != DungeonDimension.DUNGEON) {
            return Optional.empty();
        }
        return DungeonRegions.get().bag(player.getUUID());
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        activeBag(event.getPlayer()).ifPresent(bag -> {
            if (pickUpIntoBag(event.getPlayer(), event.getItemEntity(), bag) != PickupResult.NOT_LOOT) {
                event.setCanPickup(TriState.FALSE);
            }
        });
    }

    public enum PickupResult {
        /** Not dungeon loot (e.g. something the player brought and threw); picked up normally. */
        NOT_LOOT,
        /** Some or all of it went into the bag. */
        BAGGED,
        /** The bag is full; it stays on the ground. */
        BAG_FULL
    }

    /** Moves as much of a dropped item into the bag as fits, leaving the rest on the ground. */
    public static PickupResult pickUpIntoBag(Player player, ItemEntity item, DungeonBag bag) {
        ItemStack stack = item.getItem();
        // Items the player threw themselves are theirs, unless they were thrown out of the bag (still marked as loot).
        if (stack.is(ModRegistries.DUNGEON_BAG.get()) || (item.getOwner() == player && !DungeonLoot.isLoot(stack))) {
            return PickupResult.NOT_LOOT;
        }
        if (item.hasPickUpDelay()) {
            return PickupResult.BAG_FULL; // Not ready to be picked up yet; try again on a later touch.
        }
        ItemStack left = bag.insert(stack);
        int taken = stack.getCount() - left.getCount();
        if (taken == 0) {
            return PickupResult.BAG_FULL;
        }
        player.take(item, taken);
        if (left.isEmpty()) {
            item.discard();
        } else {
            item.setItem(left);
        }
        return PickupResult.BAGGED;
    }

    /** Opening a container in the dungeon shows the looting screen, which sends taken items to the bag. */
    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.isSecondaryUseActive()) {
            return; // Sneaking lets players place blocks against containers as usual.
        }
        activeBag(player).ifPresent(bag -> {
            Level level = event.getLevel();
            BlockPos pos = event.getPos();
            Container container = lootableContainer(level, pos, player);
            if (container == null || !DungeonChestMenu.fits(container)) {
                return;
            }
            Component title = level.getBlockEntity(pos) instanceof Nameable named ? named.getDisplayName()
                    : Component.translatable("container.chest");
            player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> DungeonChestMenu.looting(id, inventory, container, bag), title));
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        });
    }

    /** The container at a position with its loot rolled, joining double chests; null if it isn't a container. */
    private static Container lootableContainer(Level level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        unpack(blockEntity, player);
        if (state.getBlock() instanceof ChestBlock chest) {
            if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                unpack(level.getBlockEntity(pos.relative(ChestBlock.getConnectedDirection(state))), player);
            }
            return ChestBlock.getContainer(chest, state, level, pos, true);
        }
        return blockEntity instanceof BaseContainerBlockEntity container ? container : null;
    }

    private static void unpack(BlockEntity blockEntity, Player player) {
        if (blockEntity instanceof RandomizableContainerBlockEntity lootable) {
            lootable.unpackLootTable(player);
        }
    }

    /** The bag item can't be thrown away; it goes straight back into the inventory. */
    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (stack.is(ModRegistries.DUNGEON_BAG.get())) {
            event.setCanceled(true);
            event.getPlayer().getInventory().add(stack.copy());
        }
    }
}
