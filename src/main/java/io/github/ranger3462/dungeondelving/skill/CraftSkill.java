package io.github.ranger3462.dungeondelving.skill;

import java.util.Arrays;
import java.util.Optional;

import net.minecraft.network.chat.Component;

/** The crafting skills. Each player has XP and a level in each one (see {@link CraftingSkills}). */
public enum CraftSkill {
    FORGING("forging"),
    LEATHERWORKING("leatherworking"),
    WEAVING("weaving");

    public final String id;

    CraftSkill(String id) {
        this.id = id;
    }

    public Component displayName() {
        return Component.translatable("skill.dungeondelving." + id);
    }

    public static Optional<CraftSkill> byId(String id) {
        return Arrays.stream(values()).filter(skill -> skill.id.equals(id)).findFirst();
    }
}
