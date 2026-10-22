package io.github.ranger3462.dungeondelving.entity;

import javax.annotation.Nullable;

import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * The wither skeleton archer, a tier 4 undead: a wither skeleton with a bow. Its arrows burn, as a wither skeleton's
 * do, and wither whoever they hit.
 */
public class WitherSkeletonArcher extends net.minecraft.world.entity.monster.WitherSkeleton {
    /** How long its arrows wither, in ticks. */
    public static final int WITHER_TICKS = 100;

    public WitherSkeletonArcher(EntityType<? extends WitherSkeletonArcher> type, Level level) {
        super(type, level);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
    }

    @Override
    public AbstractArrow getArrow(ItemStack arrow, float velocity, @Nullable ItemStack weapon) {
        AbstractArrow shot = super.getArrow(arrow, velocity, weapon);
        if (shot instanceof Arrow tipped) {
            tipped.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, 0));
        }
        return shot;
    }
}
