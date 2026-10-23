package io.github.ranger3462.dungeondelving.character;

import java.util.Optional;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The race traits that aren't plain attribute changes (DD-82): a dwarf's darkvision and quick hands on ore, a
 * half-orc's last stand, a dragonborn's breath (the racial ability key), and a gnome's knack for crafting (in
 * CraftingSkills).
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class RaceTraits {
    /** How much faster a dwarf mines ore. */
    public static final float DWARF_ORE_SPEED = 1.25f;
    /** A half-orc's last stand: how often it can save them, in ticks (5 minutes). */
    public static final int LAST_STAND_COOLDOWN = 6000;
    /** A dragonborn's breath: cooldown in ticks, reach in blocks, damage and seconds alight. */
    public static final int BREATH_COOLDOWN = 600;
    public static final double BREATH_RANGE = 6.0;
    public static final float BREATH_DAMAGE = 6.0f;
    public static final int BREATH_FIRE_SECONDS = 4;
    /** Gnomes earn this much more crafting skill XP. */
    public static final double GNOME_CRAFTING_XP = 1.1;
    private static final String LAST_STAND_TAG = DungeonDelving.MODID + ":last_stand";
    private static final String BREATH_TAG = DungeonDelving.MODID + ":breath";

    private RaceTraits() {}

    public static Optional<Race> race(Player player) {
        return Characters.data(player).raceOf();
    }

    /** Dwarves see in the dark underground (anywhere below sea level without sky above). */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0 || race(player).orElse(null) != Race.DWARF) {
            return;
        }
        if (player.getY() < player.level().getSeaLevel() && !player.level().canSeeSky(player.blockPosition())) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
        }
    }

    /** Dwarves mine ore faster. */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getState().is(Tags.Blocks.ORES) && race(event.getEntity()).orElse(null) == Race.DWARF) {
            event.setNewSpeed(event.getNewSpeed() * DWARF_ORE_SPEED);
        }
    }

    /** A half-orc's last stand: once every few minutes, a killing blow leaves them at half a heart instead. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || race(player).orElse(null) != Race.HALF_ORC) {
            return;
        }
        long now = player.level().getGameTime();
        long last = player.getPersistentData().getLong(LAST_STAND_TAG);
        if (last != 0 && now - last < LAST_STAND_COOLDOWN) {
            return;
        }
        event.setCanceled(true);
        player.setHealth(1.0f);
        player.getPersistentData().putLong(LAST_STAND_TAG, now);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 2));
        player.sendSystemMessage(Component.translatable("message.dungeondelving.last_stand").withStyle(ChatFormatting.RED));
        player.level().playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /** The racial ability key: dragonborn breathe fire in a cone ahead. Other races have no active ability yet. */
    public static void useAbility(ServerPlayer player) {
        if (race(player).orElse(null) != Race.DRAGONBORN) {
            player.displayClientMessage(Component.translatable("message.dungeondelving.no_racial_ability").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long now = player.level().getGameTime();
        long last = player.getPersistentData().getLong(BREATH_TAG);
        if (last != 0 && now - last < BREATH_COOLDOWN) {
            long seconds = (BREATH_COOLDOWN - (now - last)) / 20 + 1;
            player.displayClientMessage(Component.translatable("message.dungeondelving.breath_cooldown", seconds).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        player.getPersistentData().putLong(BREATH_TAG, now);
        breathe(player);
    }

    /** Breathes fire: everything in a cone ahead, within {@link #BREATH_RANGE}, is burned. Returns how many were hit. */
    public static int breathe(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        int hit = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(BREATH_RANGE),
                entity -> entity != player && entity.isAlive())) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
            if (to.length() <= BREATH_RANGE && to.normalize().dot(look) > 0.75) {
                target.hurt(player.damageSources().indirectMagic(player, player), BREATH_DAMAGE);
                target.igniteForSeconds(BREATH_FIRE_SECONDS);
                hit++;
            }
        }
        for (int i = 1; i <= 12; i++) {
            Vec3 at = eye.add(look.scale(i * BREATH_RANGE / 12));
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 4, 0.15 * i / 4, 0.15 * i / 4, 0.15 * i / 4, 0.01);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 0.8f);
        return hit;
    }
}
