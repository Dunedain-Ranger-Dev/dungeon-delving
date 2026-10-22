package io.github.ranger3462.dungeondelving.entity;

import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The Skeleton Lord, the tier 2 undead boss: a towering stray swordsman in full armor, with a mace and a shield he
 * raises to block. His blows chill like any stray swordsman's. Guarded by {@link #GUARDS} armored skeleton knights.
 */
public class SkeletonLord extends StraySwordsman {
    public static final double MAX_HEALTH = 120.0;
    /** Skeleton knights in his honor guard. */
    public static final int GUARDS = 4;

    public SkeletonLord(EntityType<? extends SkeletonLord> type, Level level) {
        super(type, level);
        xpReward = 40;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return StraySwordsman.createAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ARMOR, 6.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.SCALE, 1.5).add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.MACE));
        setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0f);
        }
    }

    /**
     * Makes the Skeleton Lord for a boss arena. He isn't added to the world yet; {@code DungeonBosses} does that, with
     * his honor guard.
     */
    public static Mob create(ServerLevel level, BlockPos pos, int tier) {
        SkeletonLord lord = ModEntities.SKELETON_LORD.get().create(level);
        lord.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180.0f, 0.0f);
        EventHooks.finalizeMobSpawn(lord, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        lord.setCustomName(Component.translatable("entity.dungeondelving.skeleton_lord").withStyle(ChatFormatting.GOLD));
        lord.setCustomNameVisible(true);
        return lord;
    }
}
