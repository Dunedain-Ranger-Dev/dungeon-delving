package io.github.ranger3462.dungeondelving.character;

import java.util.regex.Pattern;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/**
 * Where the character items come from in survival. The Tome of Calling: one free when a new character picks a race
 * (until class tutors exist, DD-84), then crafted. The Elixir of Rebirth: crafted (tier 3 ingots). The Tome of Insight:
 * only found, a small chance from each dungeon boss and objective chest, rising by tier.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class CharacterItems {
    /** A boss's chance to drop a Tome of Insight, by dungeon tier; objective chests have half. */
    static final float[] INSIGHT_FROM_BOSS = { 0.05f, 0.08f, 0.12f, 0.15f };
    private static final Pattern BOSS_TABLE = Pattern.compile("bosses/[a-z_]+/tier_([1-4])");
    private static final Pattern OBJECTIVE_TABLE = Pattern.compile("chests/tier_([1-4])/objective");

    private CharacterItems() {}

    /** Gives a new character their first Tome of Calling, unless they have a class or a tome already. */
    public static void giveStarterTome(ServerPlayer player) {
        if (Characters.data(player).classOf().isPresent() || player.getInventory().contains(new ItemStack(ModRegistries.TOME_OF_CALLING.get()))) {
            return;
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(ModRegistries.TOME_OF_CALLING.get()));
        player.sendSystemMessage(Component.translatable("message.dungeondelving.starter_tome").withStyle(ChatFormatting.GOLD));
    }

    /** The chance of a Tome of Insight from this loot table, or 0 if it's not a boss's or an objective chest's. */
    public static float insightChance(String path) {
        var boss = BOSS_TABLE.matcher(path);
        if (boss.matches()) {
            return INSIGHT_FROM_BOSS[Integer.parseInt(boss.group(1)) - 1];
        }
        var objective = OBJECTIVE_TABLE.matcher(path);
        return objective.matches() ? INSIGHT_FROM_BOSS[Integer.parseInt(objective.group(1)) - 1] / 2 : 0;
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!event.getName().getNamespace().equals(DungeonDelving.MODID)) {
            return;
        }
        float chance = insightChance(event.getName().getPath());
        if (chance > 0) {
            event.getTable().addPool(LootPool.lootPool()
                    .name(DungeonDelving.MODID + ":tome_of_insight")
                    .add(LootItem.lootTableItem(ModRegistries.TOME_OF_INSIGHT.get()))
                    .when(LootItemRandomChanceCondition.randomChance(chance))
                    .build());
        }
    }
}
