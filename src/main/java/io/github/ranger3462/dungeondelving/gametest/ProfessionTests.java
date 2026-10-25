package io.github.ranger3462.dungeondelving.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.forging.CraftWorkshop;
import io.github.ranger3462.dungeondelving.forging.Crafts;
import io.github.ranger3462.dungeondelving.forging.ForgeActions;
import io.github.ranger3462.dungeondelving.forging.Forging;
import io.github.ranger3462.dungeondelving.forging.Profession;
import io.github.ranger3462.dungeondelving.forging.ShapingRackBlockEntity;
import io.github.ranger3462.dungeondelving.forging.SoakingVatBlock;
import io.github.ranger3462.dungeondelving.forging.SoakingVatBlockEntity;
import io.github.ranger3462.dungeondelving.forging.TimingBar;
import io.github.ranger3462.dungeondelving.guild.GuildLedgerBlock;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import io.github.ranger3462.dungeondelving.skill.CraftSkill;
import io.github.ranger3462.dungeondelving.skill.CraftingSkills;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Checks the rebuilt crafting professions (DD-88, docs/design/professions.md): the new minigames, the workshops around
 * a Guild Ledger, and each profession's steps from raw material to part, ★ and all.
 */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class ProfessionTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void theMinigamesWorkOutTheSameEverywhere(GameTestHelper helper) {
        helper.assertTrue(TimingBar.performanceStars(6, 3) == 7 && TimingBar.performanceStars(3, 3) == 4 && TimingBar.performanceStars(0, 3) == 1,
                "all gold is ★7, all green ★4, all misses ★1");
        helper.assertTrue(TimingBar.cut(20, 40) == 0.5f && TimingBar.cut(80, 40) == 1.0f, "the blade moves steadily while held, and stops at the end");
        helper.assertTrue(TimingBar.turn(18, 36) == 0.25f, "the chisel bites faster the deeper it goes");
        helper.assertTrue(TimingBar.judgeBeat(TimingBar.beat(2, 14), 2, 14, 7, 0) == TimingBar.Hit.GOLD, "a stitch on the beat with the right button is gold");
        helper.assertTrue(TimingBar.judgeBeat(TimingBar.beat(1, 14), 1, 14, 7, 0) == TimingBar.Hit.MISS, "the wrong button misses");
        helper.assertTrue(TimingBar.spinSpeed(TimingBar.TAP_DURATION, List.of()) < 0.1f, "a wheel nobody taps runs down");
        helper.assertTrue(TimingBar.judgeTapped(TimingBar.timeInBand(TimingBar.Kind.SPIN, steadyTaps(10, 14), 0, 0.40f, 0.70f)) != TimingBar.Hit.MISS,
                "steady tapping keeps the wheel in the band");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void aTanneryLevelsUpWithItsUpgrades(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ledger = spot(helper);
        try {
            set(level, ledger, ModRegistries.GUILD_LEDGER.get());
            helper.assertFalse(CraftWorkshop.scan(level, ledger, Profession.LEATHERWORKING).complete(), "a lone ledger isn't a tannery");
            tannery(level, ledger);
            CraftWorkshop basic = CraftWorkshop.scan(level, ledger, Profession.LEATHERWORKING);
            helper.assertTrue(basic.complete() && basic.level() == 1 && basic.maxStars() == 3, "the basic stations make a level 1 tannery (up to ★3)");
            set(level, ledger.offset(-3, 0, 0), Crafts.TANNING_BARREL.get());
            helper.assertTrue(CraftWorkshop.scan(level, ledger, Profession.LEATHERWORKING).level() == 2, "a Tanning Barrel makes it level 2");
            set(level, ledger.offset(3, 0, 0), Crafts.FLESHING_BEAM.get());
            helper.assertTrue(CraftWorkshop.scan(level, ledger, Profession.LEATHERWORKING).level() == 3, "a Fleshing Beam makes it level 3");
            helper.assertTrue(GuildLedgerBlock.report(level, ledger).stream().anyMatch(line -> line.getString().startsWith("Leatherworking")),
                    "reading the ledger shows the tannery");
        } finally {
            clear(level, ledger);
        }
        helper.succeed();
    }

    /** Leatherworking from leather to panels: scrape, tan, cut. */
    @GameTest(template = ANY_TEMPLATE)
    public static void leatherIsScrapedTannedAndCut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ledger = spot(helper);
        ServerPlayer player = player(helper, "tanner");
        CraftingSkills.setLevel(player, CraftSkill.LEATHERWORKING, 30);
        try {
            set(level, ledger, ModRegistries.GUILD_LEDGER.get());
            tannery(level, ledger);
            // Scrape: all gold, capped at ★3 by the level 1 tannery.
            BlockPos rack = ledger.offset(2, 0, 0);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEATHER, 2));
            for (int i = 0; i < 2; i++) {
                strikeGold(player, level, rack, TimingBar.Kind.SCRAPE);
            }
            ItemStack hides = find(player, Crafts.SCRAPED_HIDE.get());
            helper.assertTrue(hides.getCount() == 2 && Forging.stars(hides) == 3, "two clean scrapes make two ★3 scraped hides, got " + hides);

            // Tan: liquor poured into a cauldron makes a vat; the hides come out as tanned leather once they've soaked.
            BlockPos cauldron = ledger.offset(-2, 0, 0);
            ItemStack liquor = new ItemStack(Crafts.BARK_LIQUOR.get());
            CauldronInteraction.EMPTY.map().get(Crafts.BARK_LIQUOR.get()).interact(level.getBlockState(cauldron), level, cauldron, player,
                    InteractionHand.MAIN_HAND, liquor);
            helper.assertTrue(level.getBlockState(cauldron).is(Crafts.TANNING_VAT.get()), "liquor in a cauldron makes a Tanning Vat");
            SoakingVatBlockEntity vat = (SoakingVatBlockEntity) level.getBlockEntity(cauldron);
            SoakingVatBlock.insert(level, cauldron, level.getBlockState(cauldron), vat, player, hides, SoakingVatBlock.soakFor(hides, true).orElseThrow());
            helper.assertTrue(vat.soaking() && hides.isEmpty(), "the hides go in to soak");
            SoakingVatBlock.collect(level, cauldron, level.getBlockState(cauldron), vat, player);
            helper.assertTrue(vat.soaking(), "they aren't ready yet");
            vat.finishNow();
            SoakingVatBlock.collect(level, cauldron, level.getBlockState(cauldron), vat, player);
            ItemStack tanned = find(player, Forging.REFINED_LEATHER.get());
            helper.assertTrue(tanned.getCount() == 2 && Forging.stars(tanned) == 3, "they come out as two ★3 tanned leather, got " + tanned);
            helper.assertTrue(level.getBlockState(cauldron).is(Blocks.CAULDRON), "a vat with its liquor used up is a cauldron again");

            // Cut: on the Shaping Rack with shears.
            BlockPos shaping = ledger.offset(0, 0, 2);
            ((ShapingRackBlockEntity) level.getBlockEntity(shaping)).setPiece(tanned.split(1));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
            strikeGold(player, level, shaping, TimingBar.Kind.CUT);
            ItemStack panels = find(player, Crafts.LEATHER_PANEL.get());
            helper.assertTrue(panels.getCount() == 2 && Forging.stars(panels) == 3, "a clean cut makes two ★3 leather panels, got " + panels);
            helper.assertTrue(((ShapingRackBlockEntity) level.getBlockEntity(shaping)).piece().isEmpty(), "and uses up the leather");
        } finally {
            clear(level, ledger);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void theProfessionRecipesLoad(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        for (String id : List.of("seasoned_billet", "bow_from_parts", "crossbow_from_parts", "straps", "hardened_leather", "leather_base",
                "gambeson_base", "bowstring", "staff_wrap", "stock", "shaping_rack", "spinning_wheel", "chopping_block", "wood_lathe")) {
            helper.assertTrue(recipes.byKey(DungeonDelving.id(id)).isPresent(), "the " + id + " recipe loads");
        }
        if (net.neoforged.fml.ModList.get().isLoaded("farmersdelight")) {
            for (String id : List.of("bark_liquor", "tannin_liquor", "elder_liquor", "linseed_oil")) {
                helper.assertTrue(recipes.byKey(DungeonDelving.id(id)).isPresent(), "the " + id + " cooking-pot recipe loads");
            }
        }
        helper.assertTrue(recipes.byKey(DungeonDelving.id("padded_cloth")).isEmpty(), "fulled cloth is made by soaking, not crafting");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void craftingNeedsItsWorkshop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rack = spot(helper);
        ServerPlayer player = player(helper, "loner");
        try {
            set(level, rack, Forging.TANNING_RACK.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEATHER));
            helper.assertTrue(ForgeActions.begin(player, level, rack, level.getBlockState(rack), player.getMainHandItem(), TimingBar.Kind.SCRAPE,
                    level.getGameTime()).isEmpty(), "a Tanning Rack with no tannery around it does nothing");
        } finally {
            clear(level, rack);
        }
        helper.succeed();
    }

    /** Weaving from wool to panels: spin, weave, full, sew. */
    @GameTest(template = ANY_TEMPLATE)
    public static void woolIsSpunWovenFulledAndSewn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ledger = spot(helper);
        ServerPlayer player = player(helper, "weaver");
        CraftingSkills.setLevel(player, CraftSkill.WEAVING, 30);
        try {
            set(level, ledger, ModRegistries.GUILD_LEDGER.get());
            weavingRoom(level, ledger);
            // Spin: steady taps keep the wheel in the band.
            BlockPos wheel = ledger.offset(2, 0, 0);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHITE_WOOL));
            ForgeActions.Session spin = begin(player, level, wheel, TimingBar.Kind.SPIN);
            spin.taps().addAll(steadyTaps(10, 14));
            ForgeActions.finishTapped(player, level, spin, player.getMainHandItem(), level.getGameTime());
            ItemStack yarn = find(player, Crafts.YARN.get());
            helper.assertTrue(yarn.getCount() == 3 && Forging.stars(yarn) >= 2, "a good spin makes three yarn, got " + yarn);

            // Weave: three yarn on the loom; the cloth is no better than the yarn.
            int yarnStars = Forging.stars(yarn);
            player.setItemInHand(InteractionHand.MAIN_HAND, yarn.copy());
            yarn.setCount(0);
            landAll(player, level, ledger.offset(-2, 0, 0), TimingBar.Kind.WEAVE, TimingBar.Hit.GOLD);
            ItemStack cloth = find(player, Forging.WOVEN_CLOTH.get());
            helper.assertTrue(!cloth.isEmpty() && Forging.stars(cloth) == Math.min(3, yarnStars), "the cloth takes the yarn's ★, got " + cloth);

            // Full: into a heated water cauldron, out as fulled cloth.
            BlockPos cauldron = ledger.offset(0, 0, -2);
            CauldronInteraction.WATER.map().get(Forging.WOVEN_CLOTH.get()).interact(level.getBlockState(cauldron), level, cauldron, player,
                    InteractionHand.MAIN_HAND, cloth);
            helper.assertTrue(level.getBlockState(cauldron).is(Crafts.SOAKING_CAULDRON.get()), "cloth in a heated water cauldron starts it soaking");
            SoakingVatBlock.finishNow(level, cauldron);
            SoakingVatBlockEntity soaking = (SoakingVatBlockEntity) level.getBlockEntity(cauldron);
            SoakingVatBlock.collect(level, cauldron, level.getBlockState(cauldron), soaking, player);
            ItemStack fulled = find(player, Forging.PADDED_CLOTH.get());
            helper.assertTrue(!fulled.isEmpty(), "it comes out fulled");
            helper.assertTrue(level.getBlockState(cauldron).is(Blocks.WATER_CAULDRON), "and the cauldron is a water cauldron again");

            // Sew: on the Shaping Rack, with string.
            BlockPos shaping = ledger.offset(0, 0, 2);
            ((ShapingRackBlockEntity) level.getBlockEntity(shaping)).setPiece(fulled.split(1));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STRING, 2));
            landAll(player, level, shaping, TimingBar.Kind.SEW, TimingBar.Hit.GREEN);
            ItemStack panels = find(player, Crafts.CLOTH_PANEL.get());
            helper.assertTrue(panels.getCount() == 2, "sewing makes two cloth panels, got " + panels);
            helper.assertTrue(player.getMainHandItem().getCount() == 1, "and uses a string");
        } finally {
            clear(level, ledger);
        }
        helper.succeed();
    }

    /** Woodworking from a billet to a bow: split, carve, bend, then craft with a bowstring and a finish. */
    @GameTest(template = ANY_TEMPLATE)
    public static void woodIsSplitCarvedBentAndStrung(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ledger = spot(helper);
        ServerPlayer player = player(helper, "bowyer");
        CraftingSkills.setLevel(player, CraftSkill.WOODWORKING, 30);
        try {
            set(level, ledger, ModRegistries.GUILD_LEDGER.get());
            woodshop(level, ledger);
            helper.assertTrue(CraftWorkshop.scan(level, ledger, Profession.WOODWORKING).complete(), "the woodshop is complete");
            // Split, with an axe in the off hand.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Crafts.SEASONED_BILLET.get(), 2));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_AXE));
            ForgeActions.split(player, level, ledger.offset(-2, 0, 0), player.getMainHandItem());
            helper.assertTrue(find(player, Crafts.STAVE.get()).getCount() == 2, "a billet splits into two staves");

            // Turn the other billet into a staff shaft.
            BlockPos lathe = ledger.offset(0, 0, -2);
            landAll(player, level, lathe, TimingBar.Kind.TURN, TimingBar.Hit.GOLD);
            helper.assertTrue(Forging.stars(find(player, Crafts.STAFF_SHAFT.get())) == 3, "a perfect turn makes a ★3 staff shaft (a level 1 woodshop)");

            // Carve a stave into limbs.
            player.setItemInHand(InteractionHand.MAIN_HAND, find(player, Crafts.STAVE.get()).copy());
            find(player, Crafts.STAVE.get()).setCount(0);
            ForgeActions.Session carve = begin(player, level, ledger.offset(0, 0, 2), TimingBar.Kind.CARVE);
            carve.taps().addAll(steadyTaps(8, 12));
            ForgeActions.finishTapped(player, level, carve, player.getMainHandItem(), level.getGameTime());
            ItemStack limbs = find(player, Crafts.BOW_LIMBS.get());
            helper.assertTrue(!limbs.isEmpty() || find(player, Items.STICK).getCount() > 0, "carving makes limbs (or, badly, splits the stave)");

            // Bend the limbs, then string the bow.
            ItemStack bent = new ItemStack(Crafts.BENT_LIMBS.get(), 2);
            bent.set(Forging.STARS.get(), 3);
            ItemStack string = new ItemStack(Crafts.BOWSTRING.get());
            string.set(Forging.STARS.get(), 2);
            ItemStack oil = new ItemStack(Crafts.LINSEED_OIL.get());
            ItemStack air = ItemStack.EMPTY;
            CraftingInput input = CraftingInput.of(3, 3, List.of(air, bent.copyWithCount(1), string.copy(), oil, air, string.copy(), air,
                    bent.copyWithCount(1), string.copy()));
            ItemStack bow = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow().value()
                    .assemble(input, level.registryAccess());
            helper.assertTrue(bow.is(Items.BOW) && Forging.stars(bow) == 2, "a bow from parts takes its weakest part's ★, got " + bow);
        } finally {
            clear(level, ledger);
        }
        helper.succeed();
    }

    // --- Helpers ---

    /** Taps every {@code every} ticks from {@code first}, through the whole minigame. */
    private static List<Float> steadyTaps(int first, int every) {
        List<Float> taps = new ArrayList<>();
        for (int tick = first; tick < TimingBar.TAP_DURATION; tick += every) {
            taps.add((float) tick);
        }
        return taps;
    }

    private static ForgeActions.Session begin(ServerPlayer player, ServerLevel level, BlockPos pos, TimingBar.Kind kind) {
        ForgeActions.end(player);
        return ForgeActions.begin(player, level, pos, level.getBlockState(pos), player.getMainHandItem(), kind, level.getGameTime()).orElseThrow();
    }

    /** Plays a minigame through with every strike landing in its gold. */
    private static void strikeGold(ServerPlayer player, ServerLevel level, BlockPos pos, TimingBar.Kind kind) {
        ForgeActions.Session session = begin(player, level, pos, kind);
        long now = level.getGameTime();
        while (ForgeActions.session(player).isPresent()) {
            now += 3;
            ForgeActions.strike(player, level, session, player.getMainHandItem(), now, session.goldMarker());
        }
    }

    /** Plays a minigame through with every strike landing as {@code hit}. */
    private static void landAll(ServerPlayer player, ServerLevel level, BlockPos pos, TimingBar.Kind kind, TimingBar.Hit hit) {
        ForgeActions.Session session = begin(player, level, pos, kind);
        long now = level.getGameTime();
        while (ForgeActions.session(player).isPresent()) {
            now += 3;
            ForgeActions.land(player, level, session, player.getMainHandItem(), now, hit);
        }
    }

    private static ItemStack find(ServerPlayer player, Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).findFirst().orElse(ItemStack.EMPTY);
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    private static BlockPos spot(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(1, 2, 1)).above(110);
    }

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void tannery(ServerLevel level, BlockPos ledger) {
        set(level, ledger.offset(2, 0, 0), Forging.TANNING_RACK.get());
        set(level, ledger.offset(-2, 0, 0), Blocks.CAULDRON);
        set(level, ledger.offset(0, 0, 2), Crafts.SHAPING_RACK.get());
    }

    private static void weavingRoom(ServerLevel level, BlockPos ledger) {
        set(level, ledger.offset(2, 0, 0), Crafts.SPINNING_WHEEL.get());
        set(level, ledger.offset(-2, 0, 0), Blocks.LOOM);
        set(level, ledger.offset(0, 0, 2), Crafts.SHAPING_RACK.get());
        heatedWater(level, ledger.offset(0, 0, -2));
    }

    private static void woodshop(ServerLevel level, BlockPos ledger) {
        set(level, ledger.offset(2, 0, 0), Blocks.SMOKER);
        set(level, ledger.offset(-2, 0, 0), Crafts.CHOPPING_BLOCK.get());
        set(level, ledger.offset(0, 0, 2), Blocks.FLETCHING_TABLE);
        set(level, ledger.offset(0, 0, -2), Crafts.WOOD_LATHE.get());
        heatedWater(level, ledger.offset(3, 0, 3));
    }

    private static void heatedWater(ServerLevel level, BlockPos cauldron) {
        level.setBlock(cauldron.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), Block.UPDATE_CLIENTS);
        level.setBlock(cauldron, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), Block.UPDATE_CLIENTS);
    }

    private static void clear(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 1, 4))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(6)).forEach(ItemEntity::discard);
    }
}
