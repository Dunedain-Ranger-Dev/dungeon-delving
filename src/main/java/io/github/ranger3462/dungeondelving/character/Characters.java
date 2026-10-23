package io.github.ranger3462.dungeondelving.character;

import java.util.HashMap;
import java.util.Map;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.skill.CraftSkill;
import io.github.ranger3462.dungeondelving.skill.CraftingSkills;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * A player's character (DD-80): their character level, worked out from their skill levels ({@link SkillLevels}), and
 * their ability scores ({@link AbilityScore}). Each character level past 1 earns an ability point, and some items
 * give bonus points; players spend them on the stats screen. Points put into a score raise its attribute, and each
 * score's effects are kept up to date with its value, so gear and races that change a score count too.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class Characters {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
            DungeonDelving.MODID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CharacterData>> DATA = ATTACHMENTS.register("character",
            () -> AttachmentType.builder(() -> CharacterData.EMPTY).serialize(CharacterData.CODEC).copyOnDeath().build());
    /** Where a score's allocated points are kept on its attribute. */
    private static final ResourceLocation ALLOCATED = DungeonDelving.id("allocated");
    /** How often scores' effects are brought up to date with gear changes, in ticks. */
    private static final int REFRESH_INTERVAL = 20;

    private Characters() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        AbilityScore.register(modEventBus);
        modEventBus.addListener(Characters::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // The handler only ever runs on the client, so the client class it names is never loaded on a server.
        event.registrar("1").playToClient(CharacterSyncPayload.TYPE, CharacterSyncPayload.STREAM_CODEC,
                (payload, context) -> io.github.ranger3462.dungeondelving.client.CharacterScreen.receive(payload));
        event.registrar("1").playToServer(AllocateScorePayload.TYPE, AllocateScorePayload.STREAM_CODEC, AllocateScorePayload::handle);
    }

    public static CharacterData data(Player player) {
        return player.getData(DATA);
    }

    /** The player's character level. */
    public static int level(ServerPlayer player) {
        return SkillLevels.characterLevel(SkillLevels.levels(player), Config.CHARACTER_LEVEL_TOP_SKILLS.get());
    }

    /** Ability points the player has earned: one per character level past 1, plus bonus points. */
    public static int earned(ServerPlayer player) {
        return level(player) - 1 + data(player).bonusPoints();
    }

    /** Ability points the player hasn't spent yet. */
    public static int available(ServerPlayer player) {
        return Math.max(0, earned(player) - data(player).spent());
    }

    /** Puts one of the player's points into a score. False if they have none left, or the score is at its maximum. */
    public static boolean allocate(ServerPlayer player, AbilityScore score) {
        AttributeInstance attribute = player.getAttribute(score.attribute());
        if (available(player) <= 0 || attribute == null || attribute.getValue() >= AbilityScore.MAX) {
            return false;
        }
        player.setData(DATA, data(player).withAllocated(score, data(player).allocated(score) + 1));
        apply(player);
        sync(player);
        return true;
    }

    /** Takes back every allocated point, to spend again. */
    public static void respec(ServerPlayer player) {
        player.setData(DATA, data(player).respec());
        apply(player);
        sync(player);
    }

    /** Gives the player bonus ability points (e.g. from a Tome of Insight). */
    public static void addBonusPoints(ServerPlayer player, int points) {
        player.setData(DATA, data(player).withBonusPoints(data(player).bonusPoints() + points));
        sync(player);
    }

    /** Brings the player's scores up to date: their allocated points, then what each score adds to other attributes. */
    public static void apply(ServerPlayer player) {
        CharacterData data = data(player);
        for (AbilityScore score : AbilityScore.values()) {
            AttributeInstance attribute = player.getAttribute(score.attribute());
            if (attribute == null) {
                continue;
            }
            setModifier(attribute, ALLOCATED, data.allocated(score), AttributeModifier.Operation.ADD_VALUE);
            double points = attribute.getValue() - AbilityScore.BASE;
            for (AbilityScore.Effect effect : score.effects) {
                AttributeInstance target = attribute(player, effect.attribute());
                if (target != null) {
                    setModifier(target, score.effectId(), points * effect.perPoint(), effect.operation());
                }
            }
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static AttributeInstance attribute(ServerPlayer player, String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return null;
        }
        return BuiltInRegistries.ATTRIBUTE.getHolder(key).map(holder -> player.getAttribute((Holder<Attribute>) holder)).orElse(null);
    }

    /** Sets a modifier's amount, only touching the attribute if it changed (0 removes it). */
    private static void setModifier(AttributeInstance attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        AttributeModifier current = attribute.getModifier(id);
        if (amount == 0) {
            if (current != null) {
                attribute.removeModifier(id);
            }
        } else if (current == null || current.amount() != amount || current.operation() != operation) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    /** Announces a new character level (once per level), with the points it brought. */
    public static void checkLevel(ServerPlayer player) {
        int level = level(player);
        CharacterData data = data(player);
        if (level > data.announcedLevel()) {
            player.setData(DATA, data.withAnnouncedLevel(level));
            player.sendSystemMessage(Component.translatable("message.dungeondelving.character_level_up", level, available(player))
                    .withStyle(ChatFormatting.GOLD));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 0.8f);
            sync(player);
        }
    }

    /** Sends the player their character level, points and allocations, for the stats screen. */
    public static void sync(ServerPlayer player) {
        Map<String, Integer> allocated = new HashMap<>(data(player).allocated());
        PacketDistributor.sendToPlayer(player, new CharacterSyncPayload(level(player), available(player), allocated));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Bring the skill trees in line with the crafting skills, e.g. XP earned before Pufferfish's Skills was added.
            for (CraftSkill skill : CraftSkill.values()) {
                SkillLevels.mirror(player, skill, CraftingSkills.xp(player, skill));
            }
            apply(player);
            checkLevel(player);
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            apply(player);
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % REFRESH_INTERVAL == 0) {
            apply(player);
        }
    }
}
