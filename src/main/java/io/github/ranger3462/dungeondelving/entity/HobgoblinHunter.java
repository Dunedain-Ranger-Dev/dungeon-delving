package io.github.ranger3462.dungeondelving.entity;

import java.util.Map;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;

/** The hobgoblin hunter (tiers 3-4): keeps its distance and shoots a crossbow, in a leather cap and jerkin. */
public class HobgoblinHunter extends Hobgoblin implements CrossbowAttackMob {
    private static final EntityDataAccessor<Boolean> CHARGING = SynchedEntityData.defineId(HobgoblinHunter.class, EntityDataSerializers.BOOLEAN);

    public HobgoblinHunter(EntityType<? extends HobgoblinHunter> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Hobgoblin.createAttributes().add(Attributes.MAX_HEALTH, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGING, false);
    }

    @Override
    protected void addAttackGoal() {
        goalSelector.addGoal(2, new RangedCrossbowAttackGoal<>(this, 1.0, 15.0f));
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(Items.CROSSBOW);
    }

    @Override
    protected Map<EquipmentSlot, ItemStack> armor() {
        return Map.of(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET), EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
    }

    /** Whether it's winding its crossbow (shown in its arm pose). */
    public boolean isChargingCrossbow() {
        return entityData.get(CHARGING);
    }

    @Override
    public void setChargingCrossbow(boolean charging) {
        entityData.set(CHARGING, charging);
    }

    @Override
    public void onCrossbowAttackPerformed() {
        noActionTime = 0;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        performCrossbowAttack(this, 1.6f);
    }

    @Override
    public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
        return weapon instanceof CrossbowItem;
    }
}
