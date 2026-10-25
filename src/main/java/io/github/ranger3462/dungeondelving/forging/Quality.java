package io.github.ranger3462.dungeondelving.forging;

import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Forging quality on other mods' gear (the RPG Series and the like): a starred item that isn't our own forged gear
 * has its own armor, toughness, attack damage, ranged damage and spell power scaled by its ★, and its durability set
 * from its ★ when crafted. ★3 is the item's own stats, so unstarred gear from loot matches a ★3 piece. The spread
 * follows our forged sword's ({@link ForgedGear#SWORD_DAMAGE}) and armor durability ({@link ForgedGear#DURABILITY}).
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class Quality {
    /** The ★ whose stats are the item's own. */
    public static final int BASE_STARS = 3;

    private Quality() {}

    /** Whether quality applies to this stack: starred, and not our own forged gear (which has its own tables). */
    public static boolean applies(ItemStack stack) {
        return stack.has(Forging.STARS.get()) && !ForgedGear.isGear(stack);
    }

    /** The stat multiplier for a ★. */
    public static double statMultiplier(int stars) {
        return ratio(ForgedGear.SWORD_DAMAGE, stars);
    }

    /** The durability multiplier for a ★. */
    public static double durabilityMultiplier(int stars) {
        double[] durability = new double[ForgedGear.DURABILITY.length];
        for (int i = 0; i < durability.length; i++) {
            durability[i] = ForgedGear.DURABILITY[i];
        }
        return ratio(durability, stars);
    }

    private static double ratio(double[] table, int stars) {
        return table[Math.clamp(stars, 1, Forging.MAX_STARS) - 1] / table[BASE_STARS - 1];
    }

    /** Whether quality scales this attribute: the ones that make gear better, not speed or reach. */
    public static boolean scales(Holder<Attribute> attribute) {
        if (attribute.is(Attributes.ARMOR) || attribute.is(Attributes.ARMOR_TOUGHNESS) || attribute.is(Attributes.ATTACK_DAMAGE)) {
            return true;
        }
        ResourceLocation id = attribute.unwrapKey().map(key -> key.location()).orElse(null);
        if (id == null) {
            return false;
        }
        return id.equals(ResourceLocation.fromNamespaceAndPath("ranged_weapon", "damage")) || id.getNamespace().equals("spell_power")
                || id.getPath().endsWith("spell_power");
    }

    /** Sets a freshly made piece's durability for its ★, from the item's own durability. */
    public static void applyDurability(ItemStack stack) {
        int base = stack.getPrototype().getOrDefault(DataComponents.MAX_DAMAGE, 0);
        if (base > 0 && applies(stack)) {
            stack.set(DataComponents.MAX_DAMAGE, Math.max(1, (int) Math.round(base * durabilityMultiplier(Forging.stars(stack)))));
        }
    }

    // Before other mods add their own modifiers (Apotheosis affixes), so only the item's own stats scale.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!applies(stack)) {
            return;
        }
        double multiplier = statMultiplier(Forging.stars(stack));
        if (multiplier == 1.0) {
            return;
        }
        for (ItemAttributeModifiers.Entry entry : List.copyOf(event.getModifiers())) {
            if (!scales(entry.attribute()) || entry.modifier().operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                continue;
            }
            AttributeModifier modifier = entry.modifier();
            double amount = modifier.amount() * multiplier;
            if (entry.attribute().is(Attributes.ATTACK_DAMAGE) && modifier.id().equals(Item.BASE_ATTACK_DAMAGE_ID)) {
                // The base modifier is the weapon's damage less the hand's 1, so scale the weapon's whole damage.
                amount = (modifier.amount() + 1) * multiplier - 1;
            }
            event.replaceModifier(entry.attribute(), new AttributeModifier(modifier.id(), amount, modifier.operation()), entry.slot());
        }
    }

    /** Other mods' items can't be renamed like ours, so their stars go on the tooltip's name line. */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        List<Component> lines = event.getToolTip();
        if (!lines.isEmpty() && applies(event.getItemStack()) && !(event.getItemStack().getItem() instanceof StarredItem)) {
            lines.set(0, lines.get(0).copy().append(" ").append(StarredItem.starsText(Forging.stars(event.getItemStack()))));
        }
    }
}
