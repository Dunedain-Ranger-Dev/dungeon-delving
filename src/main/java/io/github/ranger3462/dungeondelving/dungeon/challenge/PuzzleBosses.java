package io.github.ranger3462.dungeondelving.dungeon.challenge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.RoomSpec;
import io.github.ranger3462.dungeondelving.dungeon.SpawnerMobs;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Bosses with a puzzle (DD-58):
 * <ul>
 * <li><b>The Undead Dwarf Archivist</b> (undead, tier 1) waits at his ledgers while players work his puzzle. Failing it
 * (a Puzzle Trap is powered, or the stock puzzle gets a wrong answer), running out his patience ({@link #ARCHIVIST_PATIENCE_TICKS})
 * or attacking him wakes him at full strength. Solving it humbles him: he still fights, but weakened, and drops his
 * better loot ({@code bosses/undead_dwarf_archivist_solved}).</li>
 * <li><b>The Treasure Vault</b> (any faction, tier 2) has no boss at all: solving its puzzle opens the vault (a chest
 * of {@code bosses/treasure_vault}) and the exit. A wrong answer calls guards of the dungeon's faction.</li>
 * </ul>
 * A boss arena from builders brings its own puzzle: redstone into a Puzzle Lock (solved) and Puzzle Traps (failed).
 * Without one, a stock puzzle is built in the boss room: a row of levers under numbered signs, a stone button to
 * answer, and a lectern whose book says which levers to raise.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class PuzzleBosses {
    public static final String ARCHIVIST = "undead_dwarf_archivist";
    public static final String TREASURE_VAULT = "treasure_vault";
    /** How long the Archivist waits for his puzzle to be solved: 3 minutes. */
    public static final int ARCHIVIST_PATIENCE_TICKS = 3 * 60 * 20;
    /** Guards a wrong answer calls to the Treasure Vault, at most once per {@link #GUARD_COOLDOWN_TICKS}. */
    public static final int VAULT_GUARDS = 3;
    public static final int GUARD_COOLDOWN_TICKS = 10 * 20;
    /** Saved on the Archivist: whether he's {@link #WAITING}, {@link #ANGERED} or {@link #HUMBLED}. */
    public static final String STATE = DungeonDelving.MODID + ":puzzle_state";
    public static final String WAITING = "waiting";
    public static final String ANGERED = "angered";
    public static final String HUMBLED = "humbled";
    /** How much of his health and armor a humbled Archivist keeps. */
    public static final double HUMBLED_SHARE = 0.5;
    private static final String DEADLINE = DungeonDelving.MODID + ":puzzle_deadline";
    private static final String[] NUMERALS = { "I", "II", "III", "IV", "V", "VI" };
    private static final String[] ORDINALS = { "first", "second", "third", "fourth", "fifth", "sixth" };

    /** Waiting Archivists, for their patience running out. */
    private record Waiting(ResourceKey<Level> dimension, long deadline) {}

    private static final Map<UUID, Waiting> WAITING_ARCHIVISTS = new HashMap<>();

    private PuzzleBosses() {}

    public static boolean isPuzzleBoss(String boss) {
        return ARCHIVIST.equals(boss) || TREASURE_VAULT.equals(boss);
    }

    /**
     * Sets up a puzzle boss's room as its door opens: the Archivist starts waiting, and if the room has no Puzzle Lock of
     * its own, a stock puzzle is built. {@code mob} is the Archivist, or null for the Treasure Vault.
     */
    public static void setUp(ServerLevel level, BlockPos pos, int tier, int region, String boss, Mob mob) {
        CompoundTag puzzle = progress(region).map(DungeonProgress::bossPuzzle).orElseGet(CompoundTag::new);
        puzzle.putString("boss", boss);
        puzzle.putInt("tier", tier);
        puzzle.putLong("spawn", pos.asLong());
        if (mob != null) {
            mob.setNoAi(true);
            mob.getPersistentData().putString(STATE, WAITING);
            mob.getPersistentData().putLong(DEADLINE, level.getGameTime() + ARCHIVIST_PATIENCE_TICKS);
            puzzle.putUUID("archivist", mob.getUUID());
            WAITING_ARCHIVISTS.put(mob.getUUID(), new Waiting(level.dimension(), level.getGameTime() + ARCHIVIST_PATIENCE_TICKS));
        }
        if (!hasPuzzleLock(level, pos)) {
            buildStockPuzzle(level, pos, tier, boss, puzzle, level.getRandom());
        }
        DungeonRegions.get().progressChanged();
        DungeonChallenges.tellRegion(level, region, Component.translatable(ARCHIVIST.equals(boss)
                ? "message.dungeondelving.archivist_waits" : "message.dungeondelving.vault_sealed").withStyle(ChatFormatting.GOLD));
    }

    private static Optional<DungeonProgress> progress(int region) {
        return region < 0 ? Optional.empty() : DungeonRegions.get().progress(region);
    }

    private static boolean hasPuzzleLock(ServerLevel level, BlockPos pos) {
        BlockPos min = DungeonDimension.roomMinAt(pos);
        for (BlockPos at : BlockPos.betweenClosed(min, min.offset(RoomSpec.ROOM_SIZE - 1, RoomSpec.ROOM_HEIGHT - 1, RoomSpec.ROOM_SIZE - 1))) {
            if (level.getBlockState(at).is(ModRegistries.PUZZLE_LOCK.get())) {
                return true;
            }
        }
        return false;
    }

    /** How many levers a stock puzzle has at a tier. */
    public static int levers(int tier) {
        return Math.min(NUMERALS.length, tier + 3);
    }

    /**
     * Builds the stock puzzle a few blocks north of the boss's spot: a lectern, then a row of levers on pedestals under
     * numbered signs, then a stone button to answer. Remembers which levers are the answer, chosen at random.
     */
    static void buildStockPuzzle(ServerLevel level, BlockPos pos, int tier, String boss, CompoundTag puzzle, RandomSource random) {
        int count = levers(tier);
        int answer = 1 + random.nextInt((1 << count) - 2); // At least one lever up and one down.
        BlockPos start = pos.offset(-count, 0, -5);
        long[] leverPositions = new long[count];
        for (int i = 0; i < count; i++) {
            BlockPos pedestal = start.offset(2 * i, 0, 0);
            clear(level, pedestal);
            level.setBlock(pedestal, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Block.UPDATE_ALL);
            BlockPos lever = pedestal.above();
            level.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
                    .setValue(LeverBlock.FACING, Direction.SOUTH).setValue(LeverBlock.POWERED, false), Block.UPDATE_ALL);
            leverPositions[i] = lever.asLong();
            sign(level, pedestal.south(), NUMERALS[i]);
        }
        BlockPos buttonPedestal = start.offset(2 * count, 0, 0);
        clear(level, buttonPedestal);
        level.setBlock(buttonPedestal, Blocks.CHISELED_DEEPSLATE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos button = buttonPedestal.above();
        level.setBlock(button, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR)
                .setValue(ButtonBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        sign(level, buttonPedestal.south(), "Answer");
        BlockPos lectern = start.offset(-2, 0, 0);
        clear(level, lectern);
        level.setBlock(lectern, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH).setValue(LecternBlock.HAS_BOOK, true),
                Block.UPDATE_ALL);
        if (level.getBlockEntity(lectern) instanceof LecternBlockEntity book) {
            book.setBook(clueBook(boss, count, answer));
        }
        puzzle.put("levers", new LongArrayTag(leverPositions));
        puzzle.putInt("answer", answer);
        puzzle.putLong("button", button.asLong());
    }

    private static void clear(ServerLevel level, BlockPos pedestal) {
        for (BlockPos at : List.of(pedestal, pedestal.above(), pedestal.above(2), pedestal.south(), pedestal.south().above())) {
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void sign(ServerLevel level, BlockPos pos, String text) {
        level.setBlock(pos, Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            sign.setText(sign.getFrontText().setMessage(1, Component.literal(text)), true);
            sign.setWaxed(true);
        }
    }

    /** The book on the stock puzzle's lectern, telling which levers to raise. */
    static ItemStack clueBook(String boss, int count, int answer) {
        List<String> raised = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if ((answer & (1 << i)) != 0) {
                raised.add(ORDINALS[i]);
            }
        }
        String which = raised.size() == 1 ? "the " + raised.get(0) : "the " + String.join(", the ", raised.subList(0, raised.size() - 1)) + " and the "
                + raised.get(raised.size() - 1);
        boolean archivist = ARCHIVIST.equals(boss);
        String page = (archivist ? "Of my " : "Of the vault's ") + count + " seals, raise " + which + ". Leave the rest down, then press the stone to answer."
                + (archivist ? " Answer wrongly, or keep me waiting, and you will regret it." : " A wrong answer will be heard.");
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(archivist ? "The Archivist's Ledger" : "The Vault's Seal"),
                archivist ? "The Archivist" : "Unknown", 0, List.of(Filterable.passThrough(Component.literal(page).withStyle(Style.EMPTY))), true));
        return book;
    }

    /** The stock puzzle's answer: which of its levers are up, one bit each. */
    static int leversUp(ServerLevel level, CompoundTag puzzle) {
        long[] levers = puzzle.getLongArray("levers");
        int up = 0;
        for (int i = 0; i < levers.length; i++) {
            BlockState state = level.getBlockState(BlockPos.of(levers[i]));
            if (state.is(Blocks.LEVER) && state.getValue(BlockStateProperties.POWERED)) {
                up |= 1 << i;
            }
        }
        return up;
    }

    /** Pressing the stock puzzle's button answers it. */
    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != DungeonDimension.DUNGEON
                || !level.getBlockState(event.getPos()).is(Blocks.STONE_BUTTON)) {
            return;
        }
        int region = DungeonDimension.regionAt(event.getPos());
        progress(region).map(DungeonProgress::bossPuzzle).filter(puzzle -> puzzle.contains("button") && puzzle.getLong("button") == event.getPos().asLong())
                .ifPresent(puzzle -> answer(level, region, puzzle));
    }

    /** Checks the stock puzzle's levers against its answer. */
    public static void answer(ServerLevel level, int region, CompoundTag puzzle) {
        if (puzzle.getBoolean("solved")) {
            return;
        }
        if (leversUp(level, puzzle) == puzzle.getInt("answer")) {
            solve(level, region, puzzle);
        } else {
            fail(level, region, puzzle);
        }
    }

    /** A Puzzle Lock in a puzzle boss's room was powered: the puzzle is solved. False if it isn't a puzzle boss's room. */
    public static boolean solved(ServerLevel level, BlockPos lock) {
        int region = DungeonDimension.regionAt(lock);
        Optional<CompoundTag> puzzle = bossRoomPuzzle(region, lock);
        puzzle.ifPresent(found -> solve(level, region, found));
        return puzzle.isPresent();
    }

    /** A Puzzle Trap was powered: if it's in a puzzle boss's room, the puzzle is failed. */
    public static void failed(ServerLevel level, BlockPos trap) {
        if (level.dimension() != DungeonDimension.DUNGEON) {
            return;
        }
        int region = DungeonDimension.regionAt(trap);
        bossRoomPuzzle(region, trap).ifPresent(puzzle -> fail(level, region, puzzle));
    }

    private static Optional<CompoundTag> bossRoomPuzzle(int region, BlockPos pos) {
        return progress(region).map(DungeonProgress::bossPuzzle).filter(puzzle -> puzzle.contains("spawn")
                && DungeonDimension.roomMinAt(BlockPos.of(puzzle.getLong("spawn"))).equals(DungeonDimension.roomMinAt(pos)));
    }

    static void solve(ServerLevel level, int region, CompoundTag puzzle) {
        if (puzzle.getBoolean("solved")) {
            return;
        }
        puzzle.putBoolean("solved", true);
        DungeonRegions.get().progressChanged();
        BlockPos spawn = BlockPos.of(puzzle.getLong("spawn"));
        level.playSound(null, spawn, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.0f, 1.0f);
        if (TREASURE_VAULT.equals(puzzle.getString("boss"))) {
            openVault(level, region, puzzle.getInt("tier"), spawn);
        } else if (archivist(level, puzzle) instanceof Mob archivist) {
            humble(archivist);
            DungeonChallenges.tellRegion(level, region, Component.translatable("message.dungeondelving.archivist_humbled").withStyle(ChatFormatting.GOLD));
        }
    }

    static void fail(ServerLevel level, int region, CompoundTag puzzle) {
        if (puzzle.getBoolean("solved")) {
            return;
        }
        BlockPos spawn = BlockPos.of(puzzle.getLong("spawn"));
        if (TREASURE_VAULT.equals(puzzle.getString("boss"))) {
            if (puzzle.contains("guards_at") && level.getGameTime() < puzzle.getLong("guards_at") + GUARD_COOLDOWN_TICKS) {
                return;
            }
            puzzle.putLong("guards_at", level.getGameTime());
            DungeonRegions.get().progressChanged();
            callGuards(level, region, puzzle.getInt("tier"), spawn);
            DungeonChallenges.tellRegion(level, region, Component.translatable("message.dungeondelving.vault_guards").withStyle(ChatFormatting.RED));
        } else if (archivist(level, puzzle) instanceof Mob archivist && anger(archivist)) {
            DungeonChallenges.tellRegion(level, region, Component.translatable("message.dungeondelving.archivist_angered").withStyle(ChatFormatting.RED));
        }
        level.playSound(null, spawn, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    private static Entity archivist(ServerLevel level, CompoundTag puzzle) {
        return puzzle.hasUUID("archivist") ? level.getEntity(puzzle.getUUID("archivist")) : null;
    }

    /** Wakes a waiting Archivist at full strength. False if he was already awake. */
    public static boolean anger(Mob archivist) {
        if (!WAITING.equals(archivist.getPersistentData().getString(STATE))) {
            return false;
        }
        archivist.getPersistentData().putString(STATE, ANGERED);
        wake(archivist);
        return true;
    }

    /** Weakens the Archivist (once), switches his loot to the solved table, and wakes him if he was waiting. */
    public static void humble(Mob archivist) {
        if (HUMBLED.equals(archivist.getPersistentData().getString(STATE))) {
            return;
        }
        archivist.getPersistentData().putString(STATE, HUMBLED);
        archivist.getPersistentData().getCompound(DungeonBosses.BOSS_TAG).putString(DungeonBosses.LOOT_OVERRIDE, ARCHIVIST + "_solved");
        scale(archivist, Attributes.MAX_HEALTH);
        scale(archivist, Attributes.ARMOR);
        archivist.setHealth(Math.min(archivist.getHealth(), archivist.getMaxHealth()));
        wake(archivist);
    }

    private static void scale(Mob mob, Holder<Attribute> attribute) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * HUMBLED_SHARE);
        }
    }

    private static void wake(Mob archivist) {
        WAITING_ARCHIVISTS.remove(archivist.getUUID());
        archivist.setNoAi(false);
        if (archivist.level().getNearestPlayer(archivist, 32) instanceof ServerPlayer target && !target.isCreative() && !target.isSpectator()) {
            archivist.setTarget(target);
        }
    }

    /** Opens the Treasure Vault: a chest of its loot where the boss would stand, and the exit. */
    private static void openVault(ServerLevel level, int region, int tier, BlockPos spawn) {
        level.setBlock(spawn, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(spawn) instanceof ChestBlockEntity chest) {
            chest.setLootTable(DungeonBosses.lootTable(TREASURE_VAULT, tier), level.getRandom().nextLong());
        }
        DungeonBosses.defeated(level, region, tier, spawn, Component.translatable("entity.dungeondelving." + TREASURE_VAULT));
    }

    /** A wrong answer at the vault: guards of the dungeon's faction around it. */
    private static void callGuards(ServerLevel level, int region, int tier, BlockPos spawn) {
        String faction = progress(region).map(DungeonProgress::faction).orElse(null);
        RandomSource random = level.getRandom();
        for (int i = 0; i < VAULT_GUARDS; i++) {
            double angle = Math.PI * 2 * i / VAULT_GUARDS;
            BlockPos at = BlockPos.containing(spawn.getX() + 0.5 + Math.cos(angle) * 4, spawn.getY(), spawn.getZ() + 0.5 + Math.sin(angle) * 4);
            SpawnerMobs.pick(level, random, faction, tier).ifPresent(type -> {
                if (type.value().spawn(level, at, MobSpawnType.EVENT) instanceof Mob guard
                        && level.getNearestPlayer(guard, 32) instanceof ServerPlayer target && !target.isCreative()) {
                    guard.setTarget(target);
                }
            });
        }
    }

    /** Attacking a waiting Archivist wakes him. */
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Mob mob && WAITING.equals(mob.getPersistentData().getString(STATE))
                && event.getSource().getEntity() instanceof Player && mob.level() instanceof ServerLevel level && anger(mob)) {
            int region = mob.getPersistentData().getCompound(DungeonBosses.BOSS_TAG).getInt("region");
            DungeonChallenges.tellRegion(level, region, Component.translatable("message.dungeondelving.archivist_angered").withStyle(ChatFormatting.RED));
        }
    }

    /** A waiting Archivist loaded back in still has his deadline. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob && WAITING.equals(mob.getPersistentData().getString(STATE))) {
            WAITING_ARCHIVISTS.put(mob.getUUID(), new Waiting(event.getLevel().dimension(), mob.getPersistentData().getLong(DEADLINE)));
        }
    }

    /** Archivists whose patience has run out wake up. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (WAITING_ARCHIVISTS.isEmpty() || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        for (Map.Entry<UUID, Waiting> entry : List.copyOf(WAITING_ARCHIVISTS.entrySet())) {
            ServerLevel level = event.getServer().getLevel(entry.getValue().dimension());
            if (level == null || level.getGameTime() < entry.getValue().deadline()) {
                continue;
            }
            if (level.getEntity(entry.getKey()) instanceof Mob archivist && anger(archivist)) {
                int region = archivist.getPersistentData().getCompound(DungeonBosses.BOSS_TAG).getInt("region");
                DungeonChallenges.tellRegion(level, region, Component.translatable("message.dungeondelving.archivist_impatient").withStyle(ChatFormatting.RED));
            } else {
                WAITING_ARCHIVISTS.remove(entry.getKey());
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WAITING_ARCHIVISTS.clear();
    }
}
