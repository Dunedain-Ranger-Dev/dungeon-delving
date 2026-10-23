package io.github.ranger3462.dungeondelving;

import java.util.List;

import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.RoomCounts;
import io.github.ranger3462.dungeondelving.dungeon.RoomMobCap;
import io.github.ranger3462.dungeondelving.dungeon.SpawnerMobs;
import io.github.ranger3462.dungeondelving.dungeon.layout.LayoutType;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Settings in {@code config/dungeondelving-common.toml}. */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> EXPORT_COPY_FOLDER = BUILDER
            .comment("Folder that /dungeondelving room export also copies each exported piece into, such as a shared",
                    "drive folder or a clone of the mod's repository (src/main/resources/data/dungeondelving/structure/dungeon).",
                    "Leave empty to only save pieces into the world.")
            .define("exportCopyFolder", "");

    public static final ModConfigSpec.IntValue RUN_TIME_LIMIT_SECONDS = BUILDER
            .comment("How long a dungeon run lasts before time runs out, in seconds. Running out counts as failing the run",
                    "(dungeon loot is lost and gear is damaged). 0 turns the timer off.")
            .defineInRange("runTimeLimitSeconds", 0, 0, 24 * 60 * 60);

    public static final ModConfigSpec.DoubleValue FAILURE_GEAR_DAMAGE = BUILDER
            .comment("When a run fails (death or running out of time), each damageable item the player carries loses this",
                    "fraction of its max durability. Never fully breaks an item. 0 turns gear damage off.")
            .defineInRange("failureGearDamage", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ROOM_COUNTS = BUILDER
            .comment("How many rooms a dungeon has, for tiers 1, 2, 3 and 4 in order. Each entry is a range like \"4-6\"",
                    "(rolled fresh every run) or a single number. Every dungeon has at least 2 rooms: the start and the boss room.")
            .defineList("roomCounts", RoomCounts.DEFAULTS, () -> "4-6", value -> value instanceof String text && RoomCounts.parse(text).isPresent());

    public static final ModConfigSpec.ConfigValue<List<? extends String>> LAYOUT_TYPES = BUILDER
            .comment("How often each dungeon layout type is rolled, as \"type=weight\" entries. normal: mostly ordinary rooms;",
                    "resource: mostly quarries packed with ore; loot: lots of treasure rooms with extra chests.")
            .defineList("layoutTypes", LayoutType.DEFAULTS, () -> "normal=1", value -> value instanceof String text && LayoutType.parse(text).isPresent());

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> ROOM_MOB_CAPS = BUILDER
            .comment("The most hostile mobs a dungeon room's spawners keep alive at once, for tiers 1, 2, 3 and 4 in order. Once a",
                    "room holds this many, its spawners wait until some die, so rooms with several spawners can't swamp you.")
            .defineList("roomMobCaps", RoomMobCap.DEFAULTS, () -> 10, value -> value instanceof Integer cap && cap > 0);

    public static final ModConfigSpec.DoubleValue MOB_MARKER_CHANCE = BUILDER
            .comment("Chance that each mob marker in a dungeon becomes a spawner. Otherwise it's left empty. Hunt markers always do.")
            .defineInRange("mobMarkerChance", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue LOOT_MARKER_CHANCE = BUILDER
            .comment("Chance that each loot marker in a dungeon becomes a chest. Otherwise it's left empty.")
            .defineInRange("lootMarkerChance", 0.6, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue RESOURCE_MARKER_CHANCE = BUILDER
            .comment("Chance that each resource marker becomes an ore at all. Otherwise it becomes Riftstone.")
            .defineInRange("resourceMarkerChance", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue TIER_ORE_CHANCE = BUILDER
            .comment("When a resource marker becomes an ore, the chance it's the dungeon's tier ore.")
            .defineInRange("tierOreChance", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue COMMON_ORE_CHANCE = BUILDER
            .comment("When a resource marker becomes an ore, the chance it's the common dungeon ore. Whatever's left after the",
                    "tier and common chances is a vanilla ore from the dungeondelving:dungeon_resources block tag.")
            .defineInRange("commonOreChance", 0.45, 0.0, 1.0);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> GOBLIN_MOBS = mobList("goblinMobs", "goblin",
            DungeonFactions.GOBLIN_DEFAULTS);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> UNDEAD_MOBS = mobList("undeadMobs", "undead",
            DungeonFactions.UNDEAD_DEFAULTS);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ORC_MOBS = mobList("orcMobs", "orc", DungeonFactions.ORC_DEFAULTS);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENABLED_FACTIONS = BUILDER
            .comment("Which factions can hold dungeons: undead, goblins, orcs. Each dungeon rolls one of these that has a boss",
                    "for its tier. Goblins and orcs are Phase 2 and still being tested, so only the undead are on by default.")
            .defineList("enabledFactions", DungeonFactions.ENABLED_DEFAULTS, () -> DungeonFactions.UNDEAD, value -> value instanceof String);

    public static final ModConfigSpec.BooleanValue BLOCK_NATURAL_SPAWNS = BUILDER
            .comment("Stop mobs spawning naturally in dungeons (including at world generation and as patrols), so every",
                    "dungeon mob comes from a mob marker or spawner.")
            .define("blockNaturalSpawns", true);

    /** A faction's spawner mob list. */
    private static ModConfigSpec.ConfigValue<List<? extends String>> mobList(String key, String faction, List<String> defaults) {
        return BUILDER.comment("Which mobs spawners spawn in " + faction + " dungeons, as \"mob=weight\" entries: higher weights spawn more",
                "often. A mob is an entity id, or an entity tag starting with #. Add @tiers to limit an entry, e.g. \"...=20@2-4\".",
                "Mobs that aren't installed are skipped (the goblin shaman needs Iron's Spells).")
                .defineList(key, defaults, () -> "minecraft:zombie=1", value -> value instanceof String text && SpawnerMobs.parse(text).isPresent());
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}

    /**
     * Moves mob lists still set to an old default onto the current one. A config file keeps whatever defaults it was
     * written with, so without this a changed default never reaches anyone who has run the mod before.
     */
    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC || event instanceof ModConfigEvent.Unloading) {
            return;
        }
        if (DungeonFactions.OLD_GOBLIN_DEFAULTS.contains(List.copyOf(GOBLIN_MOBS.get()))) {
            GOBLIN_MOBS.set(DungeonFactions.GOBLIN_DEFAULTS);
            GOBLIN_MOBS.save();
        }
    }
}
