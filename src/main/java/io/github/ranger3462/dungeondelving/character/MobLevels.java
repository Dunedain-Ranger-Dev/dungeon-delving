package io.github.ranger3462.dungeondelving.character;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Mob levels: how strong a mob is, shown on its nameplate and used to scale skill experience, so a fire master
 * learns nothing from burning a level 1 zombie. In a dungeon a mob's level sits in its tier's band (tier 1 is
 * levels 1 to 10, tier 2 is 11 to 20, and so on, bosses at the top of the band); anywhere else it comes from the mob's
 * own stats, so modded mobs get one with no setup. Worked out once and kept on the mob.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class MobLevels {
    private static final String LEVEL_TAG = DungeonDelving.MODID + ":level";
    /** Levels per dungeon tier. */
    public static final int TIER_BAND = 10;
    /** Skill levels a mob can be below yours before experience starts dropping off. */
    public static final int FULL_XP_GAP = 5;
    /** Each level below that takes off this much of the experience... */
    public static final double FALLOFF_PER_LEVEL = 0.1;
    /** ...down to this trickle. */
    public static final double MIN_MULTIPLIER = 0.05;

    private MobLevels() {}

    /** A mob's level (players and non-mobs are 0, as they have none). */
    public static int level(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            return 0;
        }
        var data = mob.getPersistentData();
        if (data.contains(LEVEL_TAG)) {
            return data.getInt(LEVEL_TAG);
        }
        int level = compute(mob);
        if (!mob.level().isClientSide()) {
            data.putInt(LEVEL_TAG, level);
        }
        return level;
    }

    private static int compute(Mob mob) {
        int fromStats = fromStats(mob);
        if (mob.level().isClientSide() || mob.level().dimension() != DungeonDimension.DUNGEON) {
            return fromStats;
        }
        return DungeonRegions.get().progress(DungeonDimension.regionAt(mob.blockPosition())).map(progress -> {
            int floor = (progress.tier() - 1) * TIER_BAND;
            return DungeonBosses.isBoss(mob) ? floor + TIER_BAND : floor + Mth.clamp(fromStats, 1, TIER_BAND - 1);
        }).orElse(fromStats);
    }

    /**
     * A level from how tough a mob is: health, plus four per point of attack damage and two per point of armor, a
     * level per 12 (a zombie is about level 3, an iron golem 13, the Wither around 30).
     */
    public static int fromStats(LivingEntity entity) {
        double attack = entity.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? entity.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
        double power = entity.getMaxHealth() + 4 * attack + 2 * entity.getArmorValue();
        return Math.max(1, (int) Math.round(power / 12.0));
    }

    /**
     * How much experience a skill gets from a mob: all of it if the mob is no more than {@link #FULL_XP_GAP} levels
     * below the skill, then 10% less per level past that, down to {@link #MIN_MULTIPLIER}. Skills count from 0, so
     * a new skill always gets full experience.
     */
    public static double xpMultiplier(int skillLevel, int mobLevel) {
        int gap = skillLevel - mobLevel - FULL_XP_GAP;
        return gap <= 0 ? 1.0 : Math.max(MIN_MULTIPLIER, 1.0 - gap * FALLOFF_PER_LEVEL);
    }

    /**
     * Works a mob's level out again from its stats now (after they've been changed: a realm guard kitted out, a monster
     * levelled by its area), and tells the players who can see it.
     */
    public static int refresh(Mob mob) {
        mob.getPersistentData().remove(LEVEL_TAG);
        int level = level(mob);
        if (!mob.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntity(mob, new MobLevelPayload(mob.getId(), level));
        }
        return level;
    }

    /** Tells a player's client a mob's level when it comes into view, for its nameplate. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof Mob mob) {
            PacketDistributor.sendToPlayer(player, new MobLevelPayload(mob.getId(), level(mob)));
        }
    }
}
