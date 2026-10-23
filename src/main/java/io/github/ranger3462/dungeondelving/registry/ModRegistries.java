package io.github.ranger3462.dungeondelving.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.block.BossDoorBlock;
import io.github.ranger3462.dungeondelving.block.DungeonExitBlock;
import io.github.ranger3462.dungeondelving.block.DungeonGateBlock;
import io.github.ranger3462.dungeondelving.block.PuzzleLockBlock;
import io.github.ranger3462.dungeondelving.block.RelicAltarBlock;
import io.github.ranger3462.dungeondelving.block.WarDrumBlock;
import io.github.ranger3462.dungeondelving.character.Characters;
import io.github.ranger3462.dungeondelving.character.TomeOfInsightItem;
import io.github.ranger3462.dungeondelving.dungeon.RemoveDungeonOnlyModifier;
import io.github.ranger3462.dungeondelving.dungeon.TagDungeonLootModifier;
import io.github.ranger3462.dungeondelving.forging.Forging;
import io.github.ranger3462.dungeondelving.gate.DungeonGates;
import io.github.ranger3462.dungeondelving.integration.irons.IronsOrcs;
import io.github.ranger3462.dungeondelving.skill.CraftingSkills;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonKeyItem;
import io.github.ranger3462.dungeondelving.integration.irons.IronsGoblins;
import io.github.ranger3462.dungeondelving.integration.irons.IronsSkeletons;
import io.github.ranger3462.dungeondelving.testing.TestersCharm;
import io.github.ranger3462.dungeondelving.workshop.RunicWorkshop;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
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

    /** The generic dungeon stone: fills resource spots that don't roll an ore, and is a basic dungeon material. */
    public static final DeferredBlock<Block> RIFTSTONE = BLOCKS.registerSimpleBlock("riftstone",
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));
    public static final DeferredItem<BlockItem> RIFTSTONE_ITEM = ITEMS.registerSimpleBlockItem(RIFTSTONE);

    // Markers: placed by builders inside dungeon pieces, and turned into chests, mobs or ores each run.
    public static final DeferredBlock<Block> LOOT_MARKER = marker("loot_marker", MapColor.WOOD);
    public static final DeferredBlock<Block> MOB_MARKER = marker("mob_marker", MapColor.STONE);
    public static final DeferredBlock<Block> RESOURCE_MARKER = marker("resource_marker", MapColor.RAW_IRON);
    public static final DeferredBlock<Block> OBJECTIVE_MARKER = marker("objective_marker", MapColor.METAL);
    /** Becomes one of a challenge room's hunt targets: tougher mobs; killing them all drops a Dungeon Key. */
    public static final DeferredBlock<Block> HUNT_MARKER = marker("hunt_marker", MapColor.COLOR_RED);
    /** Where the boss appears when the boss door opens. One per boss room. */
    public static final DeferredBlock<Block> BOSS_MARKER = marker("boss_marker", MapColor.COLOR_BLACK);

    /** Seals the boss room's doorways until enough Dungeon Keys are used on it. Placed by the mod; can't be broken. */
    public static final DeferredBlock<BossDoorBlock> BOSS_DOOR = BLOCKS.registerBlock("boss_door", BossDoorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(-1.0f, 3600000.0f).noLootTable().lightLevel(state -> 4)
                    .sound(SoundType.DEEPSLATE_BRICKS));

    /** The end of a puzzle challenge room: the first time builders' redstone powers it, it drops a Dungeon Key. */
    public static final DeferredBlock<PuzzleLockBlock> PUZZLE_LOCK = BLOCKS.registerBlock("puzzle_lock", PuzzleLockBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(-1.0f, 3600000.0f).noLootTable()
                    .lightLevel(state -> state.getValue(PuzzleLockBlock.SOLVED) ? 10 : 3).sound(SoundType.VAULT));
    public static final DeferredItem<BlockItem> PUZZLE_LOCK_ITEM = ITEMS.registerSimpleBlockItem(PUZZLE_LOCK);
    /** The heart of a collection challenge room: offer it enough Relic Shards and it gives up a Dungeon Key. */
    public static final DeferredBlock<RelicAltarBlock> RELIC_ALTAR = BLOCKS.registerBlock("relic_altar", RelicAltarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0f, 3600000.0f).noLootTable().lightLevel(state -> 7)
                    .sound(SoundType.LODESTONE));
    public static final DeferredItem<BlockItem> RELIC_ALTAR_ITEM = ITEMS.registerSimpleBlockItem(RELIC_ALTAR);
    /** The heart of a wave defense challenge room: strike it to seal the room and call the waves. */
    public static final DeferredBlock<WarDrumBlock> WAR_DRUM = BLOCKS.registerBlock("war_drum", WarDrumBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(-1.0f, 3600000.0f).noLootTable().sound(SoundType.WOOD));
    public static final DeferredItem<BlockItem> WAR_DRUM_ITEM = ITEMS.registerSimpleBlockItem(WAR_DRUM);
    /** Seals a wave defense room's doorways while its waves run. Placed by the mod; can't be broken. */
    public static final DeferredBlock<Block> WAVE_SEAL = BLOCKS.registerSimpleBlock("wave_seal",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(-1.0f, 3600000.0f).noLootTable().lightLevel(state -> 6)
                    .sound(SoundType.DEEPSLATE_BRICKS));

    /** Dropped by cleared challenge rooms; opens the boss door. Only works in the dungeon it came from. */
    public static final DeferredItem<DungeonKeyItem> DUNGEON_KEY = ITEMS.registerItem("dungeon_key", DungeonKeyItem::new,
            new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    /** Dropped by mobs in collection rooms, for the room's Relic Altar. Belongs to its dungeon, like a key. */
    public static final DeferredItem<DungeonKeyItem> RELIC_SHARD = ITEMS.registerItem("relic_shard", DungeonKeyItem::new,
            new Item.Properties().rarity(Rarity.UNCOMMON));

    /** Testing tool: can't die, damage readout, fast regeneration and mana, and flight. Creative tab only. */
    /** A Tome of Insight: read it for a bonus ability point (DD-80). */
    public static final DeferredItem<TomeOfInsightItem> TOME_OF_INSIGHT = ITEMS.registerItem("tome_of_insight", TomeOfInsightItem::new,
            new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
    public static final DeferredItem<TestersCharm.CharmItem> TESTERS_CHARM = ITEMS.registerItem("testers_charm", TestersCharm.CharmItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    /** The dungeon region a Dungeon Key belongs to. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> KEY_REGION = COMPONENTS.registerComponentType("key_region",
            builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * Marks a dungeon ore item as found in the current dungeon run. Removed when the player exits; such items are lost
     * if the run fails.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> DUNGEON_LOOT = COMPONENTS.registerComponentType("dungeon_loot",
            builder -> builder.persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<TagDungeonLootModifier>> TAG_DUNGEON_LOOT =
            LOOT_MODIFIERS.register("tag_dungeon_loot", () -> TagDungeonLootModifier.CODEC);
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<RemoveDungeonOnlyModifier>> REMOVE_DUNGEON_ONLY =
            LOOT_MODIFIERS.register("remove_dungeon_only", () -> RemoveDungeonOnlyModifier.CODEC);

    /** Gate, exit and markers, for players and builders. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("dungeon_delving", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dungeondelving"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> DUNGEON_GATE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(DUNGEON_GATE_ITEM.get());
                output.accept(DungeonGates.GATE_TUNER_ITEM.get());
                output.accept(DungeonGates.BLOODSHED_STONE.get());
                output.accept(DUNGEON_EXIT_ITEM.get());
                output.accept(RunicWorkshop.BLOCK_ITEM.get());
                RunicWorkshop.KEYS.forEach(key -> output.accept(key.get()));
                RunicWorkshop.SHARDS.forEach(shard -> output.accept(shard.get()));
                output.accept(LOOT_MARKER.get());
                output.accept(MOB_MARKER.get());
                output.accept(RESOURCE_MARKER.get());
                output.accept(OBJECTIVE_MARKER.get());
                output.accept(HUNT_MARKER.get());
                output.accept(BOSS_MARKER.get());
                output.accept(PUZZLE_LOCK_ITEM.get());
                output.accept(RELIC_ALTAR_ITEM.get());
                output.accept(WAR_DRUM_ITEM.get());
                output.accept(DUNGEON_KEY.get());
                output.accept(RELIC_SHARD.get());
                output.accept(TESTERS_CHARM.get());
                output.accept(TOME_OF_INSIGHT.get());
                output.accept(ModEntities.GOBLIN_SPAWN_EGG.get());
                output.accept(ModEntities.GOBLIN_SPEAR.get());
                output.accept(ModEntities.GOBLIN_ARCHER_SPAWN_EGG.get());
                output.accept(ModEntities.GOBLIN_BOW.get());
                for (var egg : java.util.List.of(ModEntities.HOBGOBLIN_SOLDIER_SPAWN_EGG, ModEntities.HOBGOBLIN_HUNTER_SPAWN_EGG,
                        ModEntities.HOBGOBLIN_VANGUARD_SPAWN_EGG, ModEntities.HOBGOBLIN_SPELLBREAKER_SPAWN_EGG, ModEntities.GOBLIN_GLADIATOR_SPAWN_EGG,
                        ModEntities.HOBGOBLIN_VETERAN_SPAWN_EGG, ModEntities.HOBGOBLIN_GENERAL_SPAWN_EGG, ModEntities.ORC_SCRAPPER_SPAWN_EGG,
                        ModEntities.ORC_RAIDER_SPAWN_EGG, ModEntities.ORC_VETERAN_SPAWN_EGG, ModEntities.ORC_VAGABOND_SPAWN_EGG,
                        ModEntities.ORC_RAMPAGER_SPAWN_EGG, ModEntities.ORC_COMMANDER_SPAWN_EGG, ModEntities.ORC_CHIEFTAIN_SPAWN_EGG,
                        ModEntities.ORC_WARCHIEF_SPAWN_EGG)) {
                    output.accept(egg.get());
                }
                output.accept(ModEntities.SKELETON_SWORDSMAN_SPAWN_EGG.get());
                output.accept(ModEntities.SKELETON_KNIGHT_SPAWN_EGG.get());
                output.accept(ModEntities.SKELETON_LORD_SPAWN_EGG.get());
                output.accept(ModEntities.STRAY_SWORDSMAN_SPAWN_EGG.get());
                output.accept(ModEntities.BOGGED_SWORDSMAN_SPAWN_EGG.get());
                output.accept(ModEntities.WITHER_SKELETON_ARCHER_SPAWN_EGG.get());
                output.accept(ModEntities.ZOMBIE_LORD_SPAWN_EGG.get());
                for (String optional : new String[] { "goblin_shaman_spawn_egg", "goblin_pyro_spawn_egg", "goblin_chief_spawn_egg", "orc_doomsayer_spawn_egg",
                        "orc_shaman_spawn_egg", "orc_lorekeeper_spawn_egg", "goblin_totem_staff", "goblin_chief_staff",
                        "skeleton_mage_spawn_egg", "wither_skeleton_mage_spawn_egg", "lich_lord_spawn_egg" }) {
                    BuiltInRegistries.ITEM.getOptional(DungeonDelving.id(optional)).ifPresent(output::accept);
                }
                for (var forging : java.util.List.of(Forging.FORGE_HEARTH_ITEM, Forging.BELLOWS_ITEM, Forging.MASTER_ANVIL_ITEM,
                        Forging.GRINDING_WHEEL_ITEM, Forging.QUENCHING_OIL, Forging.HEATED_INGOT, Forging.STRUCK_INGOT,
                        Forging.QUENCHED_INGOT, Forging.UNSTABLE_INGOT, Forging.TEMPERED_IRON_INGOT, Forging.SAND_FLUX, Forging.BORAX,
                        Forging.BORAX_DEPOSIT_ITEM, Forging.BORAX_FLUX, Forging.RUNIC_FLUX, Forging.RUNEFORGED_FLUX, Forging.ARMOR_PLATE, Forging.RIVETS,
                        Forging.CHAIN_LINKS, Forging.TANNING_RACK_ITEM, Forging.LEATHER_SCRAPS, Forging.REFINED_LEATHER, Forging.BOILED_LEATHER, Forging.HARDENED_LEATHER, Forging.LEATHER_BASE,
                        Forging.WOVEN_CLOTH, Forging.PADDED_CLOTH, Forging.GAMBESON_BASE)) {
                    output.accept(forging.get());
                }
                Forging.gear().forEach(output::accept);
                output.accept(RIFTSTONE_ITEM.get());
                for (DungeonOre ore : DungeonOre.values()) {
                    output.accept(ore.oreItem.get());
                    output.accept(ore.raw.get());
                    output.accept(ore.ingot.get());
                    output.accept(ore.storageBlockItem.get());
                }
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
        DungeonOre.init(); // Registers the ores' blocks and items before the registers are attached.
        ModEntities.init();
        RunicWorkshop.init();
        RunicWorkshop.register(modEventBus);
        Forging.init();
        Forging.register(modEventBus);
        DungeonGates.init();
        DungeonGates.register(modEventBus);
        CraftingSkills.register(modEventBus);
        Characters.register(modEventBus);
        ModEntities.register(modEventBus);
        io.github.ranger3462.dungeondelving.entity.Mending.register(modEventBus);
        if (ModList.get().isLoaded("irons_spellbooks")) {
            IronsGoblins.register(modEventBus);
            IronsSkeletons.register(modEventBus);
            IronsOrcs.register(modEventBus);
        }
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        TABS.register(modEventBus);
        LOOT_MODIFIERS.register(modEventBus);
        modEventBus.addListener(ModRegistries::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(DUNGEON_GATE_ITEM);
        }
    }
}
