package io.github.ranger3462.dungeondelving.testing;

import java.util.List;
import java.util.Map;

import io.github.ranger3462.dungeondelving.dungeon.DungeonRun;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Tier-appropriate gear for testing dungeons without gearing up first: armor, a sword, a shield, a bow and arrows,
 * and food, better each tier (iron at tier 1 up to enchanted netherite at tier 4). Armor already worn is moved into
 * the inventory, not lost.
 */
public final class DevGear {
    /** One tier's kit: armor and sword material, and how enchanted it is. */
    private record Kit(Item helmet, Item chestplate, Item leggings, Item boots, Item sword, int protection, int sharpness, int power,
            int unbreaking, Item food, int goldenApples) {}

    private static final Map<Integer, Kit> KITS = Map.of(
            1, new Kit(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.IRON_SWORD, 0, 0, 0, 0, Items.BREAD, 0),
            2, new Kit(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.DIAMOND_SWORD, 1, 1, 1, 1,
                    Items.COOKED_BEEF, 0),
            3, new Kit(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.DIAMOND_SWORD, 3, 3, 3, 2,
                    Items.COOKED_BEEF, 2),
            4, new Kit(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, Items.NETHERITE_SWORD, 4,
                    5, 5, 3, Items.COOKED_BEEF, 4));

    private DevGear() {}

    /** Equips the player for a dungeon of this tier. */
    public static void equip(ServerPlayer player, int tier) {
        Kit kit = KITS.get(DungeonRun.clampTier(tier));
        wear(player, EquipmentSlot.HEAD, enchanted(player, kit.helmet(), kit, Enchantments.PROTECTION, kit.protection()));
        wear(player, EquipmentSlot.CHEST, enchanted(player, kit.chestplate(), kit, Enchantments.PROTECTION, kit.protection()));
        wear(player, EquipmentSlot.LEGS, enchanted(player, kit.leggings(), kit, Enchantments.PROTECTION, kit.protection()));
        wear(player, EquipmentSlot.FEET, enchanted(player, kit.boots(), kit, Enchantments.PROTECTION, kit.protection()));
        wear(player, EquipmentSlot.OFFHAND, enchanted(player, Items.SHIELD, kit, Enchantments.UNBREAKING, 0));
        for (ItemStack stack : List.of(enchanted(player, kit.sword(), kit, Enchantments.SHARPNESS, kit.sharpness()),
                enchanted(player, Items.BOW, kit, Enchantments.POWER, kit.power()), new ItemStack(Items.ARROW, 64), new ItemStack(kit.food(), 32),
                new ItemStack(Items.GOLDEN_APPLE, kit.goldenApples()))) {
            give(player, stack);
        }
        player.inventoryMenu.broadcastChanges();
    }

    /** The item with the given enchantment at this level (none at 0), plus the kit's Unbreaking. */
    private static ItemStack enchanted(ServerPlayer player, Item item, Kit kit, ResourceKey<Enchantment> enchantment, int level) {
        ItemStack stack = new ItemStack(item);
        if (level > 0) {
            stack.enchant(lookup(player, enchantment), level);
        }
        if (kit.unbreaking() > 0) {
            stack.enchant(lookup(player, Enchantments.UNBREAKING), kit.unbreaking());
        }
        return stack;
    }

    private static Holder<Enchantment> lookup(ServerPlayer player, ResourceKey<Enchantment> enchantment) {
        return player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
    }

    /** Puts gear in a slot, moving whatever was there into the inventory. */
    private static void wear(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        ItemStack old = player.getItemBySlot(slot);
        player.setItemSlot(slot, stack);
        if (!old.isEmpty()) {
            give(player, old);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
