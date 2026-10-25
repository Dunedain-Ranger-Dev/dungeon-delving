package io.github.ranger3462.dungeondelving.forging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What happens when a player uses a crafting station with the right thing in hand:
 * <ul>
 * <li>Smithing, at a forge ({@link ForgeWorkshop}): striking a heated ingot on an anvil, quenching a struck ingot, and
 * grinding a quenched one; plus salvaging forged gear at the grindstone.</li>
 * <li>Weaving: threading string on a loom.</li>
 * <li>Leatherworking: scraping leather on a Tanning Rack, and boiling it in a water cauldron over heat.</li>
 * </ul>
 * Striking, grinding, weaving and scraping are minigames ({@link TimingBar}), one strike per click. Missed strikes
 * can ruin the piece, likelier with each miss: an ingot cracks into nuggets, thread snaps (saving one string), and a
 * hide tears into scraps. Bonus results are for talents (DD-70), not the default.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class ForgeActions {
    /** A minigame left alone this long (in ticks) is put away. */
    public static final int TIMEOUT = 200;
    /** Ticks of slack, either way, on top of the player's ping, when checking a client-timed strike. */
    private static final int TIMING_SLACK = 10;
    /** Narrowest a sweet spot can get, as a share of the bar. */
    private static final float MIN_WIDTH = 0.03f;

    /** Chance the first missed strike ruins the piece, and how much each further miss in the same minigame adds. */
    public static final float MISS_FAIL = 0.30f;
    public static final float MISS_STEP = 0.15f;
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
    private static final float MAX_FAIL = 0.95f;

    public static final float STRIKE_WIDTH = 0.09f;
    public static final float GRIND_WIDTH = 0.10f;

    /** Weaving: string per cloth, stitches, how tall the needle's eye is, and how fast the eye and thread bob. */
    public static final int WEAVE_STRING = 3;
    public static final int WEAVE_STITCHES = 3;
    public static final float WEAVE_GAP = 0.28f;
    public static final int EYE_PERIOD = 34;
    public static final int THREAD_PERIOD = 22;

    /** Scraping: patches per hide, how wide each is, and how long the scraper's pass takes. */
    public static final int SCRAPE_PATCHES = 3;
    public static final float SCRAPE_WIDTH = 0.08f;
    public static final int SCRAPE_PASS = 50;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private ForgeActions() {}

    /** One player's minigame in progress. */
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
        /** Smithing: the sweet spot. */
        float low;
        float high;
        /** Weaving: the thread's period and phase. Scraping: the patches, as low, high, low, high... */
        float[] values = new float[0];
        int done;
        int misses;
        long lastAction;
        /** Counts up with each new bar, so a strike can't be applied to a bar it wasn't aimed at. */
        int bar;

        /** A marker position in the gold zone of the sweet spot (or the next patch). For tests. */
        public float goldMarker() {
            return (spotLow() + spotHigh()) / 2;
        }

        /** A marker position in the green but outside the gold. For tests. */
        public float greenMarker() {
            return spotLow() + (TimingBar.goldLow(spotLow(), spotHigh()) - spotLow()) / 2;
        }

        /** A marker position well outside the sweet spot. For tests. */
        public float missMarker() {
            return spotLow() > 0.5f ? 0.0f : 1.0f;
        }

        /** The sweet spot being aimed at: smithing's, or the next patch when scraping. */
        float spotLow() {
            return kind == TimingBar.Kind.SCRAPE ? values[Math.min(done, values.length / 2 - 1) * 2] : low;
        }

        float spotHigh() {
            return kind == TimingBar.Kind.SCRAPE ? values[Math.min(done, values.length / 2 - 1) * 2 + 1] : high;
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
        Action action = actionFor(player, held, state);
        if (action == null) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            long now = serverLevel.getGameTime();
            switch (action) {
                case STRIKE -> begin(serverPlayer, serverLevel, pos, state, held, TimingBar.Kind.STRIKE, now);
                case GRIND -> begin(serverPlayer, serverLevel, pos, state, held, TimingBar.Kind.GRIND, now);
                case QUENCH -> quench(serverPlayer, serverLevel, pos, state, held);
                case WEAVE -> begin(serverPlayer, serverLevel, pos, state, held, TimingBar.Kind.WEAVE, now);
                case SCRAPE -> begin(serverPlayer, serverLevel, pos, state, held, TimingBar.Kind.SCRAPE, now);
                case BOIL -> boil(serverPlayer, serverLevel, pos, state, held);
                case SALVAGE -> salvage(serverPlayer, serverLevel, pos, held);
            }
        }
    }

    private enum Action {
        STRIKE, QUENCH, GRIND, WEAVE, SCRAPE, BOIL, SALVAGE
    }

    private static Action actionFor(Player player, ItemStack held, BlockState state) {
        if (player.isShiftKeyDown() && ForgedGear.isGear(held) && (state.is(Blocks.GRINDSTONE) || state.is(Forging.GRINDING_WHEEL.get()))) {
            return Action.SALVAGE;
        }
        if (held.is(Forging.HEATED_INGOT.get()) && isAnvil(state)) {
            return Action.STRIKE;
        } else if (held.is(Forging.STRUCK_INGOT.get()) && (state.is(Blocks.WATER_CAULDRON) || state.is(Blocks.CAULDRON)
                || state.is(Forging.OIL_CAULDRON.get()))) {
            return Action.QUENCH;
        } else if (isGrindable(held) && (state.is(Blocks.GRINDSTONE) || state.is(Forging.GRINDING_WHEEL.get()))) {
            return Action.GRIND;
        } else if (held.is(Items.STRING) && state.is(Blocks.LOOM)) {
            return Action.WEAVE;
        } else if (held.is(Items.LEATHER) && state.is(Forging.TANNING_RACK.get())) {
            return Action.SCRAPE;
        } else if (held.is(Forging.REFINED_LEATHER.get()) && state.is(Blocks.WATER_CAULDRON)) {
            return Action.BOIL;
        }
        return null;
    }

    /** Whether a stack is what a minigame works on: hot metal, quenched metal, string or leather. */
    public static boolean worksOn(TimingBar.Kind kind, ItemStack stack) {
        return switch (kind) {
            case STRIKE -> stack.is(Forging.HEATED_INGOT.get());
            case GRIND -> isGrindable(stack);
            case WEAVE -> stack.is(Items.STRING);
            case SCRAPE -> stack.is(Items.LEATHER);
        };
    }

    /** Quenched ingots, and unstable ones getting their second chance. */
    public static boolean isGrindable(ItemStack stack) {
        return stack.is(Forging.QUENCHED_INGOT.get()) || stack.is(Forging.UNSTABLE_INGOT.get());
    }

    public static boolean isAnvil(BlockState state) {
        return state.is(BlockTags.ANVIL) || state.is(Forging.MASTER_ANVIL.get());
    }

    // --- Starting a minigame ---

    /** Starts a minigame, if the station, the player's skill and what they're holding are all up to it. */
    public static Optional<Session> begin(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, ItemStack held, TimingBar.Kind kind, long now) {
        Session current = SESSIONS.get(player.getUUID());
        if (current != null && current.kind == kind && current.station.equals(pos) && now - current.lastAction < TIMEOUT) {
            return Optional.of(current); // Strikes come in as StrikePayloads, so a click on the station mid-game does nothing.
        }
        return switch (kind) {
            case WEAVE -> beginWeaving(player, level, pos, held, now);
            case SCRAPE -> beginScraping(player, level, pos, now);
            default -> beginSmithing(player, level, pos, state, held, kind, now);
        };
    }

    private static Optional<Session> beginSmithing(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, ItemStack held,
            TimingBar.Kind kind, long now) {
        Optional<ForgeWorkshop> workshop = ForgeWorkshop.around(level, pos);
        if (workshop.isEmpty()) {
            tell(player, Component.translatable("message.dungeondelving.forge.no_workshop").withStyle(ChatFormatting.RED));
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
            basic = !state.is(Forging.MASTER_ANVIL.get());
            width = STRIKE_WIDTH + (basic ? 0.0f : MasterAnvilBlock.SWEET_SPOT_BONUS) - 0.0075f * (stars - 1);
            needed = strikesFor(stars);
            period = Math.max(16, 28 - (stars - 1));
        } else {
            basic = !state.is(Forging.GRINDING_WHEEL.get());
            width = GRIND_WIDTH + (basic ? 0.0f : GrindingWheelBlock.SWEET_SPOT_BONUS) - 0.005f * (stars - 1);
            needed = passesFor(stars);
            period = Math.max(16, 24 - (stars - 1));
        }
        width += ForgingPerks.sweetSpotBonus(player) + workshop.get().decorBonus();
        Session session = new Session(kind, pos.immutable(), now, period, basic, held.is(Forging.UNSTABLE_INGOT.get()), stars, needed, width);
        nextSpot(session, heat, level, now);
        return started(player, level, session, kind == TimingBar.Kind.STRIKE ? SoundEvents.ANVIL_PLACE : SoundEvents.GRINDSTONE_USE);
    }

    /** Weaving needs no forge, just a loom and {@link #WEAVE_STRING} string. */
    private static Optional<Session> beginWeaving(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack held, long now) {
        if (held.getCount() < WEAVE_STRING) {
            tell(player, Component.translatable("message.dungeondelving.weave.not_enough", WEAVE_STRING).withStyle(ChatFormatting.RED));
            return Optional.empty();
        }
        float gap = WEAVE_GAP + CraftPerks.sweetSpotBonus(player, CraftSkill.WEAVING);
        Session session = new Session(TimingBar.Kind.WEAVE, pos.immutable(), now, EYE_PERIOD, false, false, 1, WEAVE_STITCHES, gap);
        nextStitch(session, level, now);
        return started(player, level, session, SoundEvents.UI_LOOM_SELECT_PATTERN);
    }

    /** Scraping a hide needs a Tanning Rack. The patches are laid out along the scraper's one pass. */
    private static Optional<Session> beginScraping(ServerPlayer player, ServerLevel level, BlockPos pos, long now) {
        float width = SCRAPE_WIDTH + CraftPerks.sweetSpotBonus(player, CraftSkill.LEATHERWORKING);
        Session session = new Session(TimingBar.Kind.SCRAPE, pos.immutable(), now, SCRAPE_PASS, false, false, 1, SCRAPE_PATCHES, width);
        session.bar++;
        session.values = new float[SCRAPE_PATCHES * 2];
        // Each patch gets its own stretch of the pass, so they never overlap and come one after another.
        float stretch = 0.8f / SCRAPE_PATCHES;
        for (int i = 0; i < SCRAPE_PATCHES; i++) {
            float from = 0.15f + i * stretch;
            float center = from + width / 2 + level.getRandom().nextFloat() * Math.max(0.0f, stretch - width);
            session.values[i * 2] = center - width / 2;
            session.values[i * 2 + 1] = center + width / 2;
        }
        return started(player, level, session, SoundEvents.ARMOR_EQUIP_LEATHER.value());
    }

    private static Optional<Session> started(ServerPlayer player, ServerLevel level, Session session, SoundEvent sound) {
        SESSIONS.put(player.getUUID(), session);
        send(player, session);
        level.playSound(null, session.station, sound, SoundSource.BLOCKS, 0.4f, 1.2f);
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

    /** Sets up the next stitch: the thread starts somewhere new in its bob, so the moments it lines up with the eye move. */
    private static void nextStitch(Session session, ServerLevel level, long now) {
        session.bar++;
        session.start = now;
        session.values = new float[] { THREAD_PERIOD, level.getRandom().nextFloat() * THREAD_PERIOD, session.baseWidth };
    }

    /** The sweep's period (ticks there and back): striking cooler metal sweeps faster, up to {@link #COLD_SPEEDUP} faster. */
    public static int periodFor(TimingBar.Kind kind, int basePeriod, float heat) {
        if (kind != TimingBar.Kind.STRIKE) {
            return basePeriod;
        }
        return Math.max(MIN_PERIOD, Math.round(basePeriod * (1.0f - COLD_SPEEDUP * (1.0f - heat))));
    }

    /** A hot ingot's heat for this player (Slow Cooling slows it); 1 for anything that isn't hot metal. */
    private static float heatOf(Player player, ItemStack stack, long now) {
        return stack.getItem() instanceof HotMetalItem ? HotMetalItem.temperature(stack, now, ForgingPerks.coolTicks(player)) : 1.0f;
    }

    // --- Strikes ---

    /**
     * How long the minigame had been running when the player struck, as the client timed it, kept to what's believable.
     * The client's game starts when it reaches the client (half a ping after the server sent it) and the strike reaches
     * the server half a ping after the click, so the client's time should trail the server's by about one ping. A time
     * far outside that (more than {@link #TIMING_SLACK} ticks either way) is pulled back into it.
     */
    public static float checkedElapsed(Session session, long now, float reported, float pingTicks) {
        float serverElapsed = now - session.start;
        float earliest = serverElapsed - pingTicks - TIMING_SLACK;
        float latest = serverElapsed + TIMING_SLACK;
        return Math.clamp(reported, Math.max(0.0f, earliest), latest);
    }

    /** A strike reported by the client: which bar and strike it was for, and how long that bar had been running on the client. */
    static void onStrike(ServerPlayer player, int bar, int index, float elapsed) {
        Session session = SESSIONS.get(player.getUUID());
        ItemStack held = player.getMainHandItem();
        if (session == null || session.bar != bar || !worksOn(session.kind, held)) {
            return;
        }
        if (session.kind == TimingBar.Kind.SCRAPE && index != session.done) {
            return; // A patch already counted.
        }
        long now = player.level().getGameTime();
        float ticks = checkedElapsed(session, now, elapsed, player.connection.latency() / 50.0f);
        ServerLevel level = (ServerLevel) player.level();
        switch (session.kind) {
            case WEAVE -> land(player, level, session, held, now,
                    TimingBar.judgeStitch(ticks, session.period, session.values[0], session.values[1], session.values[2]));
            case SCRAPE -> strike(player, level, session, held, now, TimingBar.pass(ticks, session.period));
            default -> strike(player, level, session, held, now, TimingBar.marker(ticks, session.period));
        }
    }

    /** A smithing or scraping strike at game time {@code now}, with the marker at {@code marker}. */
    public static void strike(ServerPlayer player, ServerLevel level, Session session, ItemStack held, long now, float marker) {
        land(player, level, session, held, now, TimingBar.judge(marker, session.spotLow(), session.spotHigh()));
    }

    /** A strike landed: count it, and see whether the piece survives it. */
    public static void land(ServerPlayer player, ServerLevel level, Session session, ItemStack held, long now, TimingBar.Hit hit) {
        float heat = heatOf(player, held, now);
        if (heat <= 0.0f) {
            end(player);
            tell(player, Component.translatable("message.dungeondelving.forge.cold").withStyle(ChatFormatting.RED));
            return;
        }
        BlockPos pos = session.station;
        TimingBar.Kind kind = session.kind;
        CraftSkill skill = skillFor(kind);
        SoundEvent sound = switch (kind) {
            case WEAVE -> SoundEvents.UI_LOOM_TAKE_RESULT;
            case SCRAPE -> SoundEvents.ARMOR_EQUIP_LEATHER.value();
            case GRIND -> SoundEvents.GRINDSTONE_USE;
            default -> SoundEvents.ANVIL_USE;
        };
        float chance = 0.0f;
        switch (hit) {
            case GOLD -> {
                // Gold counts twice, except on a scraping pass, where each patch is scraped once however well.
                session.done += kind == TimingBar.Kind.SCRAPE ? 1 : 2;
                CraftingSkills.addXp(player, skill, 5);
                level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.7f, 1.6f);
                level.sendParticles(ParticleTypes.WAX_OFF, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 10, 0.2, 0.1, 0.2, 0.1);
                tell(player, Component.translatable(kind == TimingBar.Kind.SCRAPE ? "message.dungeondelving.forge.perfect"
                        : "message.dungeondelving.forge.critical").withStyle(ChatFormatting.GOLD));
            }
            case GREEN -> {
                session.done++;
                CraftingSkills.addXp(player, skill, 3);
                level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.6f, 1.3f);
                level.sendParticles(kind == TimingBar.Kind.STRIKE ? ParticleTypes.LAVA : ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 1.05,
                        pos.getZ() + 0.5, 6, 0.15, 0.05, 0.15, 0.05);
                tell(player, Component.translatable("message.dungeondelving.forge.good").withStyle(ChatFormatting.GREEN));
            }
            case MISS -> {
                session.done++;
                session.misses++;
                // A missed grinding pass doesn't break the ingot outright: it leaves it unstable (see the end of the bar).
                chance = kind == TimingBar.Kind.GRIND ? 0.0f : missChance(session.misses);
                CraftingSkills.addXp(player, skill, 1);
                level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.6f, 0.7f);
            }
        }
        float failMultiplier = failMultiplier(player, kind);
        chance = Math.min(MAX_FAIL, chance * failMultiplier);
        if (chance > 0.0f && level.getRandom().nextFloat() < chance) {
            end(player);
            fail(player, level, session, held);
            return;
        }
        if (hit == TimingBar.Hit.MISS && kind == TimingBar.Kind.GRIND) {
            tell(player, Component.translatable(session.unstable ? "message.dungeondelving.forge.grind_miss_unstable" : "message.dungeondelving.forge.grind_miss")
                    .withStyle(ChatFormatting.YELLOW));
        } else if (hit == TimingBar.Hit.MISS) {
            int nextRisk = Math.round(100 * Math.min(MAX_FAIL, missChance(session.misses + 1) * failMultiplier));
            tell(player, Component.translatable("message.dungeondelving.forge.off", nextRisk).withStyle(ChatFormatting.YELLOW));
        }
        session.lastAction = now;
        if (session.done < session.needed) {
            switch (kind) {
                case WEAVE -> nextStitch(session, level, now);
                case SCRAPE -> {} // The same pass carries on to the next patch.
                default -> nextSpot(session, heat, level, now);
            }
            send(player, session);
            return;
        }
        end(player);
        finishBar(player, level, session, held);
    }

    /** The skill a minigame trains. */
    static CraftSkill skillFor(TimingBar.Kind kind) {
        return switch (kind) {
            case WEAVE -> CraftSkill.WEAVING;
            case SCRAPE -> CraftSkill.LEATHERWORKING;
            default -> CraftSkill.FORGING;
        };
    }

    /** Scales the chance a miss ruins the piece, by the player's perks in that minigame's skill. */
    private static float failMultiplier(Player player, TimingBar.Kind kind) {
        CraftSkill skill = skillFor(kind);
        return skill == CraftSkill.FORGING ? ForgingPerks.crackMultiplier(player) : CraftPerks.failMultiplier(player, skill);
    }

    /** The chance the {@code misses}th miss in a minigame ruins the piece, before skill: it climbs with every miss. */
    public static float missChance(int misses) {
        return MISS_FAIL + MISS_STEP * (misses - 1);
    }

    /** A minigame finished without failing outright: make what it was for. */
    private static void finishBar(ServerPlayer player, ServerLevel level, Session session, ItemStack held) {
        switch (session.kind) {
            case WEAVE -> {
                held.shrink(WEAVE_STRING);
                give(player, new ItemStack(Forging.WOVEN_CLOTH.get()));
                CraftingSkills.addXp(player, CraftSkill.WEAVING, 5);
                level.playSound(null, session.station, SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.BLOCKS, 1.0f, 1.0f);
                tell(player, Component.translatable("message.dungeondelving.weave.done").withStyle(ChatFormatting.GOLD));
            }
            case SCRAPE -> {
                held.shrink(1);
                give(player, new ItemStack(Forging.REFINED_LEATHER.get()));
                CraftingSkills.addXp(player, CraftSkill.LEATHERWORKING, 5);
                level.playSound(null, session.station, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 1.0f, 0.8f);
                tell(player, Component.translatable("message.dungeondelving.scrape.done").withStyle(ChatFormatting.GOLD));
            }
            default -> {
                boolean failedGrind = session.kind == TimingBar.Kind.GRIND && session.misses > 0;
                if (failedGrind && session.unstable) {
                    fail(player, level, session, held); // Its second chance, missed.
                    return;
                }
                if (!failedGrind && session.basicStation && level.getRandom().nextFloat() < BASIC_STATION_CRACK) {
                    tell(player, Component.translatable("message.dungeondelving.forge.basic_station").withStyle(ChatFormatting.RED));
                    fail(player, level, session, held);
                    return;
                }
                finishSmithing(player, level, session, held, failedGrind);
            }
        }
    }

    /**
     * Turns the last strike's piece into the next stage: a struck ingot, a Tempered Ingot, or (after a grind with a
     * miss) an Unstable Ingot, which gets one more try at the grindstone.
     */
    private static void finishSmithing(ServerPlayer player, ServerLevel level, Session session, ItemStack held, boolean failedGrind) {
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

    /** The piece is ruined: metal cracks into nuggets, thread snaps (one string is saved), a hide tears into scraps. */
    private static void fail(ServerPlayer player, ServerLevel level, Session session, ItemStack held) {
        BlockPos pos = session.station;
        switch (session.kind) {
            case WEAVE -> {
                held.shrink(WEAVE_STRING);
                give(player, new ItemStack(Items.STRING));
                level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.4f);
                tell(player, Component.translatable("message.dungeondelving.weave.snapped").withStyle(ChatFormatting.RED));
            }
            case SCRAPE -> {
                held.shrink(1);
                give(player, new ItemStack(Forging.LEATHER_SCRAPS.get()));
                level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 0.7f);
                tell(player, Component.translatable("message.dungeondelving.scrape.torn").withStyle(ChatFormatting.RED));
            }
            default -> crack(player, level, pos, held);
        }
    }

    /** Puts away scraping passes that ran out without the client reporting every patch (patches never struck count as missed). */
    private static void finishOverdueScrape(ServerPlayer player, Session session, long now) {
        if (session.kind != TimingBar.Kind.SCRAPE || now - session.start <= session.period + TIMING_SLACK * 4) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        while (SESSIONS.get(player.getUUID()) == session) {
            land(player, level, session, player.getMainHandItem(), now, TimingBar.Hit.MISS);
        }
    }

    // --- Salvage ---

    /**
     * Grinds a piece of forged gear back down to tempered ingots of its ★: some of the metal it took (see
     * {@link ForgingPerks#salvageShare}), less for worn gear, with the leftover as iron nuggets.
     */
    public static void salvage(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack held) {
        if (ForgeWorkshop.around(level, pos).isEmpty()) {
            tell(player, Component.translatable("message.dungeondelving.forge.no_workshop").withStyle(ChatFormatting.RED));
            return;
        }
        float condition = held.isDamageableItem() ? 1.0f - (float) held.getDamageValue() / held.getMaxDamage() : 1.0f;
        float metal = ForgedGear.ingotCost(held) * ForgingPerks.salvageShare(player) * condition;
        int ingots = (int) metal;
        int nuggets = Math.round((metal - ingots) * 9);
        int stars = Forging.stars(held);
        held.shrink(1);
        if (ingots > 0) {
            ItemStack tempered = new ItemStack(Forging.TEMPERED_IRON_INGOT.get(), ingots);
            tempered.set(Forging.STARS.get(), stars);
            give(player, tempered);
        }
        if (nuggets > 0) {
            give(player, new ItemStack(Items.IRON_NUGGET, nuggets));
        }
        CraftingSkills.addXp(player, CraftSkill.FORGING, 5);
        level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0f, 0.8f);
        tell(player, Component.translatable("message.dungeondelving.forge.salvaged", ingots, StarredItem.starsText(stars), nuggets)
                .withStyle(ChatFormatting.GOLD));
    }

    // --- Boiling leather ---

    /** Whether there's heat under a cauldron: a lit campfire, fire, magma or lava. */
    public static boolean heatedFromBelow(Level level, BlockPos cauldron) {
        BlockState below = level.getBlockState(cauldron.below());
        return CampfireBlock.isLitCampfire(below) || below.is(BlockTags.FIRE) || below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.LAVA);
    }

    /** Boils refined leather in a water cauldron over heat, toughening it for hardened leather. Uses a level of water. */
    public static void boil(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, ItemStack held) {
        if (!heatedFromBelow(level, pos)) {
            tell(player, Component.translatable("message.dungeondelving.boil.no_heat").withStyle(ChatFormatting.RED));
            return;
        }
        if (level.getRandom().nextFloat() >= CraftPerks.boilSavesWater(player)) {
            LayeredCauldronBlock.lowerFillLevel(state, level, pos);
        }
        held.shrink(1);
        give(player, new ItemStack(Forging.BOILED_LEATHER.get()));
        CraftingSkills.addXp(player, CraftSkill.LEATHERWORKING, 3);
        level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.8f, 1.0f);
        level.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 10, 0.25, 0.1, 0.25, 0.02);
    }

    // --- Quenching ---

    public static void quench(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, ItemStack held) {
        if (ForgeWorkshop.around(level, pos).isEmpty()) {
            tell(player, Component.translatable("message.dungeondelving.forge.no_workshop").withStyle(ChatFormatting.RED));
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
        int nuggets = ForgingPerks.scrapNuggets(player);
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
        List<Float> values = new ArrayList<>();
        if (session.kind == TimingBar.Kind.STRIKE || session.kind == TimingBar.Kind.GRIND) {
            values.add(session.low);
            values.add(session.high);
        } else {
            for (float value : session.values) {
                values.add(value);
            }
        }
        PacketDistributor.sendToPlayer(player, new TimingBarPayload(session.kind.ordinal(), session.bar, session.period, values, session.done,
                session.needed));
    }

    /** Puts away a player's minigame. */
    public static void end(ServerPlayer player) {
        if (SESSIONS.remove(player.getUUID()) != null) {
            PacketDistributor.sendToPlayer(player, TimingBarPayload.HIDE);
        }
    }

    /** A player's minigame in progress, if any. For tests. */
    public static Optional<Session> session(Player player) {
        return Optional.ofNullable(SESSIONS.get(player.getUUID()));
    }

    /** Puts away minigames that were left alone, or whose piece left the player's hand, and finishes overdue scraping passes. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty() || event.getServer().getTickCount() % 10 != 0) {
            return;
        }
        for (Map.Entry<UUID, Session> entry : List.copyOf(SESSIONS.entrySet())) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null && worksOn(entry.getValue().kind, player.getMainHandItem())) {
                finishOverdueScrape(player, entry.getValue(), player.level().getGameTime());
            }
        }
        Iterator<Map.Entry<UUID, Session>> sessions = SESSIONS.entrySet().iterator();
        while (sessions.hasNext()) {
            Map.Entry<UUID, Session> entry = sessions.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();
            boolean holding = player != null && worksOn(session.kind, player.getMainHandItem());
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
