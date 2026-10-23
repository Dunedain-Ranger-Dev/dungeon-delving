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

/** The orc vagabond (tiers 3-4): a drifter with a crossbow and a chainmail shirt, shooting from the back of the warband. */
public class OrcVagabond extends Orc implements CrossbowAttackMob, CrossbowWielder {
    private static final EntityDataAccessor<Boolean> CHARGING = SynchedEntityData.defineId(OrcVagabond.class, EntityDataSerializers.BOOLEAN);

    public OrcVagabond(EntityType<? extends OrcVagabond> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Orc.createAttributes().add(Attributes.MAX_HEALTH, 28.0);
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
        return Map.of(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
    }

    @Override
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
