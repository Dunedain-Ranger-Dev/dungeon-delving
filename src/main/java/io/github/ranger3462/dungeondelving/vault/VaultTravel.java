package io.github.ranger3462.dungeondelving.vault;

import java.util.Optional;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Moves players into and out of vault runs. */
public final class VaultTravel {
    /** Placed at the center of every new region until the grid generator (DD-13) builds full vaults. */
    public static final ResourceLocation START_ROOM = DungeonDelving.id("vault/start_placeholder");

    private VaultTravel() {}

    /** Starts a run in a fresh region and sends the player into its start room. */
    public static VaultRun enter(ServerPlayer player) {
        ServerLevel vault = vaultLevel(player);
        VaultRegions regions = VaultRegions.get();
        int region = regions.allocate();

        BlockPos center = VaultDimension.regionCenter(region);
        BlockPos roomCorner = center.offset(-RoomSpec.ROOM_SIZE / 2, 0, -RoomSpec.ROOM_SIZE / 2);
        StructureTemplate room = vault.getStructureManager().get(START_ROOM)
                .orElseThrow(() -> new IllegalStateException("Missing structure " + START_ROOM));
        room.placeInWorld(vault, roomCorner, roomCorner, new StructurePlaceSettings(), vault.getRandom(), Block.UPDATE_CLIENTS);

        VaultRun run = new VaultRun(player.getUUID(), region, player.level().dimension(), player.getX(), player.getY(),
                player.getZ(), player.getYRot(), player.getXRot());
        regions.startRun(run);
        player.teleportTo(vault, center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5, player.getYRot(), player.getXRot());
        return run;
    }

    /** Ends the player's run and sends them back to where they entered from. */
    public static Optional<VaultRun> leave(ServerPlayer player) {
        Optional<VaultRun> ended = VaultRegions.get().endRun(player.getUUID());
        ended.ifPresent(run -> {
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
