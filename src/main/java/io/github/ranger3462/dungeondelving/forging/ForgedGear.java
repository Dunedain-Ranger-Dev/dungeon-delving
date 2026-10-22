package io.github.ranger3462.dungeondelving.forging;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Forged armor and weapons. One item per slot covers every ★: the stats come from the stack's ★ (see {@link #ARMOR} and
 * friends), worked out on the fly, so Apotheosis and anything else that reads an item's attributes sees them as normal.
 * Durability is set when the piece is crafted ({@link #applyStats}). Repaired with tempered ingots of the same ★ or
 * better.
 */
public final class ForgedGear {
    /** Armor weight. Light armor gives up protection for comfort; heavy armor is the strongest but slows you a little. */
    public enum Weight {
        LIGHT("brigandine", 0.6f, 0.0f, false),
        MEDIUM("hauberk", 0.8f, 0.5f, false),
        HEAVY("plate", 1.0f, 1.0f, true);

        public final String name;
        final float armorShare;
        final float toughnessShare;
        final boolean heavy;

        Weight(String name, float armorShare, float toughnessShare, boolean heavy) {
            this.name = name;
            this.armorShare = armorShare;
            this.toughnessShare = toughnessShare;
            this.heavy = heavy;
        }
    }

    /** A heavy set's total armor, toughness and knockback resistance, and the durability multiplier, by ★ (index 0 is ★1). */
    public static final double[] ARMOR = { 17, 19, 20, 20, 21, 22, 23 };
    public static final double[] TOUGHNESS = { 1, 4, 9, 12, 13, 14, 15 };
    public static final double[] KNOCKBACK = { 0, 0, 0, 0.2, 0.3, 0.3, 0.4 };
    public static final int[] DURABILITY = { 19, 23, 27, 31, 35, 39, 43 };
    /** A forged sword's attack damage, by ★ (iron is 6, diamond 7, netherite 8). The axe hits 2 harder, slower. */
    public static final double[] SWORD_DAMAGE = { 6.5, 6.8, 7.1, 7.5, 7.9, 8.2, 8.6 };
    public static final int[] WEAPON_DURABILITY = { 400, 700, 1000, 1400, 1800, 2100, 2400 };
    /** Heavy armor slows you this much per piece (a full set: 5%). */
    public static final double HEAVY_SLOW = -0.0125;

    /** Each slot's share of a set's armor, and its base durability (like vanilla's). */
    private static final Map<ArmorItem.Type, Double> SLOT_SHARE = new EnumMap<>(Map.of(ArmorItem.Type.HELMET, 0.15, ArmorItem.Type.CHESTPLATE, 0.40,
            ArmorItem.Type.LEGGINGS, 0.30, ArmorItem.Type.BOOTS, 0.15));
    private static final Map<ArmorItem.Type, Integer> SLOT_DURABILITY = new EnumMap<>(Map.of(ArmorItem.Type.HELMET, 11, ArmorItem.Type.CHESTPLATE, 16,
            ArmorItem.Type.LEGGINGS, 15, ArmorItem.Type.BOOTS, 13));

    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, DungeonDelving.MODID);

    /** Looks like leather (dyeable), chainmail and iron; the stats all come from the ★, so the materials give none. */
    public static final Map<Weight, DeferredHolder<ArmorMaterial, ArmorMaterial>> MATERIAL = new EnumMap<>(Map.of(
            Weight.LIGHT, material("brigandine", List.of(new ArmorMaterial.Layer(ResourceLocation.withDefaultNamespace("leather"), "", true),
                    new ArmorMaterial.Layer(ResourceLocation.withDefaultNamespace("leather"), "_overlay", false)), SoundEvents.ARMOR_EQUIP_LEATHER),
            Weight.MEDIUM, material("hauberk", List.of(new ArmorMaterial.Layer(ResourceLocation.withDefaultNamespace("chainmail"))),
                    SoundEvents.ARMOR_EQUIP_CHAIN),
            Weight.HEAVY, material("plate", List.of(new ArmorMaterial.Layer(ResourceLocation.withDefaultNamespace("iron"))), SoundEvents.ARMOR_EQUIP_IRON)));

    private static DeferredHolder<ArmorMaterial, ArmorMaterial> material(String name, List<ArmorMaterial.Layer> layers,
            Holder<net.minecraft.sounds.SoundEvent> sound) {
        return MATERIALS.register(name, () -> new ArmorMaterial(new EnumMap<>(Map.of(ArmorItem.Type.HELMET, 0, ArmorItem.Type.CHESTPLATE, 0,
                ArmorItem.Type.LEGGINGS, 0, ArmorItem.Type.BOOTS, 0, ArmorItem.Type.BODY, 0)), 12, sound,
                () -> Ingredient.of(Forging.TEMPERED_IRON_INGOT.get()), layers, 0.0f, 0.0f));
    }

    private ForgedGear() {}

    private static int index(int stars) {
        return Math.clamp(stars, 1, Forging.MAX_STARS) - 1;
    }

    /** Durability for an armor piece of this type and ★. */
    public static int armorDurability(ArmorItem.Type type, int stars) {
        return SLOT_DURABILITY.get(type) * DURABILITY[index(stars)];
    }

    /** Sets a freshly crafted piece's durability for its ★. Does nothing to anything that isn't forged gear. */
    public static void applyStats(ItemStack stack) {
        int stars = Forging.stars(stack);
        if (stack.getItem() instanceof Armor armor) {
            stack.set(DataComponents.MAX_DAMAGE, armorDurability(armor.getType(), stars));
        } else if (stack.getItem() instanceof Sword || stack.getItem() instanceof Axe) {
            stack.set(DataComponents.MAX_DAMAGE, WEAPON_DURABILITY[index(stars)]);
        }
    }

    /** Tempered ingots that went into a piece (plates take 2, rivets and chain links are a quarter each), for salvaging. */
    public static float ingotCost(ItemStack stack) {
        if (stack.getItem() instanceof Armor armor) {
            return switch (armor.weight) {
                case HEAVY -> switch (armor.getType()) {
                    case HELMET -> 4.5f;
                    case CHESTPLATE -> 8.5f;
                    case LEGGINGS -> 6.5f;
                    default -> 4.25f;
                };
                case MEDIUM -> switch (armor.getType()) {
                    case HELMET -> 1.0f;
                    case CHESTPLATE -> 2.0f;
                    case LEGGINGS -> 1.5f;
                    default -> 1.0f;
                };
                case LIGHT -> switch (armor.getType()) {
                    case CHESTPLATE, LEGGINGS -> 0.5f;
                    default -> 0.25f;
                };
            };
        }
        if (stack.getItem() instanceof Sword) {
            return 4.0f;
        }
        return stack.getItem() instanceof Axe ? 6.0f : 0.0f;
    }

    public static boolean isGear(ItemStack stack) {
        return stack.getItem() instanceof Armor || stack.getItem() instanceof Sword || stack.getItem() instanceof Axe;
    }

    /** Whether {@code repair} can repair {@code gear}: a tempered ingot of the same ★ or better. */
    static boolean repairs(ItemStack gear, ItemStack repair) {
        return repair.is(Forging.TEMPERED_IRON_INGOT.get()) && Forging.stars(repair) >= Forging.stars(gear);
    }

    static Component nameWithStars(Component name, ItemStack stack) {
        return stack.has(Forging.STARS.get()) ? name.copy().append(" ").append(StarredItem.starsText(Forging.stars(stack))) : name;
    }

    /** A piece of forged armor. */
    public static class Armor extends ArmorItem {
        public final Weight weight;

        public Armor(Weight weight, ArmorItem.Type type, Item.Properties properties) {
            super(MATERIAL.get(weight), type, properties.durability(SLOT_DURABILITY.get(type) * DURABILITY[0]));
            this.weight = weight;
        }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
            int i = index(Forging.stars(stack));
            double share = SLOT_SHARE.get(type);
            EquipmentSlotGroup slot = EquipmentSlotGroup.bySlot(type.getSlot());
            ResourceLocation id = DungeonDelving.id("forged_armor." + type.getName());
            ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
                    .add(Attributes.ARMOR, new AttributeModifier(id, round(ARMOR[i] * share * weight.armorShare), AttributeModifier.Operation.ADD_VALUE), slot);
            if (weight.toughnessShare > 0) {
                builder.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, round(TOUGHNESS[i] / 4 * weight.toughnessShare),
                        AttributeModifier.Operation.ADD_VALUE), slot);
            }
            if (weight.heavy) {
                if (KNOCKBACK[i] > 0) {
                    builder.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, KNOCKBACK[i] / 4, AttributeModifier.Operation.ADD_VALUE), slot);
                }
                builder.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(id, HEAVY_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), slot);
            }
            return builder.build();
        }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers() {
            return ItemAttributeModifiers.EMPTY; // Always read per stack, above.
        }

        @Override
        public boolean isValidRepairItem(ItemStack gear, ItemStack repair) {
            return repairs(gear, repair);
        }

        @Override
        public Component getName(ItemStack stack) {
            return nameWithStars(super.getName(stack), stack);
        }
    }

    /** The forged sword. */
    public static class Sword extends SwordItem {
        public Sword(Item.Properties properties) {
            super(Tiers.IRON, properties.durability(WEAPON_DURABILITY[0]));
        }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
            return weapon(SWORD_DAMAGE[index(Forging.stars(stack))], -2.4);
        }

        @Override
        public boolean isValidRepairItem(ItemStack gear, ItemStack repair) {
            return repairs(gear, repair);
        }

        @Override
        public Component getName(ItemStack stack) {
            return nameWithStars(super.getName(stack), stack);
        }
    }

    /** The forged axe: harder-hitting and slower than the sword, and a woodcutting axe too. */
    public static class Axe extends AxeItem {
        public Axe(Item.Properties properties) {
            super(Tiers.IRON, properties.durability(WEAPON_DURABILITY[0]));
        }

        @Override
        public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
            return weapon(SWORD_DAMAGE[index(Forging.stars(stack))] + 2.0, -3.0);
        }

        @Override
        public boolean isValidRepairItem(ItemStack gear, ItemStack repair) {
            return repairs(gear, repair);
        }

        @Override
        public Component getName(ItemStack stack) {
            return nameWithStars(super.getName(stack), stack);
        }
    }

    /** Attack damage (counting the player's own 1) and speed modifiers, like vanilla weapons'. */
    private static ItemAttributeModifiers weapon(double damage, double speed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage - 1.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
