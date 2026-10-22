package io.github.ranger3462.dungeondelving.gametest;

import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.forging.BellowsBlock;
import io.github.ranger3462.dungeondelving.forging.ForgeActions;
import io.github.ranger3462.dungeondelving.forging.ForgeWorkshop;
import io.github.ranger3462.dungeondelving.forging.Forging;
import io.github.ranger3462.dungeondelving.forging.ForgingPerks;
import io.github.ranger3462.dungeondelving.forging.HotMetalItem;
import io.github.ranger3462.dungeondelving.forging.TimingBar;
import io.github.ranger3462.dungeondelving.skill.CraftSkill;
import io.github.ranger3462.dungeondelving.skill.CraftingSkills;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks forging: heating, the Forge Hearth's workshop, striking, quenching and grinding, and the Forging skill. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class ForgingTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void heatingKeepsTheStarBeingWorkedTowards(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack heated = smelt(level, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(heated.is(Forging.HEATED_INGOT.get()) && Forging.stars(heated) == 1, "iron heats into a heated ingot aiming for ★1");
        ItemStack tempered = new ItemStack(Forging.TEMPERED_IRON_INGOT.get());
        tempered.set(Forging.STARS.get(), 3);
        ItemStack reheated = smelt(level, tempered);
        helper.assertTrue(reheated.is(Forging.HEATED_INGOT.get()) && Forging.stars(reheated) == 4, "a ★3 tempered ingot heats up aiming for ★4");
        ItemStack coldStruck = new ItemStack(Forging.STRUCK_INGOT.get());
        coldStruck.set(Forging.STARS.get(), 2);
        coldStruck.set(Forging.HEATED_AT.get(), 0L);
        ItemStack hotStruck = smelt(level, coldStruck);
        helper.assertTrue(hotStruck.is(Forging.STRUCK_INGOT.get()) && Forging.stars(hotStruck) == 2 && !hotStruck.has(Forging.HEATED_AT.get()),
                "a cold struck ingot heats back up as it was");
        helper.assertTrue(HotMetalItem.temperature(hotStruck, 1000, 600) == 1.0f, "metal fresh from the furnace hasn't started cooling");
        hotStruck.set(Forging.HEATED_AT.get(), 1000L);
        helper.assertTrue(Math.abs(HotMetalItem.temperature(hotStruck, 1300, 600) - 0.5f) < 0.01f, "half the cooling time leaves it half as hot");
        helper.succeed();
    }

    private static ItemStack smelt(ServerLevel level, ItemStack input) {
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, recipeInput, level).orElseThrow()
                .value().assemble(recipeInput, level.registryAccess());
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void theHearthCountsItsStations(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hearth = forgeOrigin(helper);
        try {
            set(level, hearth, Forging.FORGE_HEARTH.get().defaultBlockState());
            ForgeWorkshop empty = ForgeWorkshop.scan(level, hearth);
            helper.assertTrue(!empty.complete() && empty.missing().size() == 4 && empty.forgeLevel() == 0, "a lone hearth needs all four stations");
            buildBasicForge(level, hearth);
            ForgeWorkshop basic = ForgeWorkshop.scan(level, hearth);
            helper.assertTrue(basic.complete() && basic.forgeLevel() == 1 && basic.maxStars() == 3, "the basic stations make a level 1 forge (up to ★3)");
            set(level, hearth.offset(3, 0, 0), Blocks.BLAST_FURNACE.defaultBlockState());
            set(level, hearth.offset(-3, 0, 2), Forging.DWARVEN_ANVIL.get().defaultBlockState());
            helper.assertTrue(ForgeWorkshop.scan(level, hearth).forgeLevel() == 2, "a blast furnace and Dwarven Anvil make a level 2 forge");
            set(level, hearth.offset(4, 0, 0), Forging.BELLOWS.get().defaultBlockState());
            set(level, hearth.offset(0, 0, 4), Forging.QUENCH_TUB.get().defaultBlockState());
            set(level, hearth.offset(0, 0, -4), Forging.GRINDING_WHEEL.get().defaultBlockState());
            ForgeWorkshop full = ForgeWorkshop.scan(level, hearth);
            helper.assertTrue(full.forgeLevel() == 3 && full.maxStars() == Forging.MAX_STARS, "every upgrade makes a level 3 forge (up to ★7)");
            for (int i = 0; i < 16; i++) {
                set(level, hearth.offset(-6 + i % 8, 1, 6 - i / 8), Blocks.BRICKS.defaultBlockState());
            }
            helper.assertTrue(Math.abs(ForgeWorkshop.scan(level, hearth).decorBonus() - 0.01f) < 0.001f, "16 bricks give a +1% workshop bonus");
            helper.assertTrue(ForgeWorkshop.around(level, hearth.offset(0, 0, 3)).isPresent(), "a station in range belongs to the workshop");
            helper.assertTrue(ForgeWorkshop.around(level, hearth.offset(ForgeWorkshop.RADIUS * 3, 0, 0)).isEmpty(),
                    "a spot far from any hearth has no workshop");
        } finally {
            clear(level, hearth);
        }
        helper.succeed();
    }

    /** Heats, strikes, quenches and grinds an ingot with good timing, and gets a ★1 Tempered Iron Ingot. */
    @GameTest(template = ANY_TEMPLATE)
    public static void aPerfectRunMakesATemperedIngot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hearth = forgeOrigin(helper);
        ServerPlayer player = smith(helper);
        try {
            set(level, hearth, Forging.FORGE_HEARTH.get().defaultBlockState());
            buildBasicForge(level, hearth);
            ItemStack heated = new ItemStack(Forging.HEATED_INGOT.get());
            heated.set(Forging.STARS.get(), 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, heated);
            BlockPos anvil = hearth.offset(2, 0, 0);
            long now = level.getGameTime();
            ForgeActions.Session strike = ForgeActions.begin(player, level, anvil, level.getBlockState(anvil), player.getMainHandItem(),
                    TimingBar.Kind.STRIKE, now).orElseThrow();
            helper.assertTrue(strike.needed() == ForgeActions.strikesFor(1), "a ★1 ingot takes " + ForgeActions.strikesFor(1) + " strikes");
            int xpBefore = CraftingSkills.xp(player, CraftSkill.FORGING);
            while (ForgeActions.session(player).isPresent()) {
                now += 3;
                ForgeActions.strike(player, level, strike, player.getMainHandItem(), now, strike.greenMarker());
            }
            ItemStack struck = find(player, Forging.STRUCK_INGOT.get());
            helper.assertTrue(!struck.isEmpty() && Forging.stars(struck) == 1, "strikes in the green on glowing-hot metal never crack");
            helper.assertTrue(find(player, Forging.HEATED_INGOT.get()).isEmpty(), "the heated ingot is used up");
            helper.assertTrue(player.getMainHandItem().is(Forging.STRUCK_INGOT.get()), "the struck ingot lands in hand, ready to quench");
            helper.assertTrue(CraftingSkills.xp(player, CraftSkill.FORGING) == xpBefore + 3 * strike.needed(), "each good strike gives 3 XP");

            player.setItemInHand(InteractionHand.MAIN_HAND, struck.copy());
            struck.setCount(0);
            BlockPos tub = hearth.offset(0, 0, 4);
            set(level, tub, Forging.QUENCH_TUB.get().defaultBlockState().setValue(io.github.ranger3462.dungeondelving.forging.QuenchTubBlock.LEVEL, 4));
            ForgeActions.quench(player, level, tub, level.getBlockState(tub), player.getMainHandItem());
            ItemStack quenched = find(player, Forging.QUENCHED_INGOT.get());
            helper.assertTrue(!quenched.isEmpty(), "quenching in the oil tub never cracks");
            helper.assertTrue(level.getBlockState(tub).getValue(io.github.ranger3462.dungeondelving.forging.QuenchTubBlock.LEVEL) == 3,
                    "each quench uses a level of oil");

            player.setItemInHand(InteractionHand.MAIN_HAND, quenched.copy());
            quenched.setCount(0);
            BlockPos grindstone = hearth.offset(-2, 0, 0);
            ForgeActions.Session grind = ForgeActions.begin(player, level, grindstone, level.getBlockState(grindstone), player.getMainHandItem(),
                    TimingBar.Kind.GRIND, now).orElseThrow();
            while (ForgeActions.session(player).isPresent()) {
                now += 3;
                ForgeActions.strike(player, level, grind, player.getMainHandItem(), now, grind.greenMarker());
            }
            ItemStack tempered = find(player, Forging.TEMPERED_IRON_INGOT.get());
            helper.assertTrue(!tempered.isEmpty() && Forging.stars(tempered) == 1, "grinding finishes a ★1 Tempered Iron Ingot");
        } finally {
            clear(level, hearth);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void starsNeedABetterForgeAndMoreSkill(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hearth = forgeOrigin(helper);
        ServerPlayer player = smith(helper);
        try {
            set(level, hearth, Forging.FORGE_HEARTH.get().defaultBlockState());
            buildBasicForge(level, hearth);
            BlockPos anvil = hearth.offset(2, 0, 0);
            CraftingSkills.setLevel(player, CraftSkill.FORGING, ForgingPerks.levelFor(4));
            helper.assertTrue(tryStrike(player, level, anvil, 4).isEmpty(), "a level 1 forge can't work ★4 metal");
            CraftingSkills.setLevel(player, CraftSkill.FORGING, 1);
            helper.assertTrue(tryStrike(player, level, anvil, 2).isEmpty(), "a level 1 smith can't forge ★2");
            CraftingSkills.setLevel(player, CraftSkill.FORGING, ForgingPerks.levelFor(2));
            Optional<ForgeActions.Session> session = tryStrike(player, level, anvil, 2);
            helper.assertTrue(session.isPresent(), "a level " + ForgingPerks.levelFor(2) + " smith can forge ★2 at a level 1 forge");
            ForgeActions.end(player);
            ItemStack cold = new ItemStack(Forging.HEATED_INGOT.get());
            cold.set(Forging.HEATED_AT.get(), level.getGameTime() - 10_000);
            player.setItemInHand(InteractionHand.MAIN_HAND, cold);
            helper.assertTrue(ForgeActions.begin(player, level, anvil, level.getBlockState(anvil), cold, TimingBar.Kind.STRIKE, level.getGameTime() + 1)
                    .isEmpty(), "cold metal can't be struck");
        } finally {
            clear(level, hearth);
        }
        helper.succeed();
    }

    private static Optional<ForgeActions.Session> tryStrike(ServerPlayer player, ServerLevel level, BlockPos anvil, int stars) {
        ItemStack heated = new ItemStack(Forging.HEATED_INGOT.get());
        heated.set(Forging.STARS.get(), stars);
        player.setItemInHand(InteractionHand.MAIN_HAND, heated);
        return ForgeActions.begin(player, level, anvil, level.getBlockState(anvil), heated, TimingBar.Kind.STRIKE, level.getGameTime());
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void offStrikesCanCrackTheIngotIntoNuggets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hearth = forgeOrigin(helper);
        ServerPlayer player = smith(helper);
        try {
            set(level, hearth, Forging.FORGE_HEARTH.get().defaultBlockState());
            buildBasicForge(level, hearth);
            BlockPos anvil = hearth.offset(2, 0, 0);
            boolean cracked = false;
            long now = level.getGameTime();
            for (int attempt = 0; attempt < 30 && !cracked; attempt++) {
                ForgeActions.Session session = tryStrike(player, level, anvil, 1).orElseThrow();
                while (ForgeActions.session(player).isPresent()) {
                    now += 3;
                    ForgeActions.strike(player, level, session, player.getMainHandItem(), now, session.missMarker());
                }
                cracked = find(player, Forging.STRUCK_INGOT.get()).isEmpty() && find(player, Items.IRON_NUGGET).getCount() == ForgingPerks.scrapNuggets(1);
                player.getInventory().clearContent();
            }
            helper.assertTrue(cracked, "badly-timed strikes should crack an ingot sooner or later");
            helper.assertTrue(ForgeActions.missChance(TimingBar.Kind.STRIKE, 1) == ForgeActions.STRIKE_MISS_CRACK,
                    "the first miss has a " + ForgeActions.STRIKE_MISS_CRACK + " crack chance");
            helper.assertTrue(ForgeActions.missChance(TimingBar.Kind.STRIKE, 3) > ForgeActions.missChance(TimingBar.Kind.STRIKE, 2),
                    "each miss in a bar makes the next likelier to crack");
            helper.assertTrue(ForgeActions.coldPenalty(TimingBar.Kind.STRIKE, 0.9f) == 0.0f && ForgeActions.coldPenalty(TimingBar.Kind.STRIKE, 0.5f) > 0.0f
                    && ForgeActions.coldPenalty(TimingBar.Kind.STRIKE, 0.1f) > ForgeActions.coldPenalty(TimingBar.Kind.STRIKE, 0.5f),
                    "the cooler the metal, the riskier every strike");
            helper.assertTrue(ForgeActions.coldPenalty(TimingBar.Kind.GRIND, 0.1f) == 0.0f, "grinding works cold metal, so it has no cold penalty");
            helper.assertTrue(ForgingPerks.scrapNuggets(1) == 5 && ForgingPerks.scrapNuggets(30) == 8, "cracks return 5 nuggets, up to 8 with skill");
        } finally {
            clear(level, hearth);
        }
        helper.succeed();
    }

    /** A strike in the gold zone counts twice, so a 3-strike bar is done in 2. */
    @GameTest(template = ANY_TEMPLATE)
    public static void goldStrikesCountTwice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hearth = forgeOrigin(helper);
        ServerPlayer player = smith(helper);
        try {
            set(level, hearth, Forging.FORGE_HEARTH.get().defaultBlockState());
            buildBasicForge(level, hearth);
            ForgeActions.Session session = tryStrike(player, level, hearth.offset(2, 0, 0), 1).orElseThrow();
            long now = level.getGameTime();
            ForgeActions.strike(player, level, session, player.getMainHandItem(), now + 3, session.goldMarker());
            helper.assertTrue(session.done() == 2, "a gold strike counts as two");
            ForgeActions.strike(player, level, session, player.getMainHandItem(), now + 6, session.goldMarker());
            helper.assertTrue(ForgeActions.session(player).isEmpty() && !find(player, Forging.STRUCK_INGOT.get()).isEmpty(),
                    "two gold strikes finish a 3-strike bar");
            helper.assertTrue(TimingBar.judge(session.goldMarker(), session.low(), session.high()) == TimingBar.Hit.GOLD
                    && TimingBar.judge(session.greenMarker(), session.low(), session.high()) == TimingBar.Hit.GREEN
                    && TimingBar.judge(session.missMarker(), session.low(), session.high()) == TimingBar.Hit.MISS, "the zones judge strikes");
        } finally {
            clear(level, hearth);
        }
        helper.succeed();
    }

    /** Water quenching cracks about 30% of the time, whatever the smith's skill; only oil is safe. */
    @GameTest(template = ANY_TEMPLATE)
    public static void waterQuenchingIsRisky(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hearth = forgeOrigin(helper);
        ServerPlayer player = smith(helper);
        try {
            set(level, hearth, Forging.FORGE_HEARTH.get().defaultBlockState());
            buildBasicForge(level, hearth);
            CraftingSkills.setLevel(player, CraftSkill.FORGING, CraftingSkills.MAX_LEVEL);
            BlockPos cauldron = hearth.offset(0, 0, -2);
            int cracks = 0;
            for (int i = 0; i < 200; i++) {
                player.getInventory().clearContent();
                ItemStack struck = new ItemStack(Forging.STRUCK_INGOT.get());
                player.setItemInHand(InteractionHand.MAIN_HAND, struck);
                ForgeActions.quench(player, level, cauldron, level.getBlockState(cauldron), struck);
                if (find(player, Forging.QUENCHED_INGOT.get()).isEmpty()) {
                    cracks++;
                }
            }
            helper.assertTrue(cracks > 35 && cracks < 85, "about 30% of 200 water quenches should crack, even at max level; got " + cracks);
        } finally {
            clear(level, hearth);
        }
        helper.succeed();
    }

    /** The client's marker position is trusted only if it's where the marker could have been lately. */
    @GameTest(template = ANY_TEMPLATE)
    public static void reportedMarkersAreChecked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hearth = forgeOrigin(helper);
        ServerPlayer player = smith(helper);
        try {
            set(level, hearth, Forging.FORGE_HEARTH.get().defaultBlockState());
            buildBasicForge(level, hearth);
            ForgeActions.Session session = tryStrike(player, level, hearth.offset(2, 0, 0), 1).orElseThrow();
            long now = level.getGameTime() + 7;
            float there = TimingBar.marker(now - 1 - session.start(), session.period());
            helper.assertTrue(ForgeActions.checkedMarker(session, now, there) == there, "a plausible position is taken as reported");
            float far = there > 0.5f ? 0.0f : 1.0f;
            helper.assertTrue(Math.abs(ForgeActions.checkedMarker(session, now, far) - far) > 0.1f, "an impossible position is replaced");
        } finally {
            clear(level, hearth);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void bellowsPushAFurnaceAlong(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos furnacePos = forgeOrigin(helper);
        try {
            set(level, furnacePos, Blocks.FURNACE.defaultBlockState());
            set(level, furnacePos.relative(Direction.EAST), Forging.BELLOWS.get().defaultBlockState());
            AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(furnacePos);
            furnace.litTime = 100;
            furnace.cookingProgress = 10;
            furnace.cookingTotalTime = 100;
            helper.assertTrue(BellowsBlock.pump(level, furnacePos.relative(Direction.EAST)), "the bellows should find the lit furnace");
            helper.assertTrue(furnace.cookingProgress == 10 + BellowsBlock.PUMP_TICKS, "a pump adds " + BellowsBlock.PUMP_TICKS + " ticks of progress");
        } finally {
            set(level, furnacePos, Blocks.AIR.defaultBlockState());
            set(level, furnacePos.relative(Direction.EAST), Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void craftingSkillsLevelUpWithXp(GameTestHelper helper) {
        ServerPlayer player = smith(helper);
        helper.assertTrue(CraftingSkills.level(player, CraftSkill.FORGING) == 1, "everyone starts at level 1");
        CraftingSkills.addXp(player, CraftSkill.FORGING, CraftingSkills.xpFor(5));
        helper.assertTrue(CraftingSkills.level(player, CraftSkill.FORGING) == 5, "enough XP for level 5 reaches level 5");
        helper.assertTrue(CraftingSkills.level(player, CraftSkill.WEAVING) == 1, "skills level separately");
        for (int level = 1; level <= CraftingSkills.MAX_LEVEL; level++) {
            helper.assertTrue(CraftingSkills.levelFor(CraftingSkills.xpFor(level)) == level, "the XP for level " + level + " should reach it exactly");
        }
        helper.assertTrue(ForgingPerks.coolTicks(20) > ForgingPerks.coolTicks(1), "skilled smiths' metal cools slower");
        helper.succeed();
    }

    // --- Helpers ---

    private static ServerPlayer smith(GameTestHelper helper) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "forge_tester"));
    }

    private static BlockPos forgeOrigin(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(0, 1, 0)).above(110);
    }

    /** A furnace, anvil, water cauldron and grindstone around a hearth. */
    private static void buildBasicForge(ServerLevel level, BlockPos hearth) {
        set(level, hearth.offset(0, 0, 2), Blocks.FURNACE.defaultBlockState());
        set(level, hearth.offset(2, 0, 0), Blocks.ANVIL.defaultBlockState());
        set(level, hearth.offset(0, 0, -2), Blocks.WATER_CAULDRON.defaultBlockState());
        set(level, hearth.offset(-2, 0, 0), Blocks.GRINDSTONE.defaultBlockState());
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void clear(ServerLevel level, BlockPos hearth) {
        for (BlockPos pos : BlockPos.betweenClosed(hearth.offset(-8, -1, -8), hearth.offset(8, 2, 8))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static ItemStack find(ServerPlayer player, net.minecraft.world.item.Item item) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                return stack;
            }
        }
        return player.getMainHandItem().is(item) ? player.getMainHandItem() : ItemStack.EMPTY;
    }
}
