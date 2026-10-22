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
import net.minecraft.world.level.block.LayeredCauldronBlock;
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
    /** Ticks of slack, either way, on top of the player's ping, when checking a client-timed strike. */
    private static final int TIMING_SLACK = 10;
    /** Narrowest a sweet spot can get, as a share of the bar. */
    private static final float MIN_WIDTH = 0.03f;

    /** Crack chance for the first missed strike, and how much each further miss in the same bar adds. */
    public static final float STRIKE_MISS_CRACK = 0.30f;
    public static final float STRIKE_MISS_STEP = 0.15f;
    /**
     * Chance a basic station (a vanilla anvil or grindstone) spoils the piece when a bar is finished, however well it
     * went. The upgraded stations don't; that's much of why they're worth building.
     */
    public static final float BASIC_STATION_CRACK = 0.10f;
    /** The sweep is this much faster (as a share of its period) on metal that's gone from glowing to nearly cold. */
    public static final float COLD_SPEEDUP = 0.4f;
    private static final int MIN_PERIOD = 10;
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
        long start;
        int period;
        final int basePeriod;
        /** A vanilla anvil or grindstone, which can spoil the piece however well the bar went. */
        final boolean basicStation;
        /** Grinding an unstable ingot: any miss breaks it. */
        final boolean unstable;
        final int stars;
        final int needed;
        final float baseWidth;
        float low;
        float high;
        int done;
        int misses;
        long lastAction;
        /** Counts up with each strike, so a strike can't be applied to a bar it wasn't aimed at. */
        int bar;

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

        public int bar() {
            return bar;
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

        Session(TimingBar.Kind kind, BlockPos station, long start, int period, boolean basicStation, boolean unstable, int stars, int needed,
                float baseWidth) {
            this.kind = kind;
            this.station = station;
            this.start = start;
            this.period = period;
            this.basePeriod = period;
            this.basicStation = basicStation;
            this.unstable = unstable;
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
        } else if (held.is(Forging.STRUCK_INGOT.get()) && (state.is(Blocks.WATER_CAULDRON) || state.is(Blocks.CAULDRON)
                || state.is(Forging.OIL_CAULDRON.get()))) {
            return Action.QUENCH;
        } else if (isGrindable(held) && (state.is(Blocks.GRINDSTONE) || state.is(Forging.GRINDING_WHEEL.get()))) {
            return Action.GRIND;
        }
        return null;
    }

    /** Quenched ingots, and unstable ones getting their second chance. */
    public static boolean isGrindable(ItemStack stack) {
        return stack.is(Forging.QUENCHED_INGOT.get()) || stack.is(Forging.UNSTABLE_INGOT.get());
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
        boolean basic;
        if (kind == TimingBar.Kind.STRIKE) {
            basic = !state.is(Forging.DWARVEN_ANVIL.get());
            width = STRIKE_WIDTH + (basic ? 0.0f : DwarvenAnvilBlock.SWEET_SPOT_BONUS) - 0.0075f * (stars - 1);
            needed = strikesFor(stars);
            period = Math.max(16, 28 - (stars - 1));
        } else {
            basic = !state.is(Forging.GRINDING_WHEEL.get());
            width = GRIND_WIDTH + (basic ? 0.0f : GrindingWheelBlock.SWEET_SPOT_BONUS) - 0.005f * (stars - 1);
            needed = passesFor(stars);
            period = Math.max(16, 24 - (stars - 1));
        }
        width += ForgingPerks.sweetSpotBonus(skill) + workshop.get().decorBonus();
        Session session = new Session(kind, pos.immutable(), now, period, basic, held.is(Forging.UNSTABLE_INGOT.get()), stars, needed, width);
        nextSpot(session, heat, level, now);
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

    /**
     * Sets up the next strike: the sweet spot goes somewhere new along the bar, and the marker restarts from the left.
     * Striking metal that has cooled sweeps faster (see {@link #periodFor}).
     */
    private static void nextSpot(Session session, float heat, ServerLevel level, long now) {
        session.bar++;
        session.start = now;
        session.period = periodFor(session.kind, session.basePeriod, heat);
        float width = Math.max(MIN_WIDTH, session.baseWidth);
        float margin = 0.05f;
        float center = margin + width / 2 + level.getRandom().nextFloat() * Math.max(0.0f, 1.0f - 2 * margin - width);
        session.low = center - width / 2;
        session.high = center + width / 2;
    }

    /** The sweep's period (ticks there and back): striking cooler metal sweeps faster, up to {@link #COLD_SPEEDUP} faster. */
    public static int periodFor(TimingBar.Kind kind, int basePeriod, float heat) {
        if (kind != TimingBar.Kind.STRIKE) {
            return basePeriod;
        }
        return Math.max(MIN_PERIOD, Math.round(basePeriod * (1.0f - COLD_SPEEDUP * (1.0f - heat))));
    }

    /** A hot ingot's heat for this player (their Forging level slows cooling); 1 for anything that isn't hot metal. */
    private static float heatOf(Player player, ItemStack stack, long now) {
        return stack.getItem() instanceof HotMetalItem ? HotMetalItem.temperature(stack, now, ForgingPerks.coolTicks(ForgingPerks.level(player))) : 1.0f;
    }

    /**
     * How long the bar had been sweeping when the player struck, as the client timed it, kept to what's believable. The
     * client's bar starts when the bar reaches it (half a ping after the server sent it) and the strike reaches the
     * server half a ping after the click, so the client's time should trail the server's by about one ping. A time far
     * outside that (more than {@link #TIMING_SLACK} ticks either way) is pulled back into it.
     */
    public static float checkedElapsed(Session session, long now, float reported, float pingTicks) {
        float serverElapsed = now - session.start;
        float earliest = serverElapsed - pingTicks - TIMING_SLACK;
        float latest = serverElapsed + TIMING_SLACK;
        return Math.clamp(reported, Math.max(0.0f, earliest), latest);
    }

    /** A strike reported by the client: which bar it was for, and how long that bar had been sweeping on the client. */
    static void onStrike(ServerPlayer player, int bar, float elapsed) {
        Session session = SESSIONS.get(player.getUUID());
        ItemStack held = player.getMainHandItem();
        if (session == null || session.bar != bar
                || !(session.kind == TimingBar.Kind.STRIKE ? held.is(Forging.HEATED_INGOT.get()) : isGrindable(held))) {
            return;
        }
        long now = player.level().getGameTime();
        float pingTicks = player.connection.latency() / 50.0f;
        strike(player, (ServerLevel) player.level(), session, held, now,
                TimingBar.marker(checkedElapsed(session, now, elapsed, pingTicks), session.period));
    }

    /** One strike (or grinding pass) at game time {@code now}, with the marker at {@code marker}. */
    public static void strike(ServerPlayer player, ServerLevel level, Session session, ItemStack held, long now, float marker) {
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
                CraftingSkills.addXp(player, CraftSkill.FORGING, 3);
                level.playSound(null, pos, grinding ? SoundEvents.GRINDSTONE_USE : SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.3f);
                level.sendParticles(grinding ? ParticleTypes.CRIT : ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 6,
                        0.15, 0.05, 0.15, 0.05);
                tell(player, Component.translatable("message.dungeondelving.forge.good").withStyle(ChatFormatting.GREEN));
            }
            case MISS -> {
                session.done++;
                session.misses++;
                // A missed strike can crack the ingot. A missed grinding pass doesn't, but leaves it unstable (see finish).
                chance = grinding ? 0.0f : missChance(session.kind, session.misses);
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
        if (hit == TimingBar.Hit.MISS && grinding) {
            tell(player, Component.translatable(session.unstable ? "message.dungeondelving.forge.grind_miss_unstable" : "message.dungeondelving.forge.grind_miss")
                    .withStyle(ChatFormatting.YELLOW));
        } else if (hit == TimingBar.Hit.MISS) {
            int nextRisk = Math.round(100 * Math.min(MAX_CRACK, missChance(session.kind, session.misses + 1) * crackMultiplier));
            tell(player, Component.translatable("message.dungeondelving.forge.off", nextRisk).withStyle(ChatFormatting.YELLOW));
        }
        session.lastAction = now;
        if (session.done < session.needed) {
            nextSpot(session, heat, level, now);
            send(player, session);
            return;
        }
        end(player);
        boolean failedGrind = grinding && session.misses > 0;
        if (failedGrind && session.unstable) {
            crack(player, level, pos, held); // Its second chance, missed.
            return;
        }
        if (!failedGrind && session.basicStation && level.getRandom().nextFloat() < BASIC_STATION_CRACK) {
            tell(player, Component.translatable("message.dungeondelving.forge.basic_station").withStyle(ChatFormatting.RED));
            crack(player, level, pos, held);
            return;
        }
        finish(player, level, session, held, failedGrind);
    }

    /** The chance the {@code misses}th missed strike in a bar cracks the piece, before the Forging skill: it climbs with every miss. */
    public static float missChance(TimingBar.Kind kind, int misses) {
        return STRIKE_MISS_CRACK + STRIKE_MISS_STEP * (misses - 1);
    }

    /**
     * Turns the last strike's piece into the next stage: a struck ingot, a Tempered Ingot, or (after a grind with a
     * miss) an Unstable Ingot, which gets one more try at the grindstone.
     */
    private static void finish(ServerPlayer player, ServerLevel level, Session session, ItemStack held, boolean failedGrind) {
        ItemStack result;
        if (failedGrind) {
            result = new ItemStack(Forging.UNSTABLE_INGOT.get());
            tell(player, Component.translatable("message.dungeondelving.forge.unstable").withStyle(ChatFormatting.YELLOW));
        } else if (session.kind == TimingBar.Kind.STRIKE) {
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
        if (state.is(Forging.OIL_CAULDRON.get())) {
            OilCauldronBlock.useOil(level, pos, state);
            chance = 0.0f;
        } else if (state.is(Blocks.WATER_CAULDRON)) {
            LayeredCauldronBlock.lowerFillLevel(state, level, pos); // Each quench boils off a level of water.
            chance = WATER_CRACK;
        } else {
            tell(player, Component.translatable("message.dungeondelving.forge.cauldron_empty").withStyle(ChatFormatting.RED));
            return;
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
        PacketDistributor.sendToPlayer(player, new TimingBarPayload(session.kind.ordinal(), session.bar, session.period, session.low, session.high,
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
            boolean holding = player != null && (session.kind == TimingBar.Kind.STRIKE ? player.getMainHandItem().is(Forging.HEATED_INGOT.get())
                    : isGrindable(player.getMainHandItem()));
            if (!holding || player.level().getGameTime() - session.lastAction >= TIMEOUT) {
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
