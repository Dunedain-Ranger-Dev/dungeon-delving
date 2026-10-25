package io.github.ranger3462.dungeondelving.forging;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Forging quality on other mods' gear (the RPG Series and the like): a starred item that isn't our own forged gear
 * has its own armor, toughness, attack damage, ranged damage and spell power scaled by its ★, and its durability set
 * from its ★ when crafted. ★3 is the item's own stats. The spread follows our forged sword's
 * ({@link ForgedGear#SWORD_DAMAGE}) and armor durability ({@link ForgedGear#DURABILITY}).
 * <p>
 * So no piece reads as unrated (DD-89), every piece of gear carries a ★: dungeon loot rolls one by the dungeon's tier
 * ({@link GearStarsLootModifier}), other loot and anything that reaches a player's inventory without one gets ★3, and
 * a piece still without one (in a chest, on a mob) shows ★3 on its name.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class Quality {
    /** The ★ whose stats are the item's own. */
    public static final int BASE_STARS = 3;

    /** How often (in ticks) a player's inventory is checked for gear without a ★. */
    private static final int STAMP_INTERVAL = 20;
    private static final Pattern DUNGEON_TIER = Pattern.compile("tier_(\\d)");
    /** Whether each item is gear, worked out once. */
    private static final Map<Item, Boolean> GEAR = new ConcurrentHashMap<>();

    private Quality() {}

    /**
     * Whether an item is gear that takes a ★: something with durability whose own stats quality scales (armor, a
     * weapon, a tool, a staff) or a bow or crossbow. Our own forged gear and parts carry theirs already.
     */
    public static boolean isGear(ItemStack stack) {
        if (stack.isEmpty() || ForgedGear.isGear(stack) || stack.getItem() instanceof StarredItem) {
            return false;
        }
        return GEAR.computeIfAbsent(stack.getItem(), item -> {
            ItemStack plain = new ItemStack(item);
            if (!plain.isDamageableItem()) {
                return false;
            }
            if (item instanceof ProjectileWeaponItem) {
                return true;
            }
            // Its own modifiers are in its component; some mods add theirs through the item's default modifiers instead.
            return plain.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                    .anyMatch(entry -> scales(entry.attribute()))
                    || item.getDefaultAttributeModifiers(plain).modifiers().stream().anyMatch(entry -> scales(entry.attribute()));
        });
    }

    /** Gives a piece of gear its ★ (and the durability that goes with it), if it has none yet. */
    public static void stamp(ItemStack stack, int stars) {
        if (isGear(stack) && !stack.has(Forging.STARS.get())) {
            stack.set(Forging.STARS.get(), Math.clamp(stars, 1, Forging.MAX_STARS));
            applyDurability(stack);
        }
    }

    /**
     * The ★ a piece of gear from this loot table gets. Dungeon tables roll by the dungeon's tier: tier n gives ★n to
     * ★n+2, one better from a boss or the objective chest. Anything else is standard, ★3.
     */
    public static int lootStars(ResourceLocation table, RandomSource random) {
        if (table != null && table.getNamespace().equals(DungeonDelving.MODID)) {
            Matcher tier = DUNGEON_TIER.matcher(table.getPath());
            if (tier.find()) {
                boolean best = table.getPath().startsWith("bosses/") || table.getPath().contains("objective");
                return Math.clamp(Integer.parseInt(tier.group(1)) + random.nextInt(3) + (best ? 1 : 0), 1, Forging.MAX_STARS);
            }
        }
        return BASE_STARS;
    }

    /** Gear that reaches a player without a ★ (crafted, picked up, traded, given) gets ★3. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % STAMP_INTERVAL != 0) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            stamp(inventory.getItem(slot), BASE_STARS);
        }
        stamp(player.containerMenu.getCarried(), BASE_STARS);
    }

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

    /** Other mods' items can't be renamed like ours, so their stars go on the tooltip's name line; gear with none yet shows ★3. */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        List<Component> lines = event.getToolTip();
        ItemStack stack = event.getItemStack();
        if (lines.isEmpty() || stack.getItem() instanceof StarredItem) {
            return;
        }
        if (applies(stack) || isGear(stack)) {
            int stars = stack.has(Forging.STARS.get()) ? Forging.stars(stack) : BASE_STARS;
            lines.set(0, lines.get(0).copy().append(" ").append(StarredItem.starsText(stars)));
        }
    }
}
