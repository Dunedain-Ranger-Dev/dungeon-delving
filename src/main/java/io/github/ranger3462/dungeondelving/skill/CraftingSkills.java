package io.github.ranger3462.dungeondelving.skill;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.character.SkillLevels;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Each player's XP in the crafting skills (Forging, Leatherworking, Weaving). Levels run from 1 to {@link #MAX_LEVEL};
 * reaching level L takes {@code 20 * (L - 1)^2} XP in total. XP is kept through death. For now each skill's perks
 * unlock at set levels (see e.g. {@code ForgingPerks}); a talent tree comes later (DD-70).
 */
public final class CraftingSkills {
    public static final int MAX_LEVEL = 30;
    private static final int XP_SCALE = 20;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
            DungeonDelving.MODID);

    private static final Codec<Map<String, Integer>> XP_CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT);

    /** XP per skill id. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Map<String, Integer>>> XP = ATTACHMENTS.register("craft_skill_xp",
            () -> AttachmentType.<Map<String, Integer>>builder(() -> new HashMap<>()).serialize(XP_CODEC.xmap(HashMap::new, map -> map))
                    .copyOnDeath().build());

    private CraftingSkills() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    public static int xp(Player player, CraftSkill skill) {
        return player.getData(XP).getOrDefault(skill.id, 0);
    }

    public static int level(Player player, CraftSkill skill) {
        return levelFor(xp(player, skill));
    }

    /** The level that this much total XP reaches. */
    public static int levelFor(int xp) {
        return Mth.clamp((int) Math.floor(Math.sqrt(Math.max(0, xp) / (double) XP_SCALE)) + 1, 1, MAX_LEVEL);
    }

    /** The total XP needed to reach a level. */
    public static int xpFor(int level) {
        int below = Mth.clamp(level, 1, MAX_LEVEL) - 1;
        return XP_SCALE * below * below;
    }

    /** Every skill's level for a player. */
    public static Map<CraftSkill, Integer> levels(Player player) {
        Map<CraftSkill, Integer> levels = new EnumMap<>(CraftSkill.class);
        for (CraftSkill skill : CraftSkill.values()) {
            levels.put(skill, level(player, skill));
        }
        return levels;
    }

    /** Gives a player XP in a skill, telling them when they level up. */
    public static void addXp(Player player, CraftSkill skill, int amount) {
        if (amount <= 0 || player.level().isClientSide()) {
            return;
        }
        int before = level(player, skill);
        setXp(player, skill, xp(player, skill) + amount);
        int after = level(player, skill);
        if (after > before && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable("message.dungeondelving.skill_level_up", skill.displayName(), after)
                    .withStyle(ChatFormatting.GOLD));
            serverPlayer.level().playSound(null, serverPlayer.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.2f);
        }
    }

    public static void setXp(Player player, CraftSkill skill, int xp) {
        Map<String, Integer> all = new HashMap<>(player.getData(XP));
        all.put(skill.id, Math.max(0, xp));
        player.setData(XP, all);
        // The skill tree (Pufferfish's Skills) follows this XP, for its levels and points.
        if (player instanceof ServerPlayer serverPlayer) {
            SkillLevels.mirror(serverPlayer, skill, Math.max(0, xp));
        }
    }

    /** Sets a player's level in a skill, at the start of that level. For testing. */
    public static void setLevel(Player player, CraftSkill skill, int level) {
        setXp(player, skill, xpFor(level));
    }
}
