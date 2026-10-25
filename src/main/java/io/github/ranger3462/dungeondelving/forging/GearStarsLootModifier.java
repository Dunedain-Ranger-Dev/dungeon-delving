package io.github.ranger3462.dungeondelving.forging;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Lets gear in dungeon loot come forged (DD-89): by the dungeon's tier, a piece may roll a ★ in its material's band
 * (see {@link Quality#lootStars}). Gear anywhere else stays plain. Registered for all loot tables in
 * {@code data/neoforge/loot_modifiers/global_loot_modifiers.json}.
 */
public class GearStarsLootModifier extends LootModifier {
    public static final MapCodec<GearStarsLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(instance -> codecStart(instance).apply(instance, GearStarsLootModifier::new));

    public GearStarsLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        for (ItemStack stack : generatedLoot) {
            if (Quality.isGear(stack) && !stack.has(Forging.STARS.get())) {
                Quality.band(stack).ifPresent(band -> {
                    int stars = Quality.lootStars(context.getQueriedLootTableId(), band, context.getRandom());
                    if (stars > 0) {
                        Quality.forge(stack, stars);
                    }
                });
            }
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
