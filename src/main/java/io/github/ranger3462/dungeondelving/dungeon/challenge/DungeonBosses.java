package io.github.ranger3462.dungeondelving.dungeon.challenge;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRun;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dungeon bosses. For now there is one placeholder boss, the Barrow King (a big, buffed wither skeleton), until real
 * bosses are picked. Each boss drops its own loot table, {@code dungeondelving:bosses/<boss>/tier_<n>}, in place of
 * its normal drops, and shows a boss bar to players nearby.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class DungeonBosses {
    /** Saved on bosses: which boss it is, its tier and region. */
    public static final String BOSS_TAG = DungeonDelving.MODID + ":boss";
    public static final String BARROW_KING = "barrow_king";
    /** How close players must be to see a boss's health bar. */
    private static final double BAR_RANGE = 48.0;

    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private DungeonBosses() {}

    /** The loot table a boss drops in a dungeon of this tier. */
    public static ResourceKey<LootTable> lootTable(String boss, int tier) {
        return ResourceKey.create(Registries.LOOT_TABLE, DungeonDelving.id("bosses/" + boss + "/tier_" + DungeonRun.clampTier(tier)));
    }

    /** Spawns the boss for a dungeon of this tier. */
    public static LivingEntity spawn(ServerLevel level, BlockPos pos, int tier, int region) {
        WitherSkeleton king = EntityType.WITHER_SKELETON.create(level);
        king.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
        EventHooks.finalizeMobSpawn(king, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        setBase(king, Attributes.MAX_HEALTH, 50.0 + 50.0 * tier);
        setBase(king, Attributes.ATTACK_DAMAGE, 6.0 + 2.0 * tier);
        setBase(king, Attributes.ARMOR, 4.0 + 2.0 * tier);
        setBase(king, Attributes.KNOCKBACK_RESISTANCE, 0.6);
        setBase(king, Attributes.SCALE, 1.4);
        setBase(king, Attributes.FOLLOW_RANGE, 48.0);
        king.setHealth(king.getMaxHealth());
        king.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(tier >= 3 ? Items.NETHERITE_SWORD : Items.DIAMOND_SWORD));
        king.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            king.setDropChance(slot, 0.0f);
        }
        king.setCustomName(Component.translatable("entity.dungeondelving.barrow_king").withStyle(ChatFormatting.GOLD));
        king.setCustomNameVisible(true);
        king.setPersistenceRequired();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", BARROW_KING);
        tag.putInt("tier", tier);
        tag.putInt("region", region);
        king.getPersistentData().put(BOSS_TAG, tag);
        level.addFreshEntity(king);
        return king;
    }

    private static void setBase(LivingEntity entity, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public static boolean isBoss(Entity entity) {
        return entity.getPersistentData().contains(BOSS_TAG);
    }

    /** Replaces a boss's drops with its own loot table. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity boss = event.getEntity();
        if (!(boss.level() instanceof ServerLevel level) || !isBoss(boss)) {
            return;
        }
        CompoundTag tag = boss.getPersistentData().getCompound(BOSS_TAG);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(lootTable(tag.getString("id"), tag.getInt("tier")));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, boss)
                .withParameter(LootContextParams.ORIGIN, boss.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource())
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, event.getSource().getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, event.getSource().getDirectEntity())
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER,
                        event.getSource().getEntity() instanceof ServerPlayer player ? player : null)
                .create(LootContextParamSets.ENTITY);
        event.getDrops().clear();
        for (ItemStack stack : table.getRandomItems(params)) {
            ItemEntity drop = new ItemEntity(level, boss.getX(), boss.getY() + 0.5, boss.getZ(), stack);
            drop.setUnlimitedLifetime();
            event.getDrops().add(drop);
        }
        DungeonChallenges.tellRegion(level, tag.getInt("region"),
                Component.translatable("message.dungeondelving.boss_defeated", boss.getDisplayName()).withStyle(ChatFormatting.GOLD));
    }

    // --- Boss bars ---

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity boss && isBoss(boss)) {
            BARS.computeIfAbsent(boss.getUUID(),
                    id -> new ServerBossEvent(boss.getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (BARS.isEmpty() || event.getServer().getTickCount() % 5 != 0) {
            return;
        }
        ServerLevel dungeon = event.getServer().getLevel(DungeonDimension.DUNGEON);
        Iterator<Map.Entry<UUID, ServerBossEvent>> bars = BARS.entrySet().iterator();
        while (bars.hasNext()) {
            Map.Entry<UUID, ServerBossEvent> entry = bars.next();
            ServerBossEvent bar = entry.getValue();
            if (dungeon == null || !(dungeon.getEntity(entry.getKey()) instanceof LivingEntity boss) || !boss.isAlive()) {
                bar.removeAllPlayers();
                bars.remove();
                continue;
            }
            bar.setProgress(boss.getHealth() / boss.getMaxHealth());
            for (ServerPlayer player : dungeon.players()) {
                if (player.distanceToSqr(boss) <= BAR_RANGE * BAR_RANGE) {
                    bar.addPlayer(player);
                } else {
                    bar.removePlayer(player);
                }
            }
        }
    }
}
