package io.github.ranger3462.dungeondelving.workshop;

import java.util.List;
import java.util.stream.IntStream;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRun;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The way into higher dungeon tiers: Runic Keys, the Runic Shards bosses drop, and the Runic Workshop that combines
 * them. Runic Key I is made from iron; each key after that is made from the key below it, that tier's boss shard, and
 * ingots from that tier's dungeons.
 */
public final class RunicWorkshop {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, DungeonDelving.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, DungeonDelving.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, DungeonDelving.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<RunicWorkshopRecipe>> RECIPE_TYPE = RECIPE_TYPES.register("runic_workshop",
            () -> RecipeType.simple(DungeonDelving.id("runic_workshop")));
    public static final DeferredHolder<RecipeSerializer<?>, RunicWorkshopRecipe.Serializer> SERIALIZER = SERIALIZERS.register("runic_workshop",
            RunicWorkshopRecipe.Serializer::new);
    public static final DeferredHolder<MenuType<?>, MenuType<RunicWorkshopMenu>> MENU = MENUS.register("runic_workshop",
            () -> new MenuType<>(RunicWorkshopMenu::new, FeatureFlags.VANILLA_SET));

    /** Items that go in the workshop's shard slot. */
    public static final TagKey<Item> RUNIC_SHARDS = TagKey.create(Registries.ITEM, DungeonDelving.id("runic_shards"));

    public static final DeferredBlock<RunicWorkshopBlock> BLOCK = ModRegistries.BLOCKS.registerBlock("runic_workshop", RunicWorkshopBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(3.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS));
    public static final DeferredItem<BlockItem> BLOCK_ITEM = ModRegistries.ITEMS.registerSimpleBlockItem(BLOCK);

    /** Runic Keys I to IV, by tier (index 0 is tier 1). */
    public static final List<DeferredItem<RunicKeyItem>> KEYS = IntStream.rangeClosed(DungeonRun.MIN_TIER, DungeonRun.MAX_TIER)
            .mapToObj(tier -> ModRegistries.ITEMS.registerItem("runic_key_" + tier, properties -> new RunicKeyItem(tier, properties),
                    new Item.Properties().stacksTo(16).rarity(rarity(tier))))
            .toList();

    /** Runic Shards I to III, dropped by the bosses of tiers 1 to 3 (index 0 is tier 1). */
    public static final List<DeferredItem<Item>> SHARDS = IntStream.rangeClosed(DungeonRun.MIN_TIER, DungeonRun.MAX_TIER - 1)
            .mapToObj(tier -> ModRegistries.ITEMS.registerSimpleItem("runic_shard_" + tier, new Item.Properties().rarity(rarity(tier))))
            .toList();

    private RunicWorkshop() {}

    private static Rarity rarity(int tier) {
        return tier >= 4 ? Rarity.EPIC : tier >= 3 ? Rarity.RARE : tier >= 2 ? Rarity.UNCOMMON : Rarity.COMMON;
    }

    /** The Runic Key for a tier (1 to 4). */
    public static RunicKeyItem key(int tier) {
        return KEYS.get(DungeonRun.clampTier(tier) - DungeonRun.MIN_TIER).get();
    }

    /** The Runic Shard a tier's boss drops (tiers 1 to 3). */
    public static Item shard(int tier) {
        return SHARDS.get(tier - DungeonRun.MIN_TIER).get();
    }

    static SimpleMenuProvider menuProvider(ContainerLevelAccess access) {
        return new SimpleMenuProvider((id, inventory, player) -> new RunicWorkshopMenu(id, inventory, access),
                Component.translatable("container.dungeondelving.runic_workshop"));
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        SERIALIZERS.register(modEventBus);
        MENUS.register(modEventBus);
    }

    /** Makes sure the blocks and items above are registered; call once while setting up registries. */
    public static void init() {}
}
