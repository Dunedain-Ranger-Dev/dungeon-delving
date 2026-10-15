package io.github.ranger3462.dungeondelving;

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

    public static final ModConfigSpec.DoubleValue LOOT_MARKER_CHANCE = BUILDER
            .comment("Chance that each loot marker in a dungeon becomes a chest. Otherwise it's left empty.")
            .defineInRange("lootMarkerChance", 0.6, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue RESOURCE_MARKER_CHANCE = BUILDER
            .comment("Chance that each resource marker becomes an ore from the dungeondelving:dungeon_resources block tag.")
            .defineInRange("resourceMarkerChance", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.IntValue MAX_MOBS_PER_MARKER = BUILDER
            .comment("Each mob marker spawns between 1 and this many mobs from the dungeondelving:dungeon_mobs entity tag.")
            .defineInRange("maxMobsPerMarker", 3, 1, 16);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
