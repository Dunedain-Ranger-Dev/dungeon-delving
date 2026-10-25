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

    /** Every piece of gear carries a ★ (DD-89): what counts as gear, and the ★ loot gives it. */
    @GameTest(template = ANY_TEMPLATE)
    public static void everyPieceOfGearGetsAStar(GameTestHelper helper) {
        for (var item : new net.minecraft.world.item.Item[] { Items.IRON_SWORD, Items.DIAMOND_CHESTPLATE, Items.IRON_PICKAXE, Items.BOW,
                Items.CROSSBOW, Items.NETHERITE_AXE, Items.TRIDENT }) {
            helper.assertTrue(Quality.isGear(new ItemStack(item)), item + " is gear");
        }
        for (var item : new net.minecraft.world.item.Item[] { Items.STICK, Items.ELYTRA, Items.FISHING_ROD, Items.IRON_INGOT }) {
            helper.assertFalse(Quality.isGear(new ItemStack(item)), item + " isn't gear");
        }
        helper.assertFalse(Quality.isGear(new ItemStack(BuiltInRegistries.ITEM.get(DungeonDelving.id("leather_panel")))), "parts carry their own ★");

        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        Quality.stamp(sword, 7);
        helper.assertTrue(Forging.stars(sword) == 7 && sword.getMaxDamage() > Items.IRON_SWORD.getDefaultInstance().getMaxDamage(),
                "a stamped ★7 sword has its ★ and more durability");
        Quality.stamp(sword, 3);
        helper.assertTrue(Forging.stars(sword) == 7, "stamping never changes a ★ already there");

        var random = net.minecraft.util.RandomSource.create(1);
        for (int i = 0; i < 50; i++) {
            int room = Quality.lootStars(DungeonDelving.id("chests/tier_1/room"), random);
            int boss = Quality.lootStars(DungeonDelving.id("bosses/lich_lord/tier_4"), random);
            helper.assertTrue(room >= 1 && room <= 3, "tier 1 rooms give ★1 to ★3, got " + room);
            helper.assertTrue(boss >= 5 && boss <= 7, "a tier 4 boss gives ★5 to ★7, got " + boss);
        }
        helper.assertTrue(Quality.lootStars(ResourceLocation.withDefaultNamespace("chests/simple_dungeon"), random) == Quality.BASE_STARS,
                "loot outside dungeons is standard, ★3");
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

    /** The pack's RPG Series recipes (tools/rpg_forging.py in the pack): forged iron, then upgrades with dungeon ingots. */
    @GameTest(template = ANY_TEMPLATE)
    public static void rpgGearIsForgedThenUpgraded(GameTestHelper helper) {
        var iron = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("rogues:iron_dagger"));
        var recipes = helper.getLevel().getRecipeManager();
        var registries = helper.getLevel().registryAccess();
        if (iron.isEmpty() || recipes.byKey(ResourceLocation.parse("rogues:diamond_dagger")).isEmpty()) {
            helper.succeed(); // The RPG Series or the pack's data isn't loaded.
            return;
        }
        helper.assertTrue(recipes.byKey(ResourceLocation.parse("rogues:iron_dagger")).get().value() instanceof StarredRecipes.Shaped,
                "the iron dagger is crafted from forged parts");
        helper.assertTrue(recipes.byKey(ResourceLocation.parse("rogues:golden_dagger")).isEmpty(), "the gold tier is off");

        ItemStack dagger = starred(iron.get(), 6);
        SmithingRecipeInput input = new SmithingRecipeInput(ItemStack.EMPTY, dagger,
                new ItemStack(BuiltInRegistries.ITEM.get(DungeonDelving.id("emberstone_ingot"))));
        var match = recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMITHING, input, helper.getLevel());
        helper.assertTrue(match.isPresent(), "an iron dagger and Emberstone make a diamond dagger, with no template");
        ItemStack diamond = match.get().value().assemble(input, registries);
        helper.assertTrue(diamond.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("rogues:diamond_dagger"))) && Forging.stars(diamond) == 6,
                "the diamond dagger keeps the stars; got " + diamond + " " + Forging.stars(diamond));
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
