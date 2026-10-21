package io.github.ranger3462.dungeondelving.dungeon.challenge;

import java.util.Optional;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRun;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The path to the boss: hunt targets in challenge rooms, the Dungeon Keys they drop, and the boss door.
 * <ul>
 * <li>Hunt markers become tougher versions of dungeon mobs. They aren't highlighted (a skill may reveal them later).
 * Killing every hunt target in a room drops one Dungeon Key.</li>
 * <li>The boss door needs {@link DungeonProgress#keysFor tier x 2} keys. Using the last one opens the door and spawns
 * the boss.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class DungeonChallenges {
    /** Saved on hunt targets: which region and room they belong to. */
    public static final String HUNT_TAG = DungeonDelving.MODID + ":hunt";
    /** How much tougher hunt targets are than ordinary mobs. */
    private static final double HUNTER_HEALTH = 2.0;
    private static final double HUNTER_DAMAGE = 2.0;

    private DungeonChallenges() {}

    // --- Hunts ---

    /** Spawns a hunt target for a challenge room. Untracked (no key) if the region has no progress, e.g. in tests. */
    public static Optional<Mob> spawnHunter(ServerLevel level, BlockPos pos, RandomSource random, int region, long room) {
        Optional<Holder<EntityType<?>>> type = DungeonMarkers.pickMob(level, random, Config.COMMON_MOB_CHANCE.get());
        if (type.isEmpty() || !(type.get().value().create(level) instanceof Mob mob)) {
            return Optional.empty();
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0f, 0.0f);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * HUNTER_HEALTH);
        }
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() + HUNTER_DAMAGE);
        }
        mob.setHealth(mob.getMaxHealth());
        mob.setPersistenceRequired();
        mob.setCustomName(Component.translatable("entity.dungeondelving.hunt_target", mob.getType().getDescription()));
        mob.setCustomNameVisible(false);
        CompoundTag hunt = new CompoundTag();
        hunt.putInt("region", region);
        hunt.putLong("room", room);
        mob.getPersistentData().put(HUNT_TAG, hunt);
        level.addFreshEntity(mob);
        DungeonRegions.get().progress(region).ifPresent(progress -> {
            progress.addHunter(room);
            DungeonRegions.get().progressChanged();
        });
        return Optional.of(mob);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || !event.getEntity().getPersistentData().contains(HUNT_TAG)) {
            return;
        }
        CompoundTag hunt = event.getEntity().getPersistentData().getCompound(HUNT_TAG);
        int region = hunt.getInt("region");
        DungeonRegions.get().progress(region).flatMap(progress -> progress.hunterKilled(hunt.getLong("room"))).ifPresent(left -> {
            DungeonRegions.get().progressChanged();
            if (left > 0) {
                tellRegion(level, region, Component.translatable("message.dungeondelving.hunt_left", left).withStyle(ChatFormatting.GRAY));
                return;
            }
            ItemEntity key = new ItemEntity(level, event.getEntity().getX(), event.getEntity().getY() + 0.5, event.getEntity().getZ(),
                    DungeonKeyItem.forRegion(region));
            key.setUnlimitedLifetime();
            key.setGlowingTag(true);
            level.addFreshEntity(key);
            level.playSound(null, event.getEntity().blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
            tellRegion(level, region, Component.translatable("message.dungeondelving.key_dropped").withStyle(ChatFormatting.GOLD));
        });
    }

    // --- The boss door ---

    /** A player using the boss door, holding a stack (possibly empty). */
    public static void useDoor(ServerPlayer player, ItemStack held) {
        Optional<DungeonRun> run = DungeonRegions.get().run(player.getUUID());
        Optional<DungeonProgress> found = run.flatMap(r -> DungeonRegions.get().progress(r.region()));
        if (found.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.dungeondelving.door_sealed").withStyle(ChatFormatting.RED), true);
            return;
        }
        int region = run.get().region();
        DungeonProgress progress = found.get();
        if (held.is(ModRegistries.DUNGEON_KEY.get()) && !DungeonKeyItem.opens(held, region)) {
            held.setCount(0);
            player.sendSystemMessage(Component.translatable("message.dungeondelving.key_crumbles").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!DungeonKeyItem.opens(held, region) || !progress.useKey()) {
            player.displayClientMessage(Component.translatable("message.dungeondelving.door_needs", progress.keysUsed(), progress.keysNeeded())
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        held.shrink(1);
        DungeonRegions.get().progressChanged();
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 0.6f);
        if (!progress.doorOpen()) {
            tellRegion(level, region, Component.translatable("message.dungeondelving.key_used", progress.keysUsed(), progress.keysNeeded())
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        openDoor(level, region, progress);
    }

    /** Removes the boss door and spawns the boss. */
    public static void openDoor(ServerLevel level, int region, DungeonProgress progress) {
        for (BlockPos pos : progress.door()) {
            if (level.getBlockState(pos).is(ModRegistries.BOSS_DOOR.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        if (!progress.bossSpawned() && progress.bossSpawn() != null) {
            DungeonBosses.spawn(level, progress.bossSpawn(), progress.tier(), region);
            progress.markBossSpawned();
            DungeonRegions.get().progressChanged();
            level.playSound(null, progress.bossSpawn(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 0.8f);
        }
        tellRegion(level, region, Component.translatable("message.dungeondelving.door_open").withStyle(ChatFormatting.DARK_RED));
    }

    /** Sends a message to everyone running the dungeon in this region. */
    public static void tellRegion(ServerLevel level, int region, Component message) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension() == DungeonDimension.DUNGEON
                    && DungeonRegions.get().run(player.getUUID()).map(run -> run.region() == region).orElse(false)) {
                player.sendSystemMessage(message);
            }
        }
    }
}
