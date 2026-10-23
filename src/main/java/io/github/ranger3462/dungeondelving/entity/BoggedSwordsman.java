package io.github.ranger3462.dungeondelving.entity;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** The bogged swordsman, the tier 3 undead swordsman: a mossy skeleton swordsman whose blade carries rot (Poison). */
public class BoggedSwordsman extends SkeletonSwordsman implements MeleeHitEffect {
    public static final double MAX_HEALTH = 28.0;
    /** How long a bogged swordsman's hits poison, in ticks. */
    public static final int POISON_TICKS = 80;

    public BoggedSwordsman(EntityType<? extends BoggedSwordsman> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return SkeletonSwordsman.createAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.ATTACK_DAMAGE, 4.0);
    }

    @Override
    public void onMeleeHit(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0), this);
    }
}
