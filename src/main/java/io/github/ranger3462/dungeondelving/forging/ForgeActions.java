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
    /** How many ticks of lag a client's reported marker position is allowed to be behind the server's. */
    private static final int LAG_TICKS = 4;
    /** How far outside the marker's lag window a reported position may be, as a share of the bar. */
    private static final float LAG_SLACK = 0.08f;
    /** Fewest ticks between two strikes. */
    private static final int MIN_STRIKE_GAP = 2;
    /** Narrowest a sweet spot can get, as a share of the bar. */
    private static final float MIN_WIDTH = 0.03f;

    /** Crack chance for the first missed strike, and how much each further miss in the same bar adds. */
    public static final float STRIKE_MISS_CRACK = 0.30f;
    public static final float STRIKE_MISS_STEP = 0.15f;
    public static final float GRIND_MISS_CRACK = 0.20f;
    public static final float GRIND_MISS_STEP = 0.10f;
    /** Extra crack chance on every strike but a gold one, once the metal is merely hot, and once it's cooling. */
    public static final float HOT_PENALTY = 0.12f;
    public static final float COOLING_PENALTY = 0.25f;
    /** Crack chance when quenching in water. Only oil avoids it. */
    public static final float WATER_CRACK = 0.30f;
    private static final float MAX_CRACK = 0.95f;

    public static final float STRIKE_WIDTH = 0.09f;
    public static final float GRIND_WIDTH = 0.10f;

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
        int misses;
        long lastAction;
        long lastStrike = Long.MIN_VALUE / 2;

        /** A marker position in the gold zone. For tests. */
        public float goldMarker() {
            return (low + high) / 2;
        }

        /** A marker position in the green but outside the gold. For tests. */
        public float greenMarker() {
            return low + (TimingBar.goldLow(low, high) - low) / 2;
        }

        /** A marker position well outside the sweet spot. For tests. */
        public float missMarker() {
            return low > 0.5f ? 0.0f : 1.0f;
        }

        public int misses() {
            return misses;
        }

        public float low() {
            return low;
        }

        public float high() {
            return high;
        }

        public long start() {
            return start;
        }

        public int period() {
            return period;
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

    /** Right-clicking a station starts its timing bar. The strikes themselves come from the client ({@link StrikePayload}). */
    private static void useBar(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, ItemStack held, TimingBar.Kind kind) {
        begin(player, level, pos, state, held, kind, level.getGameTime());
    }

    /** Starts a timing bar, if the forge, the player's skill and the metal are all up to it. */
    public static Optional<Session> begin(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, ItemStack held, TimingBar.Kind kind, long now) {
        Session current = SESSIONS.get(player.getUUID());
        if (current != null && current.kind == kind && current.station.equals(pos) && now - current.lastAction < TIMEOUT) {
            return Optional.of(current); // Strikes come in as StrikePayloads, so a click on the station mid-bar does nothing.
        }
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
            width = STRIKE_WIDTH + (state.is(Forging.DWARVEN_ANVIL.get()) ? DwarvenAnvilBlock.SWEET_SPOT_BONUS : 0.0f) - 0.0075f * (stars - 1);
            needed = strikesFor(stars);
            period = Math.max(16, 28 - (stars - 1));
        } else {
            width = GRIND_WIDTH + (state.is(Forging.GRINDING_WHEEL.get()) ? GrindingWheelBlock.SWEET_SPOT_BONUS : 0.0f) - 0.005f * (stars - 1);
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

    /** Places the sweet spot for the next strike somewhere along the bar. It's a little narrower the cooler the metal. */
    private static void nextSpot(Session session, float heat, ServerLevel level) {
        float width = Math.max(MIN_WIDTH, session.baseWidth * (session.kind == TimingBar.Kind.STRIKE ? 0.75f + 0.25f * heat : 1.0f));
        float margin = 0.05f;
        float center = margin + width / 2 + level.getRandom().nextFloat() * Math.max(0.0f, 1.0f - 2 * margin - width);
        session.low = center - width / 2;
        session.high = center + width / 2;
    }

    /** A hot ingot's heat for this player (their Forging level slows cooling); 1 for anything that isn't hot metal. */
    private static float heatOf(Player player, ItemStack stack, long now) {
        return stack.getItem() instanceof HotMetalItem ? HotMetalItem.temperature(stack, now, ForgingPerks.coolTicks(ForgingPerks.level(player))) : 1.0f;
    }

    /**
     * Where the marker was for a strike the client reports. The client draws the marker smoothly, between server ticks,
     * so its position is the precise one; but it has to fall within where the marker could have been over the last few
     * ticks (allowing for lag), or the server's own guess is used instead.
     */
    public static float checkedMarker(Session session, long now, float reported) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int lag = 0; lag <= LAG_TICKS; lag++) {
            float marker = TimingBar.marker(now - lag - session.start, session.period);
            min = Math.min(min, marker);
            max = Math.max(max, marker);
        }
        if (reported >= min - LAG_SLACK && reported <= max + LAG_SLACK) {
            return reported;
        }
        return TimingBar.marker(now - 1 - session.start, session.period);
    }

    /** A strike reported by the client, with where it saw the marker. */
    static void onStrike(ServerPlayer player, float reported) {
        Session session = SESSIONS.get(player.getUUID());
        ItemStack held = player.getMainHandItem();
        if (session == null || !held.is(session.kind == TimingBar.Kind.STRIKE ? Forging.HEATED_INGOT.get() : Forging.QUENCHED_INGOT.get())) {
            return;
        }
        long now = player.level().getGameTime();
        strike(player, (ServerLevel) player.level(), session, held, now, checkedMarker(session, now, reported));
    }

    /** One strike (or grinding pass) at game time {@code now}, with the marker at {@code marker}. */
    public static void strike(ServerPlayer player, ServerLevel level, Session session, ItemStack held, long now, float marker) {
        if (now - session.lastStrike < MIN_STRIKE_GAP) {
            return;
        }
        session.lastStrike = now;
        float heat = heatOf(player, held, now);
        if (heat <= 0.0f) {
            end(player);
            tell(player, Component.translatable("message.dungeondelving.forge.cold").withStyle(ChatFormatting.RED));
            return;
        }
        BlockPos pos = session.station;
        boolean grinding = session.kind == TimingBar.Kind.GRIND;
        TimingBar.Hit hit = TimingBar.judge(marker, session.low, session.high);
        float chance = 0.0f;
        switch (hit) {
            case GOLD -> {
                session.done += 2;
                CraftingSkills.addXp(player, CraftSkill.FORGING, 5);
                level.playSound(null, pos, grinding ? SoundEvents.GRINDSTONE_USE : SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7f, 1.6f);
                level.sendParticles(ParticleTypes.WAX_OFF, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 10, 0.2, 0.1, 0.2, 0.1);
                tell(player, Component.translatable("message.dungeondelving.forge.critical").withStyle(ChatFormatting.GOLD));
            }
            case GREEN -> {
                session.done++;
                chance = coldPenalty(session.kind, heat);
                CraftingSkills.addXp(player, CraftSkill.FORGING, 3);
                level.playSound(null, pos, grinding ? SoundEvents.GRINDSTONE_USE : SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.3f);
                level.sendParticles(grinding ? ParticleTypes.CRIT : ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 6,
                        0.15, 0.05, 0.15, 0.05);
                tell(player, Component.translatable("message.dungeondelving.forge.good").withStyle(ChatFormatting.GREEN));
            }
            case MISS -> {
                session.done++;
                session.misses++;
                chance = missChance(session.kind, session.misses) + coldPenalty(session.kind, heat);
                CraftingSkills.addXp(player, CraftSkill.FORGING, 1);
                level.playSound(null, pos, grinding ? SoundEvents.GRINDSTONE_USE : SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 0.7f);
            }
        }
        float crackMultiplier = ForgingPerks.crackMultiplier(ForgingPerks.level(player));
        chance = Math.min(MAX_CRACK, chance * crackMultiplier);
        if (chance > 0.0f && level.getRandom().nextFloat() < chance) {
            end(player);
            crack(player, level, pos, held);
            return;
        }
        if (hit == TimingBar.Hit.MISS) {
            int nextRisk = Math.round(100 * Math.min(MAX_CRACK, missChance(session.kind, session.misses + 1) * crackMultiplier));
            tell(player, Component.translatable("message.dungeondelving.forge.off", nextRisk).withStyle(ChatFormatting.YELLOW));
        }
        session.lastAction = now;
        if (session.done < session.needed) {
            nextSpot(session, heat, level);
            send(player, session);
            return;
        }
        end(player);
        finish(player, level, session, held);
    }

    /** The chance the {@code misses}th missed strike in a bar cracks the piece, before the Forging skill: it climbs with every miss. */
    public static float missChance(TimingBar.Kind kind, int misses) {
        return kind == TimingBar.Kind.GRIND ? GRIND_MISS_CRACK + GRIND_MISS_STEP * (misses - 1) : STRIKE_MISS_CRACK + STRIKE_MISS_STEP * (misses - 1);
    }

    /** Extra crack chance for striking metal that's no longer glowing hot. Grinding works cold metal, so it has none. */
    public static float coldPenalty(TimingBar.Kind kind, float heat) {
        if (kind != TimingBar.Kind.STRIKE || heat > 0.66f) {
            return 0.0f;
        }
        return heat > 0.33f ? HOT_PENALTY : COOLING_PENALTY;
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
            chance = WATER_CRACK;
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
    public static void end(ServerPlayer player) {
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
