package io.github.ranger3462.dungeondelving.entity;

import java.util.EnumSet;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * Lets a mob with a shield in its off hand raise it when its target gets close, blocking hits from the front for a
 * second or so before lowering it again. Players can time their swings around it, or break the guard with an axe,
 * which knocks the shield down for a few seconds like it does to players.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public class ShieldBlockGoal extends Goal {
    /** Saved on mobs whose guard was broken: the game time they may block again. */
    private static final String BROKEN_UNTIL = DungeonDelving.MODID + ":guard_broken_until";
    private static final double RANGE = 4.0;
    /** Chance per tick of raising the shield while the target is in range. */
    private static final float BLOCK_CHANCE = 0.08f;
    private static final int GUARD_BREAK_TICKS = 100;

    private final Mob mob;
    private int blockTicks;
    private int cooldown;

    public ShieldBlockGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && mob.getOffhandItem().getItem() instanceof ShieldItem && !guardBroken(mob)
                && mob.distanceToSqr(target) < RANGE * RANGE && mob.getRandom().nextFloat() < BLOCK_CHANCE;
    }

    @Override
    public void start() {
        mob.startUsingItem(InteractionHand.OFF_HAND);
        blockTicks = 15 + mob.getRandom().nextInt(20);
    }

    @Override
    public boolean canContinueToUse() {
        return blockTicks > 0 && mob.isUsingItem() && !guardBroken(mob);
    }

    @Override
    public void tick() {
        blockTicks--;
        if (mob.getTarget() != null) {
            mob.getLookControl().setLookAt(mob.getTarget(), 30.0f, 30.0f);
        }
    }

    @Override
    public void stop() {
        mob.stopUsingItem();
        cooldown = 30 + mob.getRandom().nextInt(30);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private static boolean guardBroken(Mob mob) {
        return mob.level().getGameTime() < mob.getPersistentData().getLong(BROKEN_UNTIL);
    }

    /** An axe hit on a blocking mob knocks its shield down for a few seconds. */
    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (event.getEntity() instanceof Mob mob && event.getBlocked()
                && event.getDamageSource().getDirectEntity() instanceof LivingEntity attacker && attacker.getMainHandItem().getItem() instanceof AxeItem) {
            mob.getPersistentData().putLong(BROKEN_UNTIL, mob.level().getGameTime() + GUARD_BREAK_TICKS);
            mob.stopUsingItem();
            mob.playSound(SoundEvents.SHIELD_BREAK, 0.8f, 0.8f + mob.getRandom().nextFloat() * 0.4f);
        }
    }
}
