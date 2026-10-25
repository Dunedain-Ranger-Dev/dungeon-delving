package io.github.ranger3462.dungeondelving.forging;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A profession's workshop around a Guild Ledger: which of its stations are in range ({@link ForgeWorkshop#RADIUS}
 * across, {@link ForgeWorkshop#HEIGHT} up or down) and what they add up to. Like a forge, it's level 1 with every
 * basic station, 2 with the first upgrade and 3 with both, capping the best ★ made there at 3, 5 and 7; and its own
 * decoration widens its sweet spots a little.
 */
public record CraftWorkshop(Profession profession, BlockPos ledger, Set<String> stations, int decor) {
    /** Looks over everything in range of a ledger, for one profession. */
    public static CraftWorkshop scan(Level level, BlockPos ledger, Profession profession) {
        Set<String> stations = new HashSet<>();
        int decor = 0;
        int radius = ForgeWorkshop.RADIUS;
        int height = ForgeWorkshop.HEIGHT;
        for (BlockPos pos : BlockPos.betweenClosed(ledger.offset(-radius, -height, -radius), ledger.offset(radius, height, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            stations.addAll(profession.stations(state));
            if (state.is(profession.decor)) {
                decor++;
            }
        }
        return new CraftWorkshop(profession, ledger.immutable(), stations, decor);
    }

    /** The complete workshop a station belongs to: the best one among the ledgers in range of it. */
    public static Optional<CraftWorkshop> around(Level level, BlockPos station, Profession profession) {
        CraftWorkshop best = null;
        int radius = ForgeWorkshop.RADIUS;
        int height = ForgeWorkshop.HEIGHT;
        for (BlockPos pos : BlockPos.betweenClosed(station.offset(-radius, -height, -radius), station.offset(radius, height, radius))) {
            if (level.getBlockState(pos).is(ModRegistries.GUILD_LEDGER.get())) {
                CraftWorkshop workshop = scan(level, pos, profession);
                if (workshop.complete() && (best == null || workshop.level() > best.level())) {
                    best = workshop;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public boolean complete() {
        return stations.containsAll(profession.required);
    }

    /** The basic stations still missing. */
    public List<Component> missing() {
        List<Component> missing = new ArrayList<>();
        for (String id : profession.required) {
            if (!stations.contains(id)) {
                missing.add(Component.translatable("craft.dungeondelving.station." + id));
            }
        }
        return missing;
    }

    /** 0 if incomplete; 1 with the basic stations; 2 with the first upgrade; 3 with both. */
    public int level() {
        if (!complete()) {
            return 0;
        }
        if (stations.contains(profession.upgrade2)) {
            return stations.contains(profession.upgrade3) ? 3 : 2;
        }
        return 1;
    }

    /** The best ★ this workshop can make. */
    public int maxStars() {
        return switch (level()) {
            case 3 -> Forging.MAX_STARS;
            case 2 -> 5;
            case 1 -> 3;
            default -> 0;
        };
    }

    /** Extra sweet-spot width from decoration, the same rate as a forge's. */
    public float decorBonus() {
        return Math.min(ForgeWorkshop.MAX_DECOR_BONUS, 0.005f * (decor / ForgeWorkshop.DECOR_PER_BONUS));
    }
}
