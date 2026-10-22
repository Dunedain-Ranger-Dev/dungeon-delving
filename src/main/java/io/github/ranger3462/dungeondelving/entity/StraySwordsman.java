package io.github.ranger3462.dungeondelving.entity;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** The stray swordsman, the tier 2 undead swordsman: a skeleton swordsman of the frozen wastes whose blows chill (Slowness). */
public class StraySwordsman extends SkeletonSwordsman {
    public static final double MAX_HEALTH = 26.0;
    /** How long a stray swordsman's hits slow, in ticks. */
    public static final int SLOW_TICKS = 60;

    public StraySwordsman(EntityType<? extends StraySwordsman> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return SkeletonSwordsman.createAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.ATTACK_DAMAGE, 3.5);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 0), this);
        }
        return hit;
    }
}
