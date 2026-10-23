package io.github.ranger3462.dungeondelving.gate;

import java.util.List;

import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces.Kind;
import net.minecraft.resources.ResourceLocation;

/** The dungeon stones tuned into a run, and what they change together. Their effects multiply. */
public record RunTuning(List<DungeonStone> stones) {
    public static final RunTuning NONE = new RunTuning(List.of());

    public RunTuning {
        stones = List.copyOf(stones);
    }

    public boolean isEmpty() {
        return stones.isEmpty();
    }

    /** How likely a piece is to be picked from its pool with these stones (1 is unchanged). */
    public double pieceWeight(Kind kind, ResourceLocation piece) {
        double weight = 1.0;
        for (DungeonStone stone : stones) {
            weight *= stone.pieceWeight(kind, piece);
        }
        return weight;
    }
}
