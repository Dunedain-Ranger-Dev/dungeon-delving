package io.github.ranger3462.dungeondelving.gate;

import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces.Kind;
import net.minecraft.resources.ResourceLocation;

/**
 * What a dungeon stone does to the run it's tuned into. Each stone is crafted from a fitting Iron's Spells item, and is
 * used up when the run starts. Several stones (in several Gate Tuners) stack.
 */
public enum DungeonStone {
    /** Blood Vial: challenge rooms lean toward fighting, wave defense and hunts. */
    BLOODSHED("bloodshed") {
        @Override
        public double pieceWeight(Kind kind, ResourceLocation piece) {
            if (kind != Kind.CHALLENGE) {
                return 1.0;
            }
            String path = piece.getPath();
            return path.contains("hunt") || path.contains("wave") ? FAVORED : 1.0;
        }
    };

    /** How much more often a stone's favored rooms are picked. */
    public static final double FAVORED = 4.0;

    private final String id;

    DungeonStone(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /**
     * How likely a piece of this kind is to be picked, relative to the others in its pool (1 is unchanged). Pieces are
     * told apart by name, e.g. {@code challenge_waves_*} for wave defense rooms.
     */
    public double pieceWeight(Kind kind, ResourceLocation piece) {
        return 1.0;
    }
}
