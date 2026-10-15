package io.github.ranger3462.dungeondelving.vault;

import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.vault.layout.VaultBuildQueue;
import io.github.ranger3462.dungeondelving.vault.layout.VaultBuilder;
import io.github.ranger3462.dungeondelving.vault.layout.VaultBuilder.Placement;
import io.github.ranger3462.dungeondelving.vault.layout.VaultLayout;
import io.github.ranger3462.dungeondelving.vault.layout.VaultLayoutGenerator;
import io.github.ranger3462.dungeondelving.vault.layout.VaultPieces;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Moves players into and out of vault runs. */
public final class VaultTravel {
    private VaultTravel() {}

    /** The result of entering a vault: the run, and the layout being built for it. */
    public record Entry(VaultRun run, VaultLayout layout) {}

    /**
     * Starts a run in a fresh region: generates a layout, places the start room right away, queues the rest to be
     * built over the next few ticks, and sends the player into the start room.
     */
    public static Entry enter(ServerPlayer player, int roomCount) {
        ServerLevel vault = vaultLevel(player);
        VaultRegions regions = VaultRegions.get();
        int region = regions.allocate();
        BlockPos center = VaultDimension.regionCenter(region);

        VaultLayout layout = VaultLayoutGenerator.generate(vault.getRandom(), roomCount);
        VaultPieces pieces = VaultPieces.load(player.server.getResourceManager());
        List<Placement> placements = VaultBuilder.plan(layout, pieces, center, vault.getRandom());
        VaultBuilder.place(vault, placements.getFirst());
        VaultBuildQueue.add(region, placements.subList(1, placements.size()));

        VaultRun run = new VaultRun(player.getUUID(), region, player.level().dimension(), player.getX(), player.getY(),
                player.getZ(), player.getYRot(), player.getXRot());
        regions.startRun(run);
        player.teleportTo(vault, center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5, player.getYRot(), player.getXRot());
        return new Entry(run, layout);
    }

    /** Ends the player's run and sends them back to where they entered from. */
    public static Optional<VaultRun> leave(ServerPlayer player) {
        Optional<VaultRun> ended = VaultRegions.get().endRun(player.getUUID());
        ended.ifPresent(run -> {
            if (VaultRegions.get().runs().stream().noneMatch(other -> other.region() == run.region())) {
                VaultBuildQueue.cancel(run.region());
            }
            ServerLevel target = player.server.getLevel(run.returnLevel());
            if (target == null) {
                target = player.server.overworld();
            }
            player.teleportTo(target, run.returnX(), run.returnY(), run.returnZ(), run.returnYRot(), run.returnXRot());
        });
        return ended;
    }

    private static ServerLevel vaultLevel(ServerPlayer player) {
        ServerLevel vault = player.server.getLevel(VaultDimension.VAULT);
        if (vault == null) {
            throw new IllegalStateException("The vault dimension is missing");
        }
        return vault;
    }
}
