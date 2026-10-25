package io.github.ranger3462.dungeondelving.forging;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The blocks and items of the crafting professions other than Forging (DD-88): Leatherworking and Weaving rebuilt, and
 * Woodworking. See docs/design/professions.md. Forging's own, and the leather and cloth items gear is assembled from, are
 * in {@link Forging}.
 */
public final class Crafts {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DungeonDelving.MODID);

    // --- Shared stations ---

    /** Where tanned leather is cut and fulled cloth sewn. */
    public static final DeferredBlock<ShapingRackBlock> SHAPING_RACK = ModRegistries.BLOCKS.registerBlock("shaping_rack", ShapingRackBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> SHAPING_RACK_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(SHAPING_RACK);

    // --- Leatherworking ---

    /** A cauldron of tanning liquor. It has no item: pour liquor into a cauldron to make one. */
    public static final DeferredBlock<SoakingVatBlock> TANNING_VAT = ModRegistries.BLOCKS.registerBlock("tanning_vat",
            properties -> new SoakingVatBlock(properties, SoakingVatBlock.Type.TANNING_VAT), BlockBehaviour.Properties.ofLegacyCopy(Blocks.CAULDRON));
    /** A bigger, faster vat: a level 2 tannery. */
    public static final DeferredBlock<SoakingVatBlock> TANNING_BARREL = ModRegistries.BLOCKS.registerBlock("tanning_barrel",
            properties -> new SoakingVatBlock(properties, SoakingVatBlock.Type.TANNING_BARREL),
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD));
    public static final DeferredItem<BlockItem> TANNING_BARREL_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(TANNING_BARREL);
    /** A beam a hide is thrown over to be fleshed: a level 3 tannery, with a wider scraping patch. */
    public static final DeferredBlock<CraftStationBlock> FLESHING_BEAM = station("fleshing_beam", Block.box(0, 0, 4, 16, 12, 12));
    public static final DeferredItem<BlockItem> FLESHING_BEAM_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(FLESHING_BEAM);

    /** A hide scraped clean, waiting to be tanned. */
    public static final DeferredItem<StarredItem> SCRAPED_HIDE = starred("scraped_hide");
    /** Tanned leather cut to shape: what leather gear is assembled from. */
    public static final DeferredItem<StarredItem> LEATHER_PANEL = starred("leather_panel");
    public static final DeferredItem<StarredItem> STRAPS = starred("straps");
    public static final DeferredItem<LiquorItem> BARK_LIQUOR = liquor("bark_liquor", 3);
    public static final DeferredItem<LiquorItem> TANNIN_LIQUOR = liquor("tannin_liquor", 5);
    public static final DeferredItem<LiquorItem> ELDER_LIQUOR = liquor("elder_liquor", 7);
    /** Dungeon herbs for the better liquors. Found in dungeons (DD-88 phase 6). */
    public static final DeferredItem<Item> BARROWMOSS = ModRegistries.ITEMS.registerSimpleItem("barrowmoss", new Item.Properties());
    public static final DeferredItem<Item> EMBERROOT = ModRegistries.ITEMS.registerSimpleItem("emberroot", new Item.Properties());

    // --- Weaving ---

    public static final DeferredBlock<CraftStationBlock> SPINNING_WHEEL = station("spinning_wheel", Block.box(1, 0, 3, 15, 16, 13));
    public static final DeferredItem<BlockItem> SPINNING_WHEEL_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(SPINNING_WHEEL);
    /** A loom with a wider shed: a level 2 weaving room, and a loom in its own right. */
    public static final DeferredBlock<CraftStationBlock> GREAT_LOOM = station("great_loom", Block.box(0, 0, 0, 16, 16, 16));
    public static final DeferredItem<BlockItem> GREAT_LOOM_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(GREAT_LOOM);
    /** A rack of dye vats: a level 3 weaving room. */
    public static final DeferredBlock<CraftStationBlock> DYE_WORKS = station("dye_works", Block.box(0, 0, 0, 16, 12, 16));
    public static final DeferredItem<BlockItem> DYE_WORKS_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(DYE_WORKS);
    /** A water cauldron over heat with something soaking in it. It has no item: put cloth or limbs into a heated water cauldron. */
    public static final DeferredBlock<SoakingVatBlock> SOAKING_CAULDRON = ModRegistries.BLOCKS.registerBlock("soaking_cauldron",
            properties -> new SoakingVatBlock(properties, SoakingVatBlock.Type.SOAKING_CAULDRON), BlockBehaviour.Properties.ofLegacyCopy(Blocks.CAULDRON));

    /** Spun wool: thread for Weaving, good for cloth up to ★3. */
    public static final DeferredItem<StarredItem> YARN = starred("yarn");
    /** Raw silk from dungeon spiders (DD-88 phase 6), spun into silk thread. */
    public static final DeferredItem<Item> SPIDER_SILK = ModRegistries.ITEMS.registerSimpleItem("spider_silk", new Item.Properties());
    /** Spun silk: thread good for cloth up to ★5. */
    public static final DeferredItem<StarredItem> SILK_THREAD = starred("silk_thread");
    /** Silk thread steeped in arcane dust: thread good for cloth up to ★7, for the best mage robes. */
    public static final DeferredItem<StarredItem> SPELLSILK = starred("spellsilk");
    /** Dust from dungeon casters (DD-88 phase 6), for steeping Spellsilk. */
    public static final DeferredItem<Item> ARCANE_DUST = ModRegistries.ITEMS.registerSimpleItem("arcane_dust", new Item.Properties());
    /** Fulled cloth sewn to shape: what cloth gear is assembled from. */
    public static final DeferredItem<StarredItem> CLOTH_PANEL = starred("cloth_panel");
    public static final DeferredItem<StarredItem> BOWSTRING = starred("bowstring");
    public static final DeferredItem<StarredItem> STAFF_WRAP = starred("staff_wrap");

    // --- Woodworking ---

    /** A stump with an axe in it, for splitting seasoned billets. */
    public static final DeferredBlock<CraftStationBlock> CHOPPING_BLOCK = station("chopping_block", Block.box(1, 0, 1, 15, 10, 15));
    public static final DeferredItem<BlockItem> CHOPPING_BLOCK_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(CHOPPING_BLOCK);
    public static final DeferredBlock<CraftStationBlock> WOOD_LATHE = station("wood_lathe", Block.box(0, 0, 2, 16, 14, 14));
    public static final DeferredItem<BlockItem> WOOD_LATHE_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(WOOD_LATHE);
    /** A foot-driven lathe: a level 2 woodshop, a lathe with a wider green, and a lathe in its own right. */
    public static final DeferredBlock<CraftStationBlock> TREADLE_LATHE = station("treadle_lathe", Block.box(0, 0, 2, 16, 15, 14));
    public static final DeferredItem<BlockItem> TREADLE_LATHE_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(TREADLE_LATHE);
    /** A bowyer's bench: a level 3 woodshop, with a wider carving band, and a carving station in its own right. */
    public static final DeferredBlock<CraftStationBlock> BOWYERS_BENCH = station("bowyers_bench", Block.box(0, 0, 0, 16, 14, 16));
    public static final DeferredItem<BlockItem> BOWYERS_BENCH_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(BOWYERS_BENCH);

    /** A log dried in a smoker: green wood is no good for bows or staves. */
    public static final DeferredItem<Item> SEASONED_BILLET = ModRegistries.ITEMS.registerSimpleItem("seasoned_billet", new Item.Properties());
    /** A billet split along the grain: carved into bow limbs, or turned into hafts. */
    public static final DeferredItem<Item> STAVE = ModRegistries.ITEMS.registerSimpleItem("stave", new Item.Properties());
    /** A crossbow's stock, from two staves. */
    public static final DeferredItem<Item> STOCK = ModRegistries.ITEMS.registerSimpleItem("stock", new Item.Properties());
    public static final DeferredItem<StarredItem> HAFT = starred("haft");
    public static final DeferredItem<StarredItem> STAFF_SHAFT = starred("staff_shaft");
    public static final DeferredItem<StarredItem> WAND_CORE = starred("wand_core");
    public static final DeferredItem<StarredItem> BOW_LIMBS = starred("bow_limbs");
    /** Bow limbs bent in hot water: ready to string. */
    public static final DeferredItem<StarredItem> BENT_LIMBS = starred("bent_limbs");
    public static final DeferredItem<FinishItem> LINSEED_OIL = finish("linseed_oil", 3);
    /** Dryad finishes (DD-88 phase 7). */
    public static final DeferredItem<FinishItem> HEARTWOOD_RESIN = finish("heartwood_resin", 5);
    public static final DeferredItem<FinishItem> SYLVAN_SAP = finish("sylvan_sap", 7);

    // --- Block entities ---

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShapingRackBlockEntity>> SHAPING_RACK_ENTITY = BLOCK_ENTITIES.register(
            "shaping_rack", () -> BlockEntityType.Builder.of(ShapingRackBlockEntity::new, SHAPING_RACK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SoakingVatBlockEntity>> SOAKING_VAT_ENTITY = BLOCK_ENTITIES.register(
            "soaking_vat", () -> BlockEntityType.Builder.of(SoakingVatBlockEntity::new, TANNING_VAT.get(), TANNING_BARREL.get(),
                    SOAKING_CAULDRON.get()).build(null));

    private Crafts() {}

    private static DeferredBlock<CraftStationBlock> station(String name, VoxelShape shape) {
        return ModRegistries.BLOCKS.registerBlock(name, properties -> new CraftStationBlock(properties, shape),
                BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion());
    }

    private static DeferredItem<StarredItem> starred(String name) {
        return ModRegistries.ITEMS.registerItem(name, StarredItem::new, new Item.Properties());
    }

    /** Bottled, so a cooking pot fills them into glass bottles (and crafting with them hands the bottle back). */
    private static DeferredItem<LiquorItem> liquor(String name, int maxStars) {
        return ModRegistries.ITEMS.registerItem(name, properties -> new LiquorItem(properties, maxStars),
                new Item.Properties().stacksTo(16).craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE));
    }

    private static DeferredItem<FinishItem> finish(String name, int maxStars) {
        return ModRegistries.ITEMS.registerItem(name, properties -> new FinishItem(properties, maxStars),
                new Item.Properties().stacksTo(16).craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE));
    }

    /** Makes sure the blocks and items above are registered; call once while setting up registries. */
    public static void init() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(SoakingVatBlock::registerInteractions));
    }

    /** The professions' items, for the creative tab. */
    public static java.util.List<DeferredItem<? extends Item>> creativeItems() {
        return java.util.List.of(SHAPING_RACK_ITEM, TANNING_BARREL_ITEM, FLESHING_BEAM_ITEM, SCRAPED_HIDE, LEATHER_PANEL, STRAPS, BARK_LIQUOR,
                TANNIN_LIQUOR, ELDER_LIQUOR, BARROWMOSS, EMBERROOT, SPINNING_WHEEL_ITEM, GREAT_LOOM_ITEM, DYE_WORKS_ITEM, YARN, SPIDER_SILK,
                SILK_THREAD, SPELLSILK, ARCANE_DUST, CLOTH_PANEL, BOWSTRING, STAFF_WRAP, CHOPPING_BLOCK_ITEM, WOOD_LATHE_ITEM, TREADLE_LATHE_ITEM,
                BOWYERS_BENCH_ITEM, SEASONED_BILLET, STAVE, STOCK, HAFT, STAFF_SHAFT, WAND_CORE, BOW_LIMBS, BENT_LIMBS, LINSEED_OIL,
                HEARTWOOD_RESIN, SYLVAN_SAP);
    }
}
