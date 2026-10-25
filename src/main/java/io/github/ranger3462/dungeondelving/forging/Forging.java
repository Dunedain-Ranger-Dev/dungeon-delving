package io.github.ranger3462.dungeondelving.forging;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Forging: heat iron, strike it on an anvil, quench it and grind it into Tempered Ingots, then make those into forged
 * gear. See {@code docs/design/forging.md}. Stations only count when they're in range of a Guild Ledger with a complete forge
 * (see {@link ForgeWorkshop}).
 */
public final class Forging {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, DungeonDelving.MODID);

    /** The highest ★ anything can be forged to. */
    public static final int MAX_STARS = 7;

    // --- Components ---

    /** A tempered piece's ★, or on hot and quenched metal, the ★ it's being worked towards. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STARS = ModRegistries.COMPONENTS.registerComponentType("stars",
            builder -> builder.persistent(Codec.intRange(1, MAX_STARS)).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** The game time hot metal started cooling. Missing until it first leaves the furnace. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> HEATED_AT = ModRegistries.COMPONENTS.registerComponentType("heated_at",
            builder -> builder.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));

    // --- Tags ---

    /** Blocks near a Guild Ledger that count towards its forge's workshop bonus. */
    public static final TagKey<Block> FORGE_DECOR = TagKey.create(Registries.BLOCK, DungeonDelving.id("forge_decor"));

    // --- Blocks ---

    public static final DeferredBlock<BellowsBlock> BELLOWS = ModRegistries.BLOCKS.registerBlock("bellows", BellowsBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> BELLOWS_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(BELLOWS);
    public static final DeferredBlock<MasterAnvilBlock> MASTER_ANVIL = ModRegistries.BLOCKS.registerBlock("master_anvil", MasterAnvilBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 1200.0f).requiresCorrectToolForDrops().sound(SoundType.ANVIL).noOcclusion());
    public static final DeferredItem<BlockItem> MASTER_ANVIL_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(MASTER_ANVIL);
    public static final DeferredBlock<GrindingWheelBlock> GRINDING_WHEEL = ModRegistries.BLOCKS.registerBlock("grinding_wheel", GrindingWheelBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.STONE).noOcclusion());
    public static final DeferredItem<BlockItem> GRINDING_WHEEL_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(GRINDING_WHEEL);
    /** A cauldron of Quenching Oil. It has no item of its own: pour oil into a cauldron to make one. */
    public static final DeferredBlock<OilCauldronBlock> OIL_CAULDRON = ModRegistries.BLOCKS.registerBlock("oil_cauldron", OilCauldronBlock::new,
            BlockBehaviour.Properties.ofLegacyCopy(net.minecraft.world.level.block.Blocks.CAULDRON));

    // --- Items ---

    /** Iron fresh from the furnace, cooling until it's struck. */
    public static final DeferredItem<HotMetalItem> HEATED_INGOT = ModRegistries.ITEMS.registerItem("heated_ingot", HotMetalItem::new,
            new Item.Properties().stacksTo(1));
    /** A heated ingot that's been struck into shape, still hot, waiting to be quenched. */
    public static final DeferredItem<HotMetalItem> STRUCK_INGOT = ModRegistries.ITEMS.registerItem("struck_ingot", HotMetalItem::new,
            new Item.Properties().stacksTo(1));
    /** A quenched ingot, waiting to be ground. */
    public static final DeferredItem<StarredItem> QUENCHED_INGOT = ModRegistries.ITEMS.registerItem("quenched_ingot", StarredItem::new,
            new Item.Properties().stacksTo(16));
    /** A quenched ingot that was ground badly: one more clean grind tempers it, another miss breaks it. */
    public static final DeferredItem<StarredItem> UNSTABLE_INGOT = ModRegistries.ITEMS.registerItem("unstable_ingot", StarredItem.WithTooltip.of(
            "tooltip.dungeondelving.unstable_ingot"), new Item.Properties().stacksTo(16));
    /** The finished metal: the ★ says how many times it's been through the forge. */
    public static final DeferredItem<StarredItem> TEMPERED_IRON_INGOT = ModRegistries.ITEMS.registerItem("tempered_iron_ingot", StarredItem::new,
            new Item.Properties());
    /** Poured into a cauldron, makes an Oil Cauldron. */
    public static final DeferredItem<Item> QUENCHING_OIL = ModRegistries.ITEMS.registerSimpleItem("quenching_oil", new Item.Properties().stacksTo(16));
    /**
     * An iron ingot worked together with coal, ready to become steel in a blast furnace. A step of its own, so plain
     * iron ingots in a blast furnace still heat for forging (the Witcher's steel recipe used to take them first).
     */
    public static final DeferredItem<Item> COKE_INFUSED_IRON_INGOT = ModRegistries.ITEMS.registerSimpleItem("coke_infused_iron_ingot",
            new Item.Properties());

    // --- Flux ---

    /** Silica sand flux, like early smiths used: good for parts up to ★3. */
    public static final DeferredItem<FluxItem> SAND_FLUX = flux("sand_flux", 3);
    /** Borax, a mineral found in deeper dungeons (tier 2 and up). */
    public static final DeferredItem<Item> BORAX = ModRegistries.ITEMS.registerSimpleItem("borax", new Item.Properties());
    public static final DeferredBlock<DropExperienceBlock> BORAX_DEPOSIT = ModRegistries.BLOCKS.registerBlock("borax_deposit",
            properties -> new DropExperienceBlock(UniformInt.of(1, 3), properties),
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f, 3.0f).requiresCorrectToolForDrops().sound(SoundType.STONE));
    public static final DeferredItem<BlockItem> BORAX_DEPOSIT_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(BORAX_DEPOSIT);
    /** Borax flux: good for parts up to ★5. */
    public static final DeferredItem<FluxItem> BORAX_FLUX = flux("borax_flux", 5);
    /** Borax flux worked with a tier 3 Runic Shard: good for ★6. */
    public static final DeferredItem<FluxItem> RUNIC_FLUX = flux("runic_flux", 6);
    /** Runic flux worked with Thailite, from tier 4 dungeons: good for ★7. */
    public static final DeferredItem<FluxItem> RUNEFORGED_FLUX = flux("runeforged_flux", 7);

    // --- Parts ---

    /** Two tempered ingots and flux, hammered into a plate: heavy armor and weapons. */
    public static final DeferredItem<StarredItem> ARMOR_PLATE = ModRegistries.ITEMS.registerItem("armor_plate", StarredItem::new, new Item.Properties());
    /** Rivets, four from an ingot: hold every forged piece together. */
    public static final DeferredItem<StarredItem> RIVETS = ModRegistries.ITEMS.registerItem("rivets", StarredItem::new, new Item.Properties());
    /** Chain links, four from an ingot: medium armor. */
    public static final DeferredItem<StarredItem> CHAIN_LINKS = ModRegistries.ITEMS.registerItem("chain_links", StarredItem::new, new Item.Properties());

    // --- Leather and cloth ---

    /** Where leather is scraped into a scraped hide, ready for tanning. */
    public static final DeferredBlock<TanningRackBlock> TANNING_RACK = ModRegistries.BLOCKS.registerBlock("tanning_rack", TanningRackBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> TANNING_RACK_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(TANNING_RACK);
    /** What's left of a hide torn while scraping. Four make a leather again. */
    public static final DeferredItem<Item> LEATHER_SCRAPS = ModRegistries.ITEMS.registerSimpleItem("leather_scraps", new Item.Properties());
    /**
     * Tanned leather (its id is from when it was called refined leather): a scraped hide tanned in liquor (DD-88). Cut on
     * a Shaping Rack into leather panels. Boiled leather, which tanning replaced, loads as this.
     */
    public static final DeferredItem<StarredItem> REFINED_LEATHER = ModRegistries.ITEMS.registerItem("refined_leather", StarredItem::new, new Item.Properties());
    /** Leather panels waxed hard, for brigandine. */
    public static final DeferredItem<StarredItem> HARDENED_LEATHER = ModRegistries.ITEMS.registerItem("hardened_leather", StarredItem::new, new Item.Properties());
    /** The backing every medium and heavy piece is built on. */
    public static final DeferredItem<StarredItem> LEATHER_BASE = ModRegistries.ITEMS.registerItem("leather_base", StarredItem::new, new Item.Properties());
    public static final DeferredItem<StarredItem> WOVEN_CLOTH = ModRegistries.ITEMS.registerItem("woven_cloth", StarredItem::new, new Item.Properties());
    /** Fulled cloth (its id is from when it was called padded cloth): woven cloth thickened in hot water (DD-88). */
    public static final DeferredItem<StarredItem> PADDED_CLOTH = ModRegistries.ITEMS.registerItem("padded_cloth", StarredItem::new, new Item.Properties());
    /** The quilted backing light armor is built on. */
    public static final DeferredItem<StarredItem> GAMBESON_BASE = ModRegistries.ITEMS.registerItem("gambeson_base", StarredItem::new, new Item.Properties());

    static {
        ModRegistries.ITEMS.addAlias(DungeonDelving.id("boiled_leather"), DungeonDelving.id("refined_leather"));
    }

    // --- Gear ---

    /** Forged armor by weight and slot, e.g. {@code plate_chestplate}. */
    public static final Map<ForgedGear.Weight, Map<ArmorItem.Type, DeferredItem<ForgedGear.Armor>>> ARMOR = armor();
    public static final DeferredItem<ForgedGear.Sword> FORGED_SWORD = ModRegistries.ITEMS.registerItem("forged_sword", ForgedGear.Sword::new,
            new Item.Properties());
    public static final DeferredItem<ForgedGear.Axe> FORGED_AXE = ModRegistries.ITEMS.registerItem("forged_axe", ForgedGear.Axe::new, new Item.Properties());

    private static DeferredItem<FluxItem> flux(String name, int maxStars) {
        return ModRegistries.ITEMS.registerItem(name, properties -> new FluxItem(properties, maxStars), new Item.Properties());
    }

    private static Map<ForgedGear.Weight, Map<ArmorItem.Type, DeferredItem<ForgedGear.Armor>>> armor() {
        Map<ForgedGear.Weight, Map<ArmorItem.Type, DeferredItem<ForgedGear.Armor>>> all = new EnumMap<>(ForgedGear.Weight.class);
        for (ForgedGear.Weight weight : ForgedGear.Weight.values()) {
            Map<ArmorItem.Type, DeferredItem<ForgedGear.Armor>> pieces = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type type : List.of(ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)) {
                pieces.put(type, ModRegistries.ITEMS.registerItem(weight.name + "_" + type.getName(),
                        properties -> new ForgedGear.Armor(weight, type, properties), new Item.Properties()));
            }
            all.put(weight, pieces);
        }
        return all;
    }

    /** Every forged armor piece and weapon. */
    public static List<Item> gear() {
        List<Item> gear = new java.util.ArrayList<>();
        ARMOR.values().forEach(pieces -> pieces.values().forEach(piece -> gear.add(piece.get())));
        gear.add(FORGED_SWORD.get());
        gear.add(FORGED_AXE.get());
        return gear;
    }

    // --- Recipes ---

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<HeatingRecipe.Smelting>> HEATING_SMELTING =
            SERIALIZERS.register("heating_smelting", () -> HeatingRecipe.serializer(HeatingRecipe.Smelting::new, 100));
    public static final DeferredHolder<RecipeSerializer<?>, StarredRecipes.ShapedSerializer> STARRED_SHAPED = SERIALIZERS.register("starred_shaped",
            StarredRecipes.ShapedSerializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, StarredRecipes.ShapelessSerializer> STARRED_SHAPELESS =
            SERIALIZERS.register("starred_shapeless", StarredRecipes.ShapelessSerializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, StarredRecipes.SmithingSerializer> STARRED_SMITHING =
            SERIALIZERS.register("starred_smithing", StarredRecipes.SmithingSerializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<HeatingRecipe.Blasting>> HEATING_BLASTING =
            SERIALIZERS.register("heating_blasting", () -> HeatingRecipe.serializer(HeatingRecipe.Blasting::new, 50));

    private Forging() {}

    /** Makes sure the blocks and items above are registered; call once while setting up registries. */
    public static void init() {}

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
        ForgedGear.MATERIALS.register(modEventBus);
        modEventBus.addListener(Forging::onRegisterPayloads);
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(OilCauldronBlock::registerInteractions));
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // The handler only ever runs on the client, so the client class it names is never loaded on a server.
        event.registrar("1").playToClient(TimingBarPayload.TYPE, TimingBarPayload.STREAM_CODEC,
                (payload, context) -> io.github.ranger3462.dungeondelving.client.TimingBarOverlay.handle(payload));
        event.registrar("1").playToServer(StrikePayload.TYPE, StrikePayload.STREAM_CODEC, StrikePayload::handle);
    }

    /** A stack's ★ (1 if it has none). */
    public static int stars(net.minecraft.world.item.ItemStack stack) {
        return stack.getOrDefault(STARS.get(), 1);
    }
}
