package io.github.ranger3462.dungeondelving.gametest;

import java.util.ArrayList;
import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.forging.Forging;
import io.github.ranger3462.dungeondelving.forging.Quality;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Checks forged versions of other gear (DD-81, DD-89): plain gear is untouched; a forged piece's ★ sits in its
 * material's band (iron ★1–3, diamond ★4–5, netherite ★6–7) and makes it stronger; smithing moves it up a band.
 */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class QualityTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void forgedGearIsStrongerThanPlain(GameTestHelper helper) {
        ItemStack plain = new ItemStack(Items.IRON_SWORD);
        double damage = total(plain, Attributes.ATTACK_DAMAGE) + 1;
        double speed = total(plain, Attributes.ATTACK_SPEED);
        helper.assertFalse(Quality.applies(plain), "a plain sword has no ★ and its own stats");
        ItemStack one = starred(Items.IRON_SWORD, 1);
        ItemStack three = starred(Items.IRON_SWORD, 3);
        helper.assertTrue(near(total(one, Attributes.ATTACK_DAMAGE) + 1, damage * 1.05), "★1 iron is 5% better than plain");
        helper.assertTrue(near(total(three, Attributes.ATTACK_DAMAGE) + 1, damage * 1.15), "★3 iron is 15% better");
        helper.assertTrue(near(total(three, Attributes.ATTACK_SPEED), speed), "speed isn't scaled");
        double diamond = total(new ItemStack(Items.DIAMOND_SWORD), Attributes.ATTACK_DAMAGE) + 1;
        helper.assertTrue(near(total(starred(Items.DIAMOND_SWORD, 4), Attributes.ATTACK_DAMAGE) + 1, diamond * 1.05), "★4 diamond is 5% better");
        helper.assertTrue(total(starred(Items.DIAMOND_SWORD, 4), Attributes.ATTACK_DAMAGE) > total(three, Attributes.ATTACK_DAMAGE),
                "a higher ★ is always the stronger piece: ★4 diamond beats ★3 iron");
        double armor = total(new ItemStack(Items.IRON_CHESTPLATE), Attributes.ARMOR);
        helper.assertTrue(total(starred(Items.IRON_CHESTPLATE, 2), Attributes.ARMOR) > armor, "forged armor is better too");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void eachMaterialHasItsBand(GameTestHelper helper) {
        helper.assertTrue(Quality.band(new ItemStack(Items.IRON_SWORD)).orElseThrow() == Quality.Band.IRON, "iron is ★1–3");
        helper.assertTrue(Quality.band(new ItemStack(Items.DIAMOND_LEGGINGS)).orElseThrow() == Quality.Band.DIAMOND, "diamond is ★4–5");
        helper.assertTrue(Quality.band(new ItemStack(Items.NETHERITE_AXE)).orElseThrow() == Quality.Band.NETHERITE, "netherite is ★6–7");
        helper.assertTrue(Quality.band(new ItemStack(Items.GOLDEN_SWORD)).isEmpty(), "gold waits for Jewelcrafting");
        for (Item item : new Item[] { Items.IRON_SWORD, Items.DIAMOND_CHESTPLATE, Items.BOW, Items.NETHERITE_AXE }) {
            helper.assertTrue(Quality.isGear(new ItemStack(item)), item + " is gear");
        }
        for (Item item : new Item[] { Items.STICK, Items.ELYTRA, Items.FISHING_ROD, Items.IRON_INGOT }) {
            helper.assertFalse(Quality.isGear(new ItemStack(item)), item + " isn't gear");
        }
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        Quality.forge(sword, 7);
        helper.assertTrue(Forging.stars(sword) == 5 && sword.getMaxDamage() > Items.DIAMOND_SWORD.getDefaultInstance().getMaxDamage(),
                "a diamond piece is kept to ★5, and lasts longer than plain");

        RandomSource random = RandomSource.create(1);
        List<Integer> rooms = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            int room = Quality.lootStars(DungeonDelving.id("chests/tier_1/room"), Quality.Band.IRON, random);
            int boss = Quality.lootStars(DungeonDelving.id("bosses/lich_lord/tier_4"), Quality.Band.NETHERITE, random);
            helper.assertTrue(room == 0 || room >= 1 && room <= 3, "tier 1 loot is plain or ★1–3 iron, got " + room);
            helper.assertTrue(boss == 6 || boss == 7, "a boss always gives forged loot, got " + boss);
            rooms.add(room);
        }
        helper.assertTrue(rooms.contains(0) && rooms.stream().anyMatch(stars -> stars > 0), "tier 1 rooms give some forged gear, mostly plain");
        helper.assertTrue(Quality.lootStars(ResourceLocation.withDefaultNamespace("chests/simple_dungeon"), Quality.Band.IRON, random) == 0,
                "loot outside dungeons stays plain");
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

    /** Vanilla's own recipes stay; forged plates make forged iron, a diamond moves it up, netherite up again. */
    @GameTest(template = ANY_TEMPLATE)
    public static void vanillaGearIsForgedAndSmithedUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack air = ItemStack.EMPTY;
        ItemStack ingot = new ItemStack(Items.IRON_INGOT);
        ItemStack plain = craft(level, CraftingInput.of(1, 3, List.of(ingot, ingot.copy(), new ItemStack(Items.STICK))));
        helper.assertTrue(plain.is(Items.IRON_SWORD) && !plain.has(Forging.STARS.get()), "the vanilla iron sword is still plain iron and a stick");
        ItemStack plate = new ItemStack(Forging.ARMOR_PLATE.get());
        plate.set(Forging.STARS.get(), 2);
        ItemStack fine = plate.copy();
        fine.set(Forging.STARS.get(), 5);
        ItemStack forged = craft(level, CraftingInput.of(1, 3, List.of(plate, fine, new ItemStack(Items.STICK))));
        helper.assertTrue(forged.is(Items.IRON_SWORD) && Forging.stars(forged) == 2, "forged plates make a forged iron sword at the worst plate's ★");
        ItemStack best = craft(level, CraftingInput.of(1, 3, List.of(fine, fine.copy(), new ItemStack(Items.STICK))));
        helper.assertTrue(Forging.stars(best) == 3, "forged iron tops out at ★3, whatever its plates");

        ItemStack diamond = smith(level, ItemStack.EMPTY, forged, new ItemStack(Items.DIAMOND));
        helper.assertTrue(diamond.is(Items.DIAMOND_SWORD) && Forging.stars(diamond) == 4, "a ★2 iron sword and a diamond make a ★4 diamond sword");
        ItemStack top = smith(level, ItemStack.EMPTY, best, new ItemStack(Items.DIAMOND));
        helper.assertTrue(Forging.stars(top) == 5, "from ★3 iron, ★5 diamond");
        int base = Items.DIAMOND_SWORD.components().getOrDefault(DataComponents.MAX_DAMAGE, 0);
        helper.assertTrue(top.getMaxDamage() == (int) Math.round(base * 1.2), "its durability is worked out for diamond: got " + top.getMaxDamage());
        helper.assertTrue(smith(level, ItemStack.EMPTY, plain, new ItemStack(Items.DIAMOND)).isEmpty(), "a plain iron sword can't be smithed up");

        ItemStack template = new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        ItemStack netherite = smith(level, template, top, new ItemStack(Items.NETHERITE_INGOT));
        helper.assertTrue(netherite.is(Items.NETHERITE_SWORD) && Forging.stars(netherite) == 7, "★5 diamond and netherite make ★7 netherite");
        ItemStack plainNetherite = smith(level, template, new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.NETHERITE_INGOT));
        helper.assertTrue(plainNetherite.is(Items.NETHERITE_SWORD) && !plainNetherite.has(Forging.STARS.get()), "vanilla's upgrade still works, plain");
        helper.succeed();
    }

    /** The pack's RPG Series recipes (tools/rpg_forging.py in the pack): their own recipes stay, with forged versions beside. */
    @GameTest(template = ANY_TEMPLATE)
    public static void rpgGearHasForgedVersions(GameTestHelper helper) {
        var iron = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("rogues:iron_dagger"));
        var recipes = helper.getLevel().getRecipeManager();
        if (iron.isEmpty() || recipes.byKey(ResourceLocation.parse("rogues:forged/iron_dagger")).isEmpty()) {
            helper.succeed(); // The RPG Series or the pack's data isn't loaded.
            return;
        }
        helper.assertTrue(recipes.byKey(ResourceLocation.parse("rogues:iron_dagger")).isPresent(), "the dagger's own recipe is back");
        helper.assertTrue(recipes.byKey(ResourceLocation.parse("rogues:golden_dagger")).isPresent(), "and the gold one");
        ItemStack dagger = starred(iron.get(), 3);
        ItemStack diamond = smith(helper.getLevel(), ItemStack.EMPTY, dagger, new ItemStack(Items.DIAMOND));
        helper.assertTrue(diamond.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("rogues:diamond_dagger"))) && Forging.stars(diamond) == 5,
                "a ★3 forged iron dagger and a diamond make a ★5 diamond dagger; got " + diamond);
        helper.succeed();
    }

    private static ItemStack craft(ServerLevel level, CraftingInput input) {
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).map(recipe -> recipe.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    private static ItemStack smith(ServerLevel level, ItemStack template, ItemStack base, ItemStack addition) {
        SmithingRecipeInput input = new SmithingRecipeInput(template, base, addition);
        return level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, level).map(recipe -> recipe.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    private static ItemStack starred(Item item, int stars) {
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
