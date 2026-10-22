package io.github.ranger3462.dungeondelving.forging;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.skill.CraftSkill;
import io.github.ranger3462.dungeondelving.skill.CraftingSkills;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What happens when a player uses forge stations with metal in hand: striking a heated ingot on an anvil, quenching a
 * struck ingot, and grinding a quenched one. Striking and grinding play out on a {@link TimingBar}, one strike per
 * right-click. Every station has to be in range of a complete Forge Hearth ({@link ForgeWorkshop}).
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class ForgeActions {
    /** A bar left alone this long (in ticks) is put away. */
    public static final int TIMEOUT = 200;
    /** Strikes are checked against where the marker was this many ticks back too, to make up for lag. */
    private static final int LAG_TICKS = 2;
    /** Narrowest a sweet spot can get, as a share of the bar. */
    private static final float MIN_WIDTH = 0.05f;

    /** Crack chance for an off strike: this, plus how far off it was (as a share of the bar). */
    public static final float STRIKE_CRACK_BASE = 0.12f;
    public static final float GRIND_CRACK_BASE = 0.06f;
    public static final float GRIND_CRACK_SLOPE = 0.6f;
    /** Crack chance when quenching in water, before the Forging skill. */
    public static final float WATER_CRACK = 0.10f;
    private static final float MAX_CRACK = 0.9f;

    public static final float STRIKE_WIDTH = 0.18f;
    public static final float GRIND_WIDTH = 0.20f;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private ForgeActions() {}

    /** One player's timing bar in progress. */
    public static final class Session {
        final TimingBar.Kind kind;
        final BlockPos station;
        final long start;
        final int period;
        final int stars;
        final int needed;
        final float baseWidth;
        float low;
        float high;
        int done;
        long lastAction;

        /** A game time when the marker is in the middle of the sweet spot, at or after {@code from}. For tests. */
        public long perfectTickAfter(long from) {
            for (long tick = from; tick < from + period; tick++) {
                if (TimingBar.offBy(TimingBar.marker(tick - start, period), low + 0.01f, high - 0.01f) <= 0.0f) {
                    return tick;
                }
            }
            return from;
        }

        /** A game time when the marker is as far from the sweet spot as it gets, at or after {@code from}. For tests. */
        public long worstTickAfter(long from) {
            long worst = from;
            float off = -1.0f;
            for (long tick = from; tick < from + period; tick++) {
                float here = TimingBar.offBy(TimingBar.marker(tick - start, period), low, high);
                if (here > off) {
                    off = here;
                    worst = tick;
                }
            }
            return worst;
        }

        public int done() {
            return done;
        }

        public int needed() {
            return needed;
        }

        public float width() {
            return high - low;
        }

        Session(TimingBar.Kind kind, BlockPos station, long start, int period, int stars, int needed, float baseWidth) {
            this.kind = kind;
            this.station = station;
            this.start = start;
            this.period = period;
            this.stars = stars;
            this.needed = needed;
            this.baseWidth = baseWidth;
            this.lastAction = start;
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack held = player.getMainHandItem();
        Action action = actionFor(held, state);
        if (action == null) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            switch (action) {
                case STRIKE -> useBar(serverPlayer, serverLevel, pos, state, held, TimingBar.Kind.STRIKE);
                case GRIND -> useBar(serverPlayer, serverLevel, pos, state, held, TimingBar.Kind.GRIND);
                case QUENCH -> quench(serverPlayer, serverLevel, pos, state, held);
            }
        }
    }

    private enum Action {
        STRIKE, QUENCH, GRIND
    }

    private static Action actionFor(ItemStack held, BlockState state) {
        if (held.is(Forging.HEATED_INGOT.get()) && isAnvil(state)) {
            return Action.STRIKE;
        } else if (held.is(Forging.STRUCK_INGOT.get()) && (state.is(Blocks.WATER_CAULDRON) || state.is(Forging.QUENCH_TUB.get()))) {
            return Action.QUENCH;
        } else if (held.is(Forging.QUENCHED_INGOT.get()) && (state.is(Blocks.GRINDSTONE) || state.is(Forging.GRINDING_WHEEL.get()))) {
            return Action.GRIND;
        }
        return null;
    }

    public static boolean isAnvil(BlockState state) {
        return state.is(BlockTags.ANVIL) || state.is(Forging.DWARVEN_ANVIL.get());
    }

    // --- Striking and grinding ---

    private static void useBar(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, ItemStack held, TimingBar.Kind kind) {
        long now = level.getGameTime();
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.kind == kind && session.station.equals(pos) && now - session.lastAction < TIMEOUT) {
            strike(player, level, session, held, now);
        } else {
            begin(player, level, pos, state, held, kind, now);
        }
    }

    /** Starts a timing bar, if the forge, the player's skill and the metal are all up to it. */
    public static Optional<Session> begin(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, ItemStack held, TimingBar.Kind kind, long now) {
        Optional<ForgeWorkshop> workshop = ForgeWorkshop.around(level, pos);
        if (workshop.isEmpty()) {
            tell(player, Component.translatable("message.dungeondelving.forge.no_hearth").withStyle(ChatFormatting.RED));
            return Optional.empty();
        }
        int stars = Forging.stars(held);
        int skill = ForgingPerks.level(player);
        if (stars > workshop.get().maxStars()) {
            tell(player, Component.translatable("message.dungeondelving.forge.forge_too_low", StarredItem.starsText(workshop.get().maxStars()))
                    .withStyle(ChatFormatting.RED));
            return Optional.empty();
        }
        if (skill < ForgingPerks.levelFor(stars)) {
            tell(player, Component.translatable("message.dungeondelving.forge.skill_too_low", ForgingPerks.levelFor(stars), StarredItem.starsText(stars))
                    .withStyle(ChatFormatting.RED));
            return Optional.empty();
        }
        float heat = heatOf(player, held, now);
        if (heat <= 0.0f) {
            tell(player, Component.translatable("message.dungeondelving.forge.cold").withStyle(ChatFormatting.RED));
            return Optional.empty();
        }
        float width;
        int needed;
        int period;
        if (kind == TimingBar.Kind.STRIKE) {
            width = STRIKE_WIDTH + (state.is(Forging.DWARVEN_ANVIL.get()) ? DwarvenAnvilBlock.SWEET_SPOT_BONUS : 0.0f) - 0.015f * (stars - 1);
            needed = strikesFor(stars);
            period = Math.max(16, 28 - (stars - 1));
        } else {
            width = GRIND_WIDTH + (state.is(Forging.GRINDING_WHEEL.get()) ? GrindingWheelBlock.SWEET_SPOT_BONUS : 0.0f) - 0.01f * (stars - 1);
            needed = passesFor(stars);
            period = Math.max(16, 24 - (stars - 1));
        }
        width += ForgingPerks.sweetSpotBonus(skill) + workshop.get().decorBonus();
        Session session = new Session(kind, pos.immutable(), now, period, stars, needed, width);
        nextSpot(session, heat, level);
        SESSIONS.put(player.getUUID(), session);
        send(player, session);
        level.playSound(null, pos, kind == TimingBar.Kind.STRIKE ? SoundEvents.ANVIL_PLACE : SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.3f, 1.2f);
        return Optional.of(session);
    }

    /** Strikes needed to shape an ingot aiming for this ★: 3, plus 1 for every 2 ★. */
    public static int strikesFor(int stars) {
        return 3 + stars / 2;
    }

    /** Grinding passes needed: 2, or 3 from ★5. */
    public static int passesFor(int stars) {
        return stars >= 5 ? 3 : 2;
    }

    /** Places the sweet spot for the next strike somewhere along the bar. It's narrower the cooler the metal. */
    private static void nextSpot(Session session, float heat, ServerLevel level) {
        float width = Math.max(MIN_WIDTH, session.baseWidth * (session.kind == TimingBar.Kind.STRIKE ? 0.5f + 0.5f * heat : 1.0f));
        float margin = 0.05f;
        float center = margin + width / 2 + level.getRandom().nextFloat() * Math.max(0.0f, 1.0f - 2 * margin - width);
        session.low = center - width / 2;
        session.high = center + width / 2;
    }

    /** A hot ingot's heat for this player (their Forging level slows cooling); 1 for anything that isn't hot metal. */
    private static float heatOf(Player player, ItemStack stack, long now) {
        return stack.getItem() instanceof HotMetalItem ? HotMetalItem.temperature(stack, now, ForgingPerks.coolTicks(ForgingPerks.level(player))) : 1.0f;
    }

    /** How far off a strike made now would be, allowing for a couple of ticks of lag. */
    static float offBy(Session session, long now) {
        float best = Float.MAX_VALUE;
        for (int lag = 0; lag <= LAG_TICKS; lag++) {
            best = Math.min(best, TimingBar.offBy(TimingBar.marker(now - lag - session.start, session.period), session.low, session.high));
        }
        return best;
    }

    /** One strike (or grinding pass) at game time {@code now}. */
    public static void strike(ServerPlayer player, ServerLevel level, Session session, ItemStack held, long now) {
        float heat = heatOf(player, held, now);
        if (heat <= 0.0f) {
            end(player);
            tell(player, Component.translatable("message.dungeondelving.forge.cold").withStyle(ChatFormatting.RED));
            return;
        }
        float off = offBy(session, now);
        int skill = ForgingPerks.level(player);
        BlockPos pos = session.station;
        boolean grinding = session.kind == TimingBar.Kind.GRIND;
        if (off <= 0.0f) {
            CraftingSkills.addXp(player, CraftSkill.FORGING, 3);
            level.playSound(null, pos, grinding ? SoundEvents.GRINDSTONE_USE : SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.3f);
            level.sendParticles(grinding ? ParticleTypes.CRIT : ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 6, 0.15, 0.05,
                    0.15, 0.05);
            tell(player, Component.translatable("message.dungeondelving.forge.perfect").withStyle(ChatFormatting.GREEN));
        } else {
            float chance = Math.min(MAX_CRACK, crackChance(session.kind, off) * ForgingPerks.crackMultiplier(skill));
            CraftingSkills.addXp(player, CraftSkill.FORGING, 1);
            if (level.getRandom().nextFloat() < chance) {
                end(player);
                crack(player, level, pos, held);
                return;
            }
            level.playSound(null, pos, grinding ? SoundEvents.GRINDSTONE_USE : SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 0.7f);
            tell(player, Component.translatable("message.dungeondelving.forge.off").withStyle(ChatFormatting.YELLOW));
        }
        session.done++;
        session.lastAction = now;
        if (session.done < session.needed) {
            nextSpot(session, heat, level);
            send(player, session);
            return;
        }
        end(player);
        finish(player, level, session, held);
    }

    /** The chance an off strike cracks the piece, before the Forging skill, if it's {@code off} away from the sweet spot. */
    public static float crackChance(TimingBar.Kind kind, float off) {
        return kind == TimingBar.Kind.GRIND ? GRIND_CRACK_BASE + off * GRIND_CRACK_SLOPE : STRIKE_CRACK_BASE + off;
    }

    /** Turns the last strike's piece into the next stage: a struck ingot, or a Tempered Ingot. */
    private static void finish(ServerPlayer player, ServerLevel level, Session session, ItemStack held) {
        ItemStack result;
        if (session.kind == TimingBar.Kind.STRIKE) {
            result = new ItemStack(Forging.STRUCK_INGOT.get());
            Long heatedAt = held.get(Forging.HEATED_AT.get());
            if (heatedAt != null) {
                result.set(Forging.HEATED_AT.get(), heatedAt); // Still cooling: it needs quenching while it's hot.
            }
            tell(player, Component.translatable("message.dungeondelving.forge.struck").withStyle(ChatFormatting.GOLD));
        } else {
            result = new ItemStack(Forging.TEMPERED_IRON_INGOT.get());
            CraftingSkills.addXp(player, CraftSkill.FORGING, 10 * session.stars);
            tell(player, Component.translatable("message.dungeondelving.forge.tempered", StarredItem.starsText(session.stars)).withStyle(ChatFormatting.GOLD));
            level.playSound(null, session.station, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.8f, 1.0f);
        }
        result.set(Forging.STARS.get(), session.stars);
        held.shrink(1);
        give(player, result);
    }

    // --- Quenching ---

    public static void quench(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, ItemStack held) {
        if (ForgeWorkshop.around(level, pos).isEmpty()) {
            tell(player, Component.translatable("message.dungeondelving.forge.no_hearth").withStyle(ChatFormatting.RED));
            return;
        }
        if (heatOf(player, held, level.getGameTime()) <= 0.0f) {
            tell(player, Component.translatable("message.dungeondelving.forge.too_cold_to_quench").withStyle(ChatFormatting.RED));
            return;
        }
        float chance;
        if (state.is(Forging.QUENCH_TUB.get())) {
            if (!QuenchTubBlock.useOil(level, pos, state)) {
                tell(player, Component.translatable("message.dungeondelving.forge.tub_dry").withStyle(ChatFormatting.RED));
                return;
            }
            chance = 0.0f;
        } else {
            chance = WATER_CRACK * ForgingPerks.crackMultiplier(ForgingPerks.level(player));
        }
        level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.0f);
        level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.25, 0.2, 0.25, 0.02);
        CraftingSkills.addXp(player, CraftSkill.FORGING, 2);
        if (level.getRandom().nextFloat() < chance) {
            crack(player, level, pos, held);
            return;
        }
        ItemStack quenched = new ItemStack(Forging.QUENCHED_INGOT.get());
        quenched.set(Forging.STARS.get(), Forging.stars(held));
        held.shrink(1);
        give(player, quenched);
        tell(player, Component.translatable("message.dungeondelving.forge.quenched").withStyle(ChatFormatting.GOLD));
    }

    // --- Shared ---

    /** The piece in hand cracks: it's replaced by iron nuggets. */
    private static void crack(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack held) {
        int nuggets = ForgingPerks.scrapNuggets(ForgingPerks.level(player));
        held.shrink(1);
        give(player, new ItemStack(Items.IRON_NUGGET, nuggets));
        level.playSound(null, pos, SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 0.6f, 1.2f);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 10, 0.2, 0.1, 0.2, 0.02);
        tell(player, Component.translatable("message.dungeondelving.forge.cracked", nuggets).withStyle(ChatFormatting.RED));
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }

    private static void send(ServerPlayer player, Session session) {
        PacketDistributor.sendToPlayer(player, new TimingBarPayload(session.kind.ordinal(), session.start, session.period, session.low, session.high,
                session.done, session.needed));
    }

    /** Puts away a player's timing bar. */
    static void end(ServerPlayer player) {
        if (SESSIONS.remove(player.getUUID()) != null) {
            PacketDistributor.sendToPlayer(player, TimingBarPayload.HIDE);
        }
    }

    /** A player's timing bar in progress, if any. For tests. */
    public static Optional<Session> session(Player player) {
        return Optional.ofNullable(SESSIONS.get(player.getUUID()));
    }

    /** Puts away bars that were left alone, or whose metal left the player's hand. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty() || event.getServer().getTickCount() % 10 != 0) {
            return;
        }
        Iterator<Map.Entry<UUID, Session>> sessions = SESSIONS.entrySet().iterator();
        while (sessions.hasNext()) {
            Map.Entry<UUID, Session> entry = sessions.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();
            Item expected = session.kind == TimingBar.Kind.STRIKE ? Forging.HEATED_INGOT.get() : Forging.QUENCHED_INGOT.get();
            if (player == null || !player.getMainHandItem().is(expected) || player.level().getGameTime() - session.lastAction >= TIMEOUT) {
                sessions.remove();
                if (player != null) {
                    PacketDistributor.sendToPlayer(player, TimingBarPayload.HIDE);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }
}
