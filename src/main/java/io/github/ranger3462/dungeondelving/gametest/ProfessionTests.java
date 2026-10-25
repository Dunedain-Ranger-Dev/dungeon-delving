package io.github.ranger3462.dungeondelving.gametest;

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
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Checks the rebuilt crafting professions (DD-88, docs/design/professions.md): the minigames, the vats' timing, the
 * workshops around a Guild Ledger, and each profession's steps from raw material to part, ★ and all. Each follows
 * Forging's pattern: prepare, soak in a vat (which sets the ★), work at the bench, craft.
 */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class ProfessionTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void theMinigamesWorkOutTheSameEverywhere(GameTestHelper helper) {
        helper.assertTrue(TimingBar.cut(20, 40) == 0.5f && TimingBar.cut(80, 40) == 1.0f, "the blade moves steadily while held, and stops at the end");
        helper.assertTrue(TimingBar.turn(18, 36) == 0.25f, "the chisel bites faster the deeper it goes");
        helper.succeed();
    }

    /** Better tiers take longer: 30 seconds more for each ★, in a vat or heating an ingot in a furnace. */
    @GameTest(template = ANY_TEMPLATE)
    public static void betterTiersTakeLonger(GameTestHelper helper) {
        helper.assertTrue(SoakingVatBlock.soakTicks(3) == 2400 && SoakingVatBlock.soakTicks(5) == 3600 && SoakingVatBlock.soakTicks(7) == 4800,
                "a vat takes 2:00 at ★3, 3:00 at ★5 and 4:00 at ★7");
        ServerLevel level = helper.getLevel();
        int previous = 0;
        for (int stars = 0; stars < Forging.MAX_STARS; stars++) {
            ItemStack ingot = stars == 0 ? new ItemStack(Items.IRON_INGOT) : new ItemStack(Forging.TEMPERED_IRON_INGOT.get());
            if (stars > 0) {
                ingot.set(Forging.STARS.get(), stars);
            }
            int time = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(ingot), level).orElseThrow().value()
                    .getCookingTime();
            helper.assertTrue(stars == 0 ? time == 100 : time == previous + 600, "heating for ★" + (stars + 1) + " takes 30s more than the last, got " + time);
            previous = time;
        }
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

    /** Leatherworking from leather to panels: scrape, tan (the liquor sets the ★), cut. */
    @GameTest(template = ANY_TEMPLATE)
    public static void leatherIsScrapedTannedAndCut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ledger = spot(helper);
        ServerPlayer player = player(helper, "tanner");
        CraftingSkills.setLevel(player, CraftSkill.LEATHERWORKING, 30);
        try {
            set(level, ledger, ModRegistries.GUILD_LEDGER.get());
            tannery(level, ledger);
            // Scrape: no minigame, just a plain scraped hide.
            BlockPos rack = ledger.offset(2, 0, 0);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEATHER, 2));
            for (int i = 0; i < 2; i++) {
                scrape(player, level, rack);
            }
            ItemStack hides = find(player, Crafts.SCRAPED_HIDE.get());
            helper.assertTrue(hides.getCount() == 2 && !hides.has(Forging.STARS.get()), "two scrapes make two scraped hides, no ★ yet, got " + hides);

            // Tan: liquor poured into a cauldron makes a vat; the hides come out as tanned leather once they've soaked.
            BlockPos cauldron = ledger.offset(-2, 0, 0);
            ItemStack liquor = new ItemStack(Crafts.BARK_LIQUOR.get());
            CauldronInteraction.EMPTY.map().get(Crafts.BARK_LIQUOR.get()).interact(level.getBlockState(cauldron), level, cauldron, player,
                    InteractionHand.MAIN_HAND, liquor);
            helper.assertTrue(level.getBlockState(cauldron).is(Crafts.TANNING_VAT.get()), "liquor in a cauldron makes a Tanning Vat");
            SoakingVatBlockEntity vat = (SoakingVatBlockEntity) level.getBlockEntity(cauldron);
            SoakingVatBlock.insert(level, cauldron, level.getBlockState(cauldron), vat, player, hides,
                    SoakingVatBlock.soakFor(hides, Profession.LEATHERWORKING).orElseThrow());
            helper.assertTrue(vat.soaking() && hides.isEmpty(), "the hides go in to soak");
            SoakingVatBlock.collect(level, cauldron, level.getBlockState(cauldron), vat, player);
            helper.assertTrue(vat.soaking(), "they aren't ready yet");
            vat.finishNow();
            SoakingVatBlock.collect(level, cauldron, level.getBlockState(cauldron), vat, player);
            ItemStack tanned = find(player, Forging.REFINED_LEATHER.get());
            helper.assertTrue(tanned.getCount() == 2 && Forging.stars(tanned) == 3, "they come out as two tanned leather at Bark Liquor's ★3, got " + tanned);
            helper.assertTrue(level.getBlockState(cauldron).is(Blocks.CAULDRON), "a vat with its liquor used up is a cauldron again");

            // Cut: on the Shaping Rack with shears.
            BlockPos shaping = ledger.offset(0, 0, 2);
            ((ShapingRackBlockEntity) level.getBlockEntity(shaping)).setPiece(tanned.split(1));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
            strikeGold(player, level, shaping, TimingBar.Kind.CUT);
            ItemStack panels = find(player, Crafts.LEATHER_PANEL.get());
            helper.assertTrue(panels.getCount() == 2 && Forging.stars(panels) == 3, "a clean cut makes two ★3 leather panels, got " + panels);
            helper.assertTrue(((ShapingRackBlockEntity) level.getBlockEntity(shaping)).piece().isEmpty(), "and uses up the leather");

            // Like Forging, the bench refuses a piece better than the workshop can work.
            ItemStack fine = new ItemStack(Forging.REFINED_LEATHER.get());
            fine.set(Forging.STARS.get(), 5);
            ((ShapingRackBlockEntity) level.getBlockEntity(shaping)).setPiece(fine);
            ForgeActions.end(player);
            helper.assertTrue(ForgeActions.begin(player, level, shaping, level.getBlockState(shaping), player.getMainHandItem(), TimingBar.Kind.CUT,
                    level.getGameTime()).isEmpty(), "a level 1 tannery can't cut ★5 leather");
        } finally {
            clear(level, ledger);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void theProfessionRecipesLoad(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        for (String id : List.of("seasoned_billet", "bow_from_parts", "crossbow_from_parts", "straps", "hardened_leather", "leather_base",
                "gambeson_base", "bowstring", "staff_wrap", "stock", "bow_limbs", "haft", "staff_shaft", "wand_core", "shaping_rack", "spinning_wheel",
                "wood_lathe")) {
            helper.assertTrue(recipes.byKey(DungeonDelving.id(id)).isPresent(), "the " + id + " recipe loads");
        }
        if (net.neoforged.fml.ModList.get().isLoaded("farmersdelight")) {
            for (String id : List.of("bark_liquor", "tannin_liquor", "elder_liquor", "wheat_sizing", "silk_sizing", "arcane_sizing", "linseed_oil")) {
                helper.assertTrue(recipes.byKey(DungeonDelving.id(id)).isPresent(), "the " + id + " cooking-pot recipe loads");
            }
        }
        helper.assertTrue(recipes.byKey(DungeonDelving.id("chopping_block")).isEmpty(), "the chopping block is gone");
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
            scrape(player, level, rack);
            helper.assertTrue(player.getMainHandItem().getCount() == 1, "a Tanning Rack with no tannery around it does nothing");
        } finally {
            clear(level, rack);
        }
        helper.succeed();
    }

    /** Weaving from wool to panels: spin, size (the sizing sets the ★), weave. */
    @GameTest(template = ANY_TEMPLATE)
    public static void woolIsSpunSizedAndWoven(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ledger = spot(helper);
        ServerPlayer player = player(helper, "weaver");
        CraftingSkills.setLevel(player, CraftSkill.WEAVING, 30);
        try {
            set(level, ledger, ModRegistries.GUILD_LEDGER.get());
            weavingRoom(level, ledger);
            helper.assertTrue(CraftWorkshop.scan(level, ledger, Profession.WEAVING).complete(), "the weaving room is complete");
            // Spin: no minigame, three yarn from a wool.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHITE_WOOL));
            ForgeActions.prepare(player, level, ledger.offset(2, 0, 0), player.getMainHandItem(), Profession.WEAVING, Crafts.YARN, ForgeActions.YARN_PER_WOOL,
                    net.minecraft.sounds.SoundEvents.WOOL_PLACE, "message.dungeondelving.spin.done");
            ItemStack yarn = find(player, Crafts.YARN.get());
            helper.assertTrue(yarn.getCount() == 3, "a wool spins into three yarn, got " + yarn);

            // Size: sizing poured into a cauldron makes a Sizing Vat; the yarn comes out at the sizing's ★.
            BlockPos cauldron = ledger.offset(0, 0, 2);
            CauldronInteraction.EMPTY.map().get(Crafts.WHEAT_SIZING.get()).interact(level.getBlockState(cauldron), level, cauldron, player,
                    InteractionHand.MAIN_HAND, new ItemStack(Crafts.WHEAT_SIZING.get()));
            helper.assertTrue(level.getBlockState(cauldron).is(Crafts.SIZING_VAT.get()), "sizing in a cauldron makes a Sizing Vat");
            SoakingVatBlockEntity vat = (SoakingVatBlockEntity) level.getBlockEntity(cauldron);
            helper.assertTrue(SoakingVatBlock.soakFor(new ItemStack(Crafts.SCRAPED_HIDE.get()), Profession.WEAVING).isEmpty(), "hides don't go in sizing");
            SoakingVatBlock.insert(level, cauldron, level.getBlockState(cauldron), vat, player, yarn, SoakingVatBlock.soakFor(yarn, Profession.WEAVING).orElseThrow());
            vat.finishNow();
            SoakingVatBlock.collect(level, cauldron, level.getBlockState(cauldron), vat, player);
            ItemStack sized = find(player, Crafts.SIZED_YARN.get());
            helper.assertTrue(sized.getCount() == 3 && Forging.stars(sized) == 3, "three ★3 sized yarn, got " + sized);

            // Weave: three sized yarn on the loom make two cloth panels, keeping the ★.
            player.setItemInHand(InteractionHand.MAIN_HAND, sized.copy());
            sized.setCount(0);
            landAll(player, level, ledger.offset(-2, 0, 0), TimingBar.Kind.WEAVE, TimingBar.Hit.GOLD);
            ItemStack panels = find(player, Crafts.CLOTH_PANEL.get());
            helper.assertTrue(panels.getCount() == 2 && Forging.stars(panels) == 3, "weaving makes two ★3 cloth panels, got " + panels);
        } finally {
            clear(level, ledger);
        }
        helper.succeed();
    }

    /** Woodworking from a billet to a bow: cure (the finish sets the ★), turn into staves, then craft the parts and the bow. */
    @GameTest(template = ANY_TEMPLATE)
    public static void woodIsCuredTurnedAndCrafted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ledger = spot(helper);
        ServerPlayer player = player(helper, "bowyer");
        CraftingSkills.setLevel(player, CraftSkill.WOODWORKING, 30);
        try {
            set(level, ledger, ModRegistries.GUILD_LEDGER.get());
            woodshop(level, ledger);
            helper.assertTrue(CraftWorkshop.scan(level, ledger, Profession.WOODWORKING).complete(), "the woodshop is complete");
            // Cure: billets in a vat of linseed oil come out ★3.
            BlockPos cauldron = ledger.offset(-2, 0, 0);
            CauldronInteraction.EMPTY.map().get(Crafts.LINSEED_OIL.get()).interact(level.getBlockState(cauldron), level, cauldron, player,
                    InteractionHand.MAIN_HAND, new ItemStack(Crafts.LINSEED_OIL.get()));
            helper.assertTrue(level.getBlockState(cauldron).is(Crafts.FINISHING_VAT.get()), "linseed oil in a cauldron makes a Finishing Vat");
            SoakingVatBlockEntity vat = (SoakingVatBlockEntity) level.getBlockEntity(cauldron);
            ItemStack billets = new ItemStack(Crafts.SEASONED_BILLET.get(), 2);
            SoakingVatBlock.insert(level, cauldron, level.getBlockState(cauldron), vat, player, billets,
                    SoakingVatBlock.soakFor(billets, Profession.WOODWORKING).orElseThrow());
            vat.finishNow();
            SoakingVatBlock.collect(level, cauldron, level.getBlockState(cauldron), vat, player);
            ItemStack cured = find(player, Crafts.CURED_BILLET.get());
            helper.assertTrue(cured.getCount() == 2 && Forging.stars(cured) == 3, "two ★3 cured billets, got " + cured);

            // Turn each into two staves.
            player.setItemInHand(InteractionHand.MAIN_HAND, cured.copy());
            cured.setCount(0);
            BlockPos lathe = ledger.offset(0, 0, -2);
            landAll(player, level, lathe, TimingBar.Kind.TURN, TimingBar.Hit.GOLD);
            landAll(player, level, lathe, TimingBar.Kind.TURN, TimingBar.Hit.GOLD);
            ItemStack staves = find(player, Crafts.STAVE.get());
            helper.assertTrue(staves.getCount() == 4 && Forging.stars(staves) == 3, "turning makes two ★3 staves a billet, got " + staves);

            // Craft limbs and a haft, then a bow, which takes its weakest part's ★.
            ItemStack stave = staves.copyWithCount(1);
            ItemStack air = ItemStack.EMPTY;
            ItemStack limbs = craft(level, CraftingInput.of(2, 2, List.of(air, stave, stave.copy(), air)));
            helper.assertTrue(limbs.is(Crafts.BOW_LIMBS.get()) && limbs.getCount() == 2 && Forging.stars(limbs) == 3, "two staves make two bow limbs, got " + limbs);
            ItemStack haft = craft(level, CraftingInput.of(1, 1, List.of(stave.copy())));
            helper.assertTrue(haft.is(Crafts.HAFT.get()) && Forging.stars(haft) == 3, "a stave makes hafts, got " + haft);
            ItemStack string = new ItemStack(Crafts.BOWSTRING.get());
            string.set(Forging.STARS.get(), 2);
            ItemStack bow = craft(level, CraftingInput.of(3, 3, List.of(air, limbs.copyWithCount(1), air, haft.copyWithCount(1), air, string, air,
                    limbs.copyWithCount(1), air)));
            helper.assertTrue(bow.is(Items.BOW) && Forging.stars(bow) == 2, "a bow from parts takes its weakest part's ★, got " + bow);
        } finally {
            clear(level, ledger);
        }
        helper.succeed();
    }

    /** Goldsmithing from a gold ingot to jewelry and gilded gear: refine (the salt sets the ★), chase, craft (DD-89). */
    @GameTest(template = ANY_TEMPLATE)
    public static void goldIsRefinedChasedAndCrafted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ledger = spot(helper);
        ServerPlayer player = player(helper, "goldsmith");
        CraftingSkills.setLevel(player, CraftSkill.GOLDSMITHING, 30);
        try {
            set(level, ledger, ModRegistries.GUILD_LEDGER.get());
            set(level, ledger.offset(2, 0, 0), Blocks.FURNACE);
            BlockPos crucible = ledger.offset(-2, 0, 0);
            set(level, crucible, Crafts.CRUCIBLE.get());
            BlockPos bench = ledger.offset(0, 0, 2);
            set(level, bench, Crafts.JEWELERS_BENCH.get());
            helper.assertTrue(CraftWorkshop.scan(level, ledger, Profession.GOLDSMITHING).complete(), "the goldsmithy is complete");

            // Refine: like a furnace, gold ingots on top and bone ash as the fuel; fine gold comes out at the salt's ★3.
            var refiner = (io.github.ranger3462.dungeondelving.forging.CrucibleBlockEntity) level.getBlockEntity(crucible);
            refiner.setItem(io.github.ranger3462.dungeondelving.forging.CrucibleBlockEntity.INPUT, new ItemStack(Items.GOLD_INGOT, 5));
            refiner.setItem(io.github.ranger3462.dungeondelving.forging.CrucibleBlockEntity.SALT, new ItemStack(Crafts.BONE_ASH.get()));
            refiner.runUntilIdle(level);
            ItemStack fine = refiner.getItem(io.github.ranger3462.dungeondelving.forging.CrucibleBlockEntity.OUTPUT).copy();
            helper.assertTrue(fine.getCount() == 4 && Forging.stars(fine) == 3, "one bone ash refines four ingots into ★3 fine gold, got " + fine);
            helper.assertTrue(refiner.getItem(io.github.ranger3462.dungeondelving.forging.CrucibleBlockEntity.INPUT).getCount() == 1,
                    "and the fifth waits for more salt");
            helper.assertTrue(io.github.ranger3462.dungeondelving.forging.CrucibleBlockEntity.ticksFor(7, false)
                    == io.github.ranger3462.dungeondelving.forging.CrucibleBlockEntity.ticksFor(5, false) + 600, "each better salt takes 30s more an ingot");
            fine.setCount(2);

            // Chase both at the Jeweler's Bench: two fittings each.
            player.setItemInHand(InteractionHand.MAIN_HAND, fine.copy());
            fine.setCount(0);
            landAll(player, level, bench, TimingBar.Kind.CHASE, TimingBar.Hit.GOLD);
            landAll(player, level, bench, TimingBar.Kind.CHASE, TimingBar.Hit.GOLD);
            ItemStack fittings = find(player, Crafts.GOLD_FITTINGS.get());
            helper.assertTrue(fittings.getCount() == 4 && Forging.stars(fittings) == 3, "four ★3 gold fittings, got " + fittings);

            // A ring, and a gilded helm.
            ItemStack f = fittings.copyWithCount(1);
            ItemStack air = ItemStack.EMPTY;
            ItemStack ring = craft(level, CraftingInput.of(3, 3, List.of(air, f, air, f.copy(), air, f.copy(), air, f.copy(), air)));
            helper.assertTrue(ring.is(Crafts.GOLD_RING.get()) && Forging.stars(ring) == 3
                    && ((io.github.ranger3462.dungeondelving.forging.JewelryItem) ring.getItem()).bonus(ring) == 3.0, "a ★3 ring gives +3 max health, got " + ring);
            ItemStack helm = craft(level, CraftingInput.of(3, 2, List.of(f, f.copy(), f.copy(), f.copy(), air, f.copy())));
            helper.assertTrue(helm.is(Forging.ARMOR.get(io.github.ranger3462.dungeondelving.forging.ForgedGear.Weight.GILDED)
                    .get(net.minecraft.world.item.ArmorItem.Type.HELMET).get()) && Forging.stars(helm) == 3, "fittings in a helmet's shape make a ★3 gilded helm");
            helper.assertTrue(helm.makesPiglinsNeutral(player), "piglins take gilded armor for gold");
        } finally {
            clear(level, ledger);
        }
        helper.succeed();
    }

    // --- Helpers ---

    private static ForgeActions.Session begin(ServerPlayer player, ServerLevel level, BlockPos pos, TimingBar.Kind kind) {
        ForgeActions.end(player);
        return ForgeActions.begin(player, level, pos, level.getBlockState(pos), player.getMainHandItem(), kind, level.getGameTime()).orElseThrow();
    }

    private static ItemStack craft(ServerLevel level, CraftingInput input) {
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow().value().assemble(input, level.registryAccess());
    }

    /** Scrapes the held leather on a Tanning Rack. */
    private static void scrape(ServerPlayer player, ServerLevel level, BlockPos rack) {
        ForgeActions.prepare(player, level, rack, player.getMainHandItem(), Profession.LEATHERWORKING, Crafts.SCRAPED_HIDE, 1,
                net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER.value(), "message.dungeondelving.scrape.done");
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
        set(level, ledger.offset(0, 0, 2), Blocks.CAULDRON);
    }

    private static void woodshop(ServerLevel level, BlockPos ledger) {
        set(level, ledger.offset(2, 0, 0), Blocks.SMOKER);
        set(level, ledger.offset(-2, 0, 0), Blocks.CAULDRON);
        set(level, ledger.offset(0, 0, -2), Crafts.WOOD_LATHE.get());
    }

    private static void clear(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 1, 4))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(6)).forEach(ItemEntity::discard);
    }
}
