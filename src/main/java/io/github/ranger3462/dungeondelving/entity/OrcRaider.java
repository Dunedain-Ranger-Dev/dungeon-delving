package io.github.ranger3462.dungeondelving.entity;

import java.util.Map;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** The orc raider (tiers 1-2): hangs back and hurls hand axes ({@link ThrownAxe}), one every couple of seconds. */
public class OrcRaider extends Orc implements RangedAttackMob {
    public OrcRaider(EntityType<? extends OrcRaider> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Orc.createAttributes().add(Attributes.MAX_HEALTH, 20.0);
    }

    @Override
    protected void addAttackGoal() {
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 40, 12.0f));
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(Items.IRON_AXE);
    }

    @Override
    protected Map<EquipmentSlot, ItemStack> armor() {
        return Map.of(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        ThrownAxe axe = new ThrownAxe(level(), this);
        double dx = target.getX() - getX();
        double dy = target.getY(0.4) - axe.getY();
        double dz = target.getZ() - getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        axe.shoot(dx, dy + flat * 0.15, dz, 1.2f, 8.0f);
        playSound(SoundEvents.TRIDENT_THROW.value(), 1.0f, 0.8f);
        level().addFreshEntity(axe);
    }
}
