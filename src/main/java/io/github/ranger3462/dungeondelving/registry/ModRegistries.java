package io.github.ranger3462.dungeondelving.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.block.DungeonExitBlock;
import io.github.ranger3462.dungeondelving.block.DungeonGateBlock;
import io.github.ranger3462.dungeondelving.dungeon.TagDungeonLootModifier;
import io.github.ranger3462.dungeondelving.dungeon.bag.DungeonBagItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Everything this mod adds to the game's registries. */
public final class ModRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DungeonDelving.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DungeonDelving.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, DungeonDelving.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DungeonDelving.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DungeonDelving.MODID);
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, DungeonDelving.MODID);

    /** Right-click in the overworld to start a dungeon run. */
    public static final DeferredBlock<DungeonGateBlock> DUNGEON_GATE = BLOCKS.registerBlock("dungeon_gate", DungeonGateBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5.0f, 1200.0f).lightLevel(state -> 10).sound(SoundType.STONE));
    public static final DeferredItem<BlockItem> DUNGEON_GATE_ITEM = ITEMS.registerSimpleBlockItem(DUNGEON_GATE);

    /** Placed in each dungeon's start room. Right-click to leave and keep your loot. Can't be broken. */
    public static final DeferredBlock<DungeonExitBlock> DUNGEON_EXIT = BLOCKS.registerBlock("dungeon_exit", DungeonExitBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0f, 3600000.0f).noLootTable().lightLevel(state -> 12)
                    .sound(SoundType.AMETHYST));
    public static final DeferredItem<BlockItem> DUNGEON_EXIT_ITEM = ITEMS.registerSimpleBlockItem(DUNGEON_EXIT);

    // Markers: placed by builders inside dungeon pieces, and turned into chests, mobs or ores each run.
    public static final DeferredBlock<Block> LOOT_MARKER = marker("loot_marker", MapColor.WOOD);
    public static final DeferredBlock<Block> MOB_MARKER = marker("mob_marker", MapColor.STONE);
    public static final DeferredBlock<Block> RESOURCE_MARKER = marker("resource_marker", MapColor.RAW_IRON);
    public static final DeferredBlock<Block> OBJECTIVE_MARKER = marker("objective_marker", MapColor.METAL);

    /** Given on entering a dungeon; right-click to see what you've found. */
    public static final DeferredItem<DungeonBagItem> DUNGEON_BAG = ITEMS.registerItem("dungeon_bag", DungeonBagItem::new,
            new Item.Properties().stacksTo(1));

    /** How many times a player has upgraded their dungeon bag (DD-44). Kept for life, including through death. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> BAG_LEVEL = ATTACHMENTS.register("bag_level",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    /** Marks an item as found in the current dungeon run. Removed when the player exits; such items are lost on death. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> DUNGEON_LOOT = COMPONENTS.registerComponentType("dungeon_loot",
            builder -> builder.persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<TagDungeonLootModifier>> TAG_DUNGEON_LOOT =
            LOOT_MODIFIERS.register("tag_dungeon_loot", () -> TagDungeonLootModifier.CODEC);

    /** Gate, exit and markers, for players and builders. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("dungeon_delving", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dungeondelving"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> DUNGEON_GATE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(DUNGEON_GATE_ITEM.get());
                output.accept(DUNGEON_EXIT_ITEM.get());
                output.accept(LOOT_MARKER.get());
                output.accept(MOB_MARKER.get());
                output.accept(RESOURCE_MARKER.get());
                output.accept(OBJECTIVE_MARKER.get());
            })
            .build());

    private ModRegistries() {}

    private static DeferredBlock<Block> marker(String name, MapColor color) {
        DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(name,
                BlockBehaviour.Properties.of().mapColor(color).strength(0.5f).noLootTable().noOcclusion().sound(SoundType.WOOD));
        ITEMS.registerSimpleBlockItem(block);
        return block;
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        TABS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        LOOT_MODIFIERS.register(modEventBus);
        modEventBus.addListener(ModRegistries::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(DUNGEON_GATE_ITEM);
        }
    }
}
