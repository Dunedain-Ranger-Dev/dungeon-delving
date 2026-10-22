package io.github.ranger3462.dungeondelving.forging;

import com.mojang.serialization.Codec;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Forging: heat iron, strike it on an anvil, quench it and grind it into Tempered Ingots, then make those into forged
 * gear. See {@code docs/design/forging.md}. Stations only count when they're in range of a complete Forge Hearth
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

    /** Blocks near a Forge Hearth that count towards its workshop bonus. */
    public static final TagKey<Block> FORGE_DECOR = TagKey.create(Registries.BLOCK, DungeonDelving.id("forge_decor"));

    // --- Blocks ---

    public static final DeferredBlock<ForgeHearthBlock> FORGE_HEARTH = ModRegistries.BLOCKS.registerBlock("forge_hearth", ForgeHearthBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.STONE)
                    .lightLevel(state -> state.getValue(ForgeHearthBlock.LIT) ? 13 : 0));
    public static final DeferredItem<BlockItem> FORGE_HEARTH_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(FORGE_HEARTH);
    public static final DeferredBlock<BellowsBlock> BELLOWS = ModRegistries.BLOCKS.registerBlock("bellows", BellowsBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> BELLOWS_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(BELLOWS);
    public static final DeferredBlock<DwarvenAnvilBlock> DWARVEN_ANVIL = ModRegistries.BLOCKS.registerBlock("dwarven_anvil", DwarvenAnvilBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 1200.0f).requiresCorrectToolForDrops().sound(SoundType.ANVIL).noOcclusion());
    public static final DeferredItem<BlockItem> DWARVEN_ANVIL_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(DWARVEN_ANVIL);
    public static final DeferredBlock<GrindingWheelBlock> GRINDING_WHEEL = ModRegistries.BLOCKS.registerBlock("grinding_wheel", GrindingWheelBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.STONE).noOcclusion());
    public static final DeferredItem<BlockItem> GRINDING_WHEEL_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(GRINDING_WHEEL);
    public static final DeferredBlock<QuenchTubBlock> QUENCH_TUB = ModRegistries.BLOCKS.registerBlock("quench_tub", QuenchTubBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> QUENCH_TUB_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(QUENCH_TUB);

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
    /** Fills the Quench Tub. */
    public static final DeferredItem<Item> QUENCHING_OIL = ModRegistries.ITEMS.registerSimpleItem("quenching_oil", new Item.Properties().stacksTo(16));

    // --- Recipes ---

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<HeatingRecipe.Smelting>> HEATING_SMELTING =
            SERIALIZERS.register("heating_smelting", () -> HeatingRecipe.serializer(HeatingRecipe.Smelting::new, 100));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<HeatingRecipe.Blasting>> HEATING_BLASTING =
            SERIALIZERS.register("heating_blasting", () -> HeatingRecipe.serializer(HeatingRecipe.Blasting::new, 50));

    private Forging() {}

    /** Makes sure the blocks and items above are registered; call once while setting up registries. */
    public static void init() {}

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
        modEventBus.addListener(Forging::onRegisterPayloads);
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
