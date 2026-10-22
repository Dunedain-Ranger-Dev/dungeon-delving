package io.github.ranger3462.dungeondelving.forging;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Forge Hearth's workshop: the stations within its range and what they add up to. Stations can be placed any way
 * the player likes, as long as they're within {@link #RADIUS} blocks across and {@link #HEIGHT} up or down.
 *
 * @param bellows a Bellows next to one of the workshop's furnaces
 * @param decor how many decoration blocks ({@link Forging#FORGE_DECOR}) are in range
 */
public record ForgeWorkshop(BlockPos hearth, boolean furnace, boolean blastFurnace, boolean bellows, boolean anvil, boolean dwarvenAnvil,
        boolean waterCauldron, boolean quenchTub, boolean grindstone, boolean grindingWheel, int decor) {
    public static final int RADIUS = 7;
    public static final int HEIGHT = 4;
    /** Decoration blocks for each +0.5% of sweet-spot width, and the most that can add. */
    public static final int DECOR_PER_BONUS = 8;
    public static final float MAX_DECOR_BONUS = 0.025f;

    /** Looks over everything in range of a hearth. */
    public static ForgeWorkshop scan(Level level, BlockPos hearth) {
        boolean furnace = false, blastFurnace = false, bellows = false, anvil = false, dwarvenAnvil = false, waterCauldron = false,
                quenchTub = false, grindstone = false, grindingWheel = false;
        int decor = 0;
        for (BlockPos pos : BlockPos.betweenClosed(hearth.offset(-RADIUS, -HEIGHT, -RADIUS), hearth.offset(RADIUS, HEIGHT, RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE)) {
                furnace = true;
                blastFurnace |= state.is(Blocks.BLAST_FURNACE);
            } else if (state.is(Forging.BELLOWS.get())) {
                bellows |= besideFurnace(level, pos);
            } else if (state.is(BlockTags.ANVIL)) {
                anvil = true;
            } else if (state.is(Forging.DWARVEN_ANVIL.get())) {
                dwarvenAnvil = true;
            } else if (state.is(Blocks.WATER_CAULDRON) || state.is(Blocks.CAULDRON)) {
                waterCauldron = true; // An empty cauldron still counts: it just needs refilling before the next quench.
            } else if (state.is(Forging.QUENCH_TUB.get())) {
                quenchTub = true;
            } else if (state.is(Blocks.GRINDSTONE)) {
                grindstone = true;
            } else if (state.is(Forging.GRINDING_WHEEL.get())) {
                grindingWheel = true;
            }
            if (state.is(Forging.FORGE_DECOR)) {
                decor++;
            }
        }
        return new ForgeWorkshop(hearth.immutable(), furnace, blastFurnace, bellows, anvil, dwarvenAnvil, waterCauldron, quenchTub, grindstone,
                grindingWheel, decor);
    }

    private static boolean besideFurnace(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockState next = level.getBlockState(pos.relative(direction));
            if (next.is(Blocks.FURNACE) || next.is(Blocks.BLAST_FURNACE)) {
                return true;
            }
        }
        return false;
    }

    /** The complete workshop a station belongs to: the best one among the hearths in range of it. */
    public static Optional<ForgeWorkshop> around(Level level, BlockPos station) {
        ForgeWorkshop best = null;
        for (BlockPos pos : BlockPos.betweenClosed(station.offset(-RADIUS, -HEIGHT, -RADIUS), station.offset(RADIUS, HEIGHT, RADIUS))) {
            if (level.getBlockState(pos).is(Forging.FORGE_HEARTH.get())) {
                ForgeWorkshop workshop = scan(level, pos);
                if (workshop.complete() && (best == null || workshop.forgeLevel() > best.forgeLevel())) {
                    best = workshop;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Whether the workshop has a station of each kind: heat, strike, quench and grind. */
    public boolean complete() {
        return missing().isEmpty();
    }

    /** The kinds of station the workshop still needs. */
    public List<Component> missing() {
        List<Component> missing = new ArrayList<>();
        if (!furnace) {
            missing.add(Component.translatable("forge.dungeondelving.station.furnace"));
        }
        if (!anvil && !dwarvenAnvil) {
            missing.add(Component.translatable("forge.dungeondelving.station.anvil"));
        }
        if (!waterCauldron && !quenchTub) {
            missing.add(Component.translatable("forge.dungeondelving.station.quench"));
        }
        if (!grindstone && !grindingWheel) {
            missing.add(Component.translatable("forge.dungeondelving.station.grindstone"));
        }
        return missing;
    }

    /** 0 if incomplete; 1 with the basic stations; 2 with a Blast Furnace and Dwarven Anvil; 3 with every upgrade. */
    public int forgeLevel() {
        if (!complete()) {
            return 0;
        }
        if (blastFurnace && dwarvenAnvil) {
            return bellows && quenchTub && grindingWheel ? 3 : 2;
        }
        return 1;
    }

    /** The highest ★ this workshop can forge. */
    public int maxStars() {
        return switch (forgeLevel()) {
            case 3 -> Forging.MAX_STARS;
            case 2 -> 5;
            case 1 -> 3;
            default -> 0;
        };
    }

    /** Extra sweet-spot width from decoration: +0.5% per {@link #DECOR_PER_BONUS} blocks, up to {@link #MAX_DECOR_BONUS}. */
    public float decorBonus() {
        return Math.min(MAX_DECOR_BONUS, 0.005f * (decor / DECOR_PER_BONUS));
    }
}
