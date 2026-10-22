package io.github.ranger3462.dungeondelving.integration.apotheosis;

import java.util.Optional;

import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.mobs.registries.InvaderRegistry;
import dev.shadowsoffire.apotheosis.mobs.types.Invader;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRun;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Ties Apotheosis to dungeons, so that dungeon runs are where its progression happens:
 * <ul>
 * <li><b>World tiers follow dungeon tiers (DD-32).</b> Outside a dungeon every player is in Haven; in a tier 1 to 4
 * dungeon they're in Frontier, Ascent, Summit or Pinnacle. This mod is the only thing that sets tiers: a tier picked
 * any other way is put back within a second.</li>
 * <li><b>Affix gear and gems only come from dungeons (DD-30).</b> The pack's data keeps Apotheosis's loot modifiers to
 * the dungeon dimension; on top of that, mobs outside dungeons never keep affix gear, and no affix gear or gems drop
 * from anything killed outside one.</li>
 * <li><b>Invaders come from dungeons (DD-31).</b> Apotheosis only turns natural spawns into invaders, and dungeons have
 * none, so dungeon spawners sometimes call one in instead ({@link #INVADER_CHANCE}). The pack's data turns natural
 * invaders off everywhere and keeps invaders and elites to the dungeon dimension.</li>
 * </ul>
 * Only loaded when Apotheosis is installed, so nothing outside this package may refer to these classes directly.
 */
public final class ApotheosisDungeons {
    /** The chance a dungeon spawner calls in an Apotheosis invader instead of its mob, by dungeon tier (index 0 is tier 1). */
    public static final float[] INVADER_CHANCE = { 0.01f, 0.015f, 0.02f, 0.025f };
    /** How often players' world tiers are checked, in ticks. */
    private static final int CHECK_INTERVAL = 20;

    private ApotheosisDungeons() {}

    /** Called from the mod constructor when Apotheosis is loaded. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ApotheosisDungeons::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ApotheosisDungeons::onLogin);
        NeoForge.EVENT_BUS.addListener(ApotheosisDungeons::onChangedDimension);
        // After Apotheosis has dressed the mob (its own handler runs at LOW).
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ApotheosisDungeons::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ApotheosisDungeons::onDrops);
    }

    // --- World tiers ---

    /** The Apotheosis tier for a dungeon tier: Frontier for tier 1, up to Pinnacle for tier 4. */
    public static WorldTier tierFor(int dungeonTier) {
        return switch (DungeonRun.clampTier(dungeonTier)) {
            case 1 -> WorldTier.FRONTIER;
            case 2 -> WorldTier.ASCENT;
            case 3 -> WorldTier.SUMMIT;
            default -> WorldTier.PINNACLE;
        };
    }

    /** The tier a player should be in right now: their dungeon's, or Haven when they're not in one. */
    public static WorldTier expectedTier(Player player) {
        if (player.level().dimension() != DungeonDimension.DUNGEON) {
            return WorldTier.HAVEN;
        }
        return DungeonRegions.get().run(player.getUUID()).map(run -> tierFor(run.tier())).orElse(WorldTier.HAVEN);
    }

    /** Puts a player in the tier they should be in, if they aren't already. */
    public static void syncTier(ServerPlayer player) {
        WorldTier expected = expectedTier(player);
        if (WorldTier.getTier(player) != expected || WorldTier.isTutorialActive(player)) {
            WorldTier.setTier(player, expected);
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % CHECK_INTERVAL == 0) {
            syncTier(player);
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTier(player);
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncTier(player);
        }
    }

    // --- Affix gear and gems ---

    /** Whether a stack is affix gear or a gem: what only dungeons give. */
    public static boolean isApotheosisLoot(ItemStack stack) {
        return stack.getItem() instanceof GemItem || AffixHelper.hasAffixes(stack);
    }

    private static boolean inDungeon(Level level) {
        return level.dimension() == DungeonDimension.DUNGEON;
    }

    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.isSpawnCancelled()) {
            return;
        }
        Mob mob = event.getEntity();
        ServerLevel level = event.getLevel().getLevel();
        if (!inDungeon(level)) {
            stripAffixGear(mob);
        } else if (event.getSpawnType() == MobSpawnType.SPAWNER && mob instanceof Monster && !DungeonBosses.isBoss(mob)) {
            tryInvader(event, mob, level);
        }
    }

    /** Takes affix gear off a mob outside a dungeon, leaving it plain. */
    public static void stripAffixGear(Mob mob) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (isApotheosisLoot(mob.getItemBySlot(slot))) {
                mob.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    /** No affix gear or gems drop from anything killed outside a dungeon. */
    private static void onDrops(LivingDropsEvent event) {
        if (!inDungeon(event.getEntity().level())) {
            event.getDrops().removeIf(drop -> isApotheosisLoot(drop.getItem()));
        }
    }

    // --- Invaders ---

    /** Sometimes swaps a dungeon spawner's mob for an Apotheosis invader of the dungeon's tier. */
    private static void tryInvader(FinalizeSpawnEvent event, Mob mob, ServerLevel level) {
        BlockPos pos = mob.blockPosition();
        Optional<Integer> tier = DungeonRegions.get().progress(DungeonDimension.regionAt(pos)).map(progress -> progress.tier());
        if (tier.isEmpty() || level.getRandom().nextFloat() >= INVADER_CHANCE[DungeonRun.clampTier(tier.get()) - DungeonRun.MIN_TIER]) {
            return;
        }
        Player player = level.getNearestPlayer(mob, -1.0);
        if (player != null && spawnInvader(level, pos, player)) {
            event.setSpawnCancelled(true);
        }
    }

    /** Calls in an Apotheosis invader at a position, for the player's tier. False if none could come (e.g. none are open to dungeons). */
    public static boolean spawnInvader(ServerLevel level, BlockPos pos, Player player) {
        GenContext context = GenContext.forPlayerAtPos(level.getRandom(), player, pos);
        Invader invader = InvaderRegistry.INSTANCE.getRandomItem(context);
        if (invader == null) {
            return false;
        }
        Mob boss = invader.createBoss(level, pos, context);
        boss.setTarget(player.isCreative() ? null : player);
        level.addFreshEntityWithPassengers(boss);
        return true;
    }
}
