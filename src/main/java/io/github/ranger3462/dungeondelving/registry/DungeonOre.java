package io.github.ranger3462.dungeondelving.registry;

import java.util.Arrays;
import java.util.Optional;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The dungeon ores: a common one found in every dungeon, and one per dungeon tier. Each has an ore block, a raw drop,
 * an ingot and a storage block. They only appear in dungeons, through resource markers. Names are placeholders.
 */
public enum DungeonOre {
    COMMON("common", 0, MapColor.COLOR_LIGHT_GRAY, 0xB8B8B8),
    TIER_1("tier_1", 1, MapColor.COLOR_GREEN, 0x4FC75A),
    TIER_2("tier_2", 2, MapColor.COLOR_LIGHT_BLUE, 0x4FA8E0),
    TIER_3("tier_3", 3, MapColor.COLOR_PURPLE, 0xA45BD8),
    TIER_4("tier_4", 4, MapColor.GOLD, 0xF2B53A);

    /** Used in ids, e.g. {@code tier_1_dungeon_ore}, {@code raw_tier_1_dungeon_ore}, {@code tier_1_dungeon_ingot}. */
    public final String key;
    /** The dungeon tier this ore belongs to; 0 for the common ore. */
    public final int tier;
    /** Placeholder color, used for the generated textures. */
    public final int color;

    public final DeferredBlock<Block> ore;
    public final DeferredItem<BlockItem> oreItem;
    public final DeferredItem<Item> raw;
    public final DeferredItem<Item> ingot;
    public final DeferredBlock<Block> storageBlock;
    public final DeferredItem<BlockItem> storageBlockItem;

    DungeonOre(String key, int tier, MapColor mapColor, int color) {
        this.key = key;
        this.tier = tier;
        this.color = color;
        // Harder ores take a little longer to mine; the tool tier needed is set by block tags.
        float hardness = 3.0f + tier * 0.5f;
        this.ore = ModRegistries.BLOCKS.registerSimpleBlock(key + "_dungeon_ore", BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                .strength(hardness, 3.0f).requiresCorrectToolForDrops().sound(SoundType.STONE));
        this.oreItem = ModRegistries.ITEMS.registerSimpleBlockItem(ore);
        this.raw = ModRegistries.ITEMS.registerSimpleItem("raw_" + key + "_dungeon_ore");
        this.ingot = ModRegistries.ITEMS.registerSimpleItem(key + "_dungeon_ingot");
        this.storageBlock = ModRegistries.BLOCKS.registerSimpleBlock(key + "_dungeon_block", BlockBehaviour.Properties.of().mapColor(mapColor)
                .strength(5.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.METAL));
        this.storageBlockItem = ModRegistries.ITEMS.registerSimpleBlockItem(storageBlock);
    }

    /** The ore for a dungeon tier (1 to 4). */
    public static Optional<DungeonOre> forTier(int tier) {
        return Arrays.stream(values()).filter(ore -> ore.tier == tier && tier > 0).findFirst();
    }

    /** Makes sure every ore is registered; call once while setting up registries. */
    static void init() {}
}
