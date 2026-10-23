package io.github.ranger3462.dungeondelving.gametest;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.forging.ForgedGear;
import io.github.ranger3462.dungeondelving.forging.Forging;
import io.github.ranger3462.dungeondelving.forging.Quality;
import io.github.ranger3462.dungeondelving.forging.StarredRecipes;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks forging quality on other mods' gear (DD-81): stats and durability from the ★, kept through smithing. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class QualityTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void starsScaleOtherGearsStats(GameTestHelper helper) {
        ItemStack plain = new ItemStack(Items.IRON_SWORD);
        double damage = total(plain, Attributes.ATTACK_DAMAGE) + 1;
        double speed = total(plain, Attributes.ATTACK_SPEED);
        helper.assertTrue(near(total(starred(Items.IRON_SWORD, 3), Attributes.ATTACK_DAMAGE) + 1, damage), "★3 is the item's own damage");
        ItemStack best = starred(Items.IRON_SWORD, 7);
        helper.assertTrue(near(total(best, Attributes.ATTACK_DAMAGE) + 1, damage * Quality.statMultiplier(7)),
                "★7 scales the weapon's whole damage; got " + (total(best, Attributes.ATTACK_DAMAGE) + 1));
        helper.assertTrue(total(best, Attributes.ATTACK_DAMAGE) + 1 > damage * 1.15, "★7 is clearly better");
        helper.assertTrue(near(total(best, Attributes.ATTACK_SPEED), speed), "speed isn't scaled");
        double armor = total(new ItemStack(Items.IRON_CHESTPLATE), Attributes.ARMOR);
        helper.assertTrue(total(starred(Items.IRON_CHESTPLATE, 1), Attributes.ARMOR) < armor, "★1 armor is a little worse");
        ItemStack forged = new ItemStack(BuiltInRegistries.ITEM.get(DungeonDelving.id("forged_sword")));
        if (ForgedGear.isGear(forged)) {
            helper.assertFalse(Quality.applies(forged), "our own forged gear keeps its own tables");
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void spellPowerAndRangedDamageScale(GameTestHelper helper) {
        helper.assertFalse(Quality.scales(Attributes.MOVEMENT_SPEED), "movement isn't quality");
        for (String id : new String[] { "spell_power:arcane", "ranged_weapon:damage", "irons_spellbooks:fire_spell_power" }) {
            var attribute = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(id));
            if (attribute.isPresent()) {
                helper.assertTrue(Quality.scales(attribute.get()), id + " scales with quality");
            }
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void starsSetDurabilityAndSurviveSmithing(GameTestHelper helper) {
        ItemStack sword = starred(Items.DIAMOND_SWORD, 5);
        ForgedGear.applyStats(sword);
        int diamond = Items.DIAMOND_SWORD.components().getOrDefault(DataComponents.MAX_DAMAGE, 0);
        helper.assertTrue(sword.getMaxDamage() == (int) Math.round(diamond * Quality.durabilityMultiplier(5)) && sword.getMaxDamage() > diamond,
                "a ★5 piece lasts longer; got " + sword.getMaxDamage());
        helper.assertTrue(new ItemStack(Items.DIAMOND_SWORD).getMaxDamage() == diamond, "unstarred gear is untouched");

        StarredRecipes.Smithing upgrade = new StarredRecipes.Smithing(Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                Ingredient.of(Items.DIAMOND_SWORD), Ingredient.of(Items.NETHERITE_INGOT), new ItemStack(Items.NETHERITE_SWORD));
        var registries = helper.getLevel().registryAccess();
        ItemStack netherite = upgrade.assemble(new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), sword,
                new ItemStack(Items.NETHERITE_INGOT)), registries);
        int base = Items.NETHERITE_SWORD.components().getOrDefault(DataComponents.MAX_DAMAGE, 0);
        helper.assertTrue(Forging.stars(netherite) == 5, "the upgrade keeps the ★");
        helper.assertTrue(netherite.getMaxDamage() == (int) Math.round(base * Quality.durabilityMultiplier(5)),
                "and its durability is worked out for netherite, not kept from diamond; got " + netherite.getMaxDamage());
        ItemStack weakIngot = starred(Items.NETHERITE_INGOT, 2);
        ItemStack capped = upgrade.assemble(new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), sword, weakIngot),
                registries);
        helper.assertTrue(Forging.stars(capped) == 2, "a lower-★ addition caps the result");
        helper.succeed();
    }

    private static ItemStack starred(net.minecraft.world.item.Item item, int stars) {
        ItemStack stack = new ItemStack(item);
        stack.set(Forging.STARS.get(), stars);
        return stack;
    }

    /** The total of a stack's modifiers to one attribute, over the slots it goes in (all the ones here add a value). */
    private static double total(ItemStack stack, Holder<Attribute> attribute) {
        double[] sum = { 0 };
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            stack.forEachModifier(slot, (holder, modifier) -> {
                if (holder.is(attribute)) {
                    sum[0] += modifier.amount();
                }
            });
        }
        return sum[0];
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }
}
