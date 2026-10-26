package io.github.ranger3462.dungeondelving.entity;

import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The armor town guards wear, by role and gear tier (0 to 3, from {@link TownGuard#gearTier}). With the pack's RPG class
 * mods installed, each role wears its class's sets: swordsmen the warrior line (Rogues & Warriors), crossbowmen the
 * archer line (Archers), battlemages the wizard line (Wizards); tiers 0-1 the first set, 2 the second, 3 its netherite
 * version. Without them, vanilla armor: leather, chainmail, iron, diamond (battlemages always leather robes).
 */
public final class GuardGear {
    /** A guard's role, and the RPG sets it wears, worst to best. */
    public enum Role {
        SWORD("rogues", "warrior_armor", "berserker_armor", "netherite_berserker_armor"),
        CROSSBOW("archers", "archer_armor", "ranger_armor", "netherite_ranger_armor"),
        MAGE("wizards", "wizard_robe", "arcane_robe", "netherite_arcane_robe");

        final String namespace;
        final String[] sets;

        Role(String namespace, String... sets) {
            this.namespace = namespace;
            this.sets = sets;
        }
    }

    private static final String[] PIECES = { "head", "chest", "legs", "feet" };
    private static final Item[][] VANILLA = {
            { Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS },
            { Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS },
            { Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS },
            { Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS } };

    private GuardGear() {}

    /** Head, chest, legs and feet for a role at a gear tier. */
    public static ItemStack[] armor(Role role, int gear) {
        Optional<Item[]> rpg = rpgSet(role, gear);
        Item[] items = rpg.orElse(role == Role.MAGE ? VANILLA[0] : VANILLA[Math.max(0, Math.min(3, gear))]);
        ItemStack[] stacks = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            stacks[i] = new ItemStack(items[i]);
        }
        return stacks;
    }

    /** The role's RPG set for a gear tier, if its mod is installed. */
    public static Optional<Item[]> rpgSet(Role role, int gear) {
        String set = role.sets[gear <= 1 ? 0 : gear == 2 ? 1 : 2];
        Item[] items = new Item[PIECES.length];
        for (int i = 0; i < PIECES.length; i++) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(role.namespace, set + "_" + PIECES[i]));
            if (item.isEmpty()) {
                return Optional.empty();
            }
            items[i] = item.get();
        }
        return Optional.of(items);
    }
}
