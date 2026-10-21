package io.github.ranger3462.dungeondelving.dungeon;

import java.util.Optional;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonProgress;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Gears up dungeon mobs by tier, so deeper dungeons are tougher without new mobs: armor in empty slots (leather at
 * tier 1, chainmail at 2, iron at 3, diamond at 4, more of it and more often enchanted the deeper you go), and
 * enchanted weapons from tier 3. Mobs never drop this gear. Applies to mobs in the {@code dungeondelving:geared}
 * entity tag (goblins, skeletons and zombies by default); bosses keep their own gear.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class DungeonGear {
    public static final TagKey<EntityType<?>> GEARED = TagKey.create(Registries.ENTITY_TYPE, DungeonDelving.id("geared"));
    /** Saved on mobs once they've been geared, so they aren't geared again after a reload. */
    private static final String GEARED_TAG = DungeonDelving.MODID + ":geared";
    private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private DungeonGear() {}

    /** Chance each empty armor slot gets a piece, by tier (index 0 is tier 1). */
    private static final float[] ARMOR_CHANCE = { 0.15f, 0.35f, 0.55f, 0.7f };
    /** Chance each piece (and the weapon) is enchanted, by tier. */
    private static final float[] ENCHANT_CHANCE = { 0.0f, 0.0f, 0.3f, 0.5f };

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || !(event.getEntity() instanceof Mob mob)
                || event.getLevel().dimension() != DungeonDimension.DUNGEON || !mob.getType().is(GEARED) || DungeonBosses.isBoss(mob)
                || mob.getPersistentData().getBoolean(GEARED_TAG)) {
            return;
        }
        Optional<DungeonProgress> progress = DungeonRegions.get().progress(DungeonDimension.regionAt(mob.blockPosition()));
        progress.ifPresent(p -> gear(mob, p.tier(), mob.getRandom()));
        mob.getPersistentData().putBoolean(GEARED_TAG, true);
    }

    /** Gears up a mob for a dungeon of this tier. */
    public static void gear(Mob mob, int tier, RandomSource random) {
        int index = DungeonRun.clampTier(tier) - DungeonRun.MIN_TIER;
        for (EquipmentSlot slot : ARMOR) {
            if (mob.getItemBySlot(slot).isEmpty() && random.nextFloat() < ARMOR_CHANCE[index]) {
                ItemStack piece = new ItemStack(armor(slot, tier));
                maybeEnchant(mob, piece, tier, random, ENCHANT_CHANCE[index]);
                mob.setItemSlot(slot, piece);
                mob.setDropChance(slot, 0.0f);
            }
        }
        ItemStack weapon = mob.getMainHandItem();
        if (!weapon.isEmpty() && weapon.isEnchantable() && !weapon.isEnchanted()) {
            maybeEnchant(mob, weapon, tier, random, ENCHANT_CHANCE[index]);
        }
    }

    private static void maybeEnchant(Mob mob, ItemStack stack, int tier, RandomSource random, float chance) {
        if (random.nextFloat() < chance) {
            ItemStack enchanted = EnchantmentHelper.enchantItem(random, stack, 5 + 5 * tier, mob.registryAccess(), Optional.empty());
            stack.applyComponents(enchanted.getComponentsPatch());
        }
    }

    /** The armor piece for a slot at a tier. */
    public static Item armor(EquipmentSlot slot, int tier) {
        return switch (DungeonRun.clampTier(tier)) {
            case 1 -> switch (slot) {
                case HEAD -> Items.LEATHER_HELMET;
                case CHEST -> Items.LEATHER_CHESTPLATE;
                case LEGS -> Items.LEATHER_LEGGINGS;
                default -> Items.LEATHER_BOOTS;
            };
            case 2 -> switch (slot) {
                case HEAD -> Items.CHAINMAIL_HELMET;
                case CHEST -> Items.CHAINMAIL_CHESTPLATE;
                case LEGS -> Items.CHAINMAIL_LEGGINGS;
                default -> Items.CHAINMAIL_BOOTS;
            };
            case 3 -> switch (slot) {
                case HEAD -> Items.IRON_HELMET;
                case CHEST -> Items.IRON_CHESTPLATE;
                case LEGS -> Items.IRON_LEGGINGS;
                default -> Items.IRON_BOOTS;
            };
            default -> switch (slot) {
                case HEAD -> Items.DIAMOND_HELMET;
                case CHEST -> Items.DIAMOND_CHESTPLATE;
                case LEGS -> Items.DIAMOND_LEGGINGS;
                default -> Items.DIAMOND_BOOTS;
            };
        };
    }
}
