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

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
