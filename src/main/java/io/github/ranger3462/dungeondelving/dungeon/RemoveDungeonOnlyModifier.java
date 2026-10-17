package io.github.ranger3462.dungeondelving.dungeon;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Removes items in the {@code dungeondelving:dungeon_only} tag from loot generated outside the dungeon dimension (chests,
 * mob drops, block drops, from any mod), so those items can only be found in dungeons. The mod leaves the tag empty;
 * the pack fills it. Registered in {@code data/neoforge/loot_modifiers/global_loot_modifiers.json}.
 */
public class RemoveDungeonOnlyModifier extends LootModifier {
    public static final MapCodec<RemoveDungeonOnlyModifier> CODEC =
            RecordCodecBuilder.mapCodec(instance -> codecStart(instance).apply(instance, RemoveDungeonOnlyModifier::new));

    public RemoveDungeonOnlyModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        DungeonLoot.removeDungeonOnly(generatedLoot, context.getLevel().dimension());
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
