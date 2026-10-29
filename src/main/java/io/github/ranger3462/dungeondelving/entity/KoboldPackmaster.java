package io.github.ranger3462.dungeondelving.entity;

import java.util.Map;

import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
 * The Kobold Packmaster, the tier 1 kobold boss: a big kobold with an iron pickaxe who comes with his pack (two looters
 * and two scavengers, see DungeonBosses), so he starts out hitting {@link Kobold#PACK_BONUS} times as hard. Kill the
 * pack and he's just a big kobold. Once, below half health, he yaps for the warren and {@link #RALLY} more looters run
 * in.
 */
public class KoboldPackmaster extends Kobold {
    public static final double MAX_HEALTH = 80.0;
    /** Looters who answer his call. */
    public static final int RALLY = 2;
    private boolean rallied;

    public KoboldPackmaster(EntityType<? extends KoboldPackmaster> type, Level level) {
        super(type, level);
        xpReward = 40;
    }

    /** Makes him for his arena, named; {@code DungeonBosses} adds him and his pack to the world. */
    public static Mob create(ServerLevel level, BlockPos pos, int tier) {
        return GoblinBosses.make(ModEntities.KOBOLD_PACKMASTER.get(), level, pos, "kobold_packmaster");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Kobold.createAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ARMOR, 4.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.5).add(Attributes.SCALE, 1.6)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(Items.IRON_PICKAXE);
    }

    @Override
    protected Map<EquipmentSlot, ItemStack> armor() {
        return Map.of(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
    }

    public boolean hasRallied() {
        return rallied;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!rallied && getHealth() < getMaxHealth() / 2) {
            rally();
        }
    }

    /** Calls the warren: {@link #RALLY} looters run in around him. */
    public void rally() {
        rallied = true;
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        playSound(SoundEvents.FOX_SCREECH, 2.0f, getVoicePitch());
        for (int i = 0; i < RALLY; i++) {
            Kobold.Looter looter = ModEntities.KOBOLD_LOOTER.get().create(level);
            if (looter == null) {
                continue;
            }
            double angle = Math.PI * 2 * i / RALLY;
            looter.moveTo(getX() + Math.cos(angle) * 2.0, getY(), getZ() + Math.sin(angle) * 2.0, getYRot(), 0.0f);
            EventHooks.finalizeMobSpawn(looter, level, level.getCurrentDifficultyAt(blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            looter.setPersistenceRequired();
            looter.setTarget(getTarget());
            level.addFreshEntity(looter);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Rallied", rallied);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        rallied = tag.getBoolean("Rallied");
    }
}
