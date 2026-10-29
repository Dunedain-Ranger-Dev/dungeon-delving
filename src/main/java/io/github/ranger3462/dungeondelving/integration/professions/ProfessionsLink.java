package io.github.ranger3462.dungeondelving.integration.professions;

import java.util.OptionalInt;

import io.github.ranger3462.ddprofessions.character.MobLevels;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/**
 * Dungeon Delving with Professions and Skills (only loaded when it's installed): mobs in a dungeon have their tier's
 * levels, which scale skill experience. Tier 1 is levels 1 to 10, tier 2 is 11 to 20 and so on, with bosses at the top
 * of their band. Its other links are data: our metals fill its tier metal tags, and its borax turns up in our dungeons.
 */
public final class ProfessionsLink {
    /** Levels per dungeon tier. */
    public static final int TIER_BAND = 10;

    private ProfessionsLink() {}

    public static void register() {
        MobLevels.addSource(ProfessionsLink::dungeonLevel);
    }

    /** A dungeon mob's level: in its tier's band, by its stats within it. Empty outside a dungeon run. */
    public static OptionalInt dungeonLevel(Mob mob, int fromStats) {
        if (mob.level().dimension() != DungeonDimension.DUNGEON) {
            return OptionalInt.empty();
        }
        return DungeonRegions.get().progress(DungeonDimension.regionAt(mob.blockPosition())).map(progress -> {
            int floor = (progress.tier() - 1) * TIER_BAND;
            return OptionalInt.of(DungeonBosses.isBoss(mob) ? floor + TIER_BAND : floor + Mth.clamp(fromStats, 1, TIER_BAND - 1));
        }).orElse(OptionalInt.empty());
    }
}
