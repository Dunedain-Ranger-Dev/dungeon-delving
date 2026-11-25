package io.github.ranger3462.dungeondelving.gametest;

import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.dungeon.challenge.PuzzleBosses;
import net.minecraft.util.RandomSource;

/** For tests: picks a faction's own boss for a tier, leaving out the puzzle bosses that can turn up in its place. */
final class BossPicks {
    private BossPicks() {}

    static String own(int tier, String faction, RandomSource random) {
        String boss = DungeonBosses.pick(tier, faction, random);
        for (int i = 0; i < 100 && PuzzleBosses.isPuzzleBoss(boss); i++) {
            boss = DungeonBosses.pick(tier, faction, random);
        }
        return boss;
    }
}
