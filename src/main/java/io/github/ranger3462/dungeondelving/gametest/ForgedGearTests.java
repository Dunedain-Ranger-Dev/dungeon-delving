package io.github.ranger3462.dungeondelving.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers;
import io.github.ranger3462.dungeondelving.forging.ForgeActions;
import io.github.ranger3462.dungeondelving.forging.ForgedGear;
import io.github.ranger3462.dungeondelving.forging.Forging;
import io.github.ranger3462.dungeondelving.forging.ForgingPerks;
import io.github.ranger3462.dungeondelving.forging.TimingBar;
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
    public static void craftedGearTakesItsPartsStar(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack plate = part(Forging.ARMOR_PLATE.get(), 2);
        ItemStack helmet = craft(level, plate, plate, plate, plate, new ItemStack(Forging.LEATHER_BASE.get()), plate);
        helper.assertTrue(helmet.is(Forging.ARMOR.get(ForgedGear.Weight.HEAVY).get(ArmorItem.Type.HELMET).get()) && Forging.stars(helmet) == 2,
                "plates in a helmet's shape around a leather backing make a ★2 Plate Helm");
        ItemStack hardened = new ItemStack(Forging.HARDENED_LEATHER.get());
        ItemStack rivet = part(Forging.RIVETS.get(), 3);
        ItemStack brigandine = craft(level, hardened, rivet, hardened, hardened, new ItemStack(Forging.GAMBESON_BASE.get()), hardened);
        helper.assertTrue(brigandine.is(Forging.ARMOR.get(ForgedGear.Weight.LIGHT).get(ArmorItem.Type.HELMET).get()) && Forging.stars(brigandine) == 3,
                "hardened leather with a rivet around a gambeson backing makes a ★3 Brigandine Helm (its ★ from the rivets)");
        helper.assertTrue(helmet.getMaxDamage() == ForgedGear.armorDurability(ArmorItem.Type.HELMET, 2), "a ★2 helm gets ★2 durability");
        ItemStack sword = craft(level, part(Forging.ARMOR_PLATE.get(), 4), ItemStack.EMPTY, ItemStack.EMPTY, part(Forging.ARMOR_PLATE.get(), 4),
                ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.STICK));
        helper.assertTrue(sword.is(Forging.FORGED_SWORD.get()) && Forging.stars(sword) == 4, "two ★4 plates and a stick make a ★4 sword");
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

    @GameTest(template = ANY_TEMPLATE)
    public static void weavingTurnsStringIntoCloth(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos loom = helper.absolutePos(new BlockPos(1, 2, 1)).above(110);
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "weave_tester"));
        try {
            level.setBlock(loom, Blocks.LOOM.defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.assertTrue(weave(player, level, loom, TimingBar.Hit.GREEN) == 1, "a clean weave makes a cloth (bonus cloth is for talents)");
            helper.assertTrue(weave(player, level, loom, TimingBar.Hit.GOLD) == 1, "gold stitches count twice, but still make one cloth");
            boolean snapped = false;
            for (int i = 0; i < 30 && !snapped; i++) {
                snapped = weave(player, level, loom, TimingBar.Hit.MISS) == 0 && strings(player) == 1;
            }
            helper.assertTrue(snapped, "missed stitches snap the thread sooner or later, saving one string");
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STRING, 2));
            helper.assertTrue(ForgeActions.begin(player, level, loom, level.getBlockState(loom), player.getMainHandItem(), TimingBar.Kind.WEAVE,
                    level.getGameTime()).isEmpty(), "weaving takes " + ForgeActions.WEAVE_STRING + " string");
        } finally {
            level.setBlock(loom, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        helper.succeed();
    }

    /** Weaves once, landing every stitch as {@code hit}, and returns the cloth made. */
    private static int weave(ServerPlayer player, ServerLevel level, BlockPos loom, TimingBar.Hit hit) {
        player.getInventory().clearContent();
        ForgeActions.end(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STRING, ForgeActions.WEAVE_STRING));
        long now = level.getGameTime();
        ForgeActions.Session session = ForgeActions.begin(player, level, loom, level.getBlockState(loom), player.getMainHandItem(), TimingBar.Kind.WEAVE,
                now).orElseThrow();
        while (ForgeActions.session(player).isPresent()) {
            now += 3;
            ForgeActions.land(player, level, session, player.getMainHandItem(), now, hit);
        }
        return count(player, Forging.WOVEN_CLOTH.get());
    }

    private static int strings(ServerPlayer player) {
        return count(player, Items.STRING);
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    /** Scraping a hide on the Tanning Rack makes refined leather; a torn hide leaves scraps. */
    @GameTest(template = ANY_TEMPLATE)
    public static void scrapingRefinesLeather(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rack = helper.absolutePos(new BlockPos(1, 2, 1)).above(110);
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "scrape_tester"));
        try {
            level.setBlock(rack, Forging.TANNING_RACK.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.assertTrue(scrape(player, level, rack, false) == 1, "a clean pass scrapes a leather into refined leather");
            boolean torn = false;
            for (int i = 0; i < 30 && !torn; i++) {
                torn = scrape(player, level, rack, true) == 0 && count(player, Forging.LEATHER_SCRAPS.get()) == 1;
            }
            helper.assertTrue(torn, "missed patches tear the hide sooner or later, leaving scraps");
            helper.assertTrue(TimingBar.pass(ForgeActions.SCRAPE_PASS * 2, ForgeActions.SCRAPE_PASS) == 1.0f, "the scraper's pass ends at the far edge");
        } finally {
            level.setBlock(rack, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        helper.succeed();
    }

    /** Scrapes a leather once, hitting every patch's middle (or missing them all), and returns the refined leather made. */
    private static int scrape(ServerPlayer player, ServerLevel level, BlockPos rack, boolean miss) {
        player.getInventory().clearContent();
        ForgeActions.end(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEATHER));
        long now = level.getGameTime();
        ForgeActions.Session session = ForgeActions.begin(player, level, rack, level.getBlockState(rack), player.getMainHandItem(), TimingBar.Kind.SCRAPE,
                now).orElseThrow();
        while (ForgeActions.session(player).isPresent()) {
            now += 3;
            ForgeActions.strike(player, level, session, player.getMainHandItem(), now, miss ? session.missMarker() : session.goldMarker());
        }
        return count(player, Forging.REFINED_LEATHER.get());
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void leatherBoilsOverHeat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cauldron = helper.absolutePos(new BlockPos(1, 2, 1)).above(110);
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "boil_tester"));
        try {
            level.setBlock(cauldron, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), Block.UPDATE_CLIENTS);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Forging.REFINED_LEATHER.get(), 2));
            ForgeActions.boil(player, level, cauldron, level.getBlockState(cauldron), player.getMainHandItem());
            helper.assertTrue(player.getMainHandItem().getCount() == 2, "cold water doesn't boil leather");
            level.setBlock(cauldron.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), Block.UPDATE_CLIENTS);
            ForgeActions.boil(player, level, cauldron, level.getBlockState(cauldron), player.getMainHandItem());
            boolean boiled = player.getInventory().items.stream().anyMatch(stack -> stack.is(Forging.BOILED_LEATHER.get()));
            helper.assertTrue(boiled && player.getMainHandItem().getCount() == 1, "leather boils over a lit campfire");
            helper.assertTrue(level.getBlockState(cauldron).getValue(LayeredCauldronBlock.LEVEL) == 2, "boiling uses a level of water");
        } finally {
            level.setBlock(cauldron, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(cauldron.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        helper.succeed();
    }

    /** Salvaging a piece gives back some of its metal as tempered ingots of its ★, more with skill, less when worn. */
    @GameTest(template = ANY_TEMPLATE)
    public static void salvagingReturnsTemperedIngots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hearth = helper.absolutePos(new BlockPos(0, 1, 0)).above(110);
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "salvage_tester"));
        try {
            level.setBlock(hearth, Forging.FORGE_HEARTH.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(hearth.offset(0, 0, 2), Blocks.FURNACE.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(hearth.offset(2, 0, 0), Blocks.ANVIL.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(hearth.offset(0, 0, -2), Blocks.WATER_CAULDRON.defaultBlockState(), Block.UPDATE_CLIENTS);
            BlockPos grindstone = hearth.offset(-2, 0, 0);
            level.setBlock(grindstone, Blocks.GRINDSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            ItemStack chest = starred(Forging.ARMOR.get(ForgedGear.Weight.HEAVY).get(ArmorItem.Type.CHESTPLATE).get(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, chest);
            ForgeActions.salvage(player, level, grindstone, chest);
            int ingots = player.getInventory().items.stream().filter(stack -> stack.is(Forging.TEMPERED_IRON_INGOT.get()) && Forging.stars(stack) == 3)
                    .mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(ingots == 3, "a new ★3 plate cuirass (8 ingots) salvages to 3 ★3 ingots at level 1, got " + ingots);
            helper.assertTrue(ForgingPerks.salvageShare(30) > ForgingPerks.salvageShare(1), "skilled smiths salvage more");
        } finally {
            for (BlockPos pos : BlockPos.betweenClosed(hearth.offset(-3, 0, -3), hearth.offset(3, 0, 3))) {
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
