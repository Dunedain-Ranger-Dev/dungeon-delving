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
import io.github.ranger3462.dungeondelving.skill.CraftingSkills;
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
        ItemStack sword = craft(level, part(Forging.BLADE.get(), 4), ItemStack.EMPTY, ItemStack.EMPTY, part(Forging.BLADE.get(), 4),
                ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.STICK));
        helper.assertTrue(sword.is(Forging.FORGED_SWORD.get()) && Forging.stars(sword) == 4, "two ★4 blades and a stick make a ★4 sword");
        helper.assertTrue(CraftingXp.skillFor(helmet) == CraftSkill.ARMORSMITHING && CraftingXp.skillFor(brigandine) == CraftSkill.WEAVING
                && CraftingXp.skillFor(new ItemStack(Forging.ARMOR.get(ForgedGear.Weight.MEDIUM).get(ArmorItem.Type.HELMET).get())) == CraftSkill.LEATHERWORKING,
                "plate trains Forging, hauberks Leatherworking, brigandine Weaving");
        helper.succeed();
    }

    /** Forging split in two (DD-89): weapon parts and weapons are Weaponsmithing's, armor parts and armor Armorsmithing's. */
    @GameTest(template = ANY_TEMPLATE)
    public static void eachSmithingSkillCapsItsOwnGear(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer smith = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "smith"));
        CraftingSkills.setLevel(smith, CraftSkill.WEAPONSMITHING, ForgingPerks.levelFor(5));
        CraftingSkills.setLevel(smith, CraftSkill.ARMORSMITHING, 1);
        try {
            net.neoforged.neoforge.common.CommonHooks.setCraftingPlayer(smith);
            ItemStack sword = craft(level, part(Forging.BLADE.get(), 4), ItemStack.EMPTY, ItemStack.EMPTY, part(Forging.BLADE.get(), 4), ItemStack.EMPTY,
                    ItemStack.EMPTY, new ItemStack(Items.STICK));
            helper.assertTrue(sword.is(Forging.FORGED_SWORD.get()) && Forging.stars(sword) == 4, "a good weaponsmith makes a ★4 sword from ★4 blades");
            ItemStack plate = part(Forging.ARMOR_PLATE.get(), 4);
            ItemStack helmet = craft(level, plate, plate, plate, plate, part(Forging.LEATHER_BASE.get(), 4), plate);
            helper.assertTrue(Forging.stars(helmet) == 1, "but a new armorsmith's helm is ★1, whatever the plates");
        } finally {
            net.neoforged.neoforge.common.CommonHooks.setCraftingPlayer(null);
        }
        helper.assertTrue(ForgingPerks.level(smith) == ForgingPerks.levelFor(5), "the forge work goes by the better of the two");
        helper.succeed();
    }

    /** Players' old Forging XP moves to both new skills, and the old tree's perks go (DD-89). */
    @GameTest(template = ANY_TEMPLATE)
    public static void oldForgingMovesToBothSmithingSkills(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "old_smith"));
        player.setData(io.github.ranger3462.dungeondelving.skill.CraftingSkills.XP, new java.util.HashMap<>(java.util.Map.of("forging", CraftingSkills.xpFor(12))));
        player.addTag(io.github.ranger3462.dungeondelving.skill.Perk.STEADY_HAND.tag(2));
        io.github.ranger3462.dungeondelving.skill.ForgingSplit.migrate(player);
        helper.assertTrue(CraftingSkills.level(player, CraftSkill.WEAPONSMITHING) == 12 && CraftingSkills.level(player, CraftSkill.ARMORSMITHING) == 12,
                "Forging 12 becomes Weaponsmithing 12 and Armorsmithing 12");
        helper.assertFalse(player.getData(io.github.ranger3462.dungeondelving.skill.CraftingSkills.XP).containsKey("forging"), "the old skill is gone");
        helper.assertFalse(player.getTags().contains(io.github.ranger3462.dungeondelving.skill.Perk.STEADY_HAND.tag(2)), "its perks are refunded");
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

    /** DD-92: staves and wands from their parts (★ the lowest part's), Iron's Spells stats by ★, and straps fitted as a grip. */
    @GameTest(template = ANY_TEMPLATE)
    public static void stavesWandsAndGripsAreMadeFromTheirParts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack focus = craft(level, ItemStack.EMPTY, new ItemStack(Items.AMETHYST_SHARD), ItemStack.EMPTY, new ItemStack(Items.AMETHYST_SHARD),
                part(io.github.ranger3462.dungeondelving.forging.Crafts.GOLD_FITTINGS.get(), 5), new ItemStack(Items.AMETHYST_SHARD), ItemStack.EMPTY,
                new ItemStack(Items.AMETHYST_SHARD));
        helper.assertTrue(focus.is(io.github.ranger3462.dungeondelving.forging.Crafts.ARCANE_FOCUS.get()) && Forging.stars(focus) == 5,
                "gold fittings set with amethyst make a ★5 focus: " + focus);
        ItemStack staff = craft(level, part(io.github.ranger3462.dungeondelving.forging.Crafts.ARCANE_FOCUS.get(), 3), ItemStack.EMPTY, ItemStack.EMPTY,
                part(io.github.ranger3462.dungeondelving.forging.Crafts.STAFF_WRAP.get(), 5), ItemStack.EMPTY, ItemStack.EMPTY,
                part(io.github.ranger3462.dungeondelving.forging.Crafts.STAFF_SHAFT.get(), 4));
        helper.assertTrue(staff.is(Forging.FORGED_STAFF.get()) && Forging.stars(staff) == 3 && staff.getMaxDamage() == ForgedGear.WEAPON_DURABILITY[2],
                "focus, wrap and shaft make a staff of the lowest part's ★: " + staff);
        ItemStack wand = craft(level, part(io.github.ranger3462.dungeondelving.forging.Crafts.ARCANE_FOCUS.get(), 6), ItemStack.EMPTY, ItemStack.EMPTY,
                part(io.github.ranger3462.dungeondelving.forging.Crafts.WAND_CORE.get(), 6));
        helper.assertTrue(wand.is(Forging.FORGED_WAND.get()) && Forging.stars(wand) == 6, "focus and core make a wand: " + wand);
        helper.assertTrue(CraftingXp.skillFor(staff) == CraftSkill.WOODWORKING, "staves and wands are Woodworking's");
        if (net.neoforged.fml.ModList.get().isLoaded("irons_spellbooks")) {
            double power = staff.getAttributeModifiers().modifiers().stream()
                    .filter(entry -> entry.attribute().is(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_power")))
                    .mapToDouble(entry -> entry.modifier().amount()).sum();
            helper.assertTrue(Math.abs(power - 3 * ForgedGear.STAFF_SPELL_POWER_PER_STAR) < 1e-6, "a ★3 staff adds 15% spell power: " + power);
            helper.assertTrue(wand.getAttributeModifiers().modifiers().stream()
                    .anyMatch(entry -> entry.attribute().is(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "cooldown_reduction"))),
                    "a wand cuts cooldowns");
        }

        ItemStack sword = starred(Forging.FORGED_SWORD.get(), 4);
        sword.setDamageValue(50);
        ItemStack gripped = craft(level, sword, part(io.github.ranger3462.dungeondelving.forging.Crafts.STRAPS.get(), 2));
        helper.assertTrue(gripped.is(Forging.FORGED_SWORD.get()) && Forging.stars(gripped) == 4 && gripped.get(Forging.GRIP.get()) == 2
                && gripped.getDamageValue() == 50, "straps fit a grip of their ★, and the sword keeps its own ★ and wear: " + gripped);
        double speed = gripped.getAttributeModifiers().modifiers().stream().filter(entry -> entry.attribute().equals(Attributes.ATTACK_SPEED))
                .mapToDouble(entry -> entry.modifier().amount()).sum();
        helper.assertTrue(Math.abs(speed - (-2.4 + 2 * ForgedGear.GRIP_SPEED_PER_STAR)) < 1e-6, "and swings a little faster: " + speed);
        helper.assertTrue(craft(level, gripped, part(io.github.ranger3462.dungeondelving.forging.Crafts.STRAPS.get(), 5)).isEmpty(), "one grip per weapon");
        helper.succeed();
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
