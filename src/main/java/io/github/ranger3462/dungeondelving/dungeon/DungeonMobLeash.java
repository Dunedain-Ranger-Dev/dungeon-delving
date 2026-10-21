package io.github.ranger3462.dungeondelving.dungeon;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Keeps dungeon mobs in the room they spawned in, so they don't wander into tunnels and pile up in other rooms. While
 * a mob has no target it stays within {@link #ROOM_RADIUS} blocks of its room's center, walking back if it's outside.
 * Once it's chasing something, or something hurts it, the leash comes off and it follows anywhere; when it loses its
 * target, it heads home again.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class DungeonMobLeash {
    /** How far from its room's center an idle mob may wander: just inside the walls of a 33-block room. */
    public static final int ROOM_RADIUS = 14;
    /** Saved on dungeon mobs: the center of their home room. */
    public static final String HOME_TAG = DungeonDelving.MODID + ":home";

    private DungeonMobLeash() {}

    /** Whether a mob is one the leash applies to: a walking mob in the dungeon dimension that isn't a boss. */
    public static boolean applies(LivingEntity entity) {
        return entity instanceof PathfinderMob && !entity.level().isClientSide() && entity.level().dimension() == DungeonDimension.DUNGEON
                && !DungeonBosses.isBoss(entity);
    }

    /** The center of a mob's home room, remembering where it first appeared. */
    public static BlockPos home(PathfinderMob mob) {
        if (!mob.getPersistentData().contains(HOME_TAG)) {
            mob.getPersistentData().putLong(HOME_TAG, DungeonDimension.roomCenterAt(mob.blockPosition()).asLong());
        }
        return BlockPos.of(mob.getPersistentData().getLong(HOME_TAG)).above();
    }

    /** Ties a mob to its home room. */
    public static void leash(PathfinderMob mob) {
        mob.restrictTo(home(mob), ROOM_RADIUS);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof PathfinderMob mob) || !applies(mob)) {
            return;
        }
        if (mob.goalSelector.getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof MoveTowardsRestrictionGoal)) {
            mob.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(mob, 1.0));
        }
        if (mob.getTarget() == null) {
            leash(mob);
        }
    }

    /** Chasing something lets a mob leave its room; losing its target sends it home. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof PathfinderMob mob) || !applies(mob)) {
            return;
        }
        if (event.getNewAboutToBeSetTarget() != null) {
            mob.clearRestriction();
        } else {
            leash(mob);
        }
    }

    /** Getting hurt lets a mob fight back, even against an attacker outside its room (e.g. shooting from a tunnel). */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof PathfinderMob mob && applies(mob) && event.getSource().getEntity() instanceof LivingEntity) {
            mob.clearRestriction();
        }
    }
}
