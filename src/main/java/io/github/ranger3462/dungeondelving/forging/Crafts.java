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
 * Woodworking. Each follows Forging's pattern: a vanilla material is prepared, gets its ★ in a timed vat (the liquid's
 * tier), is worked at a bench, then crafted into parts. See docs/design/professions.md. Forging's own, and the leather and cloth items gear is assembled from, are
 * in {@link Forging}.
 */
public final class Crafts {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DungeonDelving.MODID);

    // --- Shared stations ---

    /** Where tanned leather is cut. */
    public static final DeferredBlock<ShapingRackBlock> SHAPING_RACK = ModRegistries.BLOCKS.registerBlock("shaping_rack", ShapingRackBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> SHAPING_RACK_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(SHAPING_RACK);

    // --- Leatherworking ---

    /** A cauldron of tanning liquor. The vats have no item: pour a liquid into a cauldron to make one. */
    public static final DeferredBlock<SoakingVatBlock> TANNING_VAT = ModRegistries.BLOCKS.registerBlock("tanning_vat",
            properties -> new SoakingVatBlock(properties, SoakingVatBlock.Type.TANNING_VAT), BlockBehaviour.Properties.ofLegacyCopy(Blocks.CAULDRON));
    /** A bigger, faster vat: a level 2 tannery. */
    public static final DeferredBlock<SoakingVatBlock> TANNING_BARREL = ModRegistries.BLOCKS.registerBlock("tanning_barrel",
            properties -> new SoakingVatBlock(properties, SoakingVatBlock.Type.TANNING_BARREL),
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD));
    public static final DeferredItem<BlockItem> TANNING_BARREL_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(TANNING_BARREL);
    /** A beam a hide is thrown over to be fleshed: a level 3 tannery, with a wider cutting mark. */
    public static final DeferredBlock<CraftStationBlock> FLESHING_BEAM = station("fleshing_beam", Block.box(0, 0, 4, 16, 12, 12));
    public static final DeferredItem<BlockItem> FLESHING_BEAM_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(FLESHING_BEAM);

    /** A hide scraped clean, waiting to be tanned. Its ★ comes from the liquor it's tanned in. */
    public static final DeferredItem<Item> SCRAPED_HIDE = ModRegistries.ITEMS.registerSimpleItem("scraped_hide", new Item.Properties());
    /** Tanned leather cut to shape: what leather gear is assembled from. */
    public static final DeferredItem<StarredItem> LEATHER_PANEL = starred("leather_panel");
    public static final DeferredItem<StarredItem> STRAPS = starred("straps");
    public static final DeferredItem<LiquorItem> BARK_LIQUOR = liquor("bark_liquor", 3, Profession.LEATHERWORKING);
    public static final DeferredItem<LiquorItem> TANNIN_LIQUOR = liquor("tannin_liquor", 5, Profession.LEATHERWORKING);
    public static final DeferredItem<LiquorItem> ELDER_LIQUOR = liquor("elder_liquor", 7, Profession.LEATHERWORKING);
    /** Dungeon herbs for the better liquors. Found in dungeons (DD-88 phase 6). */
    public static final DeferredItem<Item> BARROWMOSS = ModRegistries.ITEMS.registerSimpleItem("barrowmoss", new Item.Properties());
    public static final DeferredItem<Item> EMBERROOT = ModRegistries.ITEMS.registerSimpleItem("emberroot", new Item.Properties());

    // --- Weaving ---

    public static final DeferredBlock<CraftStationBlock> SPINNING_WHEEL = station("spinning_wheel", Block.box(1, 0, 3, 15, 16, 13));
    public static final DeferredItem<BlockItem> SPINNING_WHEEL_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(SPINNING_WHEEL);
    /** A loom with a wider shed: a level 2 weaving room, and a loom in its own right. */
    public static final DeferredBlock<CraftStationBlock> GREAT_LOOM = station("great_loom", Block.box(0, 0, 0, 16, 16, 16));
    public static final DeferredItem<BlockItem> GREAT_LOOM_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(GREAT_LOOM);
    /** A cauldron of sizing. */
    public static final DeferredBlock<SoakingVatBlock> SIZING_VAT = ModRegistries.BLOCKS.registerBlock("sizing_vat",
            properties -> new SoakingVatBlock(properties, SoakingVatBlock.Type.SIZING_VAT), BlockBehaviour.Properties.ofLegacyCopy(Blocks.CAULDRON));
    /** A rack of dye vats: a level 3 weaving room, and a bigger, faster sizing vat. */
    public static final DeferredBlock<SoakingVatBlock> DYE_WORKS = ModRegistries.BLOCKS.registerBlock("dye_works",
            properties -> new SoakingVatBlock(properties, SoakingVatBlock.Type.DYE_WORKS),
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> DYE_WORKS_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(DYE_WORKS);

    /** Spun wool, sized in a vat before it's woven. */
    public static final DeferredItem<Item> YARN = ModRegistries.ITEMS.registerSimpleItem("yarn", new Item.Properties());
    /** Yarn (or string) stiffened in sizing: ready for the loom. Its ★ comes from the sizing. */
    public static final DeferredItem<StarredItem> SIZED_YARN = starred("sized_yarn");
    public static final DeferredItem<LiquorItem> WHEAT_SIZING = liquor("wheat_sizing", 3, Profession.WEAVING);
    public static final DeferredItem<LiquorItem> SILK_SIZING = liquor("silk_sizing", 5, Profession.WEAVING);
    public static final DeferredItem<LiquorItem> ARCANE_SIZING = liquor("arcane_sizing", 7, Profession.WEAVING);
    /** Raw silk from dungeon spiders (DD-88 phase 6), for Silk Sizing. */
    public static final DeferredItem<Item> SPIDER_SILK = ModRegistries.ITEMS.registerSimpleItem("spider_silk", new Item.Properties());
    /** Dust from dungeon casters (DD-88 phase 6), for Arcane Sizing. */
    public static final DeferredItem<Item> ARCANE_DUST = ModRegistries.ITEMS.registerSimpleItem("arcane_dust", new Item.Properties());
    /** Woven cloth: what cloth gear is assembled from. */
    public static final DeferredItem<StarredItem> CLOTH_PANEL = starred("cloth_panel");
    public static final DeferredItem<StarredItem> BOWSTRING = starred("bowstring");
    public static final DeferredItem<StarredItem> STAFF_WRAP = starred("staff_wrap");

    // --- Woodworking ---

    /** A cauldron of wood finish. */
    public static final DeferredBlock<SoakingVatBlock> FINISHING_VAT = ModRegistries.BLOCKS.registerBlock("finishing_vat",
            properties -> new SoakingVatBlock(properties, SoakingVatBlock.Type.FINISHING_VAT), BlockBehaviour.Properties.ofLegacyCopy(Blocks.CAULDRON));
    public static final DeferredBlock<CraftStationBlock> WOOD_LATHE = station("wood_lathe", Block.box(0, 0, 2, 16, 14, 14));
    public static final DeferredItem<BlockItem> WOOD_LATHE_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(WOOD_LATHE);
    /** A foot-driven lathe: a level 2 woodshop, a lathe with a wider green, and a lathe in its own right. */
    public static final DeferredBlock<CraftStationBlock> TREADLE_LATHE = station("treadle_lathe", Block.box(0, 0, 2, 16, 15, 14));
    public static final DeferredItem<BlockItem> TREADLE_LATHE_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(TREADLE_LATHE);
    /** A bowyer's bench: a level 3 woodshop, widening the lathe's green a little more. */
    public static final DeferredBlock<CraftStationBlock> BOWYERS_BENCH = station("bowyers_bench", Block.box(0, 0, 0, 16, 14, 16));
    public static final DeferredItem<BlockItem> BOWYERS_BENCH_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(BOWYERS_BENCH);

    /** A log dried in a smoker: green wood is no good for bows or staves. */
    public static final DeferredItem<Item> SEASONED_BILLET = ModRegistries.ITEMS.registerSimpleItem("seasoned_billet", new Item.Properties());
    /** A billet soaked in a finish: ready for the lathe. Its ★ comes from the finish. */
    public static final DeferredItem<StarredItem> CURED_BILLET = starred("cured_billet");
    /** Turned from a cured billet: what wooden parts are crafted from. */
    public static final DeferredItem<StarredItem> STAVE = starred("stave");
    /** A crossbow's stock, from two staves. */
    public static final DeferredItem<StarredItem> STOCK = starred("stock");
    public static final DeferredItem<StarredItem> HAFT = starred("haft");
    public static final DeferredItem<StarredItem> STAFF_SHAFT = starred("staff_shaft");
    public static final DeferredItem<StarredItem> WAND_CORE = starred("wand_core");
    public static final DeferredItem<StarredItem> BOW_LIMBS = starred("bow_limbs");
    public static final DeferredItem<LiquorItem> LINSEED_OIL = liquor("linseed_oil", 3, Profession.WOODWORKING);
    /** Dryad finishes (DD-88 phase 7). */
    public static final DeferredItem<LiquorItem> HEARTWOOD_RESIN = liquor("heartwood_resin", 5, Profession.WOODWORKING);
    public static final DeferredItem<LiquorItem> SYLVAN_SAP = liquor("sylvan_sap", 7, Profession.WOODWORKING);

    // --- Goldsmithing (DD-89) ---

    /** A crucible of refining salt: gold ingots refine into fine gold, by the salt's ★. */
    public static final DeferredBlock<SoakingVatBlock> CRUCIBLE = ModRegistries.BLOCKS.registerBlock("crucible",
            properties -> new SoakingVatBlock(properties, SoakingVatBlock.Type.CRUCIBLE),
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5f).sound(SoundType.STONE).noOcclusion());
    public static final DeferredItem<BlockItem> CRUCIBLE_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(CRUCIBLE);
    /** A bigger, faster crucible: a level 2 goldsmithy. */
    public static final DeferredBlock<SoakingVatBlock> GREAT_CRUCIBLE = ModRegistries.BLOCKS.registerBlock("great_crucible",
            properties -> new SoakingVatBlock(properties, SoakingVatBlock.Type.GREAT_CRUCIBLE),
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f).sound(SoundType.STONE).noOcclusion());
    public static final DeferredItem<BlockItem> GREAT_CRUCIBLE_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(GREAT_CRUCIBLE);
    /** Where fine gold is chased into fittings. */
    public static final DeferredBlock<CraftStationBlock> JEWELERS_BENCH = station("jewelers_bench", Block.box(0, 0, 0, 16, 14, 16));
    public static final DeferredItem<BlockItem> JEWELERS_BENCH_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(JEWELERS_BENCH);
    /** A loupe on a stand: a level 3 goldsmithy, with a wider sweet spot at the bench. */
    public static final DeferredBlock<CraftStationBlock> JEWELERS_LOUPE = station("jewelers_loupe", Block.box(5, 0, 5, 11, 12, 11));
    public static final DeferredItem<BlockItem> JEWELERS_LOUPE_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(JEWELERS_LOUPE);

    /** Refining salts: what a crucible refines gold with, setting its ★. */
    public static final DeferredItem<LiquorItem> BONE_ASH = salt("bone_ash", 3);
    public static final DeferredItem<LiquorItem> ASSAY_SALT = salt("assay_salt", 5);
    public static final DeferredItem<LiquorItem> SUNSALT = salt("sunsalt", 7);
    /** Gold refined in a crucible: ready for the bench. Its ★ comes from the salt. */
    public static final DeferredItem<StarredItem> FINE_GOLD = starred("fine_gold");
    /** Fine gold chased into shape: what gilded gear and jewelry are made from. */
    public static final DeferredItem<StarredItem> GOLD_FITTINGS = starred("gold_fittings");
    public static final DeferredItem<JewelryItem> GOLD_RING = ModRegistries.ITEMS.registerItem("gold_ring",
            properties -> new JewelryItem(properties, JewelryItem.Kind.RING), new Item.Properties());
    public static final DeferredItem<JewelryItem> GOLD_AMULET = ModRegistries.ITEMS.registerItem("gold_amulet",
            properties -> new JewelryItem(properties, JewelryItem.Kind.AMULET), new Item.Properties());

    // --- Block entities ---

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShapingRackBlockEntity>> SHAPING_RACK_ENTITY = BLOCK_ENTITIES.register(
            "shaping_rack", () -> BlockEntityType.Builder.of(ShapingRackBlockEntity::new, SHAPING_RACK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SoakingVatBlockEntity>> SOAKING_VAT_ENTITY = BLOCK_ENTITIES.register(
            "soaking_vat", () -> BlockEntityType.Builder.of(SoakingVatBlockEntity::new, TANNING_VAT.get(), TANNING_BARREL.get(),
                    SIZING_VAT.get(), DYE_WORKS.get(), FINISHING_VAT.get(), CRUCIBLE.get(), GREAT_CRUCIBLE.get()).build(null));

    static {
        // Retired in the DD-88 rework: bending, sewing and the chopping block are gone.
        ModRegistries.ITEMS.addAlias(DungeonDelving.id("bent_limbs"), DungeonDelving.id("bow_limbs"));
        ModRegistries.ITEMS.addAlias(DungeonDelving.id("silk_thread"), DungeonDelving.id("yarn"));
        ModRegistries.ITEMS.addAlias(DungeonDelving.id("spellsilk"), DungeonDelving.id("yarn"));
        ModRegistries.BLOCKS.addAlias(DungeonDelving.id("soaking_cauldron"), DungeonDelving.id("finishing_vat"));
    }

    private Crafts() {}

    private static DeferredBlock<CraftStationBlock> station(String name, VoxelShape shape) {
        return ModRegistries.BLOCKS.registerBlock(name, properties -> new CraftStationBlock(properties, shape),
                BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion());
    }

    private static DeferredItem<StarredItem> starred(String name) {
        return ModRegistries.ITEMS.registerItem(name, StarredItem::new, new Item.Properties());
    }

    /** Bottled, so a cooking pot fills them into glass bottles (and crafting with them hands the bottle back). */
    private static DeferredItem<LiquorItem> liquor(String name, int stars, Profession profession) {
        return ModRegistries.ITEMS.registerItem(name, properties -> new LiquorItem(properties, stars, profession),
                new Item.Properties().stacksTo(16).craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE));
    }

    /** A refining salt: a powder, used up when put into a crucible. */
    private static DeferredItem<LiquorItem> salt(String name, int stars) {
        return ModRegistries.ITEMS.registerItem(name, properties -> new LiquorItem(properties, stars, Profession.GOLDSMITHING), new Item.Properties());
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
                TANNIN_LIQUOR, ELDER_LIQUOR, BARROWMOSS, EMBERROOT, SPINNING_WHEEL_ITEM, GREAT_LOOM_ITEM, DYE_WORKS_ITEM, YARN, SIZED_YARN,
                WHEAT_SIZING, SILK_SIZING, ARCANE_SIZING, SPIDER_SILK, ARCANE_DUST, CLOTH_PANEL, BOWSTRING, STAFF_WRAP, WOOD_LATHE_ITEM,
                TREADLE_LATHE_ITEM, BOWYERS_BENCH_ITEM, SEASONED_BILLET, CURED_BILLET, STAVE, STOCK, HAFT, STAFF_SHAFT, WAND_CORE, BOW_LIMBS,
                LINSEED_OIL, HEARTWOOD_RESIN, SYLVAN_SAP, CRUCIBLE_ITEM, GREAT_CRUCIBLE_ITEM, JEWELERS_BENCH_ITEM, JEWELERS_LOUPE_ITEM, BONE_ASH,
                ASSAY_SALT, SUNSALT, FINE_GOLD, GOLD_FITTINGS, GOLD_RING, GOLD_AMULET);
    }
}
