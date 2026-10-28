package io.github.ranger3462.dungeondelving.entity;

import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The kobold scavenger: hangs back and throws spears ({@link ThrownSpear}), one every couple of seconds. Like every
 * kobold it hits harder in a pack, and so do its spears.
 */
public class KoboldScavenger extends Kobold implements RangedAttackMob {
    public KoboldScavenger(EntityType<? extends KoboldScavenger> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Kobold.createAttributes().add(Attributes.MAX_HEALTH, 10.0);
    }

    @Override
    protected void addAttackGoal() {
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 45, 14.0f));
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(ModEntities.GOBLIN_SPEAR.get());
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        ThrownSpear spear = new ThrownSpear(level(), this, (float) (ThrownSpear.DAMAGE * packMultiplier()));
        double dx = target.getX() - getX();
        double dy = target.getY(0.4) - spear.getY();
        double dz = target.getZ() - getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        spear.shoot(dx, dy + flat * 0.12, dz, 1.4f, 6.0f);
        playSound(SoundEvents.TRIDENT_THROW.value(), 1.0f, 1.3f);
        level().addFreshEntity(spear);
    }
}
