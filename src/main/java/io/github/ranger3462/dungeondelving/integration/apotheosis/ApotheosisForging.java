package io.github.ranger3462.dungeondelving.integration.apotheosis;

import java.util.List;

import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.event.GetItemSocketsEvent;
import dev.shadowsoffire.apotheosis.loot.LootController;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.apothic_attributes.modifiers.StackAttributeModifiers;
import dev.shadowsoffire.apothic_attributes.modifiers.StackAttributeModifiersEvent;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.forging.ForgedGear;
import io.github.ranger3462.dungeondelving.forging.Forging;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * What a forged piece's ★ means to Apotheosis:
 * <ul>
 * <li>★4 gives an extra gem socket, and ★5 and up give two.</li>
 * <li>★6 multiplies every attribute bonus Apotheosis puts on the piece (affixes and gems) by
 * {@link #MULTIPLIER_SIX}, and ★7 by {@link #MULTIPLIER_SEVEN}. Other affix effects are untouched.</li>
 * <li>★7 gear comes off the crafting table already rolled with an Apotheosis rarity, at least the third one
 * (normally rare).</li>
 * </ul>
 * Only loaded when Apotheosis is installed, so nothing outside this package may refer to these classes directly.
 */
public final class ApotheosisForging {
    public static final float MULTIPLIER_SIX = 1.15f;
    public static final float MULTIPLIER_SEVEN = 1.3f;
    /** The lowest rarity (by Apotheosis's ordering, 0 = common) ★7 gear is rolled with. */
    private static final int SEVEN_MIN_RARITY = 2;

    private ApotheosisForging() {}

    /** Called from the mod constructor when Apotheosis is loaded. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ApotheosisForging::onGetSockets);
        // After Apotheosis has added its affix and gem modifiers, so they can be scaled.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ApotheosisForging::onModifiers);
        NeoForge.EVENT_BUS.addListener(ApotheosisForging::onCrafted);
    }

    /** A forged piece: our own (retired) forged gear, or a forged version of any other gear (DD-89). */
    static boolean isForgedGear(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof ForgedGear.Armor || item instanceof ForgedGear.Sword || item instanceof ForgedGear.Axe
                || io.github.ranger3462.dungeondelving.forging.Quality.applies(stack) && io.github.ranger3462.dungeondelving.forging.Quality.isGear(stack);
    }

    /** Extra sockets for a forged piece of this ★. */
    public static int extraSockets(int stars) {
        return stars >= 5 ? 2 : stars >= 4 ? 1 : 0;
    }

    /** How much a forged piece of this ★ multiplies Apotheosis's attribute bonuses. */
    public static float multiplier(int stars) {
        return stars >= 7 ? MULTIPLIER_SEVEN : stars >= 6 ? MULTIPLIER_SIX : 1.0f;
    }

    private static void onGetSockets(GetItemSocketsEvent event) {
        if (isForgedGear(event.getStack())) {
            event.setSockets(event.getSockets() + extraSockets(Forging.stars(event.getStack())));
        }
    }

    /** Scales every modifier on the piece that isn't one of its own base stats (those are this mod's, or vanilla's weapon base ids). */
    private static void onModifiers(StackAttributeModifiersEvent event) {
        ItemStack stack = event.getItemStack();
        float multiplier = isForgedGear(stack) ? multiplier(Forging.stars(stack)) : 1.0f;
        if (multiplier == 1.0f) {
            return;
        }
        for (StackAttributeModifiers.Entry entry : List.copyOf(event.getModifiers())) {
            AttributeModifier modifier = entry.modifier();
            if (modifier.id().getNamespace().equals(DungeonDelving.MODID) || modifier.id().equals(Item.BASE_ATTACK_DAMAGE_ID)
                    || modifier.id().equals(Item.BASE_ATTACK_SPEED_ID)) {
                continue;
            }
            event.replaceModifier(entry.attribute(), new AttributeModifier(modifier.id(), modifier.amount() * multiplier, modifier.operation()),
                    entry.slots());
        }
    }

    /** ★7 gear is rolled with an Apotheosis rarity as it's crafted. */
    private static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack crafted = event.getCrafting();
        if (isForgedGear(crafted) && Forging.stars(crafted) >= Forging.MAX_STARS && !AffixHelper.hasAffixes(crafted)
                && !event.getEntity().level().isClientSide()) {
            roll(crafted, event.getEntity());
        }
    }

    /** Rolls affixes onto a piece at a random rarity, but no lower than {@link #SEVEN_MIN_RARITY}. */
    public static void roll(ItemStack stack, Player player) {
        GenContext context = GenContext.forPlayer(player);
        LootRarity rarity = LootRarity.random(context);
        List<LootRarity> sorted = RarityRegistry.getSortedRarities();
        if (!sorted.isEmpty()) {
            LootRarity floor = sorted.get(Math.min(SEVEN_MIN_RARITY, sorted.size() - 1));
            if (sorted.indexOf(rarity) < sorted.indexOf(floor)) {
                rarity = floor;
            }
        }
        LootController.createLootItem(stack, rarity, context);
    }
}
