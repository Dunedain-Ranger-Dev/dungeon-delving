package io.github.ranger3462.dungeondelving.forging;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Forged versions of other gear (vanilla's and the RPG Series', DD-89). A plain piece, with no ★, is the item as its
 * game or mod made it. A forged piece carries a ★ and is always stronger, by its place in its material's band:
 * <ul>
 * <li>{@link Band#IRON} ★1–3: crafted from forged parts in the vanilla shape (leather, cloth and anything without a
 * known material count as iron).</li>
 * <li>{@link Band#DIAMOND} ★4–5: a forged iron piece smithed with a diamond.</li>
 * <li>{@link Band#NETHERITE} ★6–7: a forged diamond piece smithed with netherite.</li>
 * </ul>
 * So a higher ★ is always the stronger piece. Each ★ into the band adds {@link #STAT_STEP} to the item's own armor,
 * toughness, attack damage, ranged damage and spell power, and {@link #DURABILITY_STEP} to its durability. The item
 * keeps its own look. Dungeon loot can come forged ({@link GearStarsLootModifier}). Our own retired forged gear keeps
 * its own tables ({@link ForgedGear}).
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class Quality {
    /** What each ★ into a band adds to a forged piece's stats, and to its durability, over the plain item. */
    public static final double STAT_STEP = 0.05;
    public static final double DURABILITY_STEP = 0.10;
    private static final Pattern DUNGEON_TIER = Pattern.compile("tier_(\\d)");
    /** Whether each item is gear, worked out once. */
    private static final Map<Item, Boolean> GEAR = new ConcurrentHashMap<>();

    /** A material's run of ★. */
    public enum Band {
        IRON(1, 3), DIAMOND(4, 5), NETHERITE(6, 7);

        public final int low;
        public final int high;

        Band(int low, int high) {
            this.low = low;
            this.high = high;
        }

        public int clamp(int stars) {
            return Math.clamp(stars, low, high);
        }
    }

    private Quality() {}

    /** Whether quality applies to this stack: starred, and not our own forged gear (which has its own tables). */
    public static boolean applies(ItemStack stack) {
        return stack.has(Forging.STARS.get()) && !ForgedGear.isGear(stack);
    }

    /**
     * Whether an item is gear that can be forged: something with durability whose own stats quality scales (armor, a
     * weapon, a tool, a staff) or a bow or crossbow. Our own forged gear and parts aren't.
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

    /**
     * The band a piece of gear's material puts it in: by its tool tier or armor material, else by its name (other
     * mods' metals: steel and chain count as iron). Leather, cloth and anything else count as iron. Gold has none
     * (Jewelcrafting will forge it).
     */
    public static Optional<Band> band(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof TieredItem tiered) {
            Tier tier = tiered.getTier();
            if (tier == Tiers.NETHERITE) {
                return Optional.of(Band.NETHERITE);
            } else if (tier == Tiers.DIAMOND) {
                return Optional.of(Band.DIAMOND);
            } else if (tier == Tiers.GOLD) {
                return Optional.empty();
            }
        }
        if (item instanceof ArmorItem armor) {
            if (armor.getMaterial().is(ArmorMaterials.NETHERITE)) {
                return Optional.of(Band.NETHERITE);
            } else if (armor.getMaterial().is(ArmorMaterials.DIAMOND)) {
                return Optional.of(Band.DIAMOND);
            } else if (armor.getMaterial().is(ArmorMaterials.GOLD)) {
                return Optional.empty();
            }
        }
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.contains("netherite")) {
            return Optional.of(Band.NETHERITE);
        } else if (path.contains("diamond")) {
            return Optional.of(Band.DIAMOND);
        } else if (path.contains("golden") || path.startsWith("gold_")) {
            return Optional.empty();
        }
        return Optional.of(Band.IRON);
    }

    /** How many ★ into its band a piece is: 1 at the band's first ★. 0 for a plain piece. */
    public static int step(ItemStack stack) {
        if (!stack.has(Forging.STARS.get())) {
            return 0;
        }
        Band band = band(stack).orElse(Band.IRON);
        return band.clamp(Forging.stars(stack)) - band.low + 1;
    }

    /** The stat multiplier for a piece. */
    public static double statMultiplier(ItemStack stack) {
        return 1.0 + STAT_STEP * step(stack);
    }

    /** The durability multiplier for a piece. */
    public static double durabilityMultiplier(ItemStack stack) {
        return 1.0 + DURABILITY_STEP * step(stack);
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

    /** Gives a piece of gear its ★, kept within its band, and the durability that goes with it. */
    public static void forge(ItemStack stack, int stars) {
        Optional<Band> band = band(stack);
        if (!isGear(stack) || band.isEmpty()) {
            return;
        }
        stack.set(Forging.STARS.get(), band.get().clamp(stars));
        applyDurability(stack);
    }

    /** Sets a forged piece's durability for its ★, from the item's own durability. */
    public static void applyDurability(ItemStack stack) {
        int base = stack.getPrototype().getOrDefault(DataComponents.MAX_DAMAGE, 0);
        if (base > 0 && applies(stack)) {
            stack.set(DataComponents.MAX_DAMAGE, Math.max(1, (int) Math.round(base * durabilityMultiplier(stack))));
        }
    }

    /**
     * Dungeon loot: whether a piece of gear from this loot table comes forged, and at what ★ in its band. In a tier n
     * dungeon, n in 4 pieces come forged; bosses and objective chests forge every piece and favour the top of the band.
     * Anything else stays plain (0).
     */
    public static int lootStars(ResourceLocation table, Band band, RandomSource random) {
        if (table == null || !table.getNamespace().equals(DungeonDelving.MODID)) {
            return 0;
        }
        Matcher tier = DUNGEON_TIER.matcher(table.getPath());
        if (!tier.find()) {
            return 0;
        }
        int dungeonTier = Integer.parseInt(tier.group(1));
        boolean best = table.getPath().startsWith("bosses/") || table.getPath().contains("objective");
        if (!best && random.nextInt(4) >= dungeonTier) {
            return 0;
        }
        int size = band.high - band.low + 1;
        int roll = random.nextInt(size);
        if (best) {
            roll = Math.max(roll, random.nextInt(size));
        }
        return band.low + roll;
    }

    // Before other mods add their own modifiers (Apotheosis affixes), so only the item's own stats scale.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!applies(stack)) {
            return;
        }
        double multiplier = statMultiplier(stack);
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

    /** Other mods' items can't be renamed like ours, so a forged piece's stars go on the tooltip's name line. */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        List<Component> lines = event.getToolTip();
        if (!lines.isEmpty() && applies(event.getItemStack()) && !(event.getItemStack().getItem() instanceof StarredItem)) {
            lines.set(0, lines.get(0).copy().append(" ").append(StarredItem.starsText(Forging.stars(event.getItemStack()))));
        }
    }
}
