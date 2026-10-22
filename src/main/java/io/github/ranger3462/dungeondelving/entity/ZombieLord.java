package io.github.ranger3462.dungeondelving.entity;

import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The Zombie Lord, the tier 1 undead boss: a hulking armored zombie with an axe. When he's hurt he groans for help,
 * and the dead answer: every so often {@link #CALL_SIZE} zombies claw their way up around him, up to
 * {@link #MAX_CALLED} at once.
 */
public class ZombieLord extends Zombie {
    public static final double MAX_HEALTH = 120.0;
    public static final int CALL_SIZE = 2;
    public static final int MAX_CALLED = 6;
    /** Ticks between calls for help. */
    public static final int CALL_COOLDOWN = 200;
    private int nextCall;

    public ZombieLord(EntityType<? extends ZombieLord> type, Level level) {
        super(type, level);
        xpReward = 40;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.ATTACK_DAMAGE, 6.0).add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.SCALE, 1.5).add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    /** He calls his own help (see {@link #hurt}), rather than vanilla zombies' reinforcements, which would be more Lords. */
    @Override
    protected void randomizeReinforcementsChance() {
        getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0.0);
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0f);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && level() instanceof ServerLevel level && tickCount >= nextCall && getTarget() != null) {
            nextCall = tickCount + CALL_COOLDOWN;
            callForHelp(level);
        }
        return hurt;
    }

    /** Raises up to {@link #CALL_SIZE} zombies around him, if fewer than {@link #MAX_CALLED} are already near. */
    public int callForHelp(ServerLevel level) {
        int near = level.getEntitiesOfClass(Zombie.class, getBoundingBox().inflate(16), zombie -> zombie.getType() == EntityType.ZOMBIE).size();
        int called = 0;
        for (int i = 0; i < CALL_SIZE && near + called < MAX_CALLED; i++) {
            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (zombie == null) {
                continue;
            }
            double angle = random.nextDouble() * Math.PI * 2;
            zombie.moveTo(getX() + Math.cos(angle) * 3, getY(), getZ() + Math.sin(angle) * 3, random.nextFloat() * 360, 0);
            EventHooks.finalizeMobSpawn(zombie, level, level.getCurrentDifficultyAt(blockPosition()), MobSpawnType.REINFORCEMENT, null);
            zombie.setBaby(false);
            zombie.setTarget(getTarget());
            level.addFreshEntity(zombie);
            called++;
        }
        if (called > 0) {
            playSound(SoundEvents.ZOMBIE_AMBIENT, 2.0f, 0.5f);
        }
        return called;
    }

    /** Makes the Zombie Lord for a boss arena; {@code DungeonBosses} adds him to the world. */
    public static Mob create(ServerLevel level, BlockPos pos, int tier) {
        ZombieLord lord = ModEntities.ZOMBIE_LORD.get().create(level);
        lord.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180.0f, 0.0f);
        EventHooks.finalizeMobSpawn(lord, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        lord.setBaby(false);
        lord.setCustomName(Component.translatable("entity.dungeondelving.zombie_lord").withStyle(ChatFormatting.GOLD));
        lord.setCustomNameVisible(true);
        return lord;
    }
}
