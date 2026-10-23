package io.github.ranger3462.dungeondelving.entity;

import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A hand axe thrown by an orc raider. It tumbles end over end in flight (see the client's ThrownAxeRenderer), hits
 * hard and breaks on whatever it hits, or lodges in the block it hits for a few seconds; nothing is left to pick up.
 */
public class ThrownAxe extends ThrowableItemProjectile {
    public static final float DAMAGE = 5.0f;
    /** How long it stays lodged in a block before it's gone, in ticks. */
    public static final int LODGED_TICKS = 100;
    private static final EntityDataAccessor<Boolean> LODGED = SynchedEntityData.defineId(ThrownAxe.class, EntityDataSerializers.BOOLEAN);
    private int lodgedTime;

    public ThrownAxe(EntityType<? extends ThrownAxe> type, Level level) {
        super(type, level);
    }

    public ThrownAxe(Level level, LivingEntity thrower) {
        super(ModEntities.THROWN_AXE.get(), thrower, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LODGED, false);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.IRON_AXE;
    }

    /** Whether it's stuck in a block. */
    public boolean isLodged() {
        return entityData.get(LODGED);
    }

    @Override
    public void tick() {
        if (isLodged()) {
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide && ++lodgedTime >= LODGED_TICKS) {
                discard();
            }
            return;
        }
        super.tick();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        strike(result.getEntity());
        if (!level().isClientSide) {
            playSound(SoundEvents.ITEM_BREAK, 0.6f, 1.2f);
            discard();
        }
    }

    /** Hurts what the axe hit. */
    public void strike(Entity target) {
        target.hurt(damageSources().thrown(this, getOwner()), DAMAGE);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        lodge(result.getLocation());
    }

    /** Sticks it in a block at this point, its blade sunk in just short of it. */
    public void lodge(Vec3 at) {
        if (level().isClientSide) {
            return;
        }
        Vec3 back = getDeltaMovement().lengthSqr() > 1.0e-6 ? getDeltaMovement().normalize().scale(0.15) : Vec3.ZERO;
        setPos(at.subtract(back));
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        entityData.set(LODGED, true);
        playSound(SoundEvents.TRIDENT_HIT_GROUND, 0.8f, 1.3f);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Lodged", isLodged());
        tag.putInt("LodgedTime", lodgedTime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(LODGED, tag.getBoolean("Lodged"));
        lodgedTime = tag.getInt("LodgedTime");
    }
}
