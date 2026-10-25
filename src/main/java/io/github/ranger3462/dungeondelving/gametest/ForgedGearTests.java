package io.github.ranger3462.dungeondelving.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers;
import io.github.ranger3462.dungeondelving.forging.CraftingXp;
import io.github.ranger3462.dungeondelving.forging.ForgeActions;
import io.github.ranger3462.dungeondelving.forging.ForgedGear;
import io.github.ranger3462.dungeondelving.forging.Forging;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import io.github.ranger3462.dungeondelving.forging.ForgingPerks;
import io.github.ranger3462.dungeondelving.forging.TimingBar;
import io.github.ranger3462.dungeondelving.skill.CraftSkill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks forged parts and gear: flux and ★ through crafting, gear stats per ★, repairs, tags, weaving and boiling leather. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class ForgedGearTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void partsTakeTheWorstStarCappedByTheFlux(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack plate = craft(level, tempered(3), new ItemStack(Forging.SAND_FLUX.get()));
        helper.assertTrue(plate.is(Forging.ARMOR_PLATE.get()) && Forging.stars(plate) == 3, "an ingot and flux make a ★3 plate, got " + Forging.stars(plate));
        ItemStack capped = craft(level, tempered(5), new ItemStack(Forging.SAND_FLUX.get()));
        helper.assertTrue(Forging.stars(capped) == 3, "sand flux caps a plate at ★3, got " + Forging.stars(capped));
        ItemStack borax = craft(level, tempered(5), new ItemStack(Forging.BORAX_FLUX.get()));
        helper.assertTrue(Forging.stars(borax) == 5, "borax flux allows ★5, got " + Forging.stars(borax));
        ItemStack rivets = craft(level, tempered(2), new ItemStack(Forging.SAND_FLUX.get()), new ItemStack(Items.IRON_NUGGET));
        helper.assertTrue(rivets.is(Forging.RIVETS.get()) && rivets.getCount() == 4 && Forging.stars(rivets) == 2,
                "an ingot, flux and a nugget make 4 ★2 rivets");
        ItemStack links = craft(level, tempered(2), tempered(4), new ItemStack(Forging.SAND_FLUX.get()));
        helper.assertTrue(links.is(Forging.CHAIN_LINKS.get()) && links.getCount() == 8 && Forging.stars(links) == 2,
                "two ingots and flux make 8 chain links, at the worse ingot's ★2");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void ourForgedGearIsRetired(GameTestHelper helper) {
        // DD-89: forged versions of vanilla and RPG gear took its place. The items stay, for pieces already made.
        var recipes = helper.getLevel().getRecipeManager();
        for (String id : List.of("plate_helmet", "hauberk_chestplate", "brigandine_boots", "forged_sword", "forged_axe", "leather_base", "gambeson_base")) {
            helper.assertTrue(recipes.byKey(DungeonDelving.id(id)).isEmpty(), id + " can't be crafted any more");
        }
        helper.assertTrue(CraftingXp.skillFor(new ItemStack(Forging.ARMOR.get(ForgedGear.Weight.HEAVY).get(ArmorItem.Type.HELMET).get())) == CraftSkill.FORGING,
                "pieces already made still count as Forging's");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void gearStatsScaleWithStars(GameTestHelper helper) {
        double heavyThree = setTotal(ForgedGear.Weight.HEAVY, 3, Attributes.ARMOR);
        helper.assertTrue(Math.abs(heavyThree - 20) < 0.5, "a ★3 heavy set is about 20 armor (just over diamond), got " + heavyThree);
        helper.assertTrue(Math.abs(setTotal(ForgedGear.Weight.HEAVY, 3, Attributes.ARMOR_TOUGHNESS) - 9) < 0.5, "a ★3 heavy set has about 9 toughness");
        helper.assertTrue(setTotal(ForgedGear.Weight.HEAVY, 7, Attributes.ARMOR) > setTotal(ForgedGear.Weight.HEAVY, 1, Attributes.ARMOR),
                "★7 armor beats ★1");
        helper.assertTrue(setTotal(ForgedGear.Weight.LIGHT, 3, Attributes.ARMOR) < setTotal(ForgedGear.Weight.MEDIUM, 3, Attributes.ARMOR)
                && setTotal(ForgedGear.Weight.MEDIUM, 3, Attributes.ARMOR) < heavyThree, "light < medium < heavy armor");
        helper.assertTrue(setTotal(ForgedGear.Weight.HEAVY, 3, Attributes.MOVEMENT_SPEED) < 0
                && setTotal(ForgedGear.Weight.LIGHT, 3, Attributes.MOVEMENT_SPEED) == 0, "only heavy armor slows you");
        helper.assertTrue(setTotal(ForgedGear.Weight.HEAVY, 7, Attributes.KNOCKBACK_RESISTANCE) > 0, "★7 plate resists knockback");
        ItemStack sword = starred(Forging.FORGED_SWORD.get(), 5);
        double damage = total(sword, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE) + 1;
        helper.assertTrue(Math.abs(damage - ForgedGear.SWORD_DAMAGE[4]) < 0.01, "a ★5 sword hits for " + ForgedGear.SWORD_DAMAGE[4] + ", got " + damage);
        helper.assertTrue(sword.getItem().isValidRepairItem(sword, tempered(5)) && !sword.getItem().isValidRepairItem(sword, tempered(4)),
                "forged gear is repaired with tempered ingots of its ★ or better");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void forgedGearIsEnchantable(GameTestHelper helper) {
        for (ForgedGear.Weight weight : ForgedGear.Weight.values()) {
            helper.assertTrue(new ItemStack(Forging.ARMOR.get(weight).get(ArmorItem.Type.HELMET).get()).is(ItemTags.HEAD_ARMOR_ENCHANTABLE),
                    weight + " helmets take helmet enchantments");
            helper.assertTrue(new ItemStack(Forging.ARMOR.get(weight).get(ArmorItem.Type.BOOTS).get()).is(ItemTags.FOOT_ARMOR_ENCHANTABLE),
                    weight + " boots take boot enchantments");
        }
        helper.assertTrue(new ItemStack(Forging.FORGED_SWORD.get()).is(ItemTags.SHARP_WEAPON_ENCHANTABLE), "the forged sword takes Sharpness");
        helper.assertTrue(new ItemStack(Forging.FORGED_AXE.get()).is(ItemTags.MINING_ENCHANTABLE), "the forged axe takes Efficiency");
        helper.assertTrue(new ItemStack(Forging.ARMOR.get(ForgedGear.Weight.LIGHT).get(ArmorItem.Type.CHESTPLATE).get()).is(ItemTags.DYEABLE),
                "brigandine can be dyed");
        helper.assertTrue(helper.getLevel().registryAccess().registryOrThrow(Registries.BLOCK).getTag(DungeonMarkers.tierResources(2)).orElseThrow()
                .contains(Forging.BORAX_DEPOSIT.get().builtInRegistryHolder()), "borax turns up in tier 2 dungeons");
        helper.succeed();
    }

    /** Salvaging a piece gives back some of its metal as tempered ingots of its ★, more with skill, less when worn. */
    @GameTest(template = ANY_TEMPLATE)
    public static void salvagingReturnsTemperedIngots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ledger = helper.absolutePos(new BlockPos(0, 1, 0)).above(110);
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "salvage_tester"));
        try {
            level.setBlock(ledger, ModRegistries.GUILD_LEDGER.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(ledger.offset(0, 0, 2), Blocks.FURNACE.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(ledger.offset(2, 0, 0), Blocks.ANVIL.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(ledger.offset(0, 0, -2), Blocks.WATER_CAULDRON.defaultBlockState(), Block.UPDATE_CLIENTS);
            BlockPos grindstone = ledger.offset(-2, 0, 0);
            level.setBlock(grindstone, Blocks.GRINDSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            ItemStack chest = starred(Forging.ARMOR.get(ForgedGear.Weight.HEAVY).get(ArmorItem.Type.CHESTPLATE).get(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, chest);
            ForgeActions.salvage(player, level, grindstone, chest);
            int ingots = player.getInventory().items.stream().filter(stack -> stack.is(Forging.TEMPERED_IRON_INGOT.get()) && Forging.stars(stack) == 3)
                    .mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(ingots == 3, "a new ★3 plate cuirass (8 ingots) salvages to 3 ★3 ingots without Thrifty, got " + ingots);
            float plain = ForgingPerks.salvageShare(player);
            player.addTag(io.github.ranger3462.dungeondelving.skill.Perk.THRIFTY.tag(2));
            helper.assertTrue(ForgingPerks.salvageShare(player) > plain, "Thrifty smiths salvage more");
            player.removeTag(io.github.ranger3462.dungeondelving.skill.Perk.THRIFTY.tag(2));
        } finally {
            for (BlockPos pos : BlockPos.betweenClosed(ledger.offset(-3, 0, -3), ledger.offset(3, 0, 3))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        helper.succeed();
    }

    // --- Helpers ---

    private static ItemStack tempered(int stars) {
        return part(Forging.TEMPERED_IRON_INGOT.get(), stars);
    }

    private static ItemStack part(Item item, int stars) {
        ItemStack stack = new ItemStack(item);
        stack.set(Forging.STARS.get(), stars);
        return stack;
    }

    private static ItemStack starred(Item item, int stars) {
        ItemStack stack = part(item, stars);
        ForgedGear.applyStats(stack);
        return stack;
    }

    /** Crafts whatever these items make in a 3x3 grid (filled row by row), or an empty stack. */
    private static ItemStack craft(ServerLevel level, ItemStack... items) {
        List<ItemStack> grid = new ArrayList<>(List.of(items));
        while (grid.size() < 9) {
            grid.add(ItemStack.EMPTY);
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(recipe -> recipe.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
    }

    private static double setTotal(ForgedGear.Weight weight, int stars, Holder<Attribute> attribute) {
        double total = 0;
        for (var piece : Forging.ARMOR.get(weight).values()) {
            ItemStack stack = starred(piece.get(), stars);
            total += total(stack, ((ArmorItem) stack.getItem()).getEquipmentSlot(), attribute);
        }
        return total;
    }

    private static double total(ItemStack stack, EquipmentSlot slot, Holder<Attribute> attribute) {
        double[] sum = { 0 };
        stack.forEachModifier(slot, (holder, modifier) -> {
            if (holder.equals(attribute)) {
                sum[0] += modifier.amount();
            }
        });
        return sum[0];
    }
}
