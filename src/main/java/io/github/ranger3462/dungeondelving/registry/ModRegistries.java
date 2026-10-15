package io.github.ranger3462.dungeondelving.registry;

import com.mojang.serialization.MapCodec;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.block.DungeonExitBlock;
import io.github.ranger3462.dungeondelving.block.DungeonGateBlock;
import io.github.ranger3462.dungeondelving.dungeon.TagDungeonLootModifier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
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

    /** Marks an item as found in the current dungeon run. Removed when the player exits; such items are lost on death. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> DUNGEON_LOOT = COMPONENTS.registerComponentType("dungeon_loot",
            builder -> builder.persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<TagDungeonLootModifier>> TAG_DUNGEON_LOOT =
            LOOT_MODIFIERS.register("tag_dungeon_loot", () -> TagDungeonLootModifier.CODEC);

    private ModRegistries() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        LOOT_MODIFIERS.register(modEventBus);
        modEventBus.addListener(ModRegistries::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(DUNGEON_GATE_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS && event.hasPermissions()) {
            event.accept(DUNGEON_EXIT_ITEM);
        }
    }
}
